package com.futoonpay.admin

import com.futoonpay.admin.ui.theme.CardColor
import com.futoonpay.admin.ui.theme.ButtonText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import com.futoonpay.admin.ui.PayCard
import com.futoonpay.admin.ui.PayTabs
import com.futoonpay.admin.ui.PayPills
import com.futoonpay.admin.ui.PayBadge
import com.futoonpay.admin.ui.PayTopBar
import com.futoonpay.admin.ui.ListFeedback
import com.futoonpay.admin.ui.SectionLabel
import com.futoonpay.admin.ui.DataRow
import com.futoonpay.admin.data.MonitorWindow
import com.futoonpay.admin.data.NativeField
import com.futoonpay.admin.data.NativeOption
import com.futoonpay.admin.data.metrics
import com.futoonpay.admin.data.enabledLabel
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.futoonpay.admin.data.ApiResult
import com.futoonpay.admin.data.BatchProgress
import com.futoonpay.admin.data.BatchRequest
import com.futoonpay.admin.data.PaymentBatchRow
import com.futoonpay.admin.data.paymentBatchOptions
import com.futoonpay.admin.ui.BatchDialog
import com.futoonpay.admin.ui.MultiSelectBar
import com.futoonpay.admin.ui.SelectableRecord
import com.futoonpay.admin.ui.rememberRecordSelection
import com.futoonpay.admin.data.ChannelMonitorItem
import com.futoonpay.admin.data.FutoonRepository
import com.futoonpay.admin.data.MonitorGroupDimension
import com.futoonpay.admin.data.MonitorHealth
import com.futoonpay.admin.data.MonitorSortDimension
import com.futoonpay.admin.data.NativeAction
import com.futoonpay.admin.data.NativeDocument
import com.futoonpay.admin.data.NativeForm
import com.futoonpay.admin.data.PaymentChannelGroupDimension
import com.futoonpay.admin.data.PaymentChannelDetail
import com.futoonpay.admin.data.PaymentChannelItem
import com.futoonpay.admin.data.PaymentChannelSortDimension
import com.futoonpay.admin.data.PaymentChannelStatus
import com.futoonpay.admin.data.PaymentChannelTab
import com.futoonpay.admin.data.PaymentTestResult
import com.futoonpay.admin.data.RollGroupDimension
import com.futoonpay.admin.data.RollGroupItem
import com.futoonpay.admin.data.RollGroupStatus
import com.futoonpay.admin.data.RollGroupTab
import com.futoonpay.admin.data.RollRouteVisualItem
import com.futoonpay.admin.data.RollSortDimension
import com.futoonpay.admin.data.sortChannelMonitorItems
import com.futoonpay.admin.data.sortPaymentChannelItems
import com.futoonpay.admin.data.sortRollGroupItems
import com.futoonpay.admin.data.toChannelMonitorItems
import com.futoonpay.admin.data.toPaymentChannelItems
import com.futoonpay.admin.data.toRollGroupItems
import com.futoonpay.admin.data.toRollRouteVisualItems
import com.futoonpay.admin.data.paymentChannelTypeFilters
import com.futoonpay.admin.ui.theme.Danger
import com.futoonpay.admin.ui.theme.GreenDark
import com.futoonpay.admin.ui.theme.Ink
import com.futoonpay.admin.ui.theme.Line
import com.futoonpay.admin.ui.theme.Muted
import com.futoonpay.admin.ui.theme.Warning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class PaymentAdminSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val path: String,
    val supportsAdd: Boolean,
)

internal object PaymentAdminCatalog {
    private val specs = listOf(
        PaymentAdminSpec("channels", "支付通道", "账户、费率、支付方式与运行状态", "pay_channel.php", true),
        PaymentAdminSpec("payment_types", "支付方式", "调用值、设备、状态与关联通道", "pay_type.php", true),
        PaymentAdminSpec("rolls", "通道轮询", "规则模式、状态与通道路由", "pay_roll.php", true),
        PaymentAdminSpec("channel_monitor", "通道监测", "检测、恢复、禁用与结果重置", "pay_channel_monitor.php", false),
        PaymentAdminSpec("proxies", "中转代理", "代理配置、运行状态与删除", "proxy.php", true),
    )

    fun contains(id: String): Boolean = specs.any { it.id == id }
    fun find(id: String): PaymentAdminSpec = specs.first { it.id == id }
}

internal data class PaymentAdminState(
    val spec: PaymentAdminSpec,
    val batch: BatchProgress? = null,
    val loading: Boolean = true,
    val page: Int = 1,
    val query: String = "",
    val rollTab: RollGroupTab = RollGroupTab.ALL,
    val channelTab: PaymentChannelTab = PaymentChannelTab.ALL,
    val channelType: String = "",
    val document: NativeDocument? = null,
    val selectedChannel: PaymentChannelItem? = null,
    val channelDetail: PaymentChannelDetail? = null,
    val channelDetailLoading: Boolean = false,
    val testChannel: PaymentChannelItem? = null,
    val testLoading: Boolean = false,
    val testResult: PaymentTestResult? = null,
    val editor: NativeForm? = null,
    val accountForm: NativeForm? = null,
    val routeForm: NativeForm? = null,
    val primarySaved: Boolean = false,
    val accountLoading: Boolean = false,
    val pluginLoading: Boolean = false,
    val routeChannels: Map<String, PaymentChannelItem> = emptyMap(),
    val routeStats: Map<String, com.futoonpay.admin.data.OrderChannelStat> = emptyMap(),
    val message: String? = null,
    val loadError: String? = null,
    val configurationChannels: List<com.futoonpay.admin.data.ConfigurationChannel> = emptyList(),
    val configurationLoading: Boolean = false,
    val configurationError: String? = null,
    val configurationLoaded: Boolean = false,
    val writeUncertain: Boolean = false,
    val testUncertain: Boolean = false,
)

internal class PaymentAdminViewModel(
    private val repository: FutoonRepository,
    spec: PaymentAdminSpec,
    initialChannelType: String = "",
    initialChannelId: String = "",
    private val drafts: com.futoonpay.admin.data.FormDraftStore? = null,
    initialDocument: NativeDocument? = null,
    loadOnStart: Boolean = true,
) : ViewModel() {
    private val _state = MutableStateFlow(PaymentAdminState(spec = spec, channelType = initialChannelType, document = initialDocument, loading = loadOnStart))
    val state = _state.asStateFlow()
    private var feedbackAfterLoad: String? = null
    private var pendingInitialChannelId = initialChannelId
    private var loadGeneration = 0L
    private var accountGeneration = 0L
    private var configurationGeneration = 0L

    fun offerBatch(request: BatchRequest) {
        if (_state.value.loading || _state.value.batch?.running == true || request.commands.isEmpty()) return
        _state.update { it.copy(batch = BatchProgress(request.copy(commands = request.commands.distinctBy { command -> command.id }))) }
    }
    fun startBatch() {
        val batch = _state.value.batch ?: return
        if (batch.started || _state.value.loading) return
        val request = batch.request
        _state.update { it.copy(batch = batch.copy(started = true, running = true), loading = true) }
        viewModelScope.launch {
            try {
                repository.runBatch(request.commands) { results -> _state.update { if (it.batch?.request === request) it.copy(batch = it.batch.copy(results = results)) else it } }
            } finally {
                _state.update { if (it.batch?.request === request) it.copy(batch = it.batch.copy(running = false), loading = false) else it }
            }
        }
    }
    fun closeBatch() {
        val batch = _state.value.batch ?: return
        if (batch.running) return
        _state.update { it.copy(batch = null) }
        if (batch.started) load()
    }
    fun retryBatch() {
        val batch = _state.value.batch ?: return
        if (batch.running || batch.retryable.isEmpty()) return
        _state.update { it.copy(batch = BatchProgress(batch.request.copy(commands = batch.retryable, warning = "仅重新执行明确失败或未执行的记录，请核对后确认。"))) }
    }

    fun loadConfigurationContext() = viewModelScope.launch {
        val generation = ++configurationGeneration
        _state.update { it.copy(configurationLoading = true, configurationError = null) }
        runCatching { repository.configurationChannels() }.onSuccess { channels ->
            if (generation == configurationGeneration) _state.update { it.copy(configurationChannels = channels, configurationLoaded = true, configurationLoading = false) }
        }.onFailure { error ->
            if (generation == configurationGeneration) _state.update { it.copy(configurationError = error.message ?: "关联通道加载失败", configurationLoading = false) }
        }
    }

    init { if (loadOnStart) load() }

    private fun restoreEditor(form: NativeForm): NativeForm = drafts?.restore(repository.backendAddress, form) ?: form
    private fun saveDraft() { _state.value.editor?.let { drafts?.save(repository.backendAddress, it) } }

    fun load(page: Int = _state.value.page, query: String = _state.value.query) =
        loadDocument(page, query, _state.value.rollTab, _state.value.channelTab, _state.value.channelType)

    fun selectRollTab(tab: RollGroupTab) {
        if (_state.value.spec.id != "rolls" || (tab == _state.value.rollTab && _state.value.document != null)) return
        loadDocument(page = 1, query = _state.value.query, rollTab = tab, channelTab = _state.value.channelTab, channelType = _state.value.channelType)
    }

    fun selectChannelTab(tab: PaymentChannelTab) {
        if (_state.value.spec.id != "channels" || (tab == _state.value.channelTab && _state.value.document != null)) return
        loadDocument(1, _state.value.query, _state.value.rollTab, tab, _state.value.channelType)
    }

    fun selectChannelType(type: String) {
        if (_state.value.spec.id != "channels" || (type == _state.value.channelType && _state.value.document != null)) return
        loadDocument(1, _state.value.query, _state.value.rollTab, _state.value.channelTab, type)
    }

    private fun loadDocument(
        page: Int,
        query: String,
        rollTab: RollGroupTab,
        channelTab: PaymentChannelTab,
        channelType: String,
    ) = viewModelScope.launch {
        val generation = ++loadGeneration
        _state.update {
            it.copy(
                loading = true,
                page = page.coerceAtLeast(1),
                query = query,
                rollTab = rollTab,
                channelTab = channelTab,
                channelType = channelType,
                message = null,
                loadError = null,
                document = if (rollTab != it.rollTab || channelTab != it.channelTab || channelType != it.channelType) null else it.document,
            )
        }
        runCatching {
            repository.paymentDocument(
                _state.value.spec.id,
                _state.value.page,
                query,
                rollTab,
                channelTab,
                channelType,
            )
        }
            .onSuccess { document ->
                if (generation != loadGeneration) return@onSuccess
                val feedback = feedbackAfterLoad.also { feedbackAfterLoad = null }
                _state.update { it.copy(loading = false, writeUncertain = false, document = document, message = feedback) }
                if (_state.value.spec.id in setOf("payment_types", "proxies")) loadConfigurationContext()
                if (_state.value.spec.id == "channels" && pendingInitialChannelId.isNotBlank()) {
                    val targetId = pendingInitialChannelId.also { pendingInitialChannelId = "" }
                    val target = document.tables.flatMap { it.toPaymentChannelItems() }.firstOrNull { it.id == targetId }
                    if (target != null) openChannelDetail(target)
                    else _state.update { it.copy(message = "没有找到支付通道 #$targetId") }
                }
            }
            .onFailure { error -> if (generation == loadGeneration) _state.update { it.copy(loading = false, loadError = error.message ?: "加载失败", message = error.message ?: "加载失败") } }
    }

    fun openShortcutEditor(id: String) = viewModelScope.launch {
        if (_state.value.loading) return@launch
        _state.update { it.copy(loading = true, loadError = null, message = null) }
        try {
            val document = repository.paymentDocument("channels", query = id)
            _state.update { it.copy(document = document, loading = false) }
            openEditor(NativeAction("编辑通道", "ajax_pay.php?act=getChannel&id=$id")).join()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { _state.update { it.copy(loading = false, loadError = e.message ?: "无法读取通道配置") } }
    }

    fun openAdd() {
        val document = _state.value.document ?: return
        val template = document.forms.filter { form ->
            when (_state.value.spec.id) {
                "channels" -> form.fields.any { it.name == "plugin" } && form.fields.any { it.name == "rate" }
                "payment_types" -> form.fields.any { it.name == "showname" } && form.fields.any { it.name == "device" }
                "rolls" -> form.fields.any { it.name == "kind" } && form.fields.any { it.name == "type" }
                else -> form.fields.any { it.name == "action" } || form.fields.size >= 3
            }
        }.maxByOrNull { it.fields.size }
        if (template == null) {
            val addAction = document.actions.firstOrNull { it.label.contains("新增") || it.label.contains("添加") }
            if (addAction != null) openEditor(addAction)
            else _state.update { it.copy(message = "后台页面未提供新增表单，请刷新后重试") }
            return
        }
        val fields = template.fields.map { field ->
            val value = when (field.name) {
                "action" -> "add"
                "id", "name", "showname", "rate", "plugin", "daytop", "paymin", "paymax" -> ""
                else -> if (field.sensitive) "" else field.value
            }
            if (_state.value.spec.id == "channels" && field.name.equals("plugin", true)) {
                field.copy(value = "", type = "select", options = emptyList())
            } else field.copy(value = value)
        }
        val action = when (_state.value.spec.id) {
            "channels" -> "ajax_pay.php?act=saveChannel"
            "payment_types" -> "ajax_pay.php?act=savePayType"
            "rolls" -> "ajax_pay.php?act=saveRoll"
            else -> template.action
        }
        _state.update { it.copy(editor = restoreEditor(template.copy(title = "新增${it.spec.title}", action = action, method = "POST", fields = fields)), accountForm = null, primarySaved = false) }
        if (_state.value.spec.id == "channels") {
            val restored = _state.value.editor ?: return
            val plugin = restored.fields.firstOrNull { it.name == "plugin" }?.value.orEmpty()
            loadPlugins(restored.fields.firstOrNull { it.name == "type" }?.value.orEmpty(), plugin)
            if (plugin.isNotBlank()) loadAccountForm(plugin)
        }
        if (_state.value.spec.id == "rolls") loadGroupRoutes()
        if (_state.value.spec.id in setOf("payment_types", "proxies")) loadConfigurationContext()
    }

    fun openEditor(action: NativeAction) = viewModelScope.launch {
        val document = _state.value.document ?: return@launch
        _state.update { it.copy(loading = true, message = null) }
        runCatching { repository.paymentEditor(_state.value.spec.id, action, document) }
            .onSuccess { loadedForm ->
                val form = if (_state.value.spec.id == "channels") loadedForm.copy(fields = loadedForm.fields.map { field ->
                    if (field.name.equals("plugin", true)) field.copy(type = "select") else field
                }) else loadedForm
                _state.update { it.copy(loading = false, editor = restoreEditor(form), accountForm = null, primarySaved = false) }
                if (_state.value.spec.id in setOf("payment_types", "proxies")) loadConfigurationContext()
                if (_state.value.spec.id == "channels") {
                    val typeId = form.fields.firstOrNull { it.name == "type" }?.value.orEmpty()
                    val plugin = form.fields.firstOrNull { it.name == "plugin" }?.value.orEmpty()
                    loadPlugins(typeId, plugin)
                    if (form.action.contains("saveChannel") && !form.action.contains("saveChannelInfo")) loadAccountForm(plugin, true)
                }
                if (_state.value.spec.id == "rolls") { loadRouteContext(); if (form.action.contains("saveRoll") && !form.action.contains("saveRollInfo")) loadGroupRoutes() }
            }
            .onFailure { error -> _state.update { it.copy(loading = false, message = error.message ?: "无法打开编辑表单") } }
    }

    fun updateEditorField(name: String, value: String) {
        val isChannelType = _state.value.spec.id == "channels" && name == "type"
        _state.update { current ->
            val form = current.editor ?: return@update current
            current.copy(
                editor = form.copy(fields = form.fields.map { field ->
                    when {
                        field.name == name -> field.copy(value = value)
                        isChannelType && field.name.equals("plugin", true) -> field.copy(value = "", type = "select", options = emptyList())
                        else -> field
                    }
                }),
                pluginLoading = isChannelType && value.isNotBlank() && value != "0",
                primarySaved = if (name.startsWith("account::") || name.startsWith("list[") || (current.primarySaved && form.fields.firstOrNull { it.name == "id" }?.value.isNullOrBlank())) current.primarySaved else false,
                message = null,
            )
        }
        saveDraft()
        if (isChannelType) loadPlugins(value, "")
        if (_state.value.spec.id == "channels" && (name == "plugin" || isChannelType)) loadAccountForm(if (isChannelType) "" else value)
    }

    private fun loadAccountForm(plugin: String, existing: Boolean = false) = viewModelScope.launch {
        val generation = ++accountGeneration
        val editor = _state.value.editor ?: return@launch
        val id = editor.fields.firstOrNull { it.name == "id" }?.value.orEmpty()
        _state.update { it.copy(accountLoading = plugin.isNotBlank(), accountForm = null, editor = it.editor?.copy(fields = it.editor.fields.filterNot { field -> field.name.startsWith("account::") })) }
        if (plugin.isBlank()) return@launch
        runCatching {
            if (id.isNotBlank() && existing) repository.paymentEditor("channels", NativeAction("账户配置", "ajax_pay.php?act=channelInfo&id=$id"), _state.value.document ?: error("页面尚未加载"))
            else repository.pluginAccountForm(plugin, id)
        }.onSuccess { loaded ->
            if (generation != accountGeneration) return@onSuccess
            if (loaded == null) { _state.update { it.copy(accountLoading = false) }; return@onSuccess }
            val account = loaded.copy(fields = loaded.fields.map { field -> if (field.sensitive) field.copy(value = "") else field })
            _state.update { current -> current.copy(accountLoading = false, accountForm = account, editor = current.editor?.let { primary ->
                restoreEditor(primary.copy(fields = primary.fields + account.fields.filter { it.type != "hidden" }.map { it.copy(name = "account::${it.name}") }))
            }) }
        }.onFailure { if (generation == accountGeneration) _state.update { it.copy(accountLoading = false, message = "账户配置未加载，请重试：${it.message}") } }
    }

    private fun loadGroupRoutes() = viewModelScope.launch {
        val editor = _state.value.editor ?: return@launch
        val id = editor.fields.firstOrNull { it.name == "id" }?.value.orEmpty()
        _state.update { it.copy(accountLoading = true, routeForm = null) }
        runCatching {
            if (id.isNotBlank()) repository.paymentEditor("rolls", NativeAction("成员通道", "ajax_pay.php?act=rollInfo&id=$id"), _state.value.document ?: error("页面尚未加载"))
            else {
                val channels = repository.paymentDocument("channels").tables.flatMap { it.toPaymentChannelItems() }
                _state.update { it.copy(routeChannels = channels.associateBy { channel -> channel.id }) }
                NativeForm("成员通道", "ajax_pay.php?act=saveRollInfo&id=", "POST", listOf(
                    NativeField("list[0][channel]", "成员通道", "", "select", channels.map { NativeOption(it.id, it.name) }),
                    NativeField("list[0][weight]", "权重", "1", "number")))
            }
        }.onSuccess { routes ->
            if (_state.value.editor?.action != editor.action) return@onSuccess
            _state.update { it.copy(accountLoading = false, routeForm = routes, editor = it.editor?.let { current -> restoreEditor(current.copy(fields = current.fields.filterNot { field -> field.name.startsWith("list[") } + routes.fields.filter { field -> field.name.startsWith("list[") })) }) }
            loadRouteContext()
        }.onFailure { _state.update { it.copy(accountLoading = false, message = "成员通道加载失败：${it.message}") } }
    }

    fun closeEditor() { accountGeneration++; _state.update { it.copy(editor = null, accountForm = null, routeForm = null, primarySaved = false, pluginLoading = false, accountLoading = false) } }

    fun appendRoute() = _state.update { current ->
        val form = current.editor ?: return@update current
        val template = form.fields.firstOrNull { it.name.endsWith("[channel]") } ?: return@update current
        val next = form.fields.mapNotNull { Regex("list\\[(\\d+)]").find(it.name)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull()?.plus(1) ?: 0
        current.copy(editor = form.copy(fields = form.fields + listOf(
            template.copy(name = "list[$next][channel]", label = "路由 ${next + 1} · 通道", value = ""),
            NativeField("list[$next][weight]", "权重", "1", "number"),
        )))
    }

    private fun loadRouteContext() = viewModelScope.launch {
        runCatching { repository.paymentDocument("channels") }.onSuccess { document ->
            val channels = document.tables.flatMap { it.toPaymentChannelItems() }.associateBy { it.id }
            _state.update { it.copy(routeChannels = channels) }
            runCatching { repository.channelStatistics(channels.keys) }.onSuccess { stats ->
                _state.update { it.copy(routeStats = stats) }
            }
        }
    }

    fun openChannelDetail(item: PaymentChannelItem) = viewModelScope.launch {
        _state.update {
            it.copy(
                selectedChannel = item,
                channelDetail = null,
                channelDetailLoading = true,
                message = null,
            )
        }
        runCatching { repository.paymentChannelDetail(item) }
            .onSuccess { detail -> _state.update { it.copy(channelDetail = detail, channelDetailLoading = false) } }
            .onFailure { error ->
                _state.update { it.copy(channelDetailLoading = false, message = error.message ?: "通道详情加载失败") }
            }
    }

    fun closeChannelDetail() = _state.update {
        it.copy(selectedChannel = null, channelDetail = null, channelDetailLoading = false)
    }

    fun openPaymentTest(item: PaymentChannelItem) = _state.update {
        it.copy(testChannel = item, testLoading = false, testResult = null)
    }

    fun closePaymentTest() = _state.update {
        it.copy(testChannel = null, testLoading = false, testResult = null)
    }

    fun submitPaymentTest(name: String, amount: String) = viewModelScope.launch {
        if (_state.value.testLoading || _state.value.testUncertain) return@launch
        val channel = _state.value.testChannel ?: return@launch
        val numericAmount = amount.toBigDecimalOrNull()
        if (name.isBlank() || numericAmount == null || numericAmount <= java.math.BigDecimal.ZERO || numericAmount.stripTrailingZeros().scale() > 2) {
            _state.update { it.copy(testResult = PaymentTestResult(false, "请填写订单名称和大于 0 的订单金额")) }
            return@launch
        }
        _state.update { it.copy(testLoading = true, testResult = null) }
        runCatching { repository.testPayment(channel.id, name.trim(), amount.trim()) }
            .onSuccess { result -> _state.update { it.copy(testLoading = false, testResult = result) } }
            .onFailure { error ->
                _state.update {
                    it.copy(testLoading = false, testUncertain = true, testResult = PaymentTestResult(false, "创建结果待核对，请先到订单列表查询，勿重复创建。"))
                }
            }
    }

    fun submit(form: NativeForm, values: Map<String, String>) = runAction {
        val snapshot = _state.value
        val compositeRoute = snapshot.routeForm != null
        val primary = form.copy(fields = form.fields.filterNot { it.name.startsWith("account::") || (compositeRoute && it.name.startsWith("list[")) })
        var id = primary.fields.firstOrNull { it.name == "id" }?.value.orEmpty()
        if (!snapshot.primarySaved) {
            val result = repository.submitPaymentForm(snapshot.spec.id, primary, values.filterKeys { !it.startsWith("account::") && !(compositeRoute && it.startsWith("list[")) })
            if (!result.success) return@runAction result
            id = id.ifBlank { result.recordId }
            if (snapshot.accountForm != null || compositeRoute) {
                _state.update { current -> current.copy(primarySaved = true, editor = current.editor?.let { editor -> editor.copy(fields = editor.fields.map { field ->
                    when (field.name) { "id" -> field.copy(value = id); "action" -> if (id.isNotBlank()) field.copy(value = "edit") else field; else -> field }
                } + if (id.isNotBlank() && editor.fields.none { it.name == "id" }) listOf(NativeField("id", "ID", id, "hidden")) else emptyList()) }) }
                if (id.isBlank()) return@runAction ApiResult(false, "基本信息已创建，后台没有返回 ID。请返回列表完成后续配置，避免重复创建。", partial = true)
            }
        }
        snapshot.accountForm?.let { account ->
            if (id.isBlank()) return@runAction ApiResult(false, "通道已创建，请返回列表完成账户配置，避免重复创建。", partial = true)
            val fields = values.filterKeys { it.startsWith("account::") }.mapKeys { it.key.removePrefix("account::") }
            val result = repository.submitPaymentForm(snapshot.spec.id, account.copy(action = "ajax_pay.php?act=saveChannelInfo&id=$id"), fields)
            if (!result.success) return@runAction result.copy(message = "基本信息已保存，账户配置失败：${result.message}", partial = true)
        }
        snapshot.routeForm?.let { route ->
            if (id.isBlank()) return@runAction ApiResult(false, "轮询组已创建，请返回列表完成成员配置，避免重复创建。", partial = true)
            val result = repository.submitPaymentForm(snapshot.spec.id, route.copy(action = "ajax_pay.php?act=saveRollInfo&id=$id"), values.filterKeys { it.startsWith("list[") })
            if (!result.success) return@runAction result.copy(message = "基本信息已保存，成员配置失败：${result.message}", partial = true)
        }
        ApiResult(true, "保存成功")
    }

    fun execute(action: NativeAction) = runAction { repository.runPaymentAction(_state.value.spec.id, action) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun loadPlugins(typeId: String, selectedPlugin: String) = viewModelScope.launch {
        if (typeId.isBlank() || typeId == "0") {
            _state.update { it.copy(pluginLoading = false) }
            return@launch
        }
        _state.update { it.copy(pluginLoading = true) }
        runCatching { repository.paymentPlugins(typeId) }
            .onSuccess { options ->
                _state.update { current ->
                    val form = current.editor ?: return@update current.copy(pluginLoading = false)
                    val activeType = form.fields.firstOrNull { it.name == "type" }?.value.orEmpty()
                    if (activeType != typeId) return@update current
                    current.copy(
                        pluginLoading = false,
                        editor = form.copy(fields = form.fields.map { field ->
                            if (field.name.equals("plugin", true)) {
                                field.copy(type = "select", value = selectedPlugin, options = options)
                            } else field
                        }),
                        message = if (options.isEmpty()) "该支付方式没有可用插件" else current.message,
                    )
                }
            }
            .onFailure { error ->
                _state.update { current ->
                    val activeType = current.editor?.fields?.firstOrNull { it.name == "type" }?.value.orEmpty()
                    if (activeType == typeId) current.copy(pluginLoading = false, message = error.message ?: "支付插件加载失败") else current
                }
            }
    }

    private fun runAction(block: suspend () -> ApiResult) = viewModelScope.launch {
        if (_state.value.loading || _state.value.writeUncertain) return@launch
        _state.update { it.copy(loading = true, message = null) }
        runCatching { block() }
            .onSuccess { result ->
                if (result.success) {
                    _state.value.editor?.let { drafts?.remove(repository.backendAddress, it) }
                    feedbackAfterLoad = result.message
                    _state.update { it.copy(editor = null) }
                    load()
                } else if (result.partial && _state.value.spec.id == "payment_types") {
                    feedbackAfterLoad = result.message
                    _state.update { it.copy(editor = null) }
                    load()
                } else _state.update { it.copy(loading = false, message = result.message) }
            }
            .onFailure { error -> _state.update { it.copy(loading = false, writeUncertain = true, message = "操作结果待核对，请返回列表刷新核对，勿重复提交。") } }
    }

    class Factory(
        private val repository: FutoonRepository,
        private val spec: PaymentAdminSpec,
        private val initialChannelType: String = "",
        private val initialChannelId: String = "",
        private val drafts: com.futoonpay.admin.data.FormDraftStore? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PaymentAdminViewModel(repository, spec, initialChannelType, initialChannelId, drafts) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaymentAdminRoot(viewModel: PaymentAdminViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    BatchDialog(state.batch, viewModel::startBatch, viewModel::closeBatch, viewModel::retryBatch)
    BackHandler(enabled = state.testChannel != null || state.editor != null || state.selectedChannel != null) {
        when {
            viewModel.state.value.testChannel != null -> viewModel.closePaymentTest()
            viewModel.state.value.editor != null -> viewModel.closeEditor()
            viewModel.state.value.selectedChannel != null -> viewModel.closeChannelDetail()
            else -> Unit
        }
    }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val navigation = com.futoonpay.admin.LocalPanelNavigation.current
    fun openChannel(channelId: String) {
        navigation.module("channels", channelId = channelId)
    }
    state.message?.let { message ->
        LaunchedEffect(message) { snackbar.showSnackbar(message); viewModel.consumeMessage() }
    }
    state.editor?.let { form ->
        PaymentEditorScreen(
            form = form,
            loading = state.loading || state.pluginLoading || state.accountLoading,
            onBack = viewModel::closeEditor,
            onFieldChange = viewModel::updateEditorField,
            onSubmit = viewModel::submit,
            onOpenChannel = ::openChannel,
            snackbar = snackbar,
            routeChannels = state.routeChannels,
            routeStats = state.routeStats,
            onAddRoute = viewModel::appendRoute,
            configurationModule = state.spec.id,
            configurationChannels = state.configurationChannels,
            configurationLoading = state.configurationLoading,
            configurationError = state.configurationError,
            onReloadConfiguration = { viewModel.loadConfigurationContext() },
            uncertain = state.writeUncertain,
        )
        return
    }
    state.selectedChannel?.let { channel ->
        PaymentChannelDetailScreen(
            channel = channel,
            detail = state.channelDetail,
            loading = state.channelDetailLoading || state.loading,
            onBack = viewModel::closeChannelDetail,
            onRefresh = { viewModel.openChannelDetail(channel) },
            onTest = { viewModel.openPaymentTest(channel) },
            onOrders = {
                navigation.orders(channelId = channel.id)
            },
            onRelatedType = {
                navigation.module("channels", channelType = state.channelDetail?.paymentTypeId.orEmpty())
            },
            onAction = viewModel::execute,
            onEditorAction = viewModel::openEditor,
            snackbar = snackbar,
        )
        state.testChannel?.let { testChannel ->
            PaymentTestDialog(
                channel = testChannel,
                loading = state.testLoading,
                result = state.testResult,
                uncertain = state.testUncertain,
                onDismiss = viewModel::closePaymentTest,
                onSubmit = viewModel::submitPaymentTest,
            )
        }
        return
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.spec.supportsAdd) Surface(color = CardColor) {
                Button(shape = RoundedCornerShape(24.dp), onClick = viewModel::openAdd, enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp)) {
                    Text("新建" + state.spec.title)
                }
            }
        },
        topBar = {
            PayTopBar(state.spec.title, onBack,
                actions = {
                    if (state.spec.supportsAdd) IconButton(onClick = viewModel::openAdd, enabled = !state.loading) { Icon(Icons.Default.Add, "新增") }
                    if (state.loading) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = { viewModel.load() }) { Icon(Icons.Default.Refresh, "刷新") }
                },
            )
        },
    ) { padding ->
        PaymentDocumentScreen(state, Modifier.padding(padding), viewModel)
    }
    state.testChannel?.let { channel ->
        PaymentTestDialog(
            channel = channel,
            loading = state.testLoading,
            result = state.testResult,
            uncertain = state.testUncertain,
            onDismiss = viewModel::closePaymentTest,
            onSubmit = viewModel::submitPaymentTest,
        )
    }
}

@Composable
private fun PaymentDocumentScreen(state: PaymentAdminState, modifier: Modifier, viewModel: PaymentAdminViewModel) {
    val context = LocalContext.current
    val navigation = com.futoonpay.admin.LocalPanelNavigation.current
    var query by rememberSaveable { mutableStateOf(state.query) }
    var pending by remember { mutableStateOf<NativeAction?>(null) }
    val document = state.document
    fun select(action: NativeAction) {
        when {
            action.endpoint.startsWith("futoon://channels") -> {
                val type = Uri.parse(action.endpoint).getQueryParameter("type").orEmpty()
                navigation.module("channels", channelType = type)
            }
            action.endpoint.startsWith("futoon://channel-detail") -> {
                val id = Uri.parse(action.endpoint).getQueryParameter("id").orEmpty()
                navigation.module("channels", channelId = id)
            }
            action.isEditorAction() -> viewModel.openEditor(action)
            action.destructive -> pending = action
            else -> viewModel.execute(action)
        }
    }

    if (state.spec.id == "channels") {
        PaymentChannelScreen(
            state,
            modifier,
            query,
            { query = it },
            viewModel::selectChannelTab,
            viewModel::selectChannelType,
            viewModel::openChannelDetail,
            viewModel::openPaymentTest,
            { item ->
                navigation.orders(channelId = item.id)
            },
            ::select,
            { viewModel.load() },
            onBatch = viewModel::offerBatch,
        )
    } else if (state.spec.id == "channel_monitor") {
        ChannelMonitorScreen(state, modifier, query, { query = it }, viewModel::load, ::select, viewModel::offerBatch)
    } else if (state.spec.id == "rolls") {
        RollGroupScreen(state, modifier, query, { query = it }, viewModel::selectRollTab, ::select, { viewModel.load() }, viewModel::offerBatch)
    } else if (state.spec.id in setOf("payment_types", "proxies")) {
        ConfigurationListScreen(state, modifier, query, { query = it }, ::select, { viewModel.loadConfigurationContext() }, { viewModel.load() }, viewModel::offerBatch)
    } else LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { OutlinedTextField(query, { query = it }, placeholder = { Text("筛选当前列表名称、ID 或配置") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth() , colors = com.futoonpay.admin.ui.ChatFieldColors()) }
        document?.notices?.forEach { notice -> item { PaymentNotice(notice) } }
        if (!document?.actions.isNullOrEmpty()) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(document?.actions.orEmpty()) { action ->
                    OutlinedButton(shape = RoundedCornerShape(24.dp), onClick = { select(action) }, enabled = !state.loading) { Text(action.label) }
                }
            }
        }
        document?.tables?.forEach { table ->
            item { Text(table.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 5.dp)) }
            itemsIndexed(table.rows) { index, row ->
                if (query.isBlank() || row.any { it.contains(query.trim(), true) }) PaymentRowCard(table.headers, row, table.rowActions.getOrNull(index).orEmpty(), ::select)
            }
        }
        item { ListFeedback(state.loading && document == null, state.loadError, !state.loading && document?.tables?.flatMap { it.rows }?.none { row -> query.isBlank() || row.any { it.contains(query.trim(), true) } } != false, { viewModel.load() }) }
    }
    pending?.let { action ->
        AlertDialog(shape = RoundedCornerShape(20.dp),
            onDismissRequest = { pending = null },
            title = { Text("确认${action.label}") },
            text = { Text(com.futoonpay.admin.data.nativeActionTarget(document, action) + configurationActionAssociations(state, action) + "\n该操作会立即更新后台状态。") },
            confirmButton = { Button(shape = RoundedCornerShape(24.dp), onClick = { viewModel.execute(action); pending = null }) { Text("确认${action.label}") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } },
        )
    }
}

@Composable
internal fun PaymentChannelScreen(state: PaymentAdminState, modifier: Modifier, query: String,
    onQueryChange: (String) -> Unit, onTabChange: (PaymentChannelTab) -> Unit, onTypeChange: (String) -> Unit,
    onOpenDetail: (PaymentChannelItem) -> Unit, onTest: (PaymentChannelItem) -> Unit,
    onOrders: (PaymentChannelItem) -> Unit, onAction: (NativeAction) -> Unit, onRetry: () -> Unit, onBatch: (BatchRequest) -> Unit = {}) {
    val document = state.document
    val channels = document?.tables.orEmpty().flatMap { it.toPaymentChannelItems() }.filter {
        query.isBlank() || it.searchableText().contains(query.trim(), true)
    }
    val selection = rememberRecordSelection(listOf(state.spec.id, state.page, state.channelTab, state.channelType, query), channels.map { it.id })
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PayPills(PaymentChannelTab.entries.map { it.name to it.label }, state.channelTab.name) { name -> onTabChange(PaymentChannelTab.valueOf(name)) } }
        item { OutlinedTextField(query, onQueryChange, placeholder = { Text("搜索名称、ID、支付方式或插件", fontSize = 12.sp) }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true , colors = com.futoonpay.admin.ui.ChatFieldColors()) }
        val filters = document?.paymentChannelTypeFilters().orEmpty()
        if (filters.isNotEmpty()) item { PayTabs(filters.map { it.value to it.label }, state.channelType, onTypeChange) }
        item { Text("${channels.size} 个通道 · 官网状态分类", color = Muted, fontSize = 12.sp) }
        item { MultiSelectBar(selection, channels.map { it.id }, paymentBatchOptions(state.spec.id, channels.filter { it.id in selection.selected }.map { PaymentBatchRow(it.id, "${it.name} #${it.id}", it.actions) }), state.loading, onBatch) }
        itemsIndexed(channels, key = { index, it -> "${it.id}:$index" }) { _, channel -> SelectableRecord(selection, channel.id, "通道 ${channel.id}") { PaymentChannelCard(channel, state.channelTab, onOpenDetail, onTest, onOrders, onAction) } }
        item { ListFeedback(state.loading && document == null, state.loadError, !state.loading && channels.isEmpty(), onRetry) }
    }
}

@Composable
private fun PaymentChannelCard(
    item: PaymentChannelItem,
    activeTab: PaymentChannelTab,
    onOpenDetail: (PaymentChannelItem) -> Unit,
    onTest: (PaymentChannelItem) -> Unit,
    onOrders: (PaymentChannelItem) -> Unit,
    onAction: (NativeAction) -> Unit,
) {
    val displayedStatus = paymentChannelDisplayedStatus(item, activeTab)
    val statusColor = when (displayedStatus) {
        PaymentChannelStatus.ENABLED -> GreenDark
        PaymentChannelStatus.DISABLED -> Danger
        PaymentChannelStatus.UNKNOWN -> Warning
    }
    Card(modifier = Modifier.clickable { onOpenDetail(item) }, colors = CardDefaults.cardColors(containerColor = CardColor), border = CardDefaults.outlinedCardBorder(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(item.name.ifBlank { "未命名支付通道" } + " #${item.id}", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val subtitle = listOfNotNull(
                        item.id.takeIf(String::isNotBlank)?.let { "ID $it" },
                        item.paymentType.takeIf(String::isNotBlank),
                        item.plugin.takeIf(String::isNotBlank),
                    ).joinToString(" · ")
                    if (subtitle.isNotBlank()) Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val toggle = item.actions.firstOrNull { action ->
                    if (displayedStatus == PaymentChannelStatus.ENABLED) action.label.contains("停用") || action.label.contains("关闭") || action.label.contains("禁用")
                    else action.label.contains("启用") || action.label.contains("开启")
                }
                androidx.compose.material3.Switch(checked = displayedStatus == PaymentChannelStatus.ENABLED, onCheckedChange = { toggle?.let { onAction(it.copy(destructive = displayedStatus == PaymentChannelStatus.ENABLED)) } }, enabled = toggle != null)
            }
            if (item.rate.isNotBlank() || item.paymentRange.isNotBlank()) {
                if (item.rate.isNotBlank()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("成本费率", color = Muted, fontSize = 13.sp); Text(item.rate, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                if (item.paymentRange.isNotBlank()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("单笔限额", color = Muted, fontSize = 13.sp); Text(item.paymentRange, fontSize = 13.sp) }
            }
            paymentChannelExtraFields(item).forEach { (label, value) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.width(86.dp))
                    Text(value, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            PayBadge(if (activeTab == PaymentChannelTab.ALL && item.statusText.isNotBlank()) item.statusText else displayedStatus.label, statusColor)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentChannelDetailScreen(
    channel: PaymentChannelItem,
    detail: PaymentChannelDetail?,
    loading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onTest: () -> Unit,
    onOrders: () -> Unit,
    onRelatedType: () -> Unit,
    onAction: (NativeAction) -> Unit,
    onEditorAction: (NativeAction) -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val navigation = com.futoonpay.admin.LocalPanelNavigation.current
    var pending by remember { mutableStateOf<NativeAction?>(null) }
    val statusColor = when (channel.status) {
        PaymentChannelStatus.ENABLED -> GreenDark
        PaymentChannelStatus.DISABLED -> Danger
        PaymentChannelStatus.UNKNOWN -> Warning
    }
    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            PayTopBar("通道详情", onBack,
                actions = {
                    if (loading) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "刷新详情") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = CardColor), border = CardDefaults.outlinedCardBorder(), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(channel.name.ifBlank { "未命名支付通道" }, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text(listOf(channel.paymentType, channel.plugin).filter(String::isNotBlank).joinToString(" · "), color = Muted, fontSize = 12.sp)
                            }
                            Text(
                                channel.statusText.ifBlank { channel.status.label }, color = statusColor,
                                fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(statusColor.copy(alpha = .10f)).padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChannelValueTile("成本费率", channel.rate.ifBlank { "—" }, Modifier.weight(1f))
                            ChannelValueTile("单笔限额", channel.paymentRange.ifBlank { "—" }, Modifier.weight(1f))
                        }
                        detail?.let {
                            ChannelDetailRow("计费模式", it.mode)
                            ChannelDetailRow("单日限额", it.dailyLimit)
                            ChannelDetailRow("单笔最低", it.minimumAmount)
                            ChannelDetailRow("单笔最高", it.maximumAmount)
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = CardColor), border = CardDefaults.outlinedCardBorder(), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("支付插件", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        if (detail == null && loading) Text("正在读取官网插件资料…", color = Muted, fontSize = 12.sp)
                        detail?.let {
                            ChannelDetailRow("插件", it.pluginDescription.ifBlank { it.pluginName.ifBlank { "—" } })
                            ChannelDetailRow("调用值", it.pluginName.ifBlank { channel.plugin.ifBlank { "—" } })
                            if (it.pluginAuthor.isNotBlank()) ChannelDetailRow("作者", it.pluginAuthor)
                            if (it.pluginPath.isNotBlank()) ChannelDetailRow("目录", it.pluginPath)
                            if (it.pluginLink.isNotBlank()) {
                                TextButton(onClick = {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.pluginLink))) }
                                }) { Text("打开插件主页") }
                            }
                        }
                    }
                }
            }
            item {
                Text("业务关联", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(shape = RoundedCornerShape(24.dp), onClick = onTest, enabled = channel.id.isNotBlank(), modifier = Modifier.weight(1f)) { Text("测试支付") }
                    OutlinedButton(shape = RoundedCornerShape(24.dp), onClick = onOrders, enabled = channel.id.isNotBlank(), modifier = Modifier.weight(1f)) { Text("通道订单") }
                }
                OutlinedButton(shape = RoundedCornerShape(24.dp),
                    onClick = onRelatedType,
                    enabled = !detail?.paymentTypeId.isNullOrBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("同支付方式通道") }
            }
            if (channel.actions.isNotEmpty()) item {
                Text("通道管理", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Card(colors = CardDefaults.cardColors(containerColor = CardColor), border = CardDefaults.outlinedCardBorder(), shape = RoundedCornerShape(12.dp)) {
                    LazyRow(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(channel.actions.distinctBy { it.label to it.endpoint }) { action ->
                            TextButton(onClick = {
                                when {
                                    action.isEditorAction() -> onEditorAction(action)
                                    action.destructive -> pending = action
                                    else -> onAction(action)
                                }
                            }) {
                                Text(action.label, color = if (action.destructive) Danger else MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    pending?.let { action ->
        AlertDialog(shape = RoundedCornerShape(20.dp),
            onDismissRequest = { pending = null },
            title = { Text("确认${action.label}") },
            text = { Text("该操作会立即更新通道状态，并可能影响正在处理的支付。") },
            confirmButton = { Button(shape = RoundedCornerShape(24.dp), onClick = { onAction(action); pending = null }) { Text("确认") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ChannelDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.width(72.dp))
        Text(value.ifBlank { "—" }, color = Ink, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PaymentTestDialog(
    channel: PaymentChannelItem,
    loading: Boolean,
    result: PaymentTestResult?,
    uncertain: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val navigation = com.futoonpay.admin.LocalPanelNavigation.current
    var name by rememberSaveable(channel.id) { mutableStateOf("支付测试") }
    var amount by rememberSaveable(channel.id) { mutableStateOf("1") }
    val paymentUrl = result?.paymentUrl.orEmpty().let { if (it.startsWith("//")) "https:$it" else it }
    val canOpen = runCatching {
        Uri.parse(paymentUrl).scheme?.lowercase() in setOf("http", "https")
    }.getOrDefault(false)
    val qr = remember(result?.qrContent, paymentUrl) { com.futoonpay.admin.data.paymentQr(result?.qrContent?.ifBlank { paymentUrl }.orEmpty()) }
    com.futoonpay.admin.ui.PaySheet("原生测试支付", { if (!loading) onDismiss() }) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 520.dp), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                item {
                Text("${channel.name} · 通道 #${channel.id}", color = Muted, fontSize = 12.sp)
                }
                item {
                OutlinedTextField(name, { name = it }, label = { Text("订单名称") }, singleLine = true, modifier = Modifier.fillMaxWidth() , colors = com.futoonpay.admin.ui.ChatFieldColors())
                }
                item {
                OutlinedTextField(amount, { amount = it }, label = { Text("测试金额") }, singleLine = true, modifier = Modifier.fillMaxWidth() , colors = com.futoonpay.admin.ui.ChatFieldColors())
                }
                result?.let { item {
                    Text(it.message, color = if (it.success) GreenDark else Danger, fontSize = 12.sp)
                    if (it.tradeNo.isNotBlank()) androidx.compose.foundation.text.selection.SelectionContainer { Text("订单号 ${it.tradeNo}", fontSize = 12.sp) }
                    if (it.success && qr != null) androidx.compose.foundation.Image(qr.asImageBitmap(), "支付二维码", Modifier.fillMaxWidth().height(220.dp))
                    if (it.success && canOpen) OutlinedButton(shape = RoundedCornerShape(24.dp),
                        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(paymentUrl))) }.onFailure {
                            android.widget.Toast.makeText(context, "未找到可打开付款页的应用，可复制付款链接", android.widget.Toast.LENGTH_LONG).show()
                        } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("打开付款页") }
                    if (it.success && paymentUrl.isNotBlank()) TextButton(onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("付款链接", paymentUrl))
                        android.widget.Toast.makeText(context, "已复制付款链接", android.widget.Toast.LENGTH_SHORT).show()
                    }) { Text("复制付款链接") }
                } }
                item {
                Text("创建测试订单会使用官网同一接口；只有点击确认后才会提交。", color = Muted, fontSize = 12.sp)
                }
            }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(shape = RoundedCornerShape(24.dp), onClick = onDismiss, enabled = !loading, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("关闭") }
            Button(shape = RoundedCornerShape(24.dp), onClick = { if (uncertain) { onDismiss(); navigation.orders(channelId = channel.id) } else onSubmit(name, amount) }, enabled = !loading && name.isNotBlank() && com.futoonpay.admin.data.validRefundAmount(amount, "999999999.99"), modifier = Modifier.weight(2f).heightIn(min = 48.dp)) {
                if (loading) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp) else Text(if (uncertain) "在订单中核对" else "创建测试订单")
            }
        }
    }
}

@Composable
private fun ChannelValueTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(10.dp)) {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(value, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PaymentChannelSortPicker(
    label: String,
    value: PaymentChannelSortDimension,
    onSelect: (PaymentChannelSortDimension) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, color = Muted, fontSize = 12.sp)
                Text(value.label, color = Ink, fontSize = 12.sp, maxLines = 1)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PaymentChannelSortDimension.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@Composable
private fun PaymentChannelGroupPicker(
    value: PaymentChannelGroupDimension,
    onSelect: (PaymentChannelGroupDimension) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text("分组维度", color = Muted, fontSize = 12.sp)
                Text(value.label, color = Ink, fontSize = 12.sp)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PaymentChannelGroupDimension.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

private fun paymentChannelDisplayedStatus(item: PaymentChannelItem, activeTab: PaymentChannelTab): PaymentChannelStatus = when (activeTab) {
    PaymentChannelTab.RUNNING -> PaymentChannelStatus.ENABLED
    PaymentChannelTab.DISABLED -> PaymentChannelStatus.DISABLED
    PaymentChannelTab.PENDING -> PaymentChannelStatus.UNKNOWN
    PaymentChannelTab.ALL -> item.status
}

private fun paymentChannelGroupLabel(
    item: PaymentChannelItem,
    dimension: PaymentChannelGroupDimension,
    activeTab: PaymentChannelTab,
): String = if (dimension == PaymentChannelGroupDimension.STATUS) {
    paymentChannelDisplayedStatus(item, activeTab).label
} else item.groupLabel(dimension)

private fun paymentChannelExtraFields(item: PaymentChannelItem): List<Pair<String, String>> {
    val hiddenTokens = listOf("id", "编号", "名称", "支付方式", "支付类型", "插件", "扣点", "费率", "范围", "限额", "状态", "操作")
    return item.fields.filterNot { (label, _) -> hiddenTokens.any { label.contains(it, ignoreCase = true) } }.take(4)
}

@Composable
internal fun RollGroupScreen(state: PaymentAdminState, modifier: Modifier, query: String, onQueryChange: (String) -> Unit,
    onTabChange: (RollGroupTab) -> Unit, onAction: (NativeAction) -> Unit, onRetry: () -> Unit, onBatch: (BatchRequest) -> Unit = {}) {
    val document = state.document
    val groups = document?.tables.orEmpty().flatMap { it.toRollGroupItems() }.filter { query.isBlank() || it.searchableText().contains(query.trim(), true) }
    val selection = rememberRecordSelection(listOf(state.spec.id, state.page, state.rollTab, query), groups.map { it.id })
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PayPills(RollGroupTab.entries.map { it.name to it.label }, state.rollTab.name) { onTabChange(RollGroupTab.valueOf(it)) } }
        item { OutlinedTextField(query, onQueryChange, placeholder = { Text("搜索名称、ID、支付方式", fontSize = 12.sp) }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth() , colors = com.futoonpay.admin.ui.ChatFieldColors()) }
        item { Text("${groups.size} 个轮询组 · 官网状态分类", color = Muted, fontSize = 12.sp) }
        item { MultiSelectBar(selection, groups.map { it.id }, paymentBatchOptions(state.spec.id, groups.filter { it.id in selection.selected }.map { PaymentBatchRow(it.id, "${it.name} #${it.id}", it.actions) }), state.loading, onBatch) }
        itemsIndexed(groups, key = { index, it -> "${it.id}:$index" }) { _, group -> SelectableRecord(selection, group.id, "轮询组 ${group.id}") { RollGroupCard(group, state.rollTab, onAction) } }
        item { ListFeedback(state.loading && document == null, state.loadError, !state.loading && groups.isEmpty(), onRetry) }
    }
}

@Composable
private fun RollGroupCard(item: RollGroupItem, activeTab: RollGroupTab, onAction: (NativeAction) -> Unit) {
    val displayedStatus = when (activeTab) {
        RollGroupTab.RUNNING -> RollGroupStatus.ENABLED
        RollGroupTab.DISABLED -> RollGroupStatus.DISABLED
        RollGroupTab.PENDING -> RollGroupStatus.UNKNOWN
        RollGroupTab.ALL -> item.status
    }
    val statusColor = when (displayedStatus) {
        RollGroupStatus.ENABLED -> GreenDark
        RollGroupStatus.DISABLED -> Danger
        RollGroupStatus.UNKNOWN -> Warning
    }
    Card(colors = CardDefaults.cardColors(containerColor = CardColor), border = CardDefaults.outlinedCardBorder(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(item.name.ifBlank { "未命名轮询组" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val subtitle = listOfNotNull(
                        item.id.takeIf(String::isNotBlank)?.let { "ID $it" },
                        item.paymentType.takeIf(String::isNotBlank),
                        item.mode.takeIf(String::isNotBlank),
                    ).joinToString(" · ")
                    if (subtitle.isNotBlank()) Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    if (activeTab == RollGroupTab.ALL && item.statusText.isNotBlank()) item.statusText else displayedStatus.label,
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(statusColor.copy(alpha = .10f)).padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
            if (item.routeSummary.isNotBlank()) {
                Text(
                    "路由配置：${item.routeSummary}",
                    color = Ink,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(10.dp),
                )
            }
            rollExtraFields(item).forEach { (label, value) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.width(86.dp))
                    Text(value, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (item.actions.isNotEmpty()) {
                HorizontalDivider(color = Line)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(item.actions.distinctBy { it.label to it.endpoint }) { action ->
                        TextButton(onClick = { onAction(action) }) {
                            Text(action.label, color = if (action.destructive) Danger else MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RollSortPicker(label: String, value: RollSortDimension, onSelect: (RollSortDimension) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, color = Muted, fontSize = 12.sp)
                Text(value.label, color = Ink, fontSize = 12.sp, maxLines = 1)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RollSortDimension.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@Composable
private fun RollGroupPicker(value: RollGroupDimension, onSelect: (RollGroupDimension) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text("分组维度", color = Muted, fontSize = 12.sp)
                Text(value.label, color = Ink, fontSize = 12.sp)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RollGroupDimension.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

private fun rollExtraFields(item: RollGroupItem): List<Pair<String, String>> {
    val hiddenTokens = listOf("id", "编号", "名称", "支付方式", "支付类型", "模式", "状态", "路由", "通道")
    return item.fields.filterNot { (label, _) -> hiddenTokens.any { label.contains(it, ignoreCase = true) } }.take(5)
}

@Composable
internal fun ChannelMonitorScreen(state: PaymentAdminState, modifier: Modifier, query: String,
    onQueryChange: (String) -> Unit, onLoad: (Int, String) -> Unit, onAction: (NativeAction) -> Unit, onBatch: (BatchRequest) -> Unit = {}) {
    var window by rememberSaveable { mutableStateOf(MonitorWindow.FIVE_MINUTES) }
    val document = state.document
    val channels = document?.tables.orEmpty().flatMap { it.toChannelMonitorItems() }
    val selection = rememberRecordSelection(listOf(state.spec.id, state.page, state.query, query), channels.map { it.id })
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PayPills(MonitorWindow.entries.map { it.name to it.label }, window.name) { window = MonitorWindow.valueOf(it) } }
        item { OutlinedTextField(query, onQueryChange, placeholder = { Text("搜索通道 ID") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            trailingIcon = { IconButton(onClick = { onLoad(1, query) }) { Icon(Icons.Default.Search, "搜索通道") } } , colors = com.futoonpay.admin.ui.ChatFieldColors()) }
        item { Text("近${window.label} · ${channels.size} 个通道", color = Muted, fontSize = 12.sp) }
        item { MultiSelectBar(selection, channels.map { it.id }, paymentBatchOptions(state.spec.id, channels.filter { it.id in selection.selected }.map { PaymentBatchRow(it.id, "${it.name} #${it.id}", it.actions) }), state.loading, onBatch) }
        itemsIndexed(channels, key = { index, it -> "${it.id}:$index" }) { _, item ->
            val metrics = item.metrics(window)
            SelectableRecord(selection, item.id, "通道 ${item.id}") {
            PayCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name.ifBlank { "通道 #${item.id}" } + " #${item.id}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    PayBadge(item.enabledLabel(), if (item.enabledLabel() == "停用") Muted else GreenDark)
                }
                Text(metrics.rate?.let(::formatMonitorRate) ?: if (metrics.total == 0) "暂无样本" else "暂无统计", color = GreenDark, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text("成功率 · 近${window.label}", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("成功 ${metrics.successful ?: "—"}", color = Muted, fontSize = 12.sp)
                    Text("总订单 ${metrics.total ?: "—"}", color = Muted, fontSize = 12.sp)
                    Text("处理中 ${metrics.processing ?: "—"}", color = Muted, fontSize = 12.sp)
                }
                HorizontalDivider(color = Line)
                Text("最近检测：${item.result.ifBlank { "未检测" }}${item.lastChecked.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}", color = Muted, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(item.actions.distinctBy { it.label to it.endpoint }) { action -> TextButton(onClick = { onAction(action) }) { Text(action.label, color = if (action.destructive) Danger else GreenDark, fontSize = 12.sp) } }
                    item { TextButton(onClick = { onAction(NativeAction("通道详情", "futoon://channel-detail?id=${item.id}")) }) { Text("通道详情", fontSize = 12.sp) } }
                }
            }
            }
        }
        item { ListFeedback(state.loading && channels.isEmpty(), state.loadError, !state.loading && channels.isEmpty(), { onLoad(state.page, query) }) }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(shape = RoundedCornerShape(24.dp), onClick = { onLoad(state.page - 1, query) }, enabled = state.page > 1 && !state.loading) { Text("上一页") }
            Text("第 ${state.page} 页", color = Muted, fontSize = 12.sp)
            OutlinedButton(shape = RoundedCornerShape(24.dp), onClick = { onLoad(state.page + 1, query) }, enabled = channels.size >= FutoonRepository.PAGE_SIZE && !state.loading) { Text("下一页") }
        } }
    }
}

@Composable
private fun MonitorMetricCard(label: String, value: String, color: Color) {
    Card(
        modifier = Modifier.width(116.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, color = Muted, fontSize = 12.sp, maxLines = 1)
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 19.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ChannelMonitorCard(item: ChannelMonitorItem, onAction: (NativeAction) -> Unit) {
    val healthColor = when (item.health) {
        MonitorHealth.HEALTHY -> GreenDark
        MonitorHealth.ABNORMAL -> Danger
        MonitorHealth.UNKNOWN -> Warning
        MonitorHealth.DISABLED -> Muted
    }
    Card(colors = CardDefaults.cardColors(containerColor = CardColor), border = CardDefaults.outlinedCardBorder(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(item.name.ifBlank { item.id.takeIf(String::isNotBlank)?.let { "通道 #$it" } ?: "未命名通道" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val subtitle = listOfNotNull(
                        item.id.takeIf(String::isNotBlank)?.let { "ID $it" },
                        item.paymentType.takeIf(String::isNotBlank),
                        item.plugin.takeIf(String::isNotBlank),
                    ).joinToString(" · ")
                    if (subtitle.isNotBlank()) Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    item.health.label,
                    color = healthColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(healthColor.copy(alpha = .10f)).padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
            if (listOf(item.fiveMinuteRate, item.thirtyMinuteRate, item.oneHourRate, item.oneDayRate).any { it != null }) {
                HorizontalDivider(color = Line)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    MonitorRateCell("5 分钟", item.fiveMinuteRate, Modifier.weight(1f))
                    MonitorRateCell("30 分钟", item.thirtyMinuteRate, Modifier.weight(1f))
                    MonitorRateCell("1 小时", item.oneHourRate, Modifier.weight(1f))
                    MonitorRateCell("24 小时", item.oneDayRate, Modifier.weight(1f))
                }
            }
            if (item.result.isNotBlank()) Text("监测结果：${item.result}", color = healthColor, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (item.lastChecked.isNotBlank()) Text("最近检测：${item.lastChecked}", color = Muted, fontSize = 12.sp)
            monitorExtraFields(item).forEach { (label, value) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.width(86.dp))
                    Text(value, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (item.id.isNotBlank() || item.actions.isNotEmpty()) {
                HorizontalDivider(color = Line)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (item.id.isNotBlank()) item {
                        TextButton(onClick = {
                            onAction(NativeAction("通道详情", "futoon://channel-detail?id=${item.id}"))
                        }) { Text("通道详情", fontSize = 12.sp) }
                    }
                    items(item.actions.distinctBy { it.label to it.endpoint }) { action ->
                        TextButton(onClick = { onAction(action) }) {
                            Text(action.label, color = if (action.destructive) Danger else MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitorRateCell(label: String, rate: Double?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Muted, fontSize = 12.sp, maxLines = 1)
        Text(rate?.let(::formatMonitorRate) ?: "—", color = monitorRateColor(rate), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun MonitorSortPicker(label: String, value: MonitorSortDimension, onSelect: (MonitorSortDimension) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, color = Muted, fontSize = 12.sp)
                Text(value.label, color = Ink, fontSize = 12.sp, maxLines = 1)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MonitorSortDimension.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@Composable
private fun MonitorGroupPicker(value: MonitorGroupDimension, onSelect: (MonitorGroupDimension) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text("分组维度", color = Muted, fontSize = 12.sp)
                Text(value.label, color = Ink, fontSize = 12.sp)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MonitorGroupDimension.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@Composable
private fun SortDirectionButton(ascending: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.width(76.dp).height(56.dp), shape = RoundedCornerShape(24.dp)) {
        Text(if (ascending) "升序" else "降序", fontSize = 12.sp)
    }
}

private fun formatMonitorRate(rate: Double): String = if (rate % 1.0 == 0.0) "${rate.toInt()}%" else "%.1f%%".format(rate)

@Composable
private fun monitorRateColor(rate: Double?): Color = when {
    rate == null -> Muted
    rate >= 90 -> GreenDark
    rate >= 70 -> Warning
    else -> Danger
}

private fun monitorExtraFields(item: ChannelMonitorItem): List<Pair<String, String>> {
    val hiddenTokens = listOf("id", "名称", "通道", "支付方式", "插件", "状态", "结果", "检测", "成功率")
    return item.fields.filterNot { (label, _) -> hiddenTokens.any { label.contains(it, ignoreCase = true) } }.take(4)
}

@Composable
private fun PaymentRowCard(headers: List<String>, row: List<String>, actions: List<NativeAction>, onAction: (NativeAction) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CardColor), border = CardDefaults.outlinedCardBorder(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEachIndexed { index, value ->
                if (value.isNotBlank()) Row(Modifier.fillMaxWidth()) {
                    Text(headers.getOrNull(index)?.ifBlank { "字段 ${index + 1}" } ?: "字段 ${index + 1}", color = Muted, fontSize = 12.sp, modifier = Modifier.width(96.dp))
                    Text(com.futoonpay.admin.data.nativeVisibleValue(headers.getOrNull(index).orEmpty(), value), fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            if (actions.isNotEmpty()) {
                HorizontalDivider(color = Line)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(actions.distinctBy { it.label to it.endpoint }) { action ->
                        TextButton(onClick = { onAction(action) }) {
                            Text(action.label, color = if (action.destructive) Danger else MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentNotice(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(10.dp)) {
        Text(text, Modifier.padding(13.dp), color = Ink, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaymentEditorScreen(
    form: NativeForm,
    loading: Boolean,
    onBack: () -> Unit,
    onFieldChange: (String, String) -> Unit,
    onSubmit: (NativeForm, Map<String, String>) -> Unit,
    onOpenChannel: (String) -> Unit,
    snackbar: SnackbarHostState,
    routeChannels: Map<String, PaymentChannelItem>,
    routeStats: Map<String, com.futoonpay.admin.data.OrderChannelStat>,
    onAddRoute: () -> Unit,
    configurationModule: String = "",
    configurationChannels: List<com.futoonpay.admin.data.ConfigurationChannel> = emptyList(),
    configurationLoading: Boolean = false,
    configurationError: String? = null,
    onReloadConfiguration: () -> Unit = {},
    uncertain: Boolean = false,
) {
    val values = form.fields.associate { it.name to it.value }
    val routeItems = form.toRollRouteVisualItems()
    val isRollRoute = form.action.contains("saveRollInfo", ignoreCase = true) || form.fields.any { it.name.startsWith("list[") }
    var pluginChange by remember { mutableStateOf<String?>(null) }
    val adding = form.fields.firstOrNull { it.name == "action" }?.value == "add"
    val missingRequired = form.fields.any {
        it.required && !it.readOnly && values[it.name].isNullOrBlank() && (!it.sensitive || adding)
    } || form.fields.any { it.name == "plugin" && (it.value.isBlank() || it.options.none { option -> option.value == it.value }) }
    val channelFields = form.fields.filter { it.name.matches(Regex("list\\[\\d+]\\[channel]")) }
    var visibleRoutes by rememberSaveable(form.action) {
        mutableIntStateOf((channelFields.indexOfLast { it.value.isNotBlank() && it.value != "0" } + 1).coerceAtLeast(1))
    }
    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(color = CardColor) { Row(Modifier.fillMaxWidth().imePadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(shape = RoundedCornerShape(24.dp), onClick = onBack, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("取消") }
                Button(shape = RoundedCornerShape(24.dp), onClick = {
                    val submitted = values.filter { (name, value) -> val field = form.fields.first { it.name == name }; !field.readOnly && (!field.sensitive || value.isNotBlank()) }
                    onSubmit(form, submitted)
                }, enabled = !loading && !missingRequired && !uncertain, modifier = Modifier.weight(2f).heightIn(min = 48.dp)) { Text(if (loading) "处理中…" else "保存") }
            } }
        },
        topBar = {
            PayTopBar(form.title, onBack, actions = { if (loading) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp) })
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().testTag("payment-editor-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (uncertain) item { PayCard { Text("保存结果待核对", color = Warning); Text("请取消返回列表并刷新核对后台状态，避免重复创建或修改。", color = Muted, fontSize = 13.sp) } }
            if (isRollRoute) {
                if (!form.action.contains("saveRollInfo")) {
                    item { SectionLabel("基本信息") }
                    item { PayCard { form.fields.filter { it.type != "hidden" && !it.name.startsWith("list[") }.forEach { field -> NativeFieldEditor(field, field.value) { onFieldChange(field.name, it) } } } }
                }
                item { SectionLabel("成员通道 · 权重") }
                items(channelFields.take(visibleRoutes), key = { it.name }) { field ->
                    val index = Regex("list\\[(\\d+)]").find(field.name)?.groupValues?.get(1).orEmpty()
                    val weightField = form.fields.firstOrNull { it.name == "list[$index][weight]" }
                    val channel = routeChannels[field.value]
                    val stat = routeStats[field.value]
                    PayCard {
                        NativeFieldEditor(field, field.value) { onFieldChange(field.name, it) }
                        weightField?.let { NativeFieldEditor(it, it.value) { value -> onFieldChange(it.name, value) } }
                        if (field.value.isNotBlank() && field.value != "0") {
                            HorizontalDivider(color = Line)
                            DataRow("近5分钟成功率", stat?.fiveMinuteRate?.let(::formatMonitorRate) ?: "暂无统计")
                            DataRow("单笔限额", channel?.paymentRange ?: "—")
                            DataRow("成本费率", channel?.rate ?: "—")
                            TextButton(onClick = { onFieldChange(field.name, "") }) { Text("移除此成员", color = Danger) }
                        }
                    }
                }
                item { OutlinedButton(shape = RoundedCornerShape(24.dp), onClick = {
                    if (visibleRoutes >= channelFields.size) onAddRoute()
                    visibleRoutes++
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("添加通道") } }
                item { RollRouteVisualization(routeItems, onOpenChannel) }
            } else if (configurationModule in setOf("payment_types", "proxies")) {
                item { ConfigurationEditorFields(form, configurationModule, configurationChannels, configurationLoading || loading, configurationError, onFieldChange, onOpenChannel, onReloadConfiguration) }
            } else {
                item { SectionLabel("基本信息") }
                val groups = form.fields.filter { it.type != "hidden" }.groupBy { it.name.startsWith("account::") }
                groups.forEach { (account, fields) ->
                    if (account) item { SectionLabel("账户配置 · 随支付插件变化") }
                    item { PayCard { fields.forEach { field -> NativeFieldEditor(field, values[field.name].orEmpty()) {
                        if (field.name == "plugin" && field.value.isNotBlank() && field.value != it && form.fields.any { f -> f.name.startsWith("account::") }) pluginChange = it
                        else onFieldChange(field.name, it)
                    } } } }
                }
            }
            if (form.fields.any { it.sensitive }) item { Text("密码与密钥留空时保留原值", color = Muted, fontSize = 12.sp) }
            item { Text("非敏感配置自动保存草稿，提交成功后清除", color = Muted, fontSize = 12.sp) }
        }
    }
    pluginChange?.let { next -> AlertDialog(shape = RoundedCornerShape(20.dp), onDismissRequest = { pluginChange = null },
        title = { Text("更换支付插件") }, text = { Text("更换后会重新加载账户配置字段，当前账户输入将清空。") },
        confirmButton = { Button(shape = RoundedCornerShape(24.dp), onClick = { onFieldChange("plugin", next); pluginChange = null }) { Text("更换插件") } },
        dismissButton = { TextButton(onClick = { pluginChange = null }) { Text("保留当前插件") } }) }
}

@Composable
private fun RollRouteVisualization(items: List<RollRouteVisualItem>, onOpenChannel: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardColor),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text("轮询路由可视化", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (items.isEmpty()) {
                Text("选择通道并填写权重后，这里会实时显示路由比例。", color = Muted, fontSize = 12.sp)
            } else items.forEach { route ->
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(route.channelLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("通道 #${route.channelId} · 权重 ${route.weight}", color = Muted, fontSize = 12.sp)
                        }
                        Text("${(route.fraction * 100).toInt()}%", color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        TextButton(onClick = { onOpenChannel(route.channelId) }) { Text("详情", fontSize = 12.sp) }
                    }
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)).background(Line)) {
                        Box(
                            Modifier.fillMaxHeight()
                                .fillMaxWidth(route.fraction.coerceIn(0f, 1f))
                                .background(GreenDark)
                        )
                    }
                }
            }
            Text("这是配置比例，不代表真实成交占比；顺序模式按配置顺序切换。", color = Muted, fontSize = 12.sp)
        }
    }
}

private fun NativeAction.isEditorAction(): Boolean =
    label.contains("新增") || label.contains("添加") || label.contains("编辑") || label.contains("修改") ||
        label.contains("配置") || label.contains("账户") || label.contains("路由")
