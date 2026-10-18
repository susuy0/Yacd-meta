package com.futoonpay.admin

import android.content.Context
import android.widget.FrameLayout
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.*
import com.futoonpay.admin.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

interface NativePanelBridge {
    fun address(): String
    fun username(): String
    fun hasSession(): Boolean
    fun session(): PanelSession
    fun accountScope(): String
    @Throws(Exception::class) fun configure(address: String, username: String)
    @Throws(Exception::class) fun call(operation: String, params: String): String
    fun collapse()
    fun copy(text: String)
    fun selectEvidence(order: String, caption: String)
    fun pickEvidenceFromChat()
    fun openGallery()
    fun evidencePaths(): Array<String>
    fun evidencePreview(path: String): android.graphics.Bitmap?
    fun editEvidence(index: Int)
    fun removeEvidence(index: Int)
    fun sendEvidence(order: String, caption: String)
}

internal data class RecognizedQuery(val value: String, val kind: String, val id: String = UUID.randomUUID().toString(), val source: MessageSource = MessageSource(), val ocr: Boolean = false, val error: String = "", val pointers: Map<String, MessageSource> = emptyMap())
internal data class ChatScope(val label: String = "当前会话", val telegramId: String = "", val source: String = "", val key: String = "", val dialog: Long = 0, val topic: Long = 0, val ticket: String = "", val role: String = "")

/** Limit cached modules while retaining navigation and editable state across tab switches. */
internal class PanelRouteModels {
    private val owners = linkedMapOf<String, ViewModelStoreOwner>()
    fun owner(key: String): ViewModelStoreOwner {
        owners.remove(key)?.let { owners[key] = it; return it }
        while (owners.size >= 16) owners.remove(owners.keys.first())?.viewModelStore?.clear()
        return object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }.also { owners[key] = it }
    }
    fun clear() { owners.values.forEach { it.viewModelStore.clear() }; owners.clear() }
}

/** Owns lifecycle, state, back handling and original Android UI inside the Telegram panel. */
class NativePanelView(context: Context, private val bridge: NativePanelBridge) : FrameLayout(context), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, OnBackPressedDispatcherOwner {
    private val registry = LifecycleRegistry(this)
    private val stateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry get() = stateController.savedStateRegistry
    override val onBackPressedDispatcher = OnBackPressedDispatcher { bridge.collapse() }
    private val compose = ComposeView(context)
    private val composition = PanelComposition(compose, registry)
    private val environment = EmbeddedEnvironment(bridge)
    private val navigation = PanelNavigator()
    private var chat by mutableStateOf(ChatScope())
    private var query by mutableStateOf<RecognizedQuery?>(null)
    private var evidenceRevision by mutableStateOf(0)
    private var closed = false

    init {
        stateController.performAttach()
        stateController.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
        setViewTreeLifecycleOwner(this)
        setViewTreeViewModelStoreOwner(this)
        setViewTreeSavedStateRegistryOwner(this)
        compose.id = android.view.View.generateViewId()
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        compose.setContent {
            CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides this) {
                FutoonTheme { EmbeddedPanelRoot(environment, bridge, navigation, chat, query, evidenceRevision) }
            }
        }
        addView(compose, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!closed) composition.prepare()
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (!closed) composition.prepare()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
    fun updateContext(label: String?, telegramId: String?, source: String?) { chat = ChatScope(label.orEmpty(), telegramId.orEmpty(), source.orEmpty()) }
    fun updateChatContext(label: String, telegramId: String, source: String, key: String, dialog: Long, topic: Long, ticket: String, role: String) { chat = ChatScope(label, telegramId, source, key, dialog, topic, ticket, role) }
    fun recognize(text: String?) { recognizeText(text.orEmpty(), chat.dialog, chat.topic, 0, false) }
    fun recognizeText(text: String, dialog: Long, topic: Long, message: Long, ocr: Boolean) { navigation.showRecognition(); query = RecognizedQuery(text, "AUTO", source = MessageSource(dialog, topic, message), ocr = ocr) }
    fun recognizeMessageBatch(text: String, sources: String) {
        val values = JSONObject(sources)
        navigation.showRecognition()
        query = RecognizedQuery(text, "AUTO", pointers = values.keys().asSequence().associateWith { MessageSource.from(values.optJSONObject(it)) })
    }
    fun recognitionError(message: String) { navigation.showRecognition(); query = RecognizedQuery("", "AUTO", error = message) }
    fun recognizeCandidate(value: String?, kind: String?) { navigation.showRecognition(); query = RecognizedQuery(value.orEmpty(), kind.orEmpty()) }
    fun updateEvidenceCount(count: Int) { evidenceRevision++ }
    fun adoptEvidence(order: String, caption: String) { navigatorEvidence(order, caption) }
    private fun navigatorEvidence(order: String, caption: String) { navigation.push(PanelRoute("evidence", module = JSONObject(order).put("_caption", caption).toString())) }
    fun handleBack(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
    fun onShown() { if (!closed) registry.currentState = Lifecycle.State.RESUMED }
    fun onHidden() { if (!closed) registry.currentState = Lifecycle.State.CREATED }
    fun close() { if (closed) return; closed = true; registry.currentState = Lifecycle.State.DESTROYED; viewModelStore.clear(); composition.close() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun EmbeddedPanelRoot(environment: PanelEnvironment, bridge: NativePanelBridge, navigator: PanelNavigator, chat: ChatScope, query: RecognizedQuery?, evidenceRevision: Int) {
    val auth: AppViewModel = viewModel(key = "session", factory = AppViewModel.Factory(environment))
    val state by auth.state.collectAsState()
    val sessionKey = state.adminUrl + ":" + state.accountName + ":" + bridge.accountScope()
    val sessionOwner = remember(sessionKey) { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
    DisposableEffect(sessionOwner) { onDispose { sessionOwner.viewModelStore.clear() } }
    val workflows: PanelWorkflows = viewModel(viewModelStoreOwner = sessionOwner, key = "workflows:$sessionKey", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = PanelWorkflows(bridge) as T
    })
    val contextModel: AppViewModel = viewModel(viewModelStoreOwner = sessionOwner, key = "context-merchant:$sessionKey", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(environment, loadOnStart = false) as T
    })
    val merchantActions = remember(contextModel) { CurrentMerchantActions(contextModel) }
    val workflowState by workflows.state.collectAsState()
    val panelLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(workflows, panelLifecycle, state.authStage) {
        if (state.authStage == AuthStage.AUTHENTICATED) panelLifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) { workflows.refreshDeliveries().join(); kotlinx.coroutines.delay(5000) }
        }
    }
    LaunchedEffect(chat, workflows, state.authStage) { if (state.authStage == AuthStage.AUTHENTICATED) workflows.updateChat(chat) }
    LaunchedEffect(query?.id, workflows) { query?.let(workflows::receive) }
    LaunchedEffect(workflowState.merchant) { workflowState.merchant?.let { contextModel.setCurrentMerchant(JSONObject(it).toMerchantSummary()) } }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(workflowState.notice) { workflowState.notice?.let { snackbar.showSnackbar(it); workflows.consumeNotice() } }
    LaunchedEffect(workflowState.error) { workflowState.error?.let { snackbar.showSnackbar(it) } }
    val models = remember(state.adminUrl, state.accountName, state.authStage) { PanelRouteModels() }
    DisposableEffect(models) { onDispose { models.clear() } }
    val navigation = remember(navigator, bridge, workflows) { object : PanelNavigation by navigator {
        override fun evidence(order: OrderSummary) {
            val data = workflows.rawOrder(order.tradeNo).put("trade_no", order.tradeNo).put("out_trade_no", order.merchantTradeNo).put("uid", order.merchantId).put("money", order.amount).put("currency", order.currency)
            if (!data.has("_source")) data.put("_source", MessageSource().json())
            val caption = evidenceCaption(data)
            bridge.selectEvidence(data.toString(), caption)
            navigator.push(PanelRoute("evidence", module = data.put("_caption", caption).toString()))
        }
    } }
    BackHandler { if (!navigator.pop()) bridge.collapse() }
    CompositionLocalProvider(LocalPanelNavigation provides navigation, LocalPanelWorkflows provides workflows, LocalWorkflowCopy provides bridge::copy) {
        PanelOverlayHost {
            Column(Modifier.fillMaxSize().background(Mist)) {
                Row(Modifier.fillMaxWidth().height(36.dp).padding(start = 12.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (chat.telegramId.isBlank()) chat.label.ifBlank { "FUTOON PAY" } else "${chat.label} · TG ${chat.telegramId}", Modifier.weight(1f), color = Muted, fontSize = 10.sp, maxLines = 1)
                    IconButton(onClick = bridge::collapse, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Close, "收起悬浮窗", Modifier.size(19.dp)) }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (state.authStage) {
                        AuthStage.CHECKING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        AuthStage.LOGIN -> ModernLogin(state, auth::connectBackend, auth::login, auth::refreshCaptcha)
                        AuthStage.AUTHENTICATED -> {
                            val holder = rememberSaveableStateHolder()
                            val route = navigator.current
                            holder.SaveableStateProvider(route.id) {
                                EmbeddedRoute(route, environment, auth, navigator, bridge, chat, evidenceRevision, snackbar, models, workflows, merchantActions)
                            }
                        }
                    }
                }
                if (state.authStage == AuthStage.AUTHENTICATED) NavigationBar(containerColor = androidx.compose.ui.graphics.Color.White, modifier = Modifier.height(64.dp), windowInsets = WindowInsets(0, 0, 0, 0)) {
                    listOf(Triple("recognition", "识别", Icons.Default.DocumentScanner), Triple("orders", "订单", Icons.Default.ReceiptLong), Triple("merchants", "商户", Icons.Default.Storefront), Triple("settlements", "结算", Icons.Default.AccountBalanceWallet), Triple("more", "更多", Icons.Default.GridView)).forEach { (id, title, icon) ->
                        NavigationBarItem(selected = navigator.tab == id, onClick = { navigator.select(id) }, icon = { Icon(icon, title, Modifier.size(20.dp)) }, label = { Text(title, fontSize = 11.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = GreenDark, selectedTextColor = GreenDark, indicatorColor = GreenTint, unselectedIconColor = Muted, unselectedTextColor = Muted))
                    }
                }
            }
            if (state.authStage == AuthStage.AUTHENTICATED) CurrentMerchantDialogs(merchantActions, workflows, chat)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { SnackbarHost(snackbar) }
        }
    }
}

@Composable private fun EmbeddedRoute(route: PanelRoute, environment: PanelEnvironment, auth: AppViewModel, navigator: PanelNavigator, bridge: NativePanelBridge, chat: ChatScope, evidenceRevision: Int, snackbar: SnackbarHostState, models: PanelRouteModels, workflows: PanelWorkflows, merchantActions: CurrentMerchantActions) {
    val back = { if (!navigator.pop()) bridge.collapse() }
    val authState by auth.state.collectAsState()
    val modelKey = environment.adminBaseUrl + ":" + authState.accountName + ":" + route.id
    val owner = models.owner(modelKey)
    if (route.kind == "recognition") { RecognitionScreen(workflows, merchantActions, chat, navigator, bridge); return }
    if (route.kind == "merchant_records") { MerchantRecordsScreen(route, workflows, back); return }
    if (route.kind == "merchant_opening") {
        val address = environment.adminBaseUrl
        val account = bridge.accountScope()
        suspend fun callOpening(operation: String, params: String = "{}"): String = withContext(Dispatchers.IO) {
            check(bridge.address() == address && bridge.accountScope() == account) { "账号或后台已变化，请重新打开开户" }
            val result = bridge.call(operation, params)
            check(bridge.address() == address && bridge.accountScope() == account) { "账号或后台已变化，请重新打开开户" }
            result
        }
        val opening: MerchantOpeningViewModel = viewModel(viewModelStoreOwner = owner, key = modelKey, factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = MerchantOpeningViewModel(environment.repository, chat,
                config = { ChatToolsConfig.parse(callOpening("chat.tools.read")) },
                writeDraft = { text, target ->
                    val result = JSONObject(callOpening("ui.draft", JSONObject().put("text", text).put("context", target.key).put("dialog", target.dialog.toString()).put("topic", target.topic.toString()).toString()))
                    check(result.optBoolean("inserted")) { "当前输入框无法写入，请核对会话后重试" }
                }) as T
        })
        val openingState by opening.state.collectAsState()
        LaunchedEffect(opening) { if (opening.state.value.form == null && opening.state.value.result == null) opening.load() }
        LaunchedEffect(openingState.result?.uid) {
            openingState.result?.takeIf { it.uid.isNotBlank() && opening.chat.key == chat.key }?.let { workflows.lookup("uid", it.uid, true, "本次开户") }
        }
        MerchantOpeningScreen(opening, chat, back) { navigator.merchants(it) }
        return
    }
    if (route.kind == "module") {
        if (PaymentAdminCatalog.contains(route.module)) {
            val model: PaymentAdminViewModel = viewModel(viewModelStoreOwner = owner, key = modelKey, factory = PaymentAdminViewModel.Factory(environment.repository, PaymentAdminCatalog.find(route.module), route.channelType, route.channelId, FormDraftStore(androidx.compose.ui.platform.LocalContext.current, bridge.accountScope())))
            PaymentAdminRoot(model, back)
        } else {
            val model: NativeModuleViewModel = viewModel(viewModelStoreOwner = owner, key = modelKey, factory = NativeModuleViewModel.Factory(environment.repository, ModuleCatalog.find(route.module)))
            NativeModuleRoot(model, back)
        }
        return
    }
    if (route.kind == "domains") {
        val model: DomainAuditViewModel = viewModel(viewModelStoreOwner = owner, key = modelKey, factory = DomainAuditViewModel.Factory(environment.repository, route.merchantId))
        DomainAuditRoot(model, back); return
    }
    if (route.kind == "settings") {
        val state by auth.state.collectAsState()
        SettingsScreen(state, back, auth::connectBackend, auth::logout); return
    }
    if (route.kind == "evidence") { EvidenceScreen(JSONObject(route.module), bridge, evidenceRevision, back); return }
    val model: AppViewModel = viewModel(viewModelStoreOwner = owner, key = modelKey, factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(environment, loadOnStart = false) as T
    })
    val state by model.state.collectAsState()
    var search by remember { mutableIntStateOf(0) }
    var merchantTab by rememberSaveable(route.id) { mutableStateOf(if (route.merchantId.isBlank()) "context" else "all") }
    var lookupError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(route.id) {
        when (route.kind) {
            "orders" -> {
                model.loadOrders(1, OrderSearchFilters(channelId = route.channelId, merchantId = route.merchantId, column = route.queryColumn.ifBlank { "trade_no" }, value = route.queryValue.ifBlank { route.orderId }))
                if (route.orderId.isNotBlank()) model.loadOrderDetail(route.orderId)
            }
            "settlements" -> model.loadSettlements(1, SettlementSearchFilters(merchantId = route.merchantId))
            "merchants" -> if (route.merchantId.isNotBlank()) model.loadMerchants(1, MerchantSearchFilters(column = "uid", value = route.merchantId))
        }
    }
    LaunchedEffect(merchantTab) { if (route.kind == "merchants" && route.merchantId.isBlank() && merchantTab == "all") model.loadMerchants(1) }
    state.message?.let { msg -> LaunchedEffect(msg) { snackbar.showSnackbar(msg); model.consumeMessage() } }
    Column(Modifier.fillMaxSize()) {
        val title = when (route.kind) { "orders" -> "订单"; "settlements" -> "结算"; "merchants" -> "商户"; else -> "更多" }
        PayTopBar(title, if (route.kind == "more") null else back) {
            if (route.kind in setOf("orders", "settlements", "merchants")) IconButton(onClick = { if (route.kind == "merchants") merchantTab = "all"; search++ }) { Icon(Icons.Default.Search, "完整搜索") }
            if (route.kind == "merchants") IconButton(onClick = navigator::merchantOpening) { Icon(Icons.Default.PersonAdd, "商户开户") }
            if (route.kind == "more") IconButton(onClick = { navigator.push(PanelRoute("settings")) }) { Icon(Icons.Default.Settings, "后台设置") }
        }
        lookupError?.let { Text(it, color = Danger, modifier = Modifier.padding(12.dp)) }
        when (route.kind) {
            "orders" -> OrdersScreen(state, model, search, showSearchEntry = false)
            "settlements" -> SettlementsScreen(state, model, search)
            "merchants" -> {
                if (route.merchantId.isBlank()) PayPills(listOf("context" to "当前会话", "all" to "全部商户"), merchantTab) { merchantTab = it }
                if (merchantTab == "context") LazyColumn(contentPadding = PaddingValues(12.dp)) { item { CurrentMerchantCard(workflows, merchantActions, chat, navigator) } }
                else MerchantsScreen(state, model, search)
            }
            else -> ModernManage({ navigator.module(it) }, { navigator.domains() }, { navigator.push(PanelRoute("settings")) })
        }
    }
    BatchDialog(state.batch, model::startBatch, model::closeBatch, model::retryBatch)
}

@Composable private fun EvidenceScreen(order: JSONObject, bridge: NativePanelBridge, revision: Int, back: () -> Unit) {
    val workflows = LocalPanelWorkflows.current
    val localState = workflows?.state?.collectAsState()?.value
    var caption by rememberSaveable(order.optString("trade_no")) { mutableStateOf(order.optString("_caption", evidenceCaption(order))) }
    var status by rememberSaveable(order.optString("trade_no")) { mutableStateOf(workflows?.case(order.optString("trade_no"))?.optString("status", "待处理") ?: "待处理") }
    LaunchedEffect(caption, status) { kotlinx.coroutines.delay(400); workflows?.saveDraft(order, caption, status) }
    val paths = remember(revision) { bridge.evidencePaths().toList() }
    Column(Modifier.fillMaxSize()) {
        PayTopBar("选择付款凭证", back)
        LazyColumn(Modifier.weight(1f).testTag("receipt-list"), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { PayCard { Text("订单 ${order.optString("trade_no")}", color = Ink); Text("商户 PID ${order.optString("uid")} · ${order.optString("currency", "CNY")} ${order.optString("money")}", color = Muted, fontSize = 12.sp) } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = bridge::openGallery, modifier = Modifier.weight(1f)) { Text("相册 / 文件") }; OutlinedButton(onClick = { workflows?.saveDraft(order, caption, status); bridge.pickEvidenceFromChat() }, modifier = Modifier.weight(1f)) { Text("从聊天选图") } } }
            paths.forEachIndexed { index, path -> item(key = path) {
                val bitmap by produceState<android.graphics.Bitmap?>(null, path, revision) { value = withContext(Dispatchers.IO) { bridge.evidencePreview(path) } }
                // Compose display lists can retain a thumbnail after this item leaves composition.
                // These bounded previews must stay valid until their rendering references are collected.
                PayCard {
                    bitmap?.let { Image(it.asImageBitmap(), "凭证 ${index + 1}", Modifier.fillMaxWidth().heightIn(max = 160.dp)) }
                    Row { TextButton(onClick = { bridge.editEvidence(index) }) { Text("编辑") }; TextButton(onClick = { bridge.removeEvidence(index) }) { Text("移除", color = Danger) } }
                }
            } }
            item { OutlinedTextField(caption, { caption = it.take(1024) }, label = { Text("凭证说明") }, modifier = Modifier.fillMaxWidth()) }
            item { PayPills(listOf("查单", "补单", "回调异常", "退款核实").map { it to it }, "") { caption = evidenceCaption(order, it) } }
            item { PayPills(listOf("待处理", "已发出", "等待回复", "已解决").map { it to it }, status) { status = it } }
            item { Text("发送对象由你下一步手动选择，商户 TGID 和支付通道不会决定收件人。", color = Muted, fontSize = 12.sp) }
            item { TextButton(onClick = { workflows?.saveDraft(order, caption, status) }) { Text("保存凭证草稿及处理状态") } }
            if (localState != null && workflows != null) {
                val record = workflows.case(order.optString("trade_no"))
                if (record.optString("sendState").isNotBlank()) item { Text(record.optString("sendState"), color = GreenDark, fontSize = 12.sp) }
                if (record.optString("note").isNotBlank()) item { Text("内部备注：${record.optString("note")}", color = Muted, fontSize = 12.sp) }
                val log = record.optJSONArray("log")
                if (log != null) items(log.length()) { index -> Text(log.optString(index), color = Muted, fontSize = 11.sp) }
            }
        }
        Button(enabled = paths.isNotEmpty(), onClick = { bridge.sendEvidence(order.toString(), caption) }, modifier = Modifier.fillMaxWidth().padding(12.dp).height(48.dp)) { Text("选择收件人并预览") }
    }
}
