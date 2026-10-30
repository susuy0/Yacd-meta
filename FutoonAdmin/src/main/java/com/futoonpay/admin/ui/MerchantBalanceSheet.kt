package com.futoonpay.admin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.theme.*
import kotlinx.coroutines.launch

/** Same editor from merchant lists and Telegram recognition; no mutation on open/change/cancel. */
@Composable
internal fun MerchantBalanceSheet(
    uid: String,
    initialAction: String,
    load: suspend () -> BalanceSession,
    submit: suspend (BalanceSession, BalanceCommand, Boolean) -> ApiResult,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    var session by remember(uid) { mutableStateOf<BalanceSession?>(null) }
    var loading by remember(uid) { mutableStateOf(true) }
    var busy by remember(uid) { mutableStateOf(false) }
    var error by remember(uid) { mutableStateOf<String?>(null) }
    var action by rememberSaveable(uid, initialAction) { mutableStateOf(ModernBalanceOperation.fromAction(initialAction)?.action ?: "add") }
    var amount by rememberSaveable(uid, initialAction) { mutableStateOf("") }
    var note by rememberSaveable(uid, initialAction) { mutableStateOf("") }
    var applicationId by rememberSaveable(uid, initialAction) { mutableStateOf("") }
    var expanded by remember(uid) { mutableStateOf(false) }
    var confirmation by remember(uid) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        loading = true
        try { session = load(); error = null } catch (e: Exception) { session = null; error = e.message ?: "余额读取失败" }
        finally { loading = false }
    }
    LaunchedEffect(uid) { refresh() }
    val pending = session?.pending
    val operation = pending?.operation ?: ModernBalanceOperation.fromAction(action) ?: ModernBalanceOperation.ADD
    val command = pending?.command() ?: BalanceCommand(operation, amount, note, applicationId)
    val info = session?.info
    val application = info?.applications?.firstOrNull { it.id == command.applicationId }
    val releaseAmount = if (pending != null) command.amount else application?.releaseAmount.orEmpty()
    val editable = !busy && !loading && session != null && pending == null
    val validation = info?.let { balanceCommandError(it, command) }

    PaySheet("余额管理 · $uid", { if (!busy) onDismiss() }) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("正在读取最新余额…") }
            if (info != null) {
                item {
                    Text("可用余额（CNY）", color = Muted, fontSize = 13.sp)
                    Text(info.available, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    DataRow("账户总余额", "${info.total} 元")
                    DataRow("冻结余额", "${info.frozen} 元")
                    ChatTextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起余额明细" else "余额明细与提现占用") }
                    if (expanded) {
                        DataRow("待确认提现申请", "${info.reserved} 元")
                        DataRow("申请冻结缺口", "${info.shortfall} 元")
                        DataRow("可扣除 / 冻结上限", "${info.freezable} 元")
                        DataRow("可解冻金额", "${info.releasable} 元")
                        Text("总余额包含冻结款；已生成结算订单的款项已经扣除，即使尚未付款也不包含在此余额中。", color = Muted, fontSize = 12.sp)
                    }
                    if (!info.consistent) Text("提现申请存在 ${info.shortfall} 元冻结缺口，请按安全上限核对。", color = Warning, fontSize = 13.sp)
                }
                item {
                    FinanceOptionPicker("操作", operation.action, ModernBalanceOperation.entries.map { it.action to it.label }, editable) { action = it; amount = ""; applicationId = ""; confirmation = false }
                }
                if (operation == ModernBalanceOperation.CANCEL_WITHDRAWAL) {
                    item {
                        FinanceOptionPicker("提现申请", command.applicationId, listOf("" to "选择具体申请") + info.applications.map { it.id to "#${it.id} · ${it.amount} ${it.unit}${if (it.canCancel) "" else " · 不可解除"}" }, editable) { applicationId = it; confirmation = false }
                        if (info.applications.isEmpty()) Text("没有待确认提现申请；已进入结算的订单请到结算管理查看。", color = Muted)
                        application?.let {
                            DataRow("本次释放冻结", "$releaseAmount 元")
                            DataRow("申请状态", it.status)
                            DataRow("网络", it.chain)
                            DataRow("申请时间（UTC+8）", it.createdAt)
                            if (!it.canCancel) Text(it.blockedReason.ifBlank { "此申请不可解除" }, color = Danger)
                        }
                        if (info.applicationsMore) Text("仅显示最早 100 笔申请，处理后刷新可查看后续申请。", color = Muted, fontSize = 12.sp)
                        Text("解除会取消所选申请，只释放实际可释放的冻结款；总余额不变。释放为 0 元时仅取消申请。", color = Muted, fontSize = 12.sp)
                    }
                } else item {
                    OutlinedTextField(command.amount, { amount = it; confirmation = false }, label = { Text("金额（元）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = editable, modifier = Modifier.fillMaxWidth(), colors = ChatFieldColors())
                    val hint = when (operation) {
                        ModernBalanceOperation.DEDUCT, ModernBalanceOperation.FREEZE -> "本次安全上限 ${info.freezable} 元"
                        ModernBalanceOperation.UNFREEZE -> "本次最多解冻 ${info.releasable} 元；提现占用需先处理对应申请"
                        else -> "充值会增加账户总余额"
                    }
                    Text("$hint · 最多 ${operation.scale} 位小数", color = Muted, fontSize = 12.sp)
                    if (operation in listOf(ModernBalanceOperation.FREEZE, ModernBalanceOperation.UNFREEZE)) Text("只调整可用与冻结余额，不改变账户总余额。", color = Muted, fontSize = 12.sp)
                }
                item {
                    OutlinedTextField(command.note, { note = it; confirmation = false }, label = { Text("操作原因（3–200字）") }, enabled = editable, minLines = 2, modifier = Modifier.fillMaxWidth(), colors = ChatFieldColors())
                }
                if (pending != null) item { Text("上次${pending.operation.label}结果尚未确认。查询会使用原操作编号和原金额，不会新建另一笔操作。", color = Warning) }
                if (pending == null && (amount.isNotBlank() || note.isNotBlank() || applicationId.isNotBlank()) && validation != null) item { Text(validation, color = Danger, fontSize = 13.sp) }
            }
            error?.let { item { Text(it, color = Danger) } }
            if (session == null && !loading) item { ChatOutlinedButton(onClick = { scope.launch { refresh() } }) { Text("重新读取余额") } }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChatOutlinedButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f)) { Text("取消") }
            ChatButton(onClick = { confirmation = true }, enabled = !busy && !loading && session != null && (pending != null || validation == null), modifier = Modifier.weight(2f)) {
                Text(if (busy) "提交中…" else if (pending != null) "查询结果 / 安全重试" else "核对${operation.label}")
            }
        }
    }
    if (confirmation && session != null) com.futoonpay.admin.PanelAlertDialog(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        onDismissRequest = { if (!busy) confirmation = false },
        title = { Text(if (pending != null) "核对原操作结果" else "确认${operation.label}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("商户 $uid · ${operation.label}")
            Text(if (operation == ModernBalanceOperation.CANCEL_WITHDRAWAL) "提现申请 #${command.applicationId} · 释放 $releaseAmount 元，解除后商户不能再确认此申请。" else "金额 ${command.amount} CNY")
            Text("原因：${command.note}")
        } },
        confirmButton = { ChatButton(onClick = {
            confirmation = false; busy = true
            scope.launch {
                try {
                    val result = submit(requireNotNull(session), command, pending != null)
                    if (result.success) { onChanged(); onDismiss() }
                    else {
                        val message = result.message
                        refresh() // read-only: obtains a fresh token, preserves the original pending request identity.
                        error = message
                    }
                } catch (e: Exception) { error = e.message ?: "操作未完成，请重新读取并核对" }
                finally { busy = false }
            }
        }, enabled = !busy) { Text(if (pending != null) "查询原操作" else "确认${operation.label}") } },
        dismissButton = { ChatTextButton(onClick = { confirmation = false }, enabled = !busy) { Text("返回修改") } },
    )
}
