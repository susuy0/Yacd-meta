package com.futoonpay.admin

import android.content.Context
import android.widget.FrameLayout
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
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
    fun editEvidence(index: Int)
    fun removeEvidence(index: Int)
    fun sendEvidence(order: String, caption: String)
}

internal data class RecognizedQuery(val value: String, val kind: String, val id: String = UUID.randomUUID().toString())
internal data class ChatScope(val label: String = "当前会话", val telegramId: String = "", val source: String = "")

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
    fun updateContext(label: String?, telegramId: String?, source: String?) { chat = ChatScope(label.orEmpty(), telegramId.orEmpty(), source.orEmpty()) }
    fun recognize(text: String?) { navigation.showOrdersForQuery(); query = RecognizedQuery(text.orEmpty(), "AUTO") }
    fun recognizeCandidate(value: String?, kind: String?) { navigation.showOrdersForQuery(); query = RecognizedQuery(value.orEmpty(), kind.orEmpty()) }
    fun updateEvidenceCount(count: Int) { evidenceRevision++ }
    fun adoptEvidence(order: String, caption: String) { navigatorEvidence(order, caption) }
    private fun navigatorEvidence(order: String, caption: String) { navigation.push(PanelRoute("evidence", module = JSONObject(order).put("_caption", caption).toString())) }
    fun handleBack(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
    fun onShown() { if (!closed) registry.currentState = Lifecycle.State.RESUMED }
    fun onHidden() { if (!closed) registry.currentState = Lifecycle.State.CREATED }
    fun close() { if (closed) return; closed = true; registry.currentState = Lifecycle.State.DESTROYED; viewModelStore.clear(); compose.disposeComposition() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun EmbeddedPanelRoot(environment: PanelEnvironment, bridge: NativePanelBridge, navigator: PanelNavigator, chat: ChatScope, query: RecognizedQuery?, evidenceRevision: Int) {
    val auth: AppViewModel = viewModel(key = "session", factory = AppViewModel.Factory(environment))
    val state by auth.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val models = remember(state.adminUrl, state.accountName, state.authStage) { PanelRouteModels() }
    DisposableEffect(models) { onDispose { models.clear() } }
    val navigation = remember(navigator, bridge) { object : PanelNavigation by navigator {
        override fun evidence(order: OrderSummary) {
            val data = JSONObject().put("trade_no", order.tradeNo).put("out_trade_no", order.merchantTradeNo).put("uid", order.merchantId).put("money", order.amount).put("currency", order.currency)
            bridge.selectEvidence(data.toString(), "订单 ${order.tradeNo} 凭证")
            navigator.push(PanelRoute("evidence", module = data.toString()))
        }
    } }
    BackHandler { if (!navigator.pop()) bridge.collapse() }
    CompositionLocalProvider(LocalPanelNavigation provides navigation) {
        PanelOverlayHost {
            Column(Modifier.fillMaxSize()) {
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
                                EmbeddedRoute(route, environment, auth, navigator, bridge, chat, query, evidenceRevision, snackbar, models)
                            }
                        }
                    }
                }
                if (state.authStage == AuthStage.AUTHENTICATED) NavigationBar(containerColor = androidx.compose.ui.graphics.Color.White, modifier = Modifier.height(64.dp), windowInsets = WindowInsets(0, 0, 0, 0)) {
                    listOf(Triple("orders", "订单", Icons.Default.ReceiptLong), Triple("settlements", "结算", Icons.Default.AccountBalanceWallet), Triple("more", "更多", Icons.Default.GridView)).forEach { (id, title, icon) ->
                        NavigationBarItem(selected = navigator.tab == id, onClick = { navigator.select(id) }, icon = { Icon(icon, title, Modifier.size(20.dp)) }, label = { Text(title, fontSize = 11.sp) })
                    }
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { SnackbarHost(snackbar) }
        }
    }
}

@Composable private fun EmbeddedRoute(route: PanelRoute, environment: PanelEnvironment, auth: AppViewModel, navigator: PanelNavigator, bridge: NativePanelBridge, chat: ChatScope, query: RecognizedQuery?, evidenceRevision: Int, snackbar: SnackbarHostState, models: PanelRouteModels) {
    val back = { if (!navigator.pop()) bridge.collapse() }
    val modelKey = environment.adminBaseUrl + ":" + auth.state.value.accountName + ":" + route.id
    val owner = models.owner(modelKey)
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
    var lookupError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(route.id) {
        when (route.kind) {
            "orders" -> model.loadOrders(1, OrderSearchFilters(channelId = route.channelId, merchantId = route.merchantId))
            "settlements" -> model.loadSettlements(1, SettlementSearchFilters(merchantId = route.merchantId))
            "merchants" -> model.loadMerchants(1, MerchantSearchFilters(column = "uid", value = route.merchantId))
        }
    }
    LaunchedEffect(query?.id) {
        if (route.kind != "orders" || query == null || query.value.isBlank() || navigator.lastQueryId == query.id) return@LaunchedEffect
        navigator.lastQueryId = query.id
        runCatching { withContext(Dispatchers.IO) { JSONObject(bridge.call("orders.search", JSONObject().put("text", query.value).put("kind", query.kind).toString())) } }.onSuccess { result ->
            val column = when (result.optString("kind")) { "PID" -> "uid"; "MERCHANT_ORDER" -> "out_trade_no"; "CHANNEL_ORDER" -> "api_trade_no"; else -> "trade_no" }
            model.loadOrders(1, OrderSearchFilters(column = column, value = result.optString("value", query.value)))
        }.onFailure { lookupError = it.message ?: "识别查询失败" }
    }
    state.message?.let { msg -> LaunchedEffect(msg) { snackbar.showSnackbar(msg); model.consumeMessage() } }
    Column(Modifier.fillMaxSize()) {
        val title = when (route.kind) { "orders" -> "订单"; "settlements" -> "结算"; "merchants" -> "商户"; else -> "更多" }
        PayTopBar(title, if (route.kind == "more") null else back) {
            if (route.kind in setOf("orders", "settlements", "merchants")) IconButton(onClick = { search++ }) { Icon(Icons.Default.Search, "完整搜索") }
            if (route.kind == "more") IconButton(onClick = { navigator.push(PanelRoute("settings")) }) { Icon(Icons.Default.Settings, "后台设置") }
        }
        lookupError?.let { Text(it, color = Danger, modifier = Modifier.padding(12.dp)) }
        when (route.kind) {
            "orders" -> OrdersScreen(state, model, search, showSearchEntry = false)
            "settlements" -> SettlementsScreen(state, model, search)
            "merchants" -> MerchantsScreen(state, model, search)
            else -> Column {
                if (chat.telegramId.isNotBlank()) TextButton(onClick = { search++ }) { Text("查询当前 TG 商户：${chat.telegramId}") }
                LaunchedEffect(search, chat.telegramId) {
                    if (search == 0 || chat.telegramId.isBlank()) return@LaunchedEffect
                    runCatching { withContext(Dispatchers.IO) { JSONObject(bridge.call("merchant.lookup", JSONObject().put("column", "telegram_id").put("value", chat.telegramId).toString())) } }.onSuccess { result ->
                        val rows = result.optJSONArray("rows")
                        if (rows?.length() == 1) navigator.merchants(rows.getJSONObject(0).getString("uid")) else lookupError = "没有找到与此 TGID 精确绑定的商户"
                    }.onFailure { lookupError = it.message }
                }
                ModernManage({ navigator.module(it) }, { navigator.domains() }, { navigator.push(PanelRoute("settings")) })
            }
        }
    }
    BatchDialog(state.batch, model::startBatch, model::closeBatch, model::retryBatch)
}

@Composable private fun EvidenceScreen(order: JSONObject, bridge: NativePanelBridge, revision: Int, back: () -> Unit) {
    var caption by remember { mutableStateOf(order.optString("_caption", "订单 ${order.optString("trade_no")} 凭证")) }
    val paths = remember(revision) { bridge.evidencePaths().toList() }
    Column(Modifier.fillMaxSize()) {
        PayTopBar("图片凭证", back)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("订单 ${order.optString("trade_no")}", color = Ink) }
            paths.forEachIndexed { index, path -> item(key = path) {
                val bitmap = remember(path, revision) { val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }; android.graphics.BitmapFactory.decodeFile(path, options); options.inSampleSize = maxOf(1, maxOf(options.outWidth, options.outHeight) / 640); options.inJustDecodeBounds = false; android.graphics.BitmapFactory.decodeFile(path, options) }
                DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
                PayCard {
                    bitmap?.let { Image(it.asImageBitmap(), "凭证 ${index + 1}", Modifier.fillMaxWidth().heightIn(max = 160.dp)) }
                    Row { TextButton(onClick = { bridge.editEvidence(index) }) { Text("编辑") }; TextButton(onClick = { bridge.removeEvidence(index) }) { Text("移除", color = Danger) } }
                }
            } }
            item { OutlinedTextField(caption, { caption = it.take(1024) }, label = { Text("凭证说明") }, modifier = Modifier.fillMaxWidth()) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = bridge::openGallery, modifier = Modifier.weight(1f)) { Text("相册") }; OutlinedButton(onClick = bridge::pickEvidenceFromChat, modifier = Modifier.weight(1f)) { Text("聊天图片") } } }
        }
        Button(enabled = paths.isNotEmpty(), onClick = { bridge.sendEvidence(order.toString(), caption) }, modifier = Modifier.fillMaxWidth().padding(12.dp).height(48.dp)) { Text("选择收件人并预览") }
    }
}
