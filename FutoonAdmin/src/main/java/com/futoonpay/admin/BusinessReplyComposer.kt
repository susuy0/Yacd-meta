package com.futoonpay.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.PaySheet
import com.futoonpay.admin.ui.theme.*
import kotlinx.coroutines.launch

internal fun businessReplyValues(order: OrderDetail?, merchant: MerchantSummary?, chat: ChatScope): Map<String, String> = mapOf(
    "系统订单号" to order?.tradeNo.orEmpty(), "商户订单号" to order?.merchantTradeNo.orEmpty(), "上游订单号" to order?.apiTradeNo.orEmpty(),
    "商户ID" to (order?.merchantId ?: merchant?.uid).orEmpty(), "金额" to order?.amount.orEmpty(), "币种" to order?.currency.orEmpty(),
    "订单状态" to (order?.let { orderStatusLabel(it.status) }).orEmpty(), "创建时间" to order?.createdAt.orEmpty(), "完成时间" to order?.completedAt.orEmpty(),
    "支付方式" to order?.paymentType.orEmpty(), "通道ID" to order?.channelId.orEmpty(), "商户名称" to merchant?.username.orEmpty(),
    "TelegramID" to merchant?.telegramId.orEmpty().ifBlank { chat.telegramId }, "会话名称" to chat.label,
)

@Composable internal fun BusinessReplyComposer(workflows: PanelWorkflows, order: OrderDetail?, merchant: MerchantSummary?, close: () -> Unit) {
    val target = remember(order?.tradeNo, merchant?.uid) { workflows.replyTarget() }
    val values = remember(order, merchant, target) { businessReplyValues(order, merchant, target) }
    var templates by remember { mutableStateOf<List<BusinessReplyTemplate>>(emptyList()) }
    var selected by rememberSaveable(order?.tradeNo, merchant?.uid) { mutableStateOf(if (order == null) "merchant" else "order-status") }
    var body by rememberSaveable(order?.tradeNo, merchant?.uid) { mutableStateOf("") }
    var original by rememberSaveable(order?.tradeNo, merchant?.uid) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var inserting by remember { mutableStateOf(false) }
    var replacing by remember { mutableStateOf<BusinessReplyTemplate?>(null) }
    var discard by remember { mutableStateOf(false) }
    val copy = LocalWorkflowCopy.current
    val scope = rememberCoroutineScope()
    fun choose(template: BusinessReplyTemplate) { selected = template.id; original = renderBusinessReply(template.body, values); body = original; error = null }
    fun back() { if (body != original) discard = true else close() }
    LaunchedEffect(workflows) {
        try { templates = workflows.replySettings().extensions.templates; if (body.isEmpty()) templates.find { it.id == selected }?.let(::choose) ?: templates.firstOrNull()?.let(::choose) }
        catch (e: Exception) { error = e.message ?: "无法读取回复模板，请重试" }
        finally { loading = false }
    }
    PaySheet("生成回复草稿", { if (!inserting) back() }) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("目标：${target.label}${if (target.topic > 0) " · 话题 ${target.topic}" else ""}", color = Muted, fontSize = 13.sp)
            if (order != null) Text("订单 ${order.tradeNo} · PID ${order.merchantId}", fontSize = 13.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                templates.forEach { template -> FilterChip(selected == template.id, { if (body != original) replacing = template else choose(template) }, label = { Text(template.title) }, enabled = !inserting) }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!loading && templates.isEmpty()) Text("还没有业务回复模板，请在 FutoonPay 设置中新增。", color = Muted)
            OutlinedTextField(body, { body = it }, label = { Text("回复草稿，可修改") }, modifier = Modifier.fillMaxWidth().weight(1f), isError = body.length > 4096, supportingText = { Text("${body.length} / 4096") }, enabled = !inserting , colors = com.futoonpay.admin.ui.ChatFieldColors())
            error?.let { Text(it, color = Danger, fontSize = 13.sp) }
            Text("核对变量与内容后填入当前会话，由你手动发送。", color = Muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = body.isNotBlank() && !inserting, onClick = { copy(body) }) { Text("复制") }
                Button(enabled = body.isNotBlank() && body.length <= 4096 && !loading && !inserting, modifier = Modifier.weight(1f), onClick = {
                    inserting = true; error = null
                    scope.launch { try { workflows.insertReply(body, target); close() } catch (e: Exception) { error = e.message ?: "填入失败，草稿已保留" } finally { inserting = false } }
                }) { Text(if (inserting) "正在填入…" else "填入聊天输入框") }
            }
        }
    }
    replacing?.let { template -> PaySheet("替换当前草稿？", { replacing = null }) { Row(Modifier.padding(16.dp)) { TextButton(onClick = { replacing = null }) { Text("继续编辑") }; TextButton(onClick = { choose(template); replacing = null }) { Text("使用新模板") } } } }
    if (discard) PaySheet("放弃草稿修改？", { discard = false }) { Row(Modifier.padding(16.dp)) { TextButton(onClick = { discard = false }) { Text("继续编辑") }; TextButton(onClick = close) { Text("放弃修改") } } }
}
