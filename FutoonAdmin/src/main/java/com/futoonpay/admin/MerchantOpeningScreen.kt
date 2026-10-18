package com.futoonpay.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.*
import com.futoonpay.admin.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class OpeningStage { FORM, REVIEW, SUBMITTING, RESULT, UNCERTAIN }
internal data class MerchantOpeningState(
    val form: NativeForm? = null, val loading: Boolean = false, val stage: OpeningStage = OpeningStage.FORM,
    val result: MerchantOpeningResult? = null, val binding: String = "", val linkLoading: Boolean = false,
    val error: String? = null, val draftInserted: Boolean = false, val inserting: Boolean = false,
)

internal class MerchantOpeningViewModel(
    private val repository: FutoonRepository,
    val chat: ChatScope,
    private val config: suspend () -> ChatToolsConfig,
    private val writeDraft: suspend (String, ChatScope) -> Unit,
) : ViewModel() {
    private val mutable = MutableStateFlow(MerchantOpeningState())
    val state = mutable.asStateFlow()
    private var submittedValues = emptyMap<String, String>()
    fun load() = viewModelScope.launch {
        if (state.value.loading || state.value.stage != OpeningStage.FORM) return@launch
        mutable.update { it.copy(loading = true, error = null) }
        try { val form = repository.merchantOpening(); mutable.update { it.copy(form = form) } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutable.update { it.copy(error = e.message ?: "开户资料读取失败") } }
        finally { mutable.update { it.copy(loading = false) } }
    }
    fun edit(name: String, value: String) {
        if (state.value.stage != OpeningStage.FORM || state.value.loading) return
        mutable.update { s -> s.copy(error = null, form = s.form?.let { form -> form.copy(fields = form.fields.map { field ->
            if (field.name == name && field.type != "hidden" && !field.readOnly) field.copy(value = value.take(4096)) else field
        }) }) }
    }
    fun review() {
        val form = state.value.form ?: return
        if (state.value.stage != OpeningStage.FORM || state.value.loading) return
        try { merchantOpeningSubmission(form, form); mutable.update { it.copy(stage = OpeningStage.REVIEW, error = null) } }
        catch (e: Exception) { mutable.update { it.copy(error = e.message) } }
    }
    fun backToForm() { if (state.value.stage == OpeningStage.REVIEW) mutable.update { it.copy(stage = OpeningStage.FORM, error = null) } }
    fun create() = viewModelScope.launch {
        val snapshot = state.value
        if (snapshot.stage != OpeningStage.REVIEW) return@launch
        val form = snapshot.form ?: return@launch
        mutable.update { it.copy(stage = OpeningStage.SUBMITTING, error = null) }
        try {
            // Template errors must be resolved before a financial account is created.
            validateOpeningTemplate(config().openingTemplate)
            val result = repository.openMerchant(form)
            when (result.outcome) {
                OpeningOutcome.REJECTED -> mutable.update { it.copy(stage = OpeningStage.FORM, error = result.message) }
                OpeningOutcome.UNCERTAIN -> mutable.update { it.copy(stage = OpeningStage.UNCERTAIN, result = result, error = result.message) }
                OpeningOutcome.CREATED -> {
                    submittedValues = form.fields.filter { !it.sensitive && it.type != "hidden" }.associate { it.name to it.value }
                    mutable.update { it.copy(stage = OpeningStage.RESULT, result = result, error = result.message.takeIf { result.key.isBlank() }, form = null) }
                    fetchBindingAndInsert()
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutable.update { it.copy(stage = OpeningStage.FORM, error = e.message ?: "开户前校验失败") } }
    }
    private suspend fun fetchBindingAndInsert() {
        val result = state.value.result ?: return
        mutable.update { it.copy(linkLoading = true) }
        try {
            val link = repository.merchantBinding(result.uid)
            mutable.update { it.copy(binding = link, error = if (result.key.isBlank()) result.message else null) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutable.update { it.copy(error = "开户已成功。绑定链接获取失败：${e.message}，可单独重取链接") } }
        finally { mutable.update { it.copy(linkLoading = false) } }
        if (state.value.binding.isNotBlank() && result.key.isNotBlank()) insertNow()
    }
    fun retryBinding() = viewModelScope.launch {
        if (state.value.stage == OpeningStage.RESULT && !state.value.linkLoading && !state.value.inserting && state.value.binding.isBlank()) fetchBindingAndInsert()
    }
    fun insert() = viewModelScope.launch { insertNow() }
    private suspend fun insertNow() {
        val snapshot = state.value
        val result = snapshot.result ?: return
        if (snapshot.stage != OpeningStage.RESULT || snapshot.inserting || snapshot.draftInserted || snapshot.binding.isBlank() || result.key.isBlank()) return
        mutable.update { it.copy(inserting = true, error = null) }
        try {
            val text = openingMessage(config().openingTemplate, result.uid, result.key, submittedValues, snapshot.binding)
            writeDraft(text, chat)
            mutable.update { it.copy(draftInserted = true) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutable.update { it.copy(error = "开户已成功，文案尚未填入：${e.message}") } }
        finally { mutable.update { it.copy(inserting = false) } }
    }
    fun cycle(): String = settlementCycle(submittedValues["settle"] ?: state.value.form?.fields?.firstOrNull { it.name == "settle" }?.value ?: "0")
}

@Composable internal fun MerchantOpeningScreen(
    model: MerchantOpeningViewModel, currentChat: ChatScope, onBack: () -> Unit, onMerchant: (String) -> Unit,
) {
    val state by model.state.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(state.stage) { listState.scrollToItem(0) }
    val sameChat = currentChat.key == model.chat.key && currentChat.dialog == model.chat.dialog && currentChat.topic == model.chat.topic
    val busy = state.stage == OpeningStage.SUBMITTING || state.linkLoading || state.inserting
    BackHandler { if (!busy) { if (state.stage == OpeningStage.REVIEW) model.backToForm() else onBack() } }
    val valid = state.form?.fields?.none { it.required && it.value.isBlank() } == true
    Scaffold(containerColor = Mist, topBar = { PayTopBar("商户开户", { if (!busy) onBack() }) }, bottomBar = {
        Surface(shadowElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (state.stage) {
                    OpeningStage.FORM -> Button(onClick = model::review, enabled = valid && !state.loading && sameChat, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("下一步 · 核对开户") }
                    OpeningStage.REVIEW, OpeningStage.SUBMITTING -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = model::backToForm, enabled = !busy, modifier = Modifier.weight(1f)) { Text("返回修改") }
                        Button(onClick = { model.create() }, enabled = !busy && sameChat, modifier = Modifier.weight(1f)) { Text(if (busy) "开户中…" else "确认开户") }
                    }
                    OpeningStage.RESULT -> if (!state.draftInserted && state.binding.isNotBlank()) Button(onClick = { model.insert() }, enabled = !busy && sameChat && state.result?.key?.isNotBlank() == true, modifier = Modifier.fillMaxWidth()) { Text(if (state.inserting) "填入中…" else "填入当前会话输入框") }
                    OpeningStage.UNCERTAIN -> OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回查询商户") }
                }
            }
        }
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().testTag("merchant-opening-list"), state = listState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { PayCard {
                Text("会话：${model.chat.label}", color = Ink)
                if (model.chat.telegramId.isNotBlank()) Text("Telegram ID：${model.chat.telegramId}", color = Muted, fontSize = 12.sp)
                Text("商户通过 Bot 链接自行绑定 Telegram。开户成功后文案填入本会话，由你修改并发送。", color = Muted, fontSize = 12.sp)
            } }
            if (!sameChat) item { Text("当前会话已变化，请返回原会话处理开户结果。", color = Danger) }
            if (state.loading || busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { error -> item { PayCard { Text(error, color = Danger) } } }
            if (state.stage == OpeningStage.FORM) {
                if (state.form == null && !state.loading) item { TextButton(onClick = { model.load() }) { Text("重新加载开户资料") } }
                state.form?.fields.orEmpty().filter { it.type != "hidden" }.forEach { field -> item(key = field.name) { PayCard {
                    if (field.name == "pwd") {
                        var reveal by remember { mutableStateOf(false) }
                        OutlinedTextField(field.value, { model.edit(field.name, it) }, label = { Text(field.label) }, enabled = !state.loading && sameChat,
                            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(), singleLine = true,
                            trailingIcon = { TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "隐藏" else "显示") } },
                            supportingText = { Text("留空则只能使用密钥登录") }, modifier = Modifier.fillMaxWidth())
                    } else NativeFieldEditor(field.copy(readOnly = field.readOnly || state.loading || !sameChat), field.value) { model.edit(field.name, it) }
                } } }
            } else if (state.stage == OpeningStage.REVIEW || state.stage == OpeningStage.SUBMITTING) {
                item { PayCard { Text("请核对本次开户信息", style = MaterialTheme.typography.titleMedium); Text("D0 开启结算权限，D1 不开启结算权限。", color = Muted, fontSize = 12.sp) } }
                state.form?.fields.orEmpty().filter { it.type != "hidden" }.forEach { field -> item { PayCard {
                    DataRow(field.label, if (field.sensitive) { if (field.value.isBlank()) "留空，仅密钥登录" else "已设置" } else field.options.firstOrNull { it.value == field.value }?.label ?: field.value.ifBlank { "未填写" })
                } } }
            } else if (state.stage == OpeningStage.RESULT) {
                val result = state.result
                item { PayCard {
                    Text("🏡 开户成功", style = MaterialTheme.typography.titleMedium, color = GreenDark)
                    DataRow("商户 ID", result?.uid.orEmpty())
                    DataRow("结算周期", model.cycle())
                    Text(if (state.draftInserted) "文案已填入输入框，请校对后手动发送。" else "开户已完成，后续操作不会再次提交开户。", color = Muted, fontSize = 12.sp)
                    TextButton(enabled = !busy, onClick = { result?.let { onMerchant(it.uid) } }) { Text("查看商户") }
                    if (state.binding.isBlank()) OutlinedButton(enabled = !busy, onClick = { model.retryBinding() }) { Text(if (state.linkLoading) "获取链接中…" else "重取绑定链接") }
                } }
            }
        }
    }
}
