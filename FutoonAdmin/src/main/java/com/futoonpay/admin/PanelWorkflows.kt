package com.futoonpay.admin

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.futoonpay.admin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal val LocalPanelWorkflows = staticCompositionLocalOf<PanelWorkflows?> { null }
internal data class MessageSource(val dialog: Long = 0, val topic: Long = 0, val message: Long = 0) {
    fun json() = JSONObject().put("dialog", dialog.toString()).put("topic", topic).put("message", message)
    companion object { fun from(json: JSONObject?) = MessageSource(json?.optLong("dialog") ?: 0, json?.optLong("topic") ?: 0, json?.optLong("message") ?: 0) }
}
internal data class QueryCandidate(
    val value: String, val kind: String = "AUTO", val selected: Boolean = true,
    val id: String = UUID.randomUUID().toString(), val phase: String = "queued",
    val orders: List<String> = emptyList(), val error: String = "", val total: Int = 0,
    val source: MessageSource = MessageSource(),
    val matchedKind: String = "",
)
internal data class WorkflowState(
    val text: String = "", val ocr: Boolean = false, val candidates: List<QueryCandidate> = emptyList(), val queryKind: String = "AUTO",
    val extracting: Boolean = false, val running: Boolean = false, val error: String? = null,
    val group: String = "none", val localLoading: Boolean = true, val localRevision: Int = 0,
    val notice: String? = null,
    val merchant: String? = null, val merchantLoading: Boolean = false, val merchantError: String? = null,
    val manualMerchant: Boolean = false, val merchantSource: String = "",
)

/** Native port of v0.5's candidate, batch, history, draft and contextLookup workflows.
 * Extraction and exact queries still execute the original Java implementation.
 * Local JSON keys/limits intentionally match v0.5 so existing encrypted data remains usable.
 */
internal class PanelWorkflows(private val bridge: NativePanelBridge) : ViewModel() {
    private val mutable = MutableStateFlow(WorkflowState())
    val state = mutable.asStateFlow()
    private var local = JSONObject()
    private val saveLock = Mutex()
    private val localReady = CompletableDeferred<Unit>()
    private val backend = bridge.address()
    private var chat = ChatScope()
    private var source = MessageSource()
    private var pointers = emptyMap<String, MessageSource>()
    private var queryGeneration = 0
    private var lookupGeneration = 0
    private var stop = false
    private var receivedId = ""
    private var lookupColumn = "telegram_id"
    private var lookupValue = ""
    private val recentOrders = linkedMapOf<String, String>()

    init { viewModelScope.launch {
        runCatching { call("local.read") }.onSuccess { local = JSONObject(it.ifBlank { "{}" }) }
            .onFailure { mutable.update { s -> s.copy(error = "本地记录读取失败：${it.message}") } }
        localReady.complete(Unit)
        mutable.update { it.copy(localLoading = false, localRevision = it.localRevision + 1) }
        if (chat.telegramId.isBlank() && binding().isNotBlank()) lookupAutomatic()
    } }

    private suspend fun call(operation: String, params: JSONObject = JSONObject()): String = withContext(Dispatchers.IO) {
        check(bridge.address() == backend) { "后台已切换，请重新打开面板" }
        val result = bridge.call(operation, params.toString())
        ensureActive()
        check(bridge.address() == backend) { "后台已切换，已忽略旧结果" }
        result
    }
    private fun failure(error: Throwable) { if (error is CancellationException) throw error; mutable.update { it.copy(error = error.message ?: "操作失败") } }
    private fun changed() {
        mutable.update { it.copy(localRevision = it.localRevision + 1) }
        val snapshot = local.toString()
        viewModelScope.launch { saveLock.withLock { runCatching { call("local.write", JSONObject(snapshot)) }.onFailure(::failure) } }
    }
    fun array(key: String): List<JSONObject> = local.optJSONArray(key).objects()
    fun drafts(): List<JSONObject> = local.optJSONObject("drafts")?.let { obj -> obj.keys().asSequence().mapNotNull { obj.optJSONObject(it) }.sortedByDescending { it.optLong("updated") }.toList() }.orEmpty()
    fun case(trade: String): JSONObject = local.optJSONObject("cases")?.optJSONObject(trade) ?: JSONObject()
    private fun objectAt(key: String) = local.optJSONObject(key) ?: JSONObject().also { local.put(key, it) }
    fun binding(): String = local.optJSONObject("groups")?.optString(chat.key).orEmpty()
    fun clearError() { mutable.update { it.copy(error = null) } }
    fun notice(message: String) { mutable.update { it.copy(notice = message) } }
    fun consumeNotice() { mutable.update { it.copy(notice = null) } }
    fun setText(text: String) { source = MessageSource(); pointers = emptyMap(); mutable.update { it.copy(text = text.take(16384), error = null) } }
    fun setGroup(group: String) { mutable.update { it.copy(group = group) } }
    fun setKind(kind: String) { mutable.update { it.copy(queryKind = kind) } }
    fun editCandidate(id: String, value: String? = null, kind: String? = null, selected: Boolean? = null) {
        if (state.value.running) return
        mutable.update { s -> s.copy(candidates = s.candidates.map { if (it.id == id) it.copy(value = value?.take(128) ?: it.value, kind = kind ?: it.kind, selected = selected ?: it.selected, phase = if (value != null || kind != null) "queued" else it.phase, orders = if (value != null || kind != null) emptyList() else it.orders) else it }) }
    }
    fun receive(query: RecognizedQuery) {
        if (query.id == receivedId) return
        if (query.error.isNotBlank()) { receivedId = query.id; mutable.update { it.copy(error = query.error) }; return }
        receivedId = query.id; queryGeneration++; stop = true
        if (query.value.length > 16384) { mutable.update { it.copy(text = query.value.take(16384), candidates = emptyList(), running = false, extracting = false, error = "每次最多识别 16384 个字符，请缩小所选消息范围") }; return }
        source = query.source
        pointers = query.pointers
        mutable.update { it.copy(text = query.value, ocr = query.ocr, candidates = emptyList(), running = false, extracting = false, queryKind = query.kind, error = query.error.takeIf(String::isNotBlank)) }
        if (query.error.isBlank()) extract(query.kind)
    }
    fun extract(kind: String = state.value.queryKind) = viewModelScope.launch {
        if (state.value.running || state.value.extracting) return@launch
        val generation = ++queryGeneration
        val input = state.value.text
        mutable.update { it.copy(extracting = true, queryKind = kind, error = null) }
        try {
            val rows = JSONArray(call("recognize", JSONObject().put("text", input))).objects()
            if (generation != queryGeneration) return@launch
            val candidates = rows.map { QueryCandidate(it.optString("value"), if (kind == "AUTO") it.optString("kind", "AUTO") else kind, source = pointers[it.optString("kind", "AUTO") + ":" + it.optString("value")] ?: source) }.distinctBy { it.kind + ":" + it.value }
            mutable.update { it.copy(candidates = candidates, extracting = false, error = if (candidates.isEmpty()) "没有提取到完整编号，请校对文字或手动输入" else null) }
        } catch (e: Throwable) { if (generation == queryGeneration) { mutable.update { it.copy(extracting = false) }; failure(e) } }
    }
    fun requestOcr() = viewModelScope.launch { runCatching { call("ocr.gallery") }.onFailure(::failure) }
    fun stopQueries() { stop = true }
    fun runQueries(onlyFailed: Boolean = false, refresh: Boolean = false) = viewModelScope.launch {
        if (state.value.running || state.value.extracting || state.value.candidates.isEmpty()) return@launch
        val generation = queryGeneration; stop = false
        mutable.update { it.copy(running = true, error = null) }
        try {
            localReady.await()
            for (candidate in state.value.candidates) {
                if (stop || generation != queryGeneration) break
                if (!candidate.selected || onlyFailed && candidate.phase != "failed" || !onlyFailed && !refresh && candidate.phase == "ok") continue
                updateCandidate(candidate.id) { it.copy(phase = "loading", error = "") }
                try {
                    val result = JSONObject(call("orders.search", JSONObject().put("text", candidate.value).put("kind", candidate.kind).put("page", 1)))
                    if (generation != queryGeneration) break
                    val rows = result.optJSONArray("rows").objects().map { row -> row.put("_source", candidate.source.json()).also(::rememberOrder).toString() }
                    updateCandidate(candidate.id) { it.copy(phase = if (rows.isEmpty()) "failed" else "ok", orders = rows, total = result.optInt("total", rows.size), matchedKind = result.optString("kind", candidate.kind), error = if (rows.isEmpty()) "未找到精确匹配订单" else result.optString("warning")) }
                    history(candidate.value, candidate.kind)
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    if (generation != queryGeneration) break
                    updateCandidate(candidate.id) { it.copy(phase = "failed", error = e.message ?: "查询失败") }
                    if (e.message.orEmpty().contains("登录") || e.message.orEmpty().contains("后台已切换")) break
                }
            }
        } finally { if (generation == queryGeneration) mutable.update { it.copy(running = false) } }
    }
    private fun updateCandidate(id: String, transform: (QueryCandidate) -> QueryCandidate) { mutable.update { s -> s.copy(candidates = s.candidates.map { if (it.id == id) transform(it) else it }) } }
    private fun history(text: String, kind: String) {
        local.put("history", JSONArray(listOf(JSONObject().put("text", text).put("kind", kind).put("time", System.currentTimeMillis())) + array("history").filter { it.optString("text") != text }.take(29)))
        changed()
    }
    fun queryHistory(row: JSONObject) { receive(RecognizedQuery(row.optString("text"), row.optString("kind", "AUTO"))) }
    fun rememberOrder(row: JSONObject) {
        val id = row.optString("trade_no"); if (id.isBlank()) return
        recentOrders.remove(id); recentOrders[id] = row.toString()
        while (recentOrders.size > 100) recentOrders.remove(recentOrders.keys.first())
    }
    fun rawOrder(trade: String): JSONObject = recentOrders[trade]?.let(::JSONObject) ?: array("favorites").firstOrNull { it.optString("trade_no") == trade }?.let { JSONObject(it.toString()) } ?: JSONObject().put("trade_no", trade)
    fun rememberDetail(detail: OrderDetail): JSONObject = rawOrder(detail.tradeNo).put("uid", detail.merchantId).put("out_trade_no", detail.merchantTradeNo).put("api_trade_no", detail.apiTradeNo).put("money", detail.amount).put("currency", detail.currency).put("status", detail.status).put("_source", rawOrder(detail.tradeNo).optJSONObject("_source") ?: MessageSource().json()).also(::rememberOrder)
    fun isFavorite(trade: String) = array("favorites").any { it.optString("trade_no") == trade }
    fun favorite(row: JSONObject) {
        if (state.value.localLoading) return
        val old = array("favorites"); val id = row.optString("trade_no")
        local.put("favorites", JSONArray(if (isFavorite(id)) old.filter { it.optString("trade_no") != id } else (listOf(row) + old).take(100))); changed()
    }
    fun saveNote(trade: String, note: String) { if (state.value.localLoading) return; val item = case(trade).put("note", note.take(4000)); objectAt("cases").put(trade, item); changed() }
    fun saveDraft(order: JSONObject, caption: String, status: String) {
        if (state.value.localLoading) return
        val trade = order.optString("trade_no"); if (trade.isBlank()) return
        val origin = MessageSource.from(order.optJSONObject("_source"))
        val drafts = objectAt("drafts")
        drafts.put(trade, JSONObject().put("order", order).put("caption", caption.take(1024)).put("sourceContext", origin.json()).put("sourceMessage", origin.message).put("updated", System.currentTimeMillis()))
        while (drafts.length() > 50) drafts.remove(drafts.keys().asSequence().minBy { drafts.getJSONObject(it).optLong("updated") })
        objectAt("cases").put(trade, case(trade).put("status", status))
        changed()
        viewModelScope.launch { runCatching { call("evidence.draft", JSONObject().put("order", order).put("caption", caption.take(1024))) }.onFailure(::failure) }
    }
    fun restoreDraft(draft: JSONObject) = viewModelScope.launch {
        val order = draft.optJSONObject("order") ?: return@launch
        val pointer = MessageSource.from(draft.optJSONObject("sourceContext")).copy(message = draft.optLong("sourceMessage"))
        order.put("_source", pointer.json()); rememberOrder(order)
        runCatching { call("evidence.restore", JSONObject().put("order", order).put("caption", draft.optString("caption"))) }.onFailure(::failure)
    }
    fun refreshDeliveries() = viewModelScope.launch {
        localReady.await()
        runCatching {
            val deliveries = JSONObject(call("delivery.read"))
            var edited = false
            deliveries.keys().asSequence().forEach { trade ->
                val delivery = deliveries.getJSONObject(trade)
                val item = case(trade)
                val text = listOfNotNull(
                    delivery.optInt("sent").takeIf { it > 0 }?.let { "Telegram 已确认 $it 张凭证" },
                    delivery.optInt("failed").takeIf { it > 0 }?.let { "$it 张发送失败" },
                    delivery.optInt("pending").takeIf { it > 0 }?.let { "$it 张发送中" },
                ).joinToString("；")
                if (item.optString("sendState") != text && text.isNotBlank()) {
                    objectAt("cases").put(trade, item.put("sendState", text).put("target", delivery.optString("dialog")).put("message", delivery.optInt("message")))
                    edited = true
                }
            }
            if (edited) changed()
        }.onFailure(::failure)
    }
    fun jumpToSource(order: JSONObject) = viewModelScope.launch {
        val pointer = MessageSource.from(order.optJSONObject("_source"))
        if (pointer.message <= 0) { mutable.update { it.copy(error = "这笔订单没有关联原聊天消息") }; return@launch }
        runCatching { call("ui.source", pointer.json()) }.onFailure(::failure)
    }
    fun exportOrders(rows: List<JSONObject>) = viewModelScope.launch {
        val fields = listOf("trade_no", "out_trade_no", "api_trade_no", "uid", "currency", "money", "status", "addtime")
        val lines = listOf(listOf("系统订单号", "商户订单号", "通道订单号", "PID", "币种", "金额", "状态", "创建时间")) + rows.map { row -> fields.map { row.optString(it) } }
        val csv = lines.joinToString("\r\n") { row -> row.joinToString(",") { csvCell(it) } }
        runCatching { call("export", JSONObject().put("name", "futoon-orders.csv").put("text", csv)) }.onFailure(::failure)
    }
    fun selectedOrders(): List<JSONObject> = state.value.candidates.filter { it.selected }.flatMap { it.orders }.map(::JSONObject).distinctBy { it.optString("trade_no") }
    fun abnormalSummary(): String = state.value.candidates.filter { it.phase == "failed" || it.orders.any { row -> JSONObject(row).optInt("status") != 1 } }.joinToString("\n") { row -> row.value + "：" + row.error.ifBlank { row.orders.joinToString("；") { value -> JSONObject(value).let { order -> orderStatusLabel(order.optInt("status")) + " " + order.optString("money") } } } }
    fun updateChat(value: ChatScope) {
        if (chat.key == value.key && chat.telegramId == value.telegramId) { chat = value; return }
        chat = value; lookupGeneration++; lookupColumn = "telegram_id"; lookupValue = ""
        mutable.update { it.copy(merchant = null, merchantError = null, manualMerchant = false, merchantLoading = false) }
        lookupAutomatic()
    }
    fun lookupAutomatic() {
        val bound = binding()
        if (chat.telegramId.isNotBlank()) lookup("telegram_id", chat.telegramId, false, chat.source)
        else if (bound.isNotBlank()) lookup("uid", bound, true, "本群记住的商户")
        else { lookupGeneration++; mutable.update { it.copy(merchant = null, merchantLoading = false, merchantError = null, manualMerchant = false, merchantSource = "未指定商户") } }
    }
    fun refreshMerchant() { if (lookupValue.isNotBlank()) lookup(lookupColumn, lookupValue, state.value.manualMerchant, state.value.merchantSource) else lookupAutomatic() }
    fun lookup(column: String, value: String, manual: Boolean = true, origin: String = "手动指定") = viewModelScope.launch {
        val generation = ++lookupGeneration
        lookupColumn = column; lookupValue = value.trim()
        mutable.update { it.copy(merchant = null, merchantLoading = true, merchantError = null, manualMerchant = manual, merchantSource = origin) }
        try {
            val rows = JSONObject(call("merchant.lookup", JSONObject().put("column", column).put("value", value.trim()))).optJSONArray("rows").objects()
            if (generation != lookupGeneration) return@launch
            check(rows.size <= 1) { "商户绑定有冲突，请到后台核对" }
            mutable.update { it.copy(merchant = rows.firstOrNull()?.toString(), merchantLoading = false, merchantError = if (rows.isEmpty()) "未找到对应商户，请核对 TGID/PID" else null) }
        } catch (e: Throwable) { if (e is CancellationException) throw e; if (generation == lookupGeneration) mutable.update { it.copy(merchantLoading = false, merchantError = e.message ?: "商户查询失败") } }
    }
    fun toggleGroupBinding() {
        if (state.value.localLoading || chat.dialog >= 0 || chat.telegramId.isNotBlank() || chat.key.isBlank()) return
        val groups = objectAt("groups")
        if (groups.has(chat.key)) groups.remove(chat.key) else state.value.merchant?.let { groups.put(chat.key, JSONObject(it).optString("uid")) }
        changed()
    }
    suspend fun merchantRecords(kind: String, uid: String, page: Int, day: String) = JSONObject(call(if (kind == "stats") "merchant.stats" else "merchant.flow", JSONObject().put("uid", uid).put("page", page).put("date", day)))

    companion object {
        fun csvCell(value: String): String = "\"" + (if (value.trimStart().startsWithAnyFormula()) "'" + value else value).replace("\"", "\"\"") + "\""
        private fun String.startsWithAnyFormula() = isNotEmpty() && first() in "=+-@\t\r"
    }
}
internal fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
internal fun queryKindLabel(kind: String) = when (kind) { "PID" -> "商户 PID"; "SYSTEM_ORDER" -> "系统订单号"; "MERCHANT_ORDER" -> "商户订单号"; "CHANNEL_ORDER" -> "通道订单号"; else -> "自动识别" }
internal val queryKinds = listOf("AUTO", "PID", "SYSTEM_ORDER", "MERCHANT_ORDER", "CHANNEL_ORDER").map { it to queryKindLabel(it) }
internal fun evidenceCaption(order: JSONObject, template: String = "查单") = "请协助${template}\n系统订单号：${order.optString("trade_no")}\n商户订单号：${order.optString("out_trade_no")}\n通道订单号：${order.optString("api_trade_no")}\n商户 PID：${order.optString("uid")}\n金额：${order.optString("currency", "CNY")} ${order.optString("money")}".take(1024)
