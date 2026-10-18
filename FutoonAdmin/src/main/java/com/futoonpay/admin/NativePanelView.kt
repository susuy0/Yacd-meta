package com.futoonpay.admin

import android.content.Context
import android.graphics.BitmapFactory
import android.util.AttributeSet
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/** Host operations that must stay on Telegram's side of the integration boundary. */
interface NativePanelBridge {
    fun address(): String
    fun username(): String
    fun hasSession(): Boolean
    @Throws(Exception::class) fun configure(address: String, username: String)
    @Throws(Exception::class) fun captcha(): ByteArray
    @Throws(Exception::class) fun login(username: String, password: String, captcha: String): String
    @Throws(Exception::class) fun logout()
    @Throws(Exception::class) fun call(operation: String, params: String): String
    fun collapse()
    fun copy(text: String)
    fun selectEvidence(order: String, caption: String)
    fun openGallery()
    fun sendEvidence(order: String, caption: String)
}

private enum class NativeTab(val title: String) { ORDERS("订单"), SETTLEMENTS("结算"), MORE("更多") }
private data class PanelContext(val label: String = "当前会话", val telegramId: String = "", val source: String = "")
private data class PendingAction(val operation: String, val params: JSONObject, val title: String, val summary: String, val refresh: () -> Unit, val result: ((JSONObject) -> Unit)? = null)
private data class PanelState(
    val authenticated: Boolean = false,
    val loading: Boolean = false,
    val message: String? = null,
    val tab: NativeTab = NativeTab.ORDERS,
    val context: PanelContext = PanelContext(),
    val recognizedText: String = "",
    val module: String? = null,
    val moduleTitle: String = "",
    val data: JSONObject? = null,
    val page: Int = 1,
    val query: String = "",
    val status: String = "-1",
    val detail: JSONObject? = null,
    val form: JSONObject? = null,
    val evidenceCount: Int = 0,
    val pending: PendingAction? = null,
)

/**
 * Fully native in-chat panel. It deliberately owns no WebView and loads no HTML assets.
 * The card/list/form structure is adapted from the original FUTOON PAY Android client.
 */
class NativePanelView(
    context: Context,
    private val bridge: NativePanelBridge,
) : ComposeView(context) {
    private val model = NativePanelModel(bridge)

    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent { FutoonPanelTheme { NativePanelRoot(model) } }
    }

    fun updateContext(label: String?, telegramId: String?, source: String?) =
        model.updateContext(label.orEmpty(), telegramId.orEmpty(), source.orEmpty())

    fun recognize(text: String?) = model.recognize(text.orEmpty())
    fun recognizeCandidate(value: String?, kind: String?) = model.recognizeCandidate(value.orEmpty(), kind.orEmpty())
    fun updateEvidenceCount(count: Int) = model.updateEvidenceCount(count)
    fun handleBack(): Boolean = model.back()
    fun onShown() = model.onShown()
    fun close() { model.close(); disposeComposition() }
}

private class NativePanelModel(private val bridge: NativePanelBridge) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PanelState(authenticated = bridge.hasSession()))
    val state = _state.asStateFlow()
    fun address() = bridge.address()
    fun username() = bridge.username()

    init { if (_state.value.authenticated) loadOrders() }
    fun close() = scope.cancel()
    fun onShown() { if (_state.value.data == null && _state.value.authenticated) refresh() }
    fun updateContext(label: String, telegramId: String, source: String) = _state.update {
        it.copy(context = PanelContext(label.ifBlank { "当前会话" }, telegramId, source))
    }
    fun updateEvidenceCount(count: Int) = _state.update { it.copy(evidenceCount = count.coerceIn(0, 10)) }
    fun recognize(text: String) {
        val value = Regex("[A-Za-z0-9_-]{4,128}").findAll(text).map { it.value }.firstOrNull().orEmpty()
        _state.update { it.copy(recognizedText = text, tab = NativeTab.ORDERS, module = null, query = value) }
        if (value.isNotBlank()) recognizeCandidate(value, "AUTO") else loadOrders(1, value)
    }
    fun recognizeCandidate(value: String, kind: String) {
        _state.update { it.copy(recognizedText = value, tab = NativeTab.ORDERS, module = null, query = value, status = "-1") }
        call("orders.search", JSONObject().put("text", value).put("kind", kind.ifBlank { "AUTO" }).put("page", 1).put("status", "-1"), onSuccess = { data ->
            _state.update { it.copy(tab = NativeTab.ORDERS, data = data, page = 1, query = value, detail = null) }
        })
    }
    fun selectTab(tab: NativeTab) {
        _state.update { it.copy(tab = tab, module = null, moduleTitle = "", detail = null, form = null, page = 1) }
        when (tab) { NativeTab.ORDERS -> loadOrders(); NativeTab.SETTLEMENTS -> loadSettlements(); NativeTab.MORE -> Unit }
    }
    fun back(): Boolean {
        val s = _state.value
        return when {
            s.pending != null -> { _state.update { it.copy(pending = null) }; true }
            s.form != null -> { _state.update { it.copy(form = null) }; true }
            s.detail != null -> { _state.update { it.copy(detail = null) }; true }
            s.module != null -> { _state.update { it.copy(module = null, moduleTitle = "", data = null, page = 1, query = "") }; true }
            else -> false
        }
    }
    fun refresh() = when (_state.value.tab) {
        NativeTab.ORDERS -> loadOrders(_state.value.page)
        NativeTab.SETTLEMENTS -> loadSettlements(_state.value.page)
        NativeTab.MORE -> _state.value.module?.let { loadModule(it, _state.value.moduleTitle, _state.value.page, _state.value.query) } ?: Unit
    }

    fun configure(address: String, username: String, password: String, code: String) = io {
        bridge.configure(address, username)
        val result = JSONObject(bridge.login(username, password, code))
        check(result.optInt("code", -1) in setOf(0, 200) || result.optBoolean("success")) { result.optString("msg", "登录失败") }
        _state.update { it.copy(authenticated = true, message = "登录成功") }
        scope.launch { loadOrders() }
    }
    fun captcha(address: String, username: String, onResult: (ByteArray?) -> Unit) = io(onError = { onResult(null) }) {
        bridge.configure(address, username)
        val bytes = bridge.captcha()
        scope.launch { onResult(bytes) }
    }
    fun logout() = io {
        bridge.logout()
        _state.value = PanelState(authenticated = false, context = _state.value.context)
    }

    fun loadOrders(page: Int = 1, search: String = _state.value.query, status: String = _state.value.status) = call(
        "orders.list", JSONObject().put("page", page).put("status", status).apply {
            if (search.isNotBlank()) {
                val column = if (search.all(Char::isDigit) && search.length <= 10) "uid" else "trade_no"
                put("column", column).put("value", search.trim())
            }
        }, onSuccess = { data -> _state.update { it.copy(tab = NativeTab.ORDERS, data = data, page = page, query = search, status = status, detail = null) } }
    )
    fun loadOrderDetail(id: String) = call("order.detail", JSONObject().put("id", id), onSuccess = { d -> _state.update { it.copy(detail = d) } })
    fun loadSettlements(page: Int = 1, query: String = _state.value.query, status: String = _state.value.status) = call(
        "settlements.list", JSONObject().put("page", page).put("uid", query.filter(Char::isDigit)).put("status", status),
        onSuccess = { data -> _state.update { it.copy(tab = NativeTab.SETTLEMENTS, data = data, page = page, query = query, status = status, detail = null) } }
    )
    fun loadModule(id: String, title: String, page: Int = 1, query: String = "", section: Int = 0) = call(
        "module.read", JSONObject().put("module", id).put("page", page).put("query", query).put("section", section),
        onSuccess = { data -> _state.update { it.copy(tab = NativeTab.MORE, module = id, moduleTitle = title, data = data, page = page, query = query, detail = null, form = null) } }
    )
    fun loadDomains(page: Int = 1, query: String = "") = call(
        "domains.list", JSONObject().put("page", page).put("uid", query.filter(Char::isDigit)).put("status", "-1"),
        onSuccess = { data -> _state.update { it.copy(tab = NativeTab.MORE, module = "domains", moduleTitle = "域名审核", data = data, page = page, query = query) } }
    )
    fun merchantLookup() {
        val tgid = _state.value.context.telegramId
        if (tgid.isBlank()) { message("当前会话没有识别到 Telegram ID"); return }
        call("merchant.lookup", JSONObject().put("column", "telegram_id").put("value", tgid), onSuccess = { data ->
            _state.update { it.copy(tab = NativeTab.MORE, module = "merchant", moduleTitle = "当前商户", data = data) }
        })
    }
    fun callMerchantProfile(uid: String) = call("merchant.profile", JSONObject().put("uid", uid), onSuccess = ::openForm)
    fun merchantStatus(uid: String, type: String, enabled: Boolean) = confirm(
        "确认商户状态", "商户 PID：$uid\n${if (enabled) "启用" else "停用"}：$type", "merchant.status",
        JSONObject().put("uid", uid).put("type", type).put("enabled", enabled)
    ) { merchantLookup() }
    fun merchantGroup(uid: String, gid: String, endTime: String) = confirm(
        "修改商户用户组", "商户 PID：$uid\n用户组：$gid" + if (endTime.isBlank()) "" else "\n到期：$endTime", "merchant.group",
        JSONObject().put("uid", uid).put("gid", gid).put("endtime", endTime)
    ) { merchantLookup() }
    fun openForm(form: JSONObject) = _state.update { it.copy(form = JSONObject(form.toString())) }
    fun openModuleEditor(kind: String, id: String = "") = call(
        "module.editor", JSONObject().put("module", _state.value.module).put("editor", kind).put("id", id),
        onSuccess = ::openForm,
    )
    fun inspectAction(action: JSONObject) = call("module.inspect", JSONObject().put("ticket", action.getString("ticket")), onSuccess = { result ->
        if (result.has("navigate")) loadModule("channels", "支付通道", query = result.optString("id")) else openForm(result)
    })
    fun runAction(action: JSONObject) {
        val params = JSONObject().put("ticket", action.getString("ticket"))
        confirm("执行${action.optString("label")}", "模块：${_state.value.moduleTitle}\n操作：${action.optString("label")}", "module.action", params) { refresh() }
    }
    fun saveForm(values: Map<String, String>) {
        val form = _state.value.form ?: return
        confirm("保存配置", "${_state.value.moduleTitle} · ${form.optString("title", "配置")}", "form.save",
            JSONObject().put("ticket", form.getString("ticket")).put("values", JSONObject(values))) {
            _state.update { it.copy(form = null) }; refresh()
        }
    }
    fun orderAction(id: String, action: String, extra: JSONObject = JSONObject()) = confirm(
        "确认订单操作", "订单：$id\n操作：${orderActionName(action)}", "order.action",
        JSONObject(extra.toString()).put("id", id).put("action", action),
    ) { loadOrders(_state.value.page) }
    fun settlementAction(row: JSONObject, status: Int, reason: String = "") = confirm(
        "确认结算操作", "结算 #${row.optString("id")}\n商户：${row.optString("uid")}\n状态：${settlementStatus(status)}", "settlement.status",
        JSONObject().put("id", row.optString("id")).put("uid", row.optString("uid")).put("status", status).put("reason", reason)
            .put("usdt_rate", row.optString("usdt_rate")).put("usdt_count", row.optString("usdt_count")),
    ) { loadSettlements(_state.value.page) }
    fun domainAction(row: JSONObject, status: Int) = confirm(
        "确认域名审核", "${row.optString("domain")}\n商户：${row.optString("uid")}", "domain.action",
        JSONObject().put("action", "status").put("id", row.optString("id")).put("status", status).put("remark", "")
    ) { loadDomains(_state.value.page, _state.value.query) }
    fun removeBlacklist(id: String) = confirm("移除黑名单", "记录 #$id", "blacklist.remove", JSONObject().put("id", id)) { refresh() }
    fun addBlacklist(type: String, content: String, reason: String) = confirm(
        "添加支付黑名单", "类型：$type\n内容：$content", "blacklist.add",
        JSONObject().put("type", type).put("content", content).put("reason", reason).put("days", "0").put("remark", "")
    ) { refresh() }
    fun channelStats(id: String) = call("channel.stats", JSONObject().put("id", id), onSuccess = { result -> message(result.optJSONArray("rows")?.toString(2) ?: "暂无统计") })
    fun testChannel(id: String, amount: String) = confirm(
        "测试支付通道", "通道 #$id\n金额：¥$amount", "channel.test",
        JSONObject().put("id", id).put("amount", amount).put("name", "原生面板测试订单"),
        refresh = { refresh() }, result = { r -> message("${r.optString("msg", "测试完成")}\n订单：${r.optString("trade_no")}\n${r.optString("qrcode")}") },
    )

    fun confirm(title: String, summary: String, operation: String, params: JSONObject, refresh: () -> Unit, result: ((JSONObject) -> Unit)? = null) =
        _state.update { it.copy(pending = PendingAction(operation, params, title, summary, refresh, result)) }
    fun cancelPending() = _state.update { it.copy(pending = null) }
    fun runPending() {
        val p = _state.value.pending ?: return
        _state.update { it.copy(pending = null) }
        call(p.operation, p.params, onSuccess = { result ->
            val code = result.optInt("code", 0)
            if (code !in setOf(0, 200)) message(result.optString("msg", "操作失败"))
            else { message(result.optString("msg", "操作成功")); p.result?.invoke(result) ?: p.refresh() }
        })
    }
    fun chooseEvidence(order: JSONObject) {
        bridge.selectEvidence(order.toString(), "订单 ${order.optString("trade_no")} 凭证")
        _state.update { it.copy(detail = order) }
    }
    fun openGallery() = bridge.openGallery()
    fun sendEvidence(order: JSONObject) = bridge.sendEvidence(order.toString(), "订单 ${order.optString("trade_no")} 凭证")
    fun copy(text: String) = bridge.copy(text)
    fun collapse() = bridge.collapse()
    fun message(text: String) = _state.update { it.copy(message = text) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun call(operation: String, params: JSONObject, onSuccess: (JSONObject) -> Unit) = io {
        val result = JSONObject(bridge.call(operation, params.toString()))
        onSuccess(result)
    }
    private fun io(onError: (Throwable) -> Unit = {}, block: suspend () -> Unit) {
        scope.launch {
            _state.update { it.copy(loading = true, message = null) }
            try { withContext(Dispatchers.IO) { block() } }
            catch (t: Throwable) {
                val message = t.message?.takeIf(String::isNotBlank) ?: "操作失败"
                _state.update { it.copy(message = message, authenticated = if (message.contains("登录") || message.contains("会话")) false else it.authenticated) }
                onError(t)
            } finally { _state.update { it.copy(loading = false) } }
        }
    }
}

private val Green = Color(0xff087a4b)
private val GreenTint = Color(0xffe8f5ee)
private val Mist = Color(0xfff4f7f5)
private val Ink = Color(0xff17211b)
private val Muted = Color(0xff66736b)
private val Line = Color(0xffdfe7e2)
private val Danger = Color(0xffba1a1a)

@Composable private fun FutoonPanelTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Green, secondary = Green, background = Mist, surface = Color.White, error = Danger), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun NativePanelRoot(model: NativePanelModel) {
    val state by model.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    state.message?.let { message -> LaunchedEffect(message) { snackbar.showSnackbar(message); model.consumeMessage() } }
    if (!state.authenticated) { NativeLogin(model); return }
    Scaffold(
        containerColor = Mist,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.moduleTitle.ifBlank { state.tab.title }, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text(if (state.context.telegramId.isBlank()) state.context.label else "${state.context.label} · TG ${state.context.telegramId}", color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    if (state.module != null || state.detail != null || state.form != null) IconButton(onClick = { model.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    else IconButton(onClick = model::collapse) { Icon(Icons.Default.Close, "收起") }
                },
                actions = {
                    if (state.loading) CircularProgressIndicator(Modifier.padding(14.dp).size(18.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = model::refresh) { Icon(Icons.Outlined.Refresh, "刷新") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        bottomBar = { if (state.module == null && state.form == null && state.detail == null) NativeBottomBar(state.tab, model::selectTab) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.form != null -> NativeFormScreen(state.form, model::saveForm)
                state.detail != null -> OrderDetailScreen(state, model)
                state.module == "domains" -> DomainScreen(state, model)
                state.module == "merchant" -> MerchantScreen(state, model)
                state.module != null -> ModuleScreen(state, model)
                state.tab == NativeTab.ORDERS -> OrdersScreen(state, model)
                state.tab == NativeTab.SETTLEMENTS -> SettlementsScreen(state, model)
                else -> MoreScreen(state, model)
            }
        }
    }
    state.pending?.let { pending ->
        AlertDialog(
            onDismissRequest = model::cancelPending,
            icon = { Icon(Icons.Outlined.WarningAmber, null, tint = Danger) },
            title = { Text(pending.title) }, text = { Text(pending.summary + "\n\n请核对对象、金额和影响后再提交。") },
            confirmButton = { Button(onClick = model::runPending) { Text("确认提交") } },
            dismissButton = { TextButton(onClick = model::cancelPending) { Text("取消") } },
        )
    }
}

@Composable private fun NativeBottomBar(tab: NativeTab, select: (NativeTab) -> Unit) {
    NavigationBar(containerColor = Color.White) {
        listOf(Triple(NativeTab.ORDERS, "订单", Icons.Outlined.ReceiptLong), Triple(NativeTab.SETTLEMENTS, "结算", Icons.Outlined.AccountBalanceWallet), Triple(NativeTab.MORE, "更多", Icons.Outlined.GridView)).forEach { (item, label, icon) ->
            NavigationBarItem(selected = tab == item, onClick = { select(item) }, icon = { Icon(icon, label) }, label = { Text(label) })
        }
    }
}

@Composable private fun NativeLogin(model: NativePanelModel) {
    var address by remember { mutableStateOf(model.address()) }
    var username by remember { mutableStateOf(model.username()) }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var captcha by remember { mutableStateOf<ByteArray?>(null) }
    val state by model.state.collectAsState()
    LazyColumn(Modifier.fillMaxSize().background(Mist), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("FUTOON PAY", color = Green, fontWeight = FontWeight.Bold, fontSize = 20.sp); Text("原生管理面板", color = Muted, fontSize = 12.sp) }
        item { NativeCard {
            NativeField("后台地址", address, { address = it }, "https://example.com/admin/")
            NativeField("管理员账号", username, { username = it }, "账号")
            OutlinedTextField(password, { password = it }, label = { Text("密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(code, { code = it.take(8) }, label = { Text("验证码") }, singleLine = true, modifier = Modifier.weight(1f))
                Box(Modifier.width(116.dp).height(54.dp).background(Color.White, RoundedCornerShape(8.dp)).clickable { model.captcha(address, username) { captcha = it } }, contentAlignment = Alignment.Center) {
                    val bitmap = remember(captcha) { captcha?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
                    if (bitmap != null) Image(bitmap, "验证码", Modifier.fillMaxSize()) else Text("加载验证码", color = Green, fontSize = 12.sp)
                }
            }
            Button(onClick = { model.configure(address, username, password, code) }, enabled = !state.loading && address.isNotBlank() && username.isNotBlank() && password.isNotBlank() && code.isNotBlank(), modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(if (state.loading) "正在登录…" else "登录") }
        } }
        state.message?.let { item { Text(it, color = Danger, fontSize = 13.sp) } }
    }
}

@Composable private fun OrdersScreen(state: PanelState, model: NativePanelModel) {
    val rows = state.data?.optJSONArray("rows") ?: JSONArray()
    var query by remember(state.query) { mutableStateOf(state.query) }
    Column(Modifier.fillMaxSize()) {
        SearchStrip(query, { query = it }, { model.loadOrders(1, query, state.status) })
        StatusStrip(state.status) { model.loadOrders(1, query, it) }
        state.data?.optJSONObject("metrics")?.let { metrics ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Metric("当前页", metrics.optInt("count").toString(), Modifier.weight(1f))
                Metric("已支付", metrics.optInt("successful").toString(), Modifier.weight(1f))
                Metric("成功率", "${metrics.optDouble("rate", 0.0)}%", Modifier.weight(1f))
            }
        }
        if (rows.length() == 0 && !state.loading) EmptyState("没有找到订单")
        else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items((0 until rows.length()).map { rows.getJSONObject(it) }, key = { it.optString("trade_no") }) { order -> OrderCard(order, state.evidenceCount, model) }
            item { Pager(state.page, state.data?.optInt("total", 0) ?: 0, { model.loadOrders(state.page - 1, query, state.status) }, { model.loadOrders(state.page + 1, query, state.status) }) }
        }
    }
}

@Composable private fun OrderCard(order: JSONObject, evidenceCount: Int, model: NativePanelModel) {
    val id = order.optString("trade_no")
    NativeCard(Modifier.clickable { model.loadOrderDetail(id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(id, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("商户 ${order.optString("uid")} · ${order.optString("username")}", color = Muted, fontSize = 11.sp)
            }
            StatusPill(orderStatus(order.optInt("status")), if (order.optInt("status") == 1) Green else Color(0xff8a5b00))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("${currencySymbol(order.optString("currency"))}${order.optString("money")}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("${order.optString("typeshowname")} · 通道 ${order.optString("channel")}", color = Muted, fontSize = 11.sp)
                Text(order.optString("addtime"), color = Muted, fontSize = 10.sp)
            }
            TextButton(onClick = { model.chooseEvidence(order) }) { Icon(Icons.Outlined.Image, null, Modifier.size(17.dp)); Spacer(Modifier.width(3.dp)); Text(if (evidenceCount > 0) "凭证 $evidenceCount" else "凭证") }
            TextButton(onClick = { model.loadOrderDetail(id) }) { Text("详情") }
        }
    }
}

@Composable private fun OrderDetailScreen(state: PanelState, model: NativePanelModel) {
    val order = state.detail ?: return
    var refund by remember { mutableStateOf(false) }
    var money by remember { mutableStateOf(order.optString("money")) }
    var remark by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { NativeCard {
            Text("订单信息", fontWeight = FontWeight.SemiBold)
            DetailRows(order, listOf("trade_no" to "系统订单号", "out_trade_no" to "商户订单号", "api_trade_no" to "通道订单号", "uid" to "商户 PID", "money" to "订单金额", "realmoney" to "实付金额", "getmoney" to "商户收入", "typeshowname" to "支付方式", "channel_name" to "通道", "buyer" to "付款方", "domain" to "来源域名", "ip" to "IP", "addtime" to "创建时间", "endtime" to "完成时间"), model)
        } }
        item { NativeCard {
            Text("订单操作", fontWeight = FontWeight.SemiBold)
            FlowButtons(listOf(
                "补发通知" to { model.orderAction(order.optString("trade_no"), "notify") },
                "查询通道" to { model.orderAction(order.optString("trade_no"), "queryChannelOrder") },
                (if (order.optInt("status") == 3) "解除冻结" else "冻结订单") to { model.orderAction(order.optString("trade_no"), if (order.optInt("status") == 3) "unfreeze" else "freeze") },
                "手工退款" to { refund = true },
            ))
        } }
        item { NativeCard {
            Text("图片凭证", fontWeight = FontWeight.SemiBold)
            Text("已选择 ${state.evidenceCount} 张；发送时仍会进入 Telegram 收件人选择和预览，不会自动发送。", color = Muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = model::openGallery, modifier = Modifier.weight(1f)) { Text("选择图片") }
                Button(onClick = { model.sendEvidence(order) }, enabled = state.evidenceCount > 0, modifier = Modifier.weight(1f)) { Text("发送预览") }
            }
        } }
    }
    if (refund) AlertDialog(onDismissRequest = { refund = false }, title = { Text("手工退款") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NativeField("退款金额", money, { money = it }, keyboard = KeyboardType.Decimal)
            NativeField("备注", remark, { remark = it }, "可选")
        }
    }, confirmButton = { TextButton(onClick = { refund = false; model.orderAction(order.optString("trade_no"), "refund", JSONObject().put("money", money).put("remark", remark)) }) { Text("核对并继续") } }, dismissButton = { TextButton(onClick = { refund = false }) { Text("取消") } })
}

@Composable private fun SettlementsScreen(state: PanelState, model: NativePanelModel) {
    val rows = state.data?.optJSONArray("rows") ?: JSONArray()
    var query by remember(state.query) { mutableStateOf(state.query) }
    Column(Modifier.fillMaxSize()) {
        SearchStrip(query, { query = it.filter(Char::isDigit) }, { model.loadSettlements(1, query, state.status) }, "商户 PID")
        StatusStrip(state.status, settlement = true) { model.loadSettlements(1, query, it) }
        if (rows.length() == 0 && !state.loading) EmptyState("没有找到结算记录")
        else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items((0 until rows.length()).map { rows.getJSONObject(it) }, key = { it.optString("id") }) { row -> SettlementCard(row, model) }
            item { Pager(state.page, state.data?.optInt("total", 0) ?: 0, { model.loadSettlements(state.page - 1, query, state.status) }, { model.loadSettlements(state.page + 1, query, state.status) }) }
        }
    }
}

@Composable private fun SettlementCard(row: JSONObject, model: NativePanelModel) {
    var fail by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    NativeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("结算 #${row.optString("id")}", fontWeight = FontWeight.SemiBold); Text("商户 ${row.optString("uid")} · ${row.optString("username")}", color = Muted, fontSize = 11.sp) }
            StatusPill(settlementStatus(row.optInt("status")), if (row.optInt("status") == 2) Green else Color(0xff8a5b00))
        }
        Text("${currencySymbol(row.optString("currency"))}${row.optString("money")}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("到账 ${row.optString("realmoney")} · ${row.optString("type")} ${row.optString("account")}", color = Muted, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { model.settlementAction(row, 1) }) { Text("处理中") }
            TextButton(onClick = { model.settlementAction(row, 2) }) { Text("完成") }
            TextButton(onClick = { fail = true }) { Text("失败", color = Danger) }
        }
    }
    if (fail) AlertDialog(onDismissRequest = { fail = false }, title = { Text("标记结算失败") }, text = { NativeField("失败理由", reason, { reason = it }, "必填") }, confirmButton = { TextButton(enabled = reason.isNotBlank(), onClick = { fail = false; model.settlementAction(row, 3, reason) }) { Text("继续") } }, dismissButton = { TextButton(onClick = { fail = false }) { Text("取消") } })
}

private data class MoreEntry(val id: String, val title: String, val subtitle: String, val icon: ImageVector)
private val moreGroups = listOf(
    "会话商户" to listOf(MoreEntry("merchant", "当前商户", "按群话题或私聊 TGID 精确查询", Icons.Outlined.PersonSearch)),
    "支付配置" to listOf(
        MoreEntry("channels", "支付通道", "通道、账户配置与测试支付", Icons.Outlined.Power),
        MoreEntry("payment_types", "支付方式", "费率与关联通道", Icons.Outlined.Payments),
        MoreEntry("rolls", "轮询组", "选路策略与成员通道", Icons.Outlined.AccountTree),
    ),
    "监测与网络" to listOf(
        MoreEntry("channel_monitor", "通道监测", "5 分钟至 24 小时成功率", Icons.Outlined.Insights),
        MoreEntry("proxies", "中转代理", "代理节点、状态和关联", Icons.Outlined.Dns),
    ),
    "审核与风控" to listOf(
        MoreEntry("domains", "域名审核", "授权域名与审核状态", Icons.Outlined.Language),
        MoreEntry("risk", "风控记录", "风险命中与请求记录", Icons.Outlined.Shield),
        MoreEntry("blacklist", "支付黑名单", "添加与移除拦截规则", Icons.Outlined.Block),
    ),
    "资金与商户" to listOf(
        MoreEntry("funds", "资金明细", "余额变化与交易流水", Icons.Outlined.AccountBalanceWallet),
        MoreEntry("merchant_stats", "商户统计", "按日期查看交易表现", Icons.Outlined.BarChart),
        MoreEntry("inactive", "5 日无流水商户", "长期无交易商户", Icons.Outlined.PersonOff),
        MoreEntry("profit_receivers", "分账接收方", "接收方账户和关系", Icons.Outlined.CallSplit),
        MoreEntry("profit_orders", "分账记录", "分账订单与结果", Icons.Outlined.ReceiptLong),
        MoreEntry("transfer", "企业付款", "付款任务和收款方", Icons.Outlined.Send),
    ),
    "运营与系统" to listOf(
        MoreEntry("announcements", "公告管理", "公告内容与启停", Icons.Outlined.Campaign),
        MoreEntry("payment_settings", "支付与结算设置", "支付、结算、预付款、企业付款", Icons.Outlined.Tune),
        MoreEntry("site_settings", "站点与账号设置", "站点、模板、邮件、任务和管理员", Icons.Outlined.Settings),
        MoreEntry("integrations", "公众号与证书", "微信、OAuth、证书和客服", Icons.Outlined.Extension),
        MoreEntry("login_logs", "登录日志", "管理员与商户登录记录", Icons.Outlined.History),
    ),
)

@Composable private fun MoreScreen(state: PanelState, model: NativePanelModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        moreGroups.forEach { (group, entries) ->
            item { Text(group, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, top = 5.dp)) }
            item { NativeCard(contentPadding = PaddingValues(horizontal = 12.dp)) {
                entries.forEachIndexed { index, entry ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 62.dp).clickable {
                        when (entry.id) { "merchant" -> model.merchantLookup(); "domains" -> model.loadDomains(); else -> model.loadModule(entry.id, entry.title) }
                    }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).background(GreenTint, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(entry.icon, null, tint = Green, modifier = Modifier.size(20.dp)) }
                        Column(Modifier.weight(1f).padding(horizontal = 11.dp)) { Text(entry.title, fontWeight = FontWeight.Medium, fontSize = 14.sp); Text(entry.subtitle, color = Muted, fontSize = 11.sp) }
                        Icon(Icons.Default.ChevronRight, null, tint = Muted)
                    }
                    if (index != entries.lastIndex) HorizontalDivider(color = Line)
                }
            } }
        }
        item { OutlinedButton(onClick = model::logout, modifier = Modifier.fillMaxWidth()) { Text("退出 FUTOON 后台登录", color = Danger) } }
    }
}

@Composable private fun ModuleScreen(state: PanelState, model: NativePanelModel) {
    val data = state.data ?: JSONObject()
    var query by remember(state.query) { mutableStateOf(state.query) }
    Column(Modifier.fillMaxSize()) {
        SearchStrip(query, { query = it }, { model.loadModule(state.module.orEmpty(), state.moduleTitle, 1, query) })
        val sections = data.optJSONArray("sections") ?: JSONArray()
        if (sections.length() > 1) LazyRow(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items((0 until sections.length()).map { sections.getJSONObject(it) }) { section ->
                FilterChip(selected = data.optInt("section") == section.optInt("index"), onClick = { model.loadModule(state.module.orEmpty(), state.moduleTitle, 1, query, section.optInt("index")) }, label = { Text(section.optString("title")) })
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            val notices = data.optJSONArray("notices") ?: JSONArray()
            items((0 until notices.length()).map { notices.optString(it) }) { Notice(it) }
            val actions = data.optJSONArray("actions") ?: JSONArray()
            if (actions.length() > 0) item { NativeCard { Text("可用操作", fontWeight = FontWeight.SemiBold); ActionButtons(actions, model) } }
            val forms = data.optJSONArray("forms") ?: JSONArray()
            if (forms.length() > 0) item { NativeCard { Text("配置表单", fontWeight = FontWeight.SemiBold); FlowButtons((0 until forms.length()).map { i -> forms.getJSONObject(i).optString("title", "编辑") to { model.openForm(forms.getJSONObject(i)) } }) } }
            if (state.module in setOf("channels", "payment_types", "rolls", "proxies")) item {
                Button(onClick = { model.openModuleEditor("add") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("新增${state.moduleTitle}") }
            }
            if (state.module == "blacklist") item { BlacklistAdd(model) }
            val rows = data.optJSONArray("rows")
            if (rows != null) items((0 until rows.length()).map { rows.getJSONObject(it) }) { row -> GenericObjectCard(row, state.module.orEmpty(), model) }
            val tables = data.optJSONArray("tables") ?: JSONArray()
            (0 until tables.length()).forEach { tableIndex ->
                val table = tables.getJSONObject(tableIndex)
                item { Text(table.optString("title", state.moduleTitle), color = Muted, fontWeight = FontWeight.Medium, fontSize = 12.sp) }
                val headers = table.optJSONArray("headers") ?: JSONArray()
                val tableRows = table.optJSONArray("rows") ?: JSONArray()
                items((0 until tableRows.length()).map { tableRows.getJSONObject(it) }) { row -> DocumentRowCard(headers, row, state.module.orEmpty(), model) }
            }
            if ((rows == null || rows.length() == 0) && tables.length() == 0 && !state.loading) item { EmptyState("后台没有返回可显示的数据") }
            item { Pager(state.page, data.optInt("total", 0), { model.loadModule(state.module.orEmpty(), state.moduleTitle, state.page - 1, query) }, { model.loadModule(state.module.orEmpty(), state.moduleTitle, state.page + 1, query) }) }
        }
    }
}

@Composable private fun DocumentRowCard(headers: JSONArray, row: JSONObject, module: String, model: NativePanelModel) {
    val cells = row.optJSONArray("cells") ?: JSONArray()
    NativeCard {
        (0 until minOf(headers.length(), cells.length())).filter { cells.optString(it).isNotBlank() }.take(12).forEach { index -> DetailRow(headers.optString(index, "字段 ${index + 1}"), cells.optString(index)) }
        val actions = row.optJSONArray("actions") ?: JSONArray()
        if (actions.length() > 0) { HorizontalDivider(color = Line); ActionButtons(actions, model) }
        val id = row.optString("id")
        if (id.isNotBlank() && module in setOf("channels", "rolls")) {
            HorizontalDivider(color = Line)
            FlowButtons(buildList {
                if (module == "channels") { add("账户配置" to { model.openModuleEditor("account", id) }); add("成功率" to { model.channelStats(id) }); add("测试支付" to { model.testChannel(id, "1.00") }) }
                if (module == "rolls") add("编辑路由" to { model.openModuleEditor("route", id) })
            })
        }
    }
}

@Composable private fun ActionButtons(actions: JSONArray, model: NativePanelModel) {
    FlowButtons((0 until actions.length()).map { index ->
        val action = actions.getJSONObject(index)
        action.optString("label", "操作") to { if (action.optBoolean("editor")) model.inspectAction(action) else model.runAction(action) }
    })
}

@Composable private fun GenericObjectCard(row: JSONObject, module: String, model: NativePanelModel) {
    NativeCard {
        val keys = row.keys().asSequence().filter { key -> key !in setOf("actions", "fields") && row.optString(key).isNotBlank() }.take(12).toList()
        keys.forEach { key -> DetailRow(fieldLabel(key), row.optString(key)) }
        if (module == "channel_monitor") {
            val fields = row.optJSONArray("fields") ?: JSONArray()
            (0 until fields.length()).map { fields.getJSONObject(it) }.forEach { DetailRow(it.optString("label"), it.optString("value")) }
        }
        val actions = row.optJSONArray("actions") ?: JSONArray()
        if (actions.length() > 0) ActionButtons(actions, model)
        if (module == "blacklist" && row.optString("id").isNotBlank()) TextButton(onClick = { model.removeBlacklist(row.optString("id")) }) { Text("移除", color = Danger) }
    }
}

@Composable private fun BlacklistAdd(model: NativePanelModel) {
    var show by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf("ip") }
    var content by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    OutlinedButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("添加黑名单") }
    if (show) AlertDialog(onDismissRequest = { show = false }, title = { Text("添加支付黑名单") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NativeField("类型", type, { type = it }, "ip / domain / user")
        NativeField("匹配内容", content, { content = it })
        NativeField("原因", reason, { reason = it }, "可选")
    } }, confirmButton = { TextButton(enabled = content.isNotBlank(), onClick = { show = false; model.addBlacklist(type, content, reason) }) { Text("核对并继续") } }, dismissButton = { TextButton(onClick = { show = false }) { Text("取消") } })
}

@Composable private fun DomainScreen(state: PanelState, model: NativePanelModel) {
    val rows = state.data?.optJSONArray("rows") ?: JSONArray()
    var query by remember(state.query) { mutableStateOf(state.query) }
    Column(Modifier.fillMaxSize()) {
        SearchStrip(query, { query = it.filter(Char::isDigit) }, { model.loadDomains(1, query) }, "商户 PID")
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items((0 until rows.length()).map { rows.getJSONObject(it) }) { row -> NativeCard {
                Text(row.optString("domain"), fontWeight = FontWeight.SemiBold)
                Text("商户 ${row.optString("uid")} · ${row.optString("addtime")}", color = Muted, fontSize = 11.sp)
                Row { TextButton(onClick = { model.domainAction(row, 1) }) { Text("通过") }; TextButton(onClick = { model.domainAction(row, 2) }) { Text("拒绝", color = Danger) } }
            } }
            item { Pager(state.page, state.data?.optInt("total", 0) ?: 0, { model.loadDomains(state.page - 1, query) }, { model.loadDomains(state.page + 1, query) }) }
        }
    }
}

@Composable private fun MerchantScreen(state: PanelState, model: NativePanelModel) {
    val rows = state.data?.optJSONArray("rows") ?: JSONArray()
    if (rows.length() == 0) { EmptyState("没有找到与 TG ${state.context.telegramId} 精确绑定的商户"); return }
    val merchant = rows.getJSONObject(0)
    val uid = merchant.optString("uid")
    var balanceDialog by remember { mutableStateOf(false) }
    var groupDialog by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var add by remember { mutableStateOf(true) }
    var gid by remember { mutableStateOf("") }
    var groupEnd by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { NativeCard { Text("${merchant.optString("username", "商户")} · PID $uid", fontWeight = FontWeight.SemiBold, fontSize = 17.sp); merchant.keys().asSequence().filter { it !in setOf("uid", "username") }.take(12).forEach { DetailRow(fieldLabel(it), merchant.optString(it)) } } }
        item { NativeCard { Text("商户操作", fontWeight = FontWeight.SemiBold); FlowButtons(listOf(
            "编辑资料" to { model.callMerchantProfile(uid) },
            "调整余额" to { balanceDialog = true },
            "修改用户组" to { groupDialog = true },
            "重置密钥" to { model.confirm("重置商户密钥", "商户 PID：$uid\n重置后旧密钥立即失效。", "merchant.resetKey", JSONObject().put("uid", uid)) { model.merchantLookup() } },
            "启用商户" to { model.merchantStatus(uid, "status", true) },
            "停用商户" to { model.merchantStatus(uid, "status", false) },
            "允许支付" to { model.merchantStatus(uid, "pay", true) },
            "禁止支付" to { model.merchantStatus(uid, "pay", false) },
            "允许结算" to { model.merchantStatus(uid, "settle", true) },
            "禁止结算" to { model.merchantStatus(uid, "settle", false) },
        )) } }
    }
    if (balanceDialog) AlertDialog(onDismissRequest = { balanceDialog = false }, title = { Text("调整商户余额") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row { FilterChip(selected = add, onClick = { add = true }, label = { Text("增加") }); Spacer(Modifier.width(8.dp)); FilterChip(selected = !add, onClick = { add = false }, label = { Text("扣除") }) }
        NativeField("金额", amount, { amount = it }, keyboard = KeyboardType.Decimal)
    } }, confirmButton = { TextButton(enabled = amount.isNotBlank(), onClick = { balanceDialog = false; model.confirm("确认余额调整", "商户 PID：$uid\n${if (add) "增加" else "扣除"}：¥$amount", "merchant.balance", JSONObject().put("uid", uid).put("direction", if (add) "add" else "deduct").put("amount", amount).put("currency", "CNY")) { model.merchantLookup() } }) { Text("核对并继续") } }, dismissButton = { TextButton(onClick = { balanceDialog = false }) { Text("取消") } })
    if (groupDialog) AlertDialog(onDismissRequest = { groupDialog = false }, title = { Text("修改用户组") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NativeField("用户组 GID", gid, { gid = it.filter(Char::isDigit) })
        NativeField("到期时间", groupEnd, { groupEnd = it }, "可选")
    } }, confirmButton = { TextButton(enabled = gid.isNotBlank(), onClick = { groupDialog = false; model.merchantGroup(uid, gid, groupEnd) }) { Text("核对并继续") } }, dismissButton = { TextButton(onClick = { groupDialog = false }) { Text("取消") } })
}

@Composable private fun NativeFormScreen(form: JSONObject, save: (Map<String, String>) -> Unit) {
    val fields = form.optJSONArray("fields") ?: JSONArray()
    val values = remember(form.optString("ticket")) { mutableStateMapOf<String, String>().apply { (0 until fields.length()).map { fields.getJSONObject(it) }.forEach { put(it.optString("name"), it.optString("value")) } } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(form.optString("title", "编辑配置"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp); Text("原生动态表单 · 字段由后台配置生成", color = Muted, fontSize = 11.sp) }
        items((0 until fields.length()).map { fields.getJSONObject(it) }, key = { it.optString("name") }) { field ->
            val name = field.optString("name")
            when (field.optString("type").lowercase()) {
                "select" -> SelectField(field.optString("label", name), values[name].orEmpty(), field.optJSONArray("options") ?: JSONArray(), field.optBoolean("readOnly")) { values[name] = it }
                "checkbox", "switch" -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Checkbox(values[name] in setOf("1", "true", "on"), { values[name] = if (it) "1" else "0" }, enabled = !field.optBoolean("readOnly")); Text(field.optString("label", name)) }
                "multiselect" -> MultiSelectField(field, values[name].orEmpty()) { values[name] = it }
                else -> OutlinedTextField(
                    value = values[name].orEmpty(), onValueChange = { values[name] = it },
                    label = { Text(field.optString("label", name) + if (field.optBoolean("required")) " *" else "") },
                    readOnly = field.optBoolean("readOnly"), singleLine = field.optString("type") !in setOf("textarea", "json"),
                    visualTransformation = if (field.optBoolean("sensitive")) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (field.optString("type") == "number") KeyboardType.Decimal else KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (form.optString("kind") == "route") item { RouteHint() }
        item { Button(onClick = { save(values.toMap()) }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("核对并保存") } }
    }
}

@Composable private fun SelectField(label: String, value: String, options: JSONArray, readOnly: Boolean, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = (0 until options.length()).map { options.getJSONObject(it) }.firstOrNull { it.optString("value") == value }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(value = selected?.optString("label") ?: value, onValueChange = {}, label = { Text(label) }, readOnly = true, enabled = !readOnly, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }, modifier = Modifier.fillMaxWidth().clickable(enabled = !readOnly) { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (0 until options.length()).map { options.getJSONObject(it) }.forEach { option -> DropdownMenuItem(text = { Text(option.optString("label", option.optString("value"))) }, onClick = { expanded = false; onChange(option.optString("value")) }) }
        }
    }
}

@Composable private fun MultiSelectField(field: JSONObject, value: String, onChange: (String) -> Unit) {
    val options = field.optJSONArray("options") ?: JSONArray()
    val selected = value.split(',', '|').map(String::trim).filter(String::isNotBlank).toMutableSet()
    NativeCard {
        Text(field.optString("label"), fontWeight = FontWeight.Medium)
        (0 until options.length()).map { options.getJSONObject(it) }.forEach { option ->
            val id = option.optString("value")
            Row(Modifier.fillMaxWidth().clickable {
                if (!selected.add(id)) selected.remove(id); onChange(selected.joinToString(","))
            }, verticalAlignment = Alignment.CenterVertically) { Checkbox(id in selected, null); Text(option.optString("label", id), fontSize = 13.sp) }
        }
    }
}

@Composable private fun RouteHint() = Notice("轮询成员和权重会与基本信息一起提交；若后续配置失败，面板会保留已保存状态，避免重复创建。")

@Composable private fun SearchStrip(value: String, onChange: (String) -> Unit, search: () -> Unit, placeholder: String = "订单号 / PID") {
    Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        OutlinedTextField(value, onChange, singleLine = true, placeholder = { Text(placeholder, fontSize = 12.sp) }, leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(19.dp)) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
        Button(onClick = search, contentPadding = PaddingValues(horizontal = 13.dp), modifier = Modifier.height(48.dp)) { Text("查询") }
    }
}

@Composable private fun StatusStrip(value: String, settlement: Boolean = false, select: (String) -> Unit) {
    val labels = if (settlement) listOf("-1" to "全部", "0" to "待处理", "1" to "处理中", "2" to "已完成", "3" to "失败") else listOf("-1" to "全部", "0" to "待支付", "1" to "已支付", "2" to "已退款", "3" to "已冻结")
    LazyRow(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 10.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(labels) { (id, label) -> FilterChip(selected = value == id, onClick = { select(id) }, label = { Text(label, fontSize = 11.sp) }) }
    }
}

@Composable private fun NativeCard(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(12.dp), content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.fillMaxWidth().padding(contentPadding), verticalArrangement = Arrangement.spacedBy(7.dp), content = content)
    }
}

@Composable private fun DetailRows(data: JSONObject, fields: List<Pair<String, String>>, model: NativePanelModel) {
    fields.filter { data.optString(it.first).isNotBlank() }.forEach { (key, label) -> DetailRow(label, data.optString(key), onCopy = { model.copy(data.optString(key)) }) }
}

@Composable private fun DetailRow(label: String, value: String, onCopy: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onCopy != null) Modifier.clickable(onClick = onCopy) else Modifier), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = Muted, fontSize = 11.sp, modifier = Modifier.widthIn(min = 78.dp, max = 112.dp))
        Text(value.ifBlank { "—" }, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

@Composable private fun NativeField(label: String, value: String, onChange: (String) -> Unit, placeholder: String = "", keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value, onChange, label = { Text(label) }, placeholder = { Text(placeholder) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = keyboard), modifier = Modifier.fillMaxWidth())
}

@Composable private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(10.dp)).padding(9.dp)) { Text(label, color = Muted, fontSize = 10.sp); Text(value, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
}

@Composable private fun StatusPill(text: String, color: Color) = Surface(color = color.copy(alpha = .1f), shape = RoundedCornerShape(20.dp)) { Text(text, color = color, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)) }

@Composable private fun Notice(text: String) = Surface(color = Color(0xfffff4d8), shape = RoundedCornerShape(10.dp)) { Text(text, color = Color(0xff6a5100), fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(10.dp)) }

@Composable private fun EmptyState(text: String) = Box(Modifier.fillMaxSize().padding(30.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.Inbox, null, tint = Muted, modifier = Modifier.size(34.dp)); Spacer(Modifier.height(8.dp)); Text(text, color = Muted, fontSize = 13.sp) } }

@Composable private fun Pager(page: Int, total: Int, previous: () -> Unit, next: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        IconButton(onClick = previous, enabled = page > 1) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "上一页") }
        Text("第 $page 页" + if (total > 0) " · 共 $total 条" else "", color = Muted, fontSize = 11.sp)
        IconButton(onClick = next, enabled = total == 0 || page * 30 < total) { Icon(Icons.Default.ArrowForward, "下一页") }
    }
}

@Composable private fun FlowButtons(actions: List<Pair<String, () -> Unit>>) {
    actions.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        row.forEach { (label, action) -> OutlinedButton(onClick = action, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 7.dp)) { Text(label, fontSize = 11.sp, maxLines = 1) } }
        if (row.size == 1) Spacer(Modifier.weight(1f))
    } }
}

private fun orderStatus(status: Int) = when (status) { 0 -> "待支付"; 1 -> "已支付"; 2 -> "已退款"; 3 -> "已冻结"; else -> "未知" }
private fun settlementStatus(status: Int) = when (status) { 0 -> "待处理"; 1 -> "处理中"; 2 -> "已完成"; 3 -> "失败"; else -> "未知" }
private fun orderActionName(action: String) = when (action) { "notify" -> "补发通知"; "queryChannelOrder" -> "查询通道订单"; "freeze" -> "冻结"; "unfreeze" -> "解除冻结"; "refund" -> "手工退款"; else -> action }
private fun currencySymbol(currency: String) = when (currency.uppercase()) { "CNY", "RMB", "" -> "¥"; "USD", "USDT" -> "$"; "HKD" -> "HK$"; else -> "$currency " }
private fun fieldLabel(key: String) = mapOf("id" to "ID", "uid" to "商户 PID", "username" to "商户", "trade_no" to "订单号", "money" to "金额", "type" to "类型", "status" to "状态", "addtime" to "创建时间", "endtime" to "完成时间", "content" to "内容", "remark" to "备注", "ip" to "IP", "city" to "地区")[key] ?: key




