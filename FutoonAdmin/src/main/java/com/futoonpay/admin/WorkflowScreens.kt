package com.futoonpay.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.*
import com.futoonpay.admin.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate

internal class CurrentMerchantActions(val model: AppViewModel) {
    var pending by mutableStateOf<MerchantAction?>(null)
    var detail by mutableStateOf<MerchantSummary?>(null)
    var days by mutableIntStateOf(7)
    var balanceError by mutableStateOf<String?>(null)
    fun edit(merchant: MerchantSummary) { model.setCurrentMerchant(merchant); model.openMerchantEditor(merchant.uid) }
    fun choose(action: MerchantAction) { model.setCurrentMerchant(action.merchant); balanceError = null; pending = action }
    fun showDetail(merchant: MerchantSummary) { detail = merchant; days = 7; model.loadMerchantDetailFlow(merchant.uid, 7) }
}

@Composable internal fun CurrentMerchantDialogs(actions: CurrentMerchantActions, workflows: PanelWorkflows, chat: ChatScope) {
    val model = actions.model
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(chat.key, chat.telegramId) { actions.pending = null; actions.detail = null; model.closeMerchantEditor() }
    LaunchedEffect(state.message) { state.message?.let { workflows.notice(it); workflows.refreshMerchant(); model.consumeMessage() } }
    if (state.merchantEditorUid.isNotBlank()) MerchantProfileDialog(state, model::closeMerchantEditor, model::editMerchantField, model::saveMerchantEditor, { model.openMerchantEditor(state.merchantEditorUid) })
    actions.pending?.let { action ->
        MerchantActionDialog(action, { actions.pending = null }, state.refreshing || state.merchantBalanceUpdating, actions.balanceError) { amount, group, end ->
            if (action.action in listOf("add", "reduce")) model.rechargeMerchant(action.merchant.uid, action.action, amount) { result ->
                if (result.success) { actions.pending = null; workflows.refreshMerchant() } else actions.balanceError = result.message
            } else scope.launch {
                when (action.action) {
                    "toggleAccount" -> model.setMerchantStatus(action.merchant.uid, "status", action.merchant.status != 1).join()
                    "togglePay" -> model.setMerchantStatus(action.merchant.uid, "pay", !action.merchant.paymentEnabled).join()
                    "toggleSettle" -> model.setMerchantStatus(action.merchant.uid, "settle", !action.merchant.settlementEnabled).join()
                    "group" -> model.setMerchantGroup(action.merchant.uid, group, end).join()
                }
                actions.pending = null
                workflows.refreshMerchant()
            }
        }
    }
    actions.detail?.let { merchant -> MerchantDetailDialog(merchant, state.merchantDetailFlow, state.merchantDetailLoading, { actions.detail = null }, actions.days, state.merchantDetailError,
        { days -> actions.days = days; model.loadMerchantDetailFlow(merchant.uid, days) },
        { actions.detail = null; actions.edit(merchant) },
        { actions.detail = null; model.offerBatch(merchantKeyRequest(listOf(merchant))) }) }
    BatchDialog(state.batch, model::startBatch, model::closeBatch, model::retryBatch)
}

@Composable internal fun CurrentMerchantCard(workflows: PanelWorkflows, actions: CurrentMerchantActions, chat: ChatScope, navigator: PanelNavigator, compact: Boolean = false) {
    val state by workflows.state.collectAsState()
    val original by actions.model.state.collectAsState()
    val matched = state.merchant?.let { JSONObject(it).toMerchantSummary() }
    val merchant = original.merchants.firstOrNull { it.uid == matched?.uid } ?: matched
    var manual by remember(chat.key) { mutableStateOf(false) }
    var column by rememberSaveable { mutableStateOf("telegram_id") }
    var value by rememberSaveable(chat.key) { mutableStateOf(chat.telegramId) }
    var expanded by rememberSaveable(chat.key, merchant?.uid) { mutableStateOf(!compact) }
    PayCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("当前会话商户", Modifier.weight(1f), color = Muted, fontSize = 12.sp)
            TextButton(onClick = workflows::refreshMerchant, enabled = !state.merchantLoading) { Text("重新识别") }
        }
        when {
            state.merchantLoading -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text(" 正在查询商户…") }
            merchant == null -> Text(state.merchantError ?: "当前会话未指定商户，可按 TGID 或 PID 手动查询", color = if (state.merchantError == null) Muted else Danger, fontSize = 12.sp)
            else -> {
                Text("${merchant.username.ifBlank { "商户" }} · PID ${merchant.uid}", style = MaterialTheme.typography.titleMedium)
                Text("TGID ${merchant.telegramId.ifBlank { "未绑定" }} · ${state.merchantSource}", color = Muted, fontSize = 11.sp)
                if (chat.ticket.isNotBlank()) Text("工单 ${chat.ticket} · ${chat.role}", color = Muted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !original.refreshing && !original.merchantEditorSaving, onClick = { actions.edit(merchant) }) { Text("编辑商户") }
                    OutlinedButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起操作" else "商户操作") }
                }
                if (expanded) {
                    merchant.balances.ifEmpty { mapOf("CNY" to merchant.balance) }.forEach { (currency, amount) -> DataRow("$currency 可用", amount) }
                    DataRow("冻结余额", merchant.frozenBalance)
                    DataRow("用户组", merchant.groupName)
                    DataRow("账号 / 支付 / 结算", "${if (merchant.status == 1) "正常" else "封禁"} / ${merchantPaymentStatusLabel(merchant.paymentStatus)} / ${if (merchant.settlementEnabled) "开启" else "关闭"}")
                    listOf(
                        listOf("增加余额" to "add", "扣除余额" to "reduce"),
                        listOf((if (merchant.status == 1) "封禁账号" else "解封账号") to "toggleAccount", (if (merchant.paymentEnabled) "关闭支付" else "开启支付") to "togglePay"),
                        listOf((if (merchant.settlementEnabled) "关闭结算" else "开启结算") to "toggleSettle", "修改用户组" to "group"),
                    ).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { (label, action) -> OutlinedButton(enabled = !original.refreshing, modifier = Modifier.weight(1f), onClick = { actions.choose(MerchantAction(label, action, merchant)) }) { Text(label, fontSize = 12.sp) } } } }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { actions.model.offerBatch(merchantKeyRequest(listOf(merchant))) }) { Text("重置密钥") }
                        TextButton(onClick = { actions.showDetail(merchant) }) { Text("详情与流水曲线") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { navigator.orders(merchantId = merchant.uid) }) { Text("商户订单") }
                        TextButton(onClick = { navigator.settlements(merchant.uid) }) { Text("商户结算") }
                        TextButton(onClick = { navigator.domains(merchant.uid) }) { Text("域名审核") }
                    }
                    Row {
                        TextButton(onClick = { navigator.push(PanelRoute("merchant_records", module = "flow", merchantId = merchant.uid)) }) { Text("资金流水") }
                        TextButton(onClick = { navigator.push(PanelRoute("merchant_records", module = "stats", merchantId = merchant.uid)) }) { Text("商户统计") }
                    }
                }
            }
        }
        Row {
            TextButton(onClick = { manual = true }) { Text(if (merchant == null) "手动查询 TGID / PID" else "切换商户") }
            if (state.manualMerchant) TextButton(onClick = workflows::lookupAutomatic) { Text("恢复自动识别") }
        }
        if (merchant != null && chat.dialog < 0 && chat.telegramId.isBlank()) TextButton(onClick = workflows::toggleGroupBinding, enabled = !state.localLoading) { Text(if (workflows.binding().isBlank()) "在本群记住此商户" else "解除本群商户绑定") }
    }
    if (manual) PaySheet("手动查询商户", { manual = false }) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PayPills(listOf("telegram_id" to "TGID", "uid" to "商户 PID"), column) { column = it }
            OutlinedTextField(value, { value = it.take(19) }, label = { Text("完整 TGID 或 PID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(enabled = value.matches(Regex("[0-9]{1,19}")), onClick = { workflows.lookup(column, value); manual = false }, modifier = Modifier.fillMaxWidth()) { Text("查询商户") }
        }
    }
}

@Composable internal fun RecognitionScreen(workflows: PanelWorkflows, actions: CurrentMerchantActions, chat: ChatScope, navigator: PanelNavigator, bridge: NativePanelBridge) {
    val state by workflows.state.collectAsState()
    var page by rememberSaveable { mutableStateOf("recognize") }
    var summary by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.text, state.ocr) { page = "recognize" }
    fun openOrder(row: JSONObject) { workflows.rememberOrder(row); navigator.openOrder(row.optString("trade_no")) }
    Column(Modifier.fillMaxSize()) {
        PayTopBar("富通识别")
        PayPills(listOf("recognize" to "识别与批量", "history" to "最近查询", "favorites" to "收藏订单", "drafts" to "凭证草稿"), page) { page = it }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.error?.let { item { Text(it, color = Danger, fontSize = 12.sp) } }
            if (page == "recognize") {
                item { CurrentMerchantCard(workflows, actions, chat, navigator, compact = true) }
                item { OutlinedTextField(state.text, workflows::setText, label = { Text(if (state.ocr) "图片识别结果，请先校对" else "粘贴消息、订单号或商户 PID") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4) }
                item { PayPills(queryKinds, state.queryKind, workflows::setKind) }
                item { Row {
                    TextButton(onClick = { workflows.requestOcr() }, enabled = !state.running) { Icon(Icons.Default.ImageSearch, null, Modifier.size(18.dp)); Text("截图 OCR / 二维码") }
                    if (state.ocr) Text("先校对 O/0、I/1", color = Warning, fontSize = 11.sp)
                } }
                if (Regex("[＊*…●•]{2,}").containsMatchIn(state.text)) item { Text("发现遮挡编号，请补全；不会用编号片段匹配订单。", color = Warning, fontSize = 12.sp) }
                if (state.candidates.isNotEmpty()) {
                    item {
                        val completed = state.candidates.count { it.phase in listOf("ok", "failed") }
                        Text("已处理 $completed / ${state.candidates.size} · 已选 ${state.candidates.count { it.selected }}", color = Muted, fontSize = 12.sp)
                        if (state.running) LinearProgressIndicator(progress = { completed.toFloat() / state.candidates.size }, modifier = Modifier.fillMaxWidth())
                        PayPills(listOf("none" to "提取顺序", "merchant" to "按商户", "channel" to "按通道", "status" to "按状态"), state.group, workflows::setGroup)
                    }
                    val candidates = if (state.group == "none") state.candidates else state.candidates.sortedBy { row -> row.orders.firstOrNull()?.let(::JSONObject)?.optString(when (state.group) { "merchant" -> "uid"; "channel" -> "channel"; else -> "status" }).orEmpty() }
                    items(candidates, key = { it.id }) { candidate -> PayCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(candidate.selected, { workflows.editCandidate(candidate.id, selected = it) }, enabled = !state.running)
                            OutlinedTextField(candidate.value, { workflows.editCandidate(candidate.id, value = it) }, modifier = Modifier.weight(1f), singleLine = true, enabled = !state.running, label = { Text(queryKindLabel(candidate.kind)) })
                        }
                        PayPills(queryKinds, candidate.kind) { workflows.editCandidate(candidate.id, kind = it) }
                        Text(when (candidate.phase) { "ok" -> "查询完成 · 共 ${candidate.total} 笔"; "failed" -> "查询失败"; "loading" -> "查询中…"; else -> "等待查询" }, color = if (candidate.phase == "failed") Danger else Muted, fontSize = 11.sp)
                        if (candidate.error.isNotBlank()) Text(candidate.error, color = Warning, fontSize = 11.sp)
                        candidate.orders.forEach { raw -> val order = JSONObject(raw); OrderResultCard(order) { openOrder(order) } }
                        if (candidate.total > candidate.orders.size) TextButton(onClick = {
                            val column = when (candidate.matchedKind.ifBlank { candidate.kind }) { "PID" -> "uid"; "MERCHANT_ORDER" -> "out_trade_no"; "CHANNEL_ORDER" -> "api_trade_no"; else -> "trade_no" }
                            navigator.push(PanelRoute("orders", queryColumn = column, queryValue = candidate.value))
                        }) { Text("查看全部结果") }
                    } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(enabled = !state.running, onClick = { workflows.runQueries(onlyFailed = true) }) { Text("重试失败项") }
                        TextButton(enabled = !state.running, onClick = { workflows.runQueries(refresh = true) }) { Text("批量刷新") }
                        TextButton(enabled = !state.running, onClick = { summary = workflows.abnormalSummary() }) { Text("异常汇总") }
                    } }
                    item { Row {
                        val rows = workflows.selectedOrders()
                        TextButton(enabled = rows.isNotEmpty() && !state.running, onClick = { workflows.exportOrders(rows) }) { Text("导出清单") }
                        TextButton(enabled = rows.isNotEmpty() && !state.running, onClick = { actions.model.offerBatch(orderBatchOptions(rows.map { it.toOrderSummary() }).first { it.title == "补发通知" }) }) { Text("批量补发通知") }
                    } }
                }
            } else if (state.localLoading) item { CircularProgressIndicator() }
            else if (page == "history") {
                items(workflows.array("history"), key = { it.optString("text") }) { row -> PayCard { TextButton(onClick = { workflows.queryHistory(row); page = "recognize" }) { Text("${queryKindLabel(row.optString("kind"))} · ${row.optString("text")}") } } }
                if (workflows.array("history").isEmpty()) item { Text("暂无查询记录", color = Muted) }
            } else if (page == "favorites") {
                items(workflows.array("favorites"), key = { it.optString("trade_no") }) { row -> OrderResultCard(row) { openOrder(row) } }
                if (workflows.array("favorites").isEmpty()) item { Text("暂无收藏订单", color = Muted) }
            } else {
                items(workflows.drafts(), key = { it.getJSONObject("order").optString("trade_no") }) { draft -> PayCard {
                    Text("订单 ${draft.getJSONObject("order").optString("trade_no")}")
                    Text(draft.optString("caption"), color = Muted, maxLines = 3, fontSize = 12.sp)
                    TextButton(onClick = { workflows.restoreDraft(draft) }) { Text("恢复付款凭证草稿") }
                } }
                if (workflows.drafts().isEmpty()) item { Text("暂无凭证草稿", color = Muted) }
            }
        }
        if (page == "recognize") Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !state.running && !state.extracting && state.text.isNotBlank(), onClick = { workflows.extract() }, modifier = Modifier.weight(1f)) { Text(if (state.extracting) "提取中…" else "提取编号") }
            if (state.running) Button(onClick = workflows::stopQueries, modifier = Modifier.weight(1f)) { Text("停止未执行项") }
            else Button(enabled = !state.extracting && state.candidates.any { it.selected }, onClick = { workflows.runQueries() }, modifier = Modifier.weight(1f)) { Text("查询所选") }
        }
    }
    summary?.let { text -> EditableWorkflowText("异常订单汇总", text, { summary = null }, bridge::copy) }
}

@Composable internal fun OrderResultCard(order: JSONObject, onOpen: () -> Unit) {
    PayCard {
        Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(order.optString("trade_no"), color = GreenDark)
            Text("PID ${order.optString("uid")} · ${order.optString("currency", "CNY")} ${order.optString("money")} · ${orderStatusLabel(order.optInt("status"))}", fontSize = 12.sp)
            Text("查看订单与选择付款凭证 ›", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable internal fun OrderWorkflowTools(detail: OrderDetail) {
    val workflows = LocalPanelWorkflows.current ?: return
    val copy = LocalWorkflowCopy.current
    val state by workflows.state.collectAsState()
    val row = workflows.rawOrder(detail.tradeNo).put("uid", detail.merchantId).put("out_trade_no", detail.merchantTradeNo).put("api_trade_no", detail.apiTradeNo).put("money", detail.amount).put("currency", detail.currency).put("status", detail.status)
    workflows.rememberOrder(row)
    var dialog by remember(detail.tradeNo) { mutableStateOf("") }
    PayCard {
        val merchant = state.merchant?.let { JSONObject(it).optString("uid") }
        if (merchant != null && merchant != detail.merchantId) Text("此订单属于 PID ${detail.merchantId}，与当前会话商户 PID $merchant 不同；请核对操作对象。", color = Warning, fontSize = 12.sp)
        Row {
            TextButton(enabled = !state.localLoading, onClick = { workflows.favorite(row) }) { Text(if (workflows.isFavorite(detail.tradeNo)) "取消收藏" else "收藏订单") }
            TextButton(onClick = { workflows.jumpToSource(row) }, enabled = MessageSource.from(row.optJSONObject("_source")).message > 0) { Text("回到原消息") }
        }
        Row {
            TextButton(onClick = { dialog = "reply" }) { Text("生成回复草稿") }
            TextButton(enabled = !state.localLoading, onClick = { dialog = "note" }) { Text("内部备注与处理记录") }
        }
    }
    if (dialog.isNotBlank()) {
        val note = dialog == "note"
        val initial = if (note) workflows.case(detail.tradeNo).optString("note") else evidenceCaption(row) + "\n当前状态：${orderStatusLabel(detail.status)}"
        EditableWorkflowText(if (note) "内部备注" else "回复原聊天草稿", initial, { dialog = "" }) { value -> if (note) workflows.saveNote(detail.tradeNo, value) else copy(value) }
    }
}

/** Clipboard action is supplied as a composition local rather than retaining an Activity. */
internal val LocalWorkflowCopy = staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable internal fun EditableWorkflowText(title: String, initial: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    var value by rememberSaveable(title, initial) { mutableStateOf(initial) }
    PaySheet(title, onClose) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value, { value = it.take(8000) }, modifier = Modifier.fillMaxWidth().weight(1f), label = { Text("可编辑内容") })
            Button(onClick = { onSave(value); onClose() }, modifier = Modifier.fillMaxWidth()) { Text(if (title == "内部备注") "保存备注" else "复制草稿") }
        }
    }
}

@Composable internal fun MerchantRecordsScreen(route: PanelRoute, workflows: PanelWorkflows, back: () -> Unit) {
    var page by rememberSaveable(route.id) { mutableIntStateOf(1) }
    var day by rememberSaveable(route.id) { mutableStateOf(LocalDate.now().toString()) }
    var queryDay by rememberSaveable(route.id) { mutableStateOf(day) }
    var revision by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(route.id, page, queryDay, revision) {
        loading = true; error = null
        runCatching { workflows.merchantRecords(route.module, route.merchantId, page, queryDay) }.onSuccess { result = it }.onFailure { error = it.message }
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        PayTopBar(if (route.module == "stats") "商户统计 · PID ${route.merchantId}" else "资金流水 · PID ${route.merchantId}", back)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (route.module == "stats") item { Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(day, { day = it }, modifier = Modifier.weight(1f), label = { Text("日期 YYYY-MM-DD") }, singleLine = true)
                TextButton(onClick = { runCatching { LocalDate.parse(day) }.onSuccess { queryDay = day; revision++ }.onFailure { error = "日期格式不正确" } }) { Text("查询") }
            } }
            if (loading) item { CircularProgressIndicator() }
            error?.let { item { Text(it, color = Danger); TextButton(onClick = { revision++ }) { Text("重试") } } }
            val rows = result?.optJSONArray("rows").objects()
            if (!loading && error == null && rows.isEmpty()) item { Text("暂无记录", color = Muted) }
            items(rows) { row -> PayCard { row.keys().asSequence().filterNot { it.contains("token", true) || it == "key" || it.contains("password", true) }.forEach { field -> DataRow(when (field) { "uid" -> "商户 PID"; "money" -> "金额"; "addtime" -> "时间"; "type" -> "类型"; "action" -> "操作"; "remark" -> "备注"; "total" -> "流水"; "balance" -> "余额"; else -> field }, row.optString(field)) } } }
        }
        if (route.module != "stats") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = page > 1 && !loading, onClick = { page-- }) { Text("上一页") }
            Text("第 $page 页", Modifier.padding(12.dp), fontSize = 12.sp)
            TextButton(enabled = !loading && (result?.optJSONArray("rows")?.length() ?: 0) >= 25, onClick = { page++ }) { Text("下一页") }
        }
    }
}
