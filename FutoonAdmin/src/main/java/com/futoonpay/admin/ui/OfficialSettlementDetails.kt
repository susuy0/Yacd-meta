package com.futoonpay.admin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.futoonpay.admin.data.SettlementReason
import com.futoonpay.admin.data.SettlementSummary
import com.futoonpay.admin.ui.theme.Danger
import com.futoonpay.admin.ui.theme.Muted
import kotlinx.coroutines.CancellationException

@Composable
internal fun OfficialSettlementDetails(
    row: SettlementSummary,
    model: AppViewModel,
    onReview: (String, String, Map<String, String>, Boolean) -> Unit,
) {
    var reason by remember(row.id) { mutableStateOf<SettlementReason?>(null) }
    var error by remember(row.id) { mutableStateOf<String?>(null) }
    var generation by remember(row.id) { mutableIntStateOf(0) }
    val uri = LocalUriHandler.current
    LaunchedEffect(row.id, generation) {
        reason = null; error = null
        try { reason = model.settlementReason(row.id) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "结算详情读取失败" }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (row.status == 1) "完成详情" else "原因与处理建议", fontWeight = FontWeight.SemiBold)
        if (reason == null && error == null) Text("正在读取官网详情…", color = Muted)
        error?.let {
            Text(it, color = Danger)
            ChatOutlinedButton(onClick = { generation++ }) { Text("重新读取详情") }
        }
        reason?.let { detail ->
            SelectionContainer { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                detail.sections.forEach { (label, value) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label, color = Muted, fontSize = 12.sp)
                        Text(value, fontSize = 14.sp)
                    }
                }
            } }
            detail.reviews.forEach { review ->
                HorizontalDivider()
                Text("${review.title} · ${review.state}", fontWeight = FontWeight.Medium)
                SelectionContainer { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(review.reason)
                    review.records.forEach { Text(it, fontSize = 13.sp) }
                    if (review.note.isNotBlank()) Text("核实依据：${review.note}")
                } }
                if (review.canApprove) ChatOutlinedButton(onClick = {
                    onReview("通过${review.title}", "approve_settlement_review", detail.reviewFields(review), true)
                }) { Text("通过${review.title}") }
            }
            if (detail.canRecheck) ChatOutlinedButton(onClick = {
                onReview("重新核验（不批准）", "approve_settlement_review", detail.recheckFields(), true)
            }) { Text("重新核验（不批准）") }
            if (detail.canApproveFull) ChatOutlinedButton(onClick = {
                onReview("审核通过并入队", "approve_full_settlement", detail.approvalFields(), false)
            }) { Text("审核通过并入队") }
            if (detail.explorerUrl.isNotBlank()) ChatOutlinedButton(onClick = { uri.openUri(detail.explorerUrl) }) { Text("查看链上交易") }
            ChatTextButton(onClick = { generation++ }) { Text("刷新详情") }
        }
    }
}
