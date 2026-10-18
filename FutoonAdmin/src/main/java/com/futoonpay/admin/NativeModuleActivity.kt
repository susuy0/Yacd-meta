package com.futoonpay.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.futoonpay.admin.data.ApiResult
import com.futoonpay.admin.data.FutoonRepository
import com.futoonpay.admin.data.GenericRow
import com.futoonpay.admin.data.NativeDocument
import com.futoonpay.admin.data.NativeField
import com.futoonpay.admin.data.NativeForm
import com.futoonpay.admin.ui.theme.Danger
import com.futoonpay.admin.ui.theme.FutoonTheme
import com.futoonpay.admin.ui.theme.GreenDark
import com.futoonpay.admin.ui.theme.Ink
import com.futoonpay.admin.ui.theme.Line
import com.futoonpay.admin.ui.theme.Muted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NativeModuleActivity : EmbeddedAdminActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        enableEdgeToEdge()
        val app = FutoonApplication.from(this)
        if (!app.hasConfiguredEndpoint) {
            startActivity(android.content.Intent(this, MainActivity::class.java))
            finish()
            return
        }
        val module = ModuleCatalog.find(intent.getStringExtra(EXTRA_MODULE))
        val repository = FutoonApplication.from(this).repository
        if (PaymentAdminCatalog.contains(module.id)) {
            setContent {
                FutoonTheme {
                    val expired by app.sessionExpired.collectAsState()
                    LaunchedEffect(expired) { if (expired) finish() }
                    val model: PaymentAdminViewModel = viewModel(
                        factory = PaymentAdminViewModel.Factory(
                            repository = repository,
                            spec = PaymentAdminCatalog.find(module.id),
                            initialChannelType = intent.getStringExtra(EXTRA_CHANNEL_TYPE).orEmpty(),
                            initialChannelId = intent.getStringExtra(EXTRA_CHANNEL_ID).orEmpty(),
                            drafts = com.futoonpay.admin.data.FormDraftStore(this@NativeModuleActivity),
                        )
                    )
                    PaymentAdminRoot(model, onBack = ::finish)
                }
            }
            return
        }
        setContent {
            FutoonTheme {
                val expired by app.sessionExpired.collectAsState()
                LaunchedEffect(expired) { if (expired) finish() }
                val model: NativeModuleViewModel = viewModel(factory = NativeModuleViewModel.Factory(repository, module))
                NativeModuleRoot(model, onBack = ::finish)
            }
        }
    }

    companion object {
        const val EXTRA_MODULE = "module"
        const val EXTRA_CHANNEL_TYPE = "channel_type"
        const val EXTRA_CHANNEL_ID = "channel_id"
    }
}

private sealed interface ModuleSource {
    data class Ajax(
        val endpoint: String,
        val referer: String,
        val idField: String,
        val fields: List<String>,
        val labels: Map<String, String>,
        val searchField: String = "",
    ) : ModuleSource

    data class Documents(val pages: List<ModulePage>) : ModuleSource
}

private data class ModulePage(val title: String, val path: String)
private data class ModuleDefinition(val id: String, val title: String, val subtitle: String, val source: ModuleSource)

private object ModuleCatalog {
    private val definitions = listOf(
        ModuleDefinition("funds", "资金明细", "账户余额变化与交易流水", ajax(
            "ajax_user.php?act=recordList", "record.php", "id",
            listOf("uid", "type", "money", "oldmoney", "newmoney", "trade_no", "date", "addtime", "action"),
            "uid" to "商户 UID", "type" to "类型", "money" to "金额", "oldmoney" to "变更前",
            "newmoney" to "变更后", "trade_no" to "关联订单", "date" to "时间", "addtime" to "时间", "action" to "说明",
            searchField = "uid",
        )),
        ModuleDefinition("merchant_stats", "商户统计", "按日期查看商户交易表现", docs(ModulePage("商户统计", "ustat.php"))),
        ModuleDefinition("risk", "风控记录", "风险命中与请求记录", ajax(
            "ajax_order.php?act=riskList", "risk.php", "id",
            listOf("uid", "type", "content", "url", "date", "addtime"),
            "uid" to "商户 UID", "type" to "风险类型", "content" to "内容", "url" to "来源", "date" to "时间", "addtime" to "时间",
            searchField = "content",
        )),
        ModuleDefinition("blacklist", "支付黑名单", "查询、添加与移除拦截规则", ajax(
            "ajax_user.php?act=blackList", "blacklist.php", "id",
            listOf("type", "content", "addtime", "endtime", "remark"),
            "type" to "类型", "content" to "匹配内容", "addtime" to "添加时间", "endtime" to "到期时间", "remark" to "备注",
            searchField = "content",
        )),
        ModuleDefinition("inactive", "无流水商户", "连续 5 日无交易的商户", docs(ModulePage("无流水商户", "inactive_merchant.php"))),
        ModuleDefinition("channels", "支付通道", "通道参数、状态与账户", docs(ModulePage("支付通道", "pay_channel.php"))),
        ModuleDefinition("payment_types", "支付方式", "支付产品与费率配置", docs(ModulePage("支付方式", "pay_type.php"))),
        ModuleDefinition("rolls", "通道轮询", "通道路由与轮询规则", docs(ModulePage("轮询规则", "pay_roll.php"))),
        ModuleDefinition("channel_monitor", "通道监测", "可用率与通道健康状态", docs(ModulePage("通道监测", "pay_channel_monitor.php"))),
        ModuleDefinition("proxies", "中转代理", "代理节点、状态与连接配置", docs(ModulePage("中转代理", "proxy.php"))),
        ModuleDefinition("profit_receivers", "分账接收方", "接收方账户与关系管理", docs(ModulePage("分账接收方", "ps_receiver.php"))),
        ModuleDefinition("profit_orders", "分账记录", "分账订单、状态与结果", ajax(
            "ajax_profitsharing.php?act=orderList", "ps_order.php", "trade_no",
            listOf("trade_no", "rid", "money", "addtime", "endtime", "status", "result"),
            "trade_no" to "订单号", "rid" to "接收方", "money" to "金额", "addtime" to "创建时间", "endtime" to "完成时间", "status" to "状态", "result" to "处理结果",
            searchField = "trade_no",
        )),
        ModuleDefinition("transfer", "企业付款", "付款任务、收款方与处理结果", docs(ModulePage("企业付款", "transfer.php"))),
        ModuleDefinition("announcements", "公告管理", "站内公告与启停状态", docs(ModulePage("公告", "gonggao.php"))),
        ModuleDefinition("payment_settings", "支付与结算设置", "资金业务全局参数", docs(
            ModulePage("支付", "set.php?mod=pay"), ModulePage("结算", "set.php?mod=settle"),
            ModulePage("预付款", "set.php?mod=prepayment"), ModulePage("企业付款", "set.php?mod=transfer"),
        )),
        ModuleDefinition("site_settings", "站点与账号设置", "站点外观、邮件、任务与管理员", docs(
            ModulePage("站点", "set.php?mod=site"), ModulePage("模板", "set.php?mod=template"),
            ModulePage("邮件", "set.php?mod=mail"), ModulePage("定时任务", "set.php?mod=cron"),
            ModulePage("管理员", "set.php?mod=account"),
        )),
        ModuleDefinition("integrations", "公众号与证书", "微信与证书集成", docs(
            ModulePage("微信公众号", "pay_weixin.php"),
            ModulePage("微信 OAuth", "pay_weixin.php?mod=oauth"), ModulePage("证书", "certificate.php"),
            ModulePage("微信客服", "set_wxkf.php"),
        )),
        ModuleDefinition("login_logs", "登录日志", "管理员与商户登录记录", ajax(
            "ajax_user.php?act=logList", "log.php", "id",
            listOf("uid", "type", "ip", "city", "date", "addtime", "data"),
            "uid" to "用户 UID", "type" to "类型", "ip" to "IP", "city" to "地区", "date" to "时间", "addtime" to "时间", "data" to "设备信息",
            searchField = "uid",
        )),
    )

    fun find(id: String?): ModuleDefinition = definitions.firstOrNull { it.id == id } ?: definitions.first()

    private fun ajax(
        endpoint: String, referer: String, idField: String, fields: List<String>,
        vararg labels: Pair<String, String>, searchField: String = "",
    ) = ModuleSource.Ajax(endpoint, referer, idField, fields, labels.toMap(), searchField)

    private fun docs(vararg pages: ModulePage) = ModuleSource.Documents(pages.toList())
}

private data class NativeModuleState(
    val definition: ModuleDefinition,
    val loading: Boolean = true,
    val page: Int = 1,
    val query: String = "",
    val total: Int = 0,
    val rows: List<GenericRow> = emptyList(),
    val selectedPath: String = "",
    val document: NativeDocument? = null,
    val message: String? = null,
)

private class NativeModuleViewModel(
    private val repository: FutoonRepository,
    definition: ModuleDefinition,
) : ViewModel() {
    private val firstPath = (definition.source as? ModuleSource.Documents)?.pages?.firstOrNull()?.path.orEmpty()
    private val _state = MutableStateFlow(NativeModuleState(definition = definition, selectedPath = firstPath))
    val state = _state.asStateFlow()
    private var feedbackAfterLoad: String? = null

    init { load() }

    fun load(page: Int = _state.value.page, query: String = _state.value.query, path: String = _state.value.selectedPath) = viewModelScope.launch {
        _state.update { it.copy(loading = true, page = page, query = query, selectedPath = path, message = null) }
        runCatching {
            when (val source = _state.value.definition.source) {
                is ModuleSource.Ajax -> {
                    val filters = if (query.isBlank()) emptyMap() else mapOf("column" to source.searchField, "value" to query.trim())
                    val result = repository.genericPage(source.endpoint, source.referer, source.idField, source.fields, page, filters)
                    val feedback = feedbackAfterLoad.also { feedbackAfterLoad = null }
                    _state.update { it.copy(loading = false, total = result.total, rows = result.rows, document = null, message = feedback) }
                }
                is ModuleSource.Documents -> {
                    val document = repository.nativeDocument(path.ifBlank { source.pages.first().path })
                    val feedback = feedbackAfterLoad.also { feedbackAfterLoad = null }
                    _state.update { it.copy(loading = false, document = document, rows = emptyList(), message = feedback) }
                }
            }
        }.onFailure { error -> _state.update { it.copy(loading = false, message = error.message ?: "加载失败") } }
    }

    fun submit(form: NativeForm, values: Map<String, String>) = runAction {
        repository.submitNativeForm(_state.value.selectedPath, form, values)
    }

    fun addBlacklist(type: String, content: String, reason: String, days: String, remark: String) = runAction {
        repository.addBlacklist(type, content, reason, days, remark)
    }

    fun deleteBlacklist(id: String) = runAction { repository.deleteBlacklist(id) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun runAction(action: suspend () -> ApiResult) = viewModelScope.launch {
        _state.update { it.copy(loading = true, message = null) }
        runCatching { action() }
            .onSuccess { result ->
                if (result.success) {
                    feedbackAfterLoad = result.message
                    load()
                } else {
                    _state.update { it.copy(loading = false, message = result.message) }
                }
            }
            .onFailure { error -> _state.update { it.copy(loading = false, message = error.message ?: "操作失败") } }
    }

    class Factory(private val repository: FutoonRepository, private val definition: ModuleDefinition) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = NativeModuleViewModel(repository, definition) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeModuleRoot(viewModel: NativeModuleViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var addBlacklist by rememberSaveable { mutableStateOf(false) }
    state.message?.let { message ->
        LaunchedEffect(message) { snackbar.showSnackbar(message); viewModel.consumeMessage() }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                title = {
                    Column {
                        Text(state.definition.title, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
                        Text(state.definition.subtitle, color = Muted, fontSize = 11.sp, maxLines = 1)
                    }
                },
                actions = {
                    if (state.definition.id == "blacklist") IconButton(onClick = { addBlacklist = true }) { Icon(Icons.Default.Add, "添加黑名单") }
                    if (state.loading) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = { viewModel.load() }) { Icon(Icons.Default.Refresh, "刷新") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = Ink),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val source = state.definition.source) {
                is ModuleSource.Ajax -> AjaxModuleScreen(state, source, viewModel::load, viewModel::deleteBlacklist)
                is ModuleSource.Documents -> DocumentModuleScreen(state, source, viewModel::load, viewModel::submit)
            }
        }
    }
    if (addBlacklist) BlacklistDialog({ addBlacklist = false }) { type, content, reason, days, remark ->
        viewModel.addBlacklist(type, content, reason, days, remark)
        addBlacklist = false
    }
}

@Composable
private fun AjaxModuleScreen(
    state: NativeModuleState,
    source: ModuleSource.Ajax,
    onLoad: (Int, String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf(state.query) }
    var deleteRow by remember { mutableStateOf<GenericRow?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索${source.labels[source.searchField] ?: "记录"}") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { IconButton(onClick = { onLoad(1, query, state.selectedPath) }) { Icon(Icons.Default.Search, "搜索") } },
                singleLine = true, shape = RoundedCornerShape(10.dp),
            )
        }
        item { Text("${state.total} 条记录", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
        items(state.rows) { row ->
            GenericRowCard(row, source) { deleteRow = row }
        }
        if (!state.loading && state.rows.isEmpty()) item { EmptyModule("没有找到记录") }
        item {
            val next = state.page * 30 < state.total && state.rows.isNotEmpty()
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = { onLoad(state.page - 1, query, state.selectedPath) }, enabled = state.page > 1) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null); Text("上一页")
                }
                Text("第 ${state.page} 页", color = Muted, fontSize = 12.sp)
                OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = { onLoad(state.page + 1, query, state.selectedPath) }, enabled = next) {
                    Text("下一页"); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }
        }
    }
    deleteRow?.let { row ->
        AlertDialog(shape = RoundedCornerShape(20.dp), 
            onDismissRequest = { deleteRow = null }, title = { Text("移除黑名单") },
            text = { Text("确认移除 ${row.values["content"].orEmpty()}？") },
            confirmButton = { Button(shape = RoundedCornerShape(10.dp), onClick = { onDelete(row.id); deleteRow = null }) { Text("确认移除") } },
            dismissButton = { TextButton(onClick = { deleteRow = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun GenericRowCard(row: GenericRow, source: ModuleSource.Ajax, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder(), shape = RoundedCornerShape(13.dp),
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.values.values.firstOrNull { it.isNotBlank() } ?: "#${row.id}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (source.referer == "blacklist.php") IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.DeleteOutline, "移除", tint = Danger)
                }
            }
            HorizontalDivider(color = Line)
            row.values.forEach { (key, value) ->
                if (value.isNotBlank()) Row(Modifier.fillMaxWidth()) {
                    Text(source.labels[key] ?: key, color = Muted, fontSize = 12.sp, modifier = Modifier.width(92.dp))
                    Text(value, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun DocumentModuleScreen(
    state: NativeModuleState,
    source: ModuleSource.Documents,
    onLoad: (Int, String, String) -> Unit,
    onSubmit: (NativeForm, Map<String, String>) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (source.pages.size > 1) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(source.pages) { page ->
                    FilterChip(
                        selected = state.selectedPath == page.path,
                        onClick = { onLoad(1, "", page.path) },
                        label = { Text(page.title) },
                    )
                }
            }
        }
        state.document?.notices?.forEach { notice -> item { NoticeCard(notice) } }
        state.document?.tables?.forEach { table ->
            item { Text(table.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 5.dp)) }
            items(table.rows) { row -> NativeTableRow(table.headers, row) }
        }
        state.document?.forms?.forEach { form -> item { NativeFormCard(form, onSubmit) } }
        if (!state.loading && state.document?.let { it.forms.isEmpty() && it.tables.isEmpty() } != false) {
            item { EmptyModule("此页面暂时没有可显示的数据或表单") }
        }
    }
}

@Composable
private fun NoticeCard(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 5.dp).size(7.dp).clip(CircleShape).background(GreenDark))
        Spacer(Modifier.width(9.dp))
        Text(text, color = Ink, fontSize = 12.sp)
    }
}

@Composable
internal fun NativeTableRow(headers: List<String>, row: List<String>) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder(), shape = RoundedCornerShape(13.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEachIndexed { index, value ->
                if (value.isNotBlank()) Row(Modifier.fillMaxWidth()) {
                    Text(headers.getOrNull(index)?.ifBlank { "字段 ${index + 1}" } ?: "字段 ${index + 1}", color = Muted, fontSize = 12.sp, modifier = Modifier.width(100.dp))
                    Text(value, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun NativeFormCard(form: NativeForm, onSubmit: (NativeForm, Map<String, String>) -> Unit) {
    val values = remember(form) { mutableStateMapOf<String, String>().apply { form.fields.forEach { put(it.name, it.value) } } }
    var confirm by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder(), shape = RoundedCornerShape(13.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(form.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text("敏感字段不会从网页回填；留空表示不修改", color = Muted, fontSize = 11.sp)
            form.fields.filter { it.type != "hidden" }.forEach { field ->
                NativeFieldEditor(field, values[field.name].orEmpty()) { values[field.name] = it }
            }
            Button(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Text("保存设置") }
        }
    }
    if (confirm) AlertDialog(shape = RoundedCornerShape(20.dp), 
        onDismissRequest = { confirm = false }, title = { Text("确认保存") },
        text = { Text("设置会立即提交到管理后台。资金、通道和权限参数请再次核对。") },
        confirmButton = {
            Button(shape = RoundedCornerShape(10.dp), onClick = {
                val submitted = values.filter { (name, value) ->
                    val field = form.fields.first { it.name == name }
                    !field.readOnly && (!field.sensitive || value.isNotBlank())
                }
                onSubmit(form, submitted)
                confirm = false
            }) { Text("确认保存") }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } },
    )
}

@Composable
internal fun NativeFieldEditor(field: NativeField, value: String, onValueChange: (String) -> Unit) {
    var reveal by remember { mutableStateOf(false) }
    when (field.type) {
        "checkbox" -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = value == "1" || value.equals("on", true), onCheckedChange = { onValueChange(if (it) "1" else "0") }, enabled = !field.readOnly)
            Text(field.label, fontSize = 13.sp)
        }
        "select", "radio" -> SelectField(field, value, onValueChange)
        else -> OutlinedTextField(
            value = value, onValueChange = onValueChange, enabled = !field.readOnly,
            label = { Text(field.label + if (field.required) " *" else "") },
            supportingText = if (field.sensitive) ({ Text("留空则保留现有值") }) else null,
            visualTransformation = if ((field.sensitive || field.type == "password") && !reveal) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            trailingIcon = if (field.sensitive || field.type == "password") ({ TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "隐藏" else "显示", fontSize = 12.sp) } }) else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = when (field.type) { "decimal" -> KeyboardType.Decimal; "number" -> KeyboardType.Number; "email" -> KeyboardType.Email; else -> KeyboardType.Text },
            ),
            singleLine = field.type != "textarea", minLines = if (field.type == "textarea") 3 else 1,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
        )
    }
}


@Composable
private fun SelectField(field: NativeField, value: String, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selected by remember(value) { mutableStateOf(value) }
    val confirmSelection = field.name.equals("plugin", true)
    val current = field.options.firstOrNull { it.value == value }?.label ?: value
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(field.label + if (field.required) " *" else "", color = Ink, fontSize = 13.sp)
        OutlinedButton(onClick = { query = ""; selected = value; expanded = true }, enabled = !field.readOnly && field.options.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(10.dp)) {
            Text(current.ifBlank { if (field.options.isEmpty()) "暂无可用选项" else "请选择" }, color = Ink,
                fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("⌄", color = Muted, fontSize = 20.sp)
        }
        if (field.options.isEmpty()) Text(if (field.name.equals("plugin", true)) "请先选择支付方式，插件列表从后台加载" else "后台未返回可选项", color = Muted, fontSize = 11.sp)
    }
    if (expanded) com.futoonpay.admin.ui.PaySheet(field.label, { expanded = false }) {
        OutlinedTextField(query, { query = it }, placeholder = { Text("搜索可选项") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        val options = field.options.filter { query.isBlank() || it.label.contains(query, true) || it.value.contains(query, true) }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(options, key = { index, it -> "${it.value}:$index" }) { _, option ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { if (confirmSelection) selected = option.value else { onValueChange(option.value); expanded = false } }
                    .background(if (option.value == selected) MaterialTheme.colorScheme.primaryContainer else Color.White, RoundedCornerShape(10.dp))
                    .padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(option.label, fontSize = 14.sp); if (field.name == "plugin") Text(option.value, color = Muted, fontSize = 11.sp) }
                    if (option.value == selected) Text("✓", color = GreenDark)
                }
            }
            if (options.isEmpty()) item { Text("没有匹配的选项", color = Muted, modifier = Modifier.padding(16.dp)) }
        }
        if (confirmSelection) Button(shape = RoundedCornerShape(10.dp), onClick = { onValueChange(selected); expanded = false },
            enabled = field.options.any { it.value == selected }, modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp)) {
            Text(field.options.firstOrNull { it.value == selected }?.label?.let { "选择 $it" } ?: "选择插件")
        }
    }
}

@Composable
private fun BlacklistDialog(onDismiss: () -> Unit, onConfirm: (String, String, String, String, String) -> Unit) {
    var type by rememberSaveable { mutableStateOf("1") }
    var content by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableStateOf("0") }
    var remark by rememberSaveable { mutableStateOf("") }
    AlertDialog(shape = RoundedCornerShape(20.dp), 
        onDismissRequest = onDismiss, title = { Text("添加黑名单") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                item { OutlinedTextField(type, { type = it }, label = { Text("类型代码") }, singleLine = true) }
                item { OutlinedTextField(content, { content = it }, label = { Text("匹配内容") }, singleLine = true) }
                item { OutlinedTextField(reason, { reason = it }, label = { Text("拦截原因") }) }
                item { OutlinedTextField(days, { days = it }, label = { Text("有效天数（0 为永久）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) }
                item { OutlinedTextField(remark, { remark = it }, label = { Text("备注") }) }
            }
        },
        confirmButton = { Button(shape = RoundedCornerShape(10.dp), onClick = { onConfirm(type, content, reason, days, remark) }, enabled = content.isNotBlank()) { Text("确认添加") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun EmptyModule(text: String) {
    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { Text(text, color = Muted) }
}
