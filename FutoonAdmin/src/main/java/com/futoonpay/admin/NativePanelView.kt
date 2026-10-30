package com.futoonpay.admin

import android.content.Context
import android.widget.FrameLayout
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
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

internal val LocalChannelOrderFound = staticCompositionLocalOf<((String) -> Unit)?> { null }

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
class NativePanelView @JvmOverloads constructor(context: Context, private val bridge: NativePanelBridge, private val conversationScope: String = "") : FrameLayout(context), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, OnBackPressedDispatcherOwner {
    private val registry = LifecycleRegistry(this)
    private val stateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    private var options by mutableStateOf(runCatching { ChatToolsConfig.parse((bridge as? NativePanelOptionsBridge)?.settingsJson().orEmpty()).extensions }.getOrDefault(ExtensionOptions()))
    private val memory = PanelMemories.acquire(if (conversationScope.isBlank()) "" else "${bridge.accountScope()}|${bridge.address()}|$conversationScope", bridge, options)
    override val viewModelStore get() = memory.store
    override val savedStateRegistry get() = stateController.savedStateRegistry
    override val onBackPressedDispatcher = OnBackPressedDispatcher { bridge.collapse() }
    private val compose = ComposeView(context)
    private val composition = PanelComposition(compose, registry)
    private val environment = memory.environment
    private val navigation = memory.navigator
    private var chat by mutableStateOf(ChatScope())
    private var query by mutableStateOf(memory.query)
    private var evidenceRevision by mutableStateOf(0)
    private var closed = false
    private var palette by mutableStateOf(FutoonPalette())
    private var fullscreen by mutableStateOf(false)
    private var keyboardVisible by mutableStateOf(false)
    private var fullscreenHandler: Runnable? = null

    init {
        stateController.performAttach()
        stateController.performRestore(memory.saved)
        registry.currentState = Lifecycle.State.CREATED
        setViewTreeLifecycleOwner(this)
        setViewTreeViewModelStoreOwner(this)
        setViewTreeSavedStateRegistryOwner(this)
        compose.id = R.id.futoon_panel_owned_compose
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        compose.setContent {
            CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides this) {
                FutoonTheme(palette) { EmbeddedPanelRoot(environment, bridge, navigation, chat, query, evidenceRevision, fullscreen, keyboardVisible, options, memory) { fullscreenHandler?.run() } }
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
    fun setThemeColors(json: String) { if (!closed) { palette = FutoonPalette.parse(json); (background as? android.graphics.drawable.GradientDrawable)?.setColor(palette.surface.toArgb()) ?: setBackgroundColor(palette.surface.toArgb()) } }
    fun setFullscreenHandler(callback: Runnable) { fullscreenHandler = callback }
    fun setWindowState(full: Boolean, keyboard: Boolean) { fullscreen = full; keyboardVisible = keyboard }
    fun setOptions(json: String) { val next = ChatToolsConfig.parse(json).extensions; if (options.panel.remember && !next.panel.remember) PanelMemories.clearAccount(bridge.accountScope()); options = next }
    fun navigationState(): String = navigation.snapshot()
    fun currentEvidence(): String = navigation.current.takeIf { it.kind == "evidence" }?.module.orEmpty()
    fun restoreNavigationState(json: String) { if (options.panel.remember && !memory.hasLiveState) navigation.restore(json) }
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
    fun openShortcutTarget(kind: String, id: String, order: String) {
        when (kind) {
            "merchant-more" -> if (id.matches(Regex("[1-9][0-9]{0,18}"))) navigation.push(PanelRoute("merchant_more", merchantId = id, module = chat.key + "|" + chat.telegramId))
            "merchant" -> navigation.merchants(id)
            "merchant-orders" -> navigation.orders(merchantId = id)
            "merchant-settlements" -> navigation.settlements(id)
            "merchant-domains" -> navigation.domains(id)
            "channel-orders" -> navigation.orders(channelId = id)
            "channel" -> navigation.module("channels", channelId = id)
            "evidence" -> navigatorEvidence(order, evidenceCaption(JSONObject(order)))
        }
    }
    fun handleBack(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
    fun onShown() { if (!closed) registry.currentState = Lifecycle.State.RESUMED }
    fun onHidden() { if (!closed) registry.currentState = Lifecycle.State.CREATED }
    fun close() {
        if (closed) return
        if (options.panel.remember) { val bundle = android.os.Bundle(); runCatching { stateController.performSave(bundle) }.onSuccess { memory.saved = bundle }; memory.query = query }
        closed = true; registry.currentState = Lifecycle.State.DESTROYED; composition.close()
        PanelMemories.release(memory, options.panel.remember)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun EmbeddedPanelRoot(environment: PanelEnvironment, bridge: NativePanelBridge, navigator: PanelNavigator, chat: ChatScope, query: RecognizedQuery?, evidenceRevision: Int, fullscreen: Boolean = false, keyboard: Boolean = false, options: ExtensionOptions = ExtensionOptions(), memory: PanelMemory? = null, onFullscreen: () -> Unit = {}) {
    val auth: AppViewModel = viewModel(key = "session", factory = AppViewModel.Factory(environment, initialAccountName = bridge.username().ifBlank { "管理员" }))
    val state by auth.state.collectAsState()
    val sessionKey = state.adminUrl + ":" + state.accountName + ":" + bridge.accountScope()
    val sessionOwner = remember(sessionKey) { memory?.sessionModels?.owner(sessionKey) ?: object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
    DisposableEffect(sessionOwner) { onDispose { if (memory == null) sessionOwner.viewModelStore.clear() } }
    val workflows: PanelWorkflows = viewModel(viewModelStoreOwner = sessionOwner, key = "workflows:$sessionKey", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = PanelWorkflows(memory?.bridge ?: bridge) as T
    })
    val contextModel: AppViewModel = viewModel(viewModelStoreOwner = sessionOwner, key = "context-merchant:$sessionKey", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(environment, loadOnStart = false) as T
    })
    val merchantActions = remember(contextModel) { memory?.merchantActions?.getOrPut(sessionKey) { CurrentMerchantActions(contextModel) } ?: CurrentMerchantActions(contextModel) }
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
    val models = remember(state.adminUrl, state.accountName, bridge.accountScope()) { memory?.routeModels ?: PanelRouteModels() }
    DisposableEffect(models) { onDispose { if (memory == null) models.clear() } }
    val holder = rememberSaveableStateHolder()
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
    CompositionLocalProvider(LocalPanelNavigation provides navigation, LocalPanelWorkflows provides workflows, LocalWorkflowCopy provides bridge::copy,
        LocalChannelOrderFound provides { channel: String -> runCatching { bridge.call("ui.channel.order", JSONObject().put("channel", channel).put("dialog", chat.dialog).put("topic", chat.topic).toString()) }; Unit }) {
        PanelOverlayHost {
            Column(Modifier.fillMaxSize().background(Mist)) {
                if (state.authStage != AuthStage.AUTHENTICATED) Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).background(CardColor).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("FUTOON PAY", Modifier.weight(1f), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    IconButton(onClick = onFullscreen, modifier = Modifier.size(48.dp)) { Icon(if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, if (fullscreen) "退出全屏" else "全屏悬浮窗") }
                    IconButton(onClick = bridge::collapse, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.ExpandMore, "收起悬浮窗") }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (state.authStage) {
                        AuthStage.CHECKING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        AuthStage.LOGIN -> Column(Modifier.fillMaxSize()) {
                            if (state.message?.contains("过期") == true) Text("任务已保留，登录后回到原页面；已提交的操作不会自动重放。", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                            Box(Modifier.weight(1f)) { ModernLogin(state, auth::connectBackend, auth::login, auth::refreshCaptcha) }
                        }
                        AuthStage.AUTHENTICATED -> {
                            val route = navigator.current
                            holder.SaveableStateProvider(route.id) {
                                BoxWithConstraints(Modifier.fillMaxSize()) {
                                    val compact = maxHeight < 410.dp
                                    val chrome = PayPanelChrome(navigator.canPop, actions = {
                                        IconButton(onClick = onFullscreen, modifier = Modifier.size(48.dp)) { Icon(if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, if (fullscreen) "退出全屏" else "全屏悬浮窗") }
                                        IconButton(onClick = bridge::collapse, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.ExpandMore, "收起悬浮窗") }
                                    }, context = { if (route.kind in setOf("recognition", "orders", "settlements", "merchants", "merchant_records", "evidence")) PanelContextStrip(chat, workflowState, keyboard || compact) })
                                    CompositionLocalProvider(LocalPayPanelChrome provides chrome) { EmbeddedRoute(route, environment, auth, navigator, bridge, chat, evidenceRevision, snackbar, models, workflows, merchantActions, memory) }
                                }
                            }
                        }
                    }
                }
                if (state.authStage == AuthStage.AUTHENTICATED) {
                    val tabs = listOf(
                        ChatNavItem("recognition", "识别", Icons.Outlined.DocumentScanner, Icons.Default.DocumentScanner),
                        ChatNavItem("orders", "订单", Icons.Outlined.ReceiptLong, Icons.Default.ReceiptLong),
                        ChatNavItem("merchants", "商户", Icons.Outlined.Storefront, Icons.Default.Storefront),
                        ChatNavItem("settlements", "结算", Icons.Outlined.AccountBalanceWallet, Icons.Default.AccountBalanceWallet),
                        ChatNavItem("more", "更多", Icons.Outlined.MoreHoriz, Icons.Default.MoreHoriz))
                    ChatNavigationBar(tabs.sortedBy { options.panel.tabOrder.indexOf(it.id) }, navigator.tab, navigator::select)
                }
            }
            if (state.authStage == AuthStage.AUTHENTICATED) CurrentMerchantDialogs(merchantActions, workflows, chat)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { SnackbarHost(snackbar) }
        }
    }
}

@Composable private fun EmbeddedRoute(route: PanelRoute, environment: PanelEnvironment, auth: AppViewModel, navigator: PanelNavigator, bridge: NativePanelBridge, chat: ChatScope, evidenceRevision: Int, snackbar: SnackbarHostState, models: PanelRouteModels, workflows: PanelWorkflows, merchantActions: CurrentMerchantActions, memory: PanelMemory?) {
    val back = { if (!navigator.pop()) bridge.collapse() }
    val authState by auth.state.collectAsState()
    val modelKey = environment.adminBaseUrl + ":" + authState.accountName + ":" + route.id
    val owner = models.owner(modelKey)
    if (route.kind == "merchant_more") {
        if (route.module != chat.key + "|" + chat.telegramId) {
            Column { PayTopBar("商户更多", back); Text("会话已变化，请返回当前会话重新打开", Modifier.padding(16.dp)) }
            return
        }
        // Reuse the recognition card and its action dialogs, isolated from other panel lookups.
        val scoped: PanelWorkflows = viewModel(viewModelStoreOwner = owner, key = "$modelKey:more", factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = PanelWorkflows(memory?.bridge ?: bridge) as T
        })
        val actionModel: AppViewModel = viewModel(viewModelStoreOwner = owner, key = "$modelKey:actions", factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(environment, loadOnStart = false, lockedMerchantId = route.merchantId) as T
        })
        val actions = remember(actionModel) { CurrentMerchantActions(actionModel) }
        val scopedState by scoped.state.collectAsState()
        LaunchedEffect(scopedState.notice) { scopedState.notice?.let { snackbar.showSnackbar(it); scoped.consumeNotice() } }
        LaunchedEffect(scopedState.error) { scopedState.error?.let { snackbar.showSnackbar(it) } }
        LaunchedEffect(scoped, chat.key, chat.telegramId) {
            scoped.updateChat(chat)
            scoped.lookup("uid", route.merchantId, true, "会话快捷栏").join()
        }
        CompositionLocalProvider(LocalPanelWorkflows provides scoped) {
            Column(Modifier.fillMaxSize()) {
                PayTopBar("商户更多", back)
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
                    item { CurrentMerchantCard(scoped, actions, chat, navigator, lockedMerchantId = route.merchantId) }
                }
            }
            CurrentMerchantDialogs(actions, scoped, chat)
        }
        return
    }
    if (route.kind == "recognition") { RecognitionScreen(workflows, merchantActions, chat, navigator, bridge); return }
    if (route.kind == "merchant_records") { MerchantRecordsScreen(route, workflows, back); return }
    if (route.kind == "merchant_opening") {
        val actionBridge = memory?.bridge ?: bridge
        val address = environment.adminBaseUrl
        val account = bridge.accountScope()
        suspend fun callOpening(operation: String, params: String = "{}"): String = withContext(Dispatchers.IO) {
            check(actionBridge.address() == address && actionBridge.accountScope() == account) { "账号或后台已变化，请重新打开开户" }
            val result = actionBridge.call(operation, params)
            check(actionBridge.address() == address && actionBridge.accountScope() == account) { "账号或后台已变化，请重新打开开户" }
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
        if (environment.repository.modernBackend && !OfficialBackendFeatures.available(route.module)) {
            Column(Modifier.padding(16.dp)) {
                Text("新版后台已移除此模块")
                com.futoonpay.admin.ui.ChatTextButton(onClick = back) { Text("返回管理") }
            }
            return
        }
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
        if (memory != null && !memory.initializedRoutes.add(route.id)) return@LaunchedEffect
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
            "settlements" -> SettlementsScreen(state, model, search, showSearchEntry = false)
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
    LaunchedEffect(caption, status) { kotlinx.coroutines.delay(400); if (caption.length <= 1024) workflows?.saveDraft(order, caption, status) }
    val paths = remember(revision) { bridge.evidencePaths().toList() }
    Column(Modifier.fillMaxSize()) {
        PayTopBar("选择付款凭证", back)
        PayCard(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), compact = true) {
            Text("订单 ${order.optString("trade_no")}", color = Ink, fontSize = 14.sp)
            Text("商户 PID ${order.optString("uid")} · ${order.optString("currency", "CNY")} ${order.optString("money")}", color = Muted, fontSize = 13.sp)
        }
        LazyColumn(Modifier.weight(1f).testTag("receipt-list"), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { com.futoonpay.admin.ui.ChatOutlinedButton(onClick = bridge::openGallery, modifier = Modifier.weight(1f)) { Text("相册 / 文件") }; com.futoonpay.admin.ui.ChatOutlinedButton(onClick = { if (caption.length <= 1024) workflows?.saveDraft(order, caption, status); bridge.pickEvidenceFromChat() }, modifier = Modifier.weight(1f)) { Text("从聊天选图") } } }
            item { Text("付款凭证图片 · ${paths.size} / 10 · 每张最多 25 MB", color = Muted, fontSize = 12.sp) }
            if (paths.isEmpty()) item { PayCard { Text("还没有付款凭证"); Text("可从相册、文件或聊天中选择图片。", color = Muted, fontSize = 13.sp) } }
            paths.chunked(3).forEachIndexed { rowIndex, images -> item(key = "images-$rowIndex") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    images.forEachIndexed { column, path -> key(path, rowIndex * 3 + column) { EvidenceTile(path, rowIndex * 3 + column, revision, bridge, Modifier.weight(1f)) } }
                    repeat(3 - images.size) { Spacer(Modifier.weight(1f)) }
                }
            } }

            item { OutlinedTextField(caption, { caption = it }, label = { Text("凭证说明") }, modifier = Modifier.fillMaxWidth(), minLines = 3, isError = caption.length > 1024, supportingText = { Text("${caption.length} / 1024") } , colors = com.futoonpay.admin.ui.ChatFieldColors()) }
            item { PayPills(listOf("查单", "补单协助", "回调异常", "退款核实").map { it to it }, "") { caption = evidenceCaption(order, it) } }
            item { PayPills(listOf("待处理", "已发出", "等待回复", "已解决").map { it to it }, status) { status = it } }
            item { Text("发送对象由你下一步手动选择，商户 TGID 和支付通道不会决定收件人。", color = Muted, fontSize = 12.sp) }
            item { com.futoonpay.admin.ui.ChatTextButton(onClick = { workflows?.saveDraft(order, caption, status) }) { Text("保存凭证草稿及处理状态") } }
            if (localState != null && workflows != null) {
                val record = workflows.case(order.optString("trade_no"))
                if (record.optString("sendState").isNotBlank()) item { Text(record.optString("sendState"), color = GreenDark, fontSize = 12.sp) }
                val log = record.optJSONArray("log")
                if (log != null) items(log.length()) { index -> Text(log.optString(index), color = Muted, fontSize = 12.sp) }
            }
        }
        Column(Modifier.fillMaxWidth().background(CardColor).padding(12.dp)) {
            if (paths.isEmpty() || caption.length > 1024) Text(if (paths.isEmpty()) "先选择至少一张图片" else "凭证说明超过 1024 字，请缩短后再发送", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            com.futoonpay.admin.ui.ChatButton(enabled = paths.isNotEmpty() && caption.length <= 1024, onClick = { bridge.sendEvidence(order.toString(), caption) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("选择收件人并预览") }
        }
    }
}

@Composable private fun PanelContextStrip(chat: ChatScope, state: WorkflowState, keyboard: Boolean) {
    var expanded by rememberSaveable(chat.key) { mutableStateOf(false) }
    val merchant = state.merchant?.let { JSONObject(it) }
    Column(Modifier.fillMaxWidth().background(GreenTint).clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = if (keyboard) 4.dp else 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(chat.label.ifBlank { "当前会话" }, Modifier.weight(1f), color = Ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "收起会话信息" else "展开会话信息", tint = GreenDark, modifier = Modifier.size(20.dp))
        }
        if (expanded) Text(when { state.merchantLoading -> "正在识别商户…"; merchant != null -> "PID ${merchant.optString("uid")} · ${state.merchantSource}"; else -> "${state.merchantSource.ifBlank { "未指定商户" }} · 可在商户页手动查询" }, color = Muted, fontSize = 12.sp, maxLines = if (expanded) 3 else 1, overflow = TextOverflow.Ellipsis)
        if (expanded) {
            Text("Telegram ID：${chat.telegramId.ifBlank { "未识别" }}", color = Ink, fontSize = 13.sp)
            if (chat.ticket.isNotBlank()) Text("工单 ${chat.ticket} · ${chat.role}", color = Muted, fontSize = 12.sp)
            if (chat.topic != 0L) Text("话题 ${chat.topic}", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable private fun EvidenceTile(path: String, index: Int, revision: Int, bridge: NativePanelBridge, modifier: Modifier) {
    var menu by remember(path) { mutableStateOf(false) }
    val preview by produceState<Pair<Boolean, android.graphics.Bitmap?>>(false to null, path, revision) {
        value = false to null
        value = true to withContext(Dispatchers.IO) { runCatching { bridge.evidencePreview(path) }.getOrNull() }
    }
    Surface(modifier, color = CardColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clickable { bridge.editEvidence(index) }, contentAlignment = Alignment.Center) {
                val bitmap = preview.second
                when {
                    bitmap != null -> Image(bitmap.asImageBitmap(), "凭证 ${index + 1}", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    !preview.first -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else -> Text("图片不可用\n请移除后重选", color = Danger, fontSize = 12.sp, modifier = Modifier.padding(6.dp))
                }
                Surface(Modifier.align(Alignment.TopStart).padding(4.dp), color = CardColor, shape = androidx.compose.foundation.shape.CircleShape) { Text("${index + 1}", color = Ink, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                com.futoonpay.admin.ui.ChatTextButton(onClick = { bridge.editEvidence(index) }, modifier = Modifier.weight(1f)) { Text("编辑", fontSize = 12.sp) }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.MoreVert, "凭证 ${index + 1} 更多操作") }
                    DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text("移除", color = Danger) }, onClick = { menu = false; bridge.removeEvidence(index) }) }
                }
            }
        }
    }
}
