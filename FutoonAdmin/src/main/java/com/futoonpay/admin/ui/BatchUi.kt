package com.futoonpay.admin.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.theme.*

internal class RecordSelection {
    var active by mutableStateOf(false)
    var selected by mutableStateOf(emptySet<String>())
    fun toggle(id: String) { if (id.isNotBlank()) selected = if (id in selected) selected - id else selected + id }
    fun clear() { active = false; selected = emptySet() }
}
@Composable
internal fun rememberRecordSelection(scope: Any, visibleIds: List<String>): RecordSelection {
    val selection = remember(scope) { RecordSelection() }
    LaunchedEffect(visibleIds) { selection.selected = selection.selected.intersect(visibleIds.filter(String::isNotBlank).toSet()) }
    return selection
}

/** Counts, read-only overview and selection share one row until selection is explicitly entered. */
@Composable
internal fun FinanceListToolbar(selection: RecordSelection, visibleIds: List<String>, options: List<BatchRequest>, busy: Boolean,
    summary: String, overviewLabel: String = "", onOverview: (() -> Unit)? = null, onRequest: (BatchRequest) -> Unit) {
    if (selection.active) MultiSelectBar(selection, visibleIds, options, busy, onRequest)
    else Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(summary, modifier = Modifier.weight(1f), color = Muted, fontSize = 12.sp)
        onOverview?.let { ChatTextButton(onClick = it) { Text(overviewLabel, fontSize = 12.sp) } }
        ChatTextButton(onClick = { selection.active = true }, enabled = !busy && visibleIds.any(String::isNotBlank)) { Text("多选", fontSize = 12.sp) }
    }
}

@Composable
internal fun MultiSelectBar(selection: RecordSelection, visibleIds: List<String>, options: List<BatchRequest>, busy: Boolean = false, onRequest: (BatchRequest) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val ids = visibleIds.filter(String::isNotBlank).toSet()
    val selected = selection.selected.intersect(ids)
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            com.futoonpay.admin.ui.ChatTextButton(onClick = { if (selection.active) selection.clear() else selection.active = true }, enabled = !busy && (selection.active || ids.isNotEmpty())) { Text(if (selection.active) "完成多选" else "多选") }
            if (selection.active) {
                Text("已选 ${selected.size} 条", color = GreenDark, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Box {
                    com.futoonpay.admin.ui.ChatButton(onClick = { expanded = true }, enabled = !busy && selected.isNotEmpty() && options.any { it.commands.any { command -> command.skipReason.isBlank() } }) { Text("批量操作") }
                    DropdownMenu(expanded, { expanded = false }) { options.forEach { request ->
                        val count = request.commands.count { it.skipReason.isBlank() }
                        DropdownMenuItem(text = { Text("${request.title} · $count 条可执行") }, enabled = count > 0, onClick = { expanded = false; onRequest(request.copy(commands = request.commands.toList())) })
                    } }
                }
            } else Spacer(Modifier.weight(1f))
        }
        if (selection.active) Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(ids.isNotEmpty() && selected == ids, { selection.selected = if (it) ids else emptySet() }, enabled = !busy && ids.isNotEmpty())
            Text("全选当前页", fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            com.futoonpay.admin.ui.ChatTextButton(onClick = { selection.selected = emptySet() }, enabled = !busy && selected.isNotEmpty()) { Text("清空") }
        }
    }
}

@Composable
internal fun SelectableRecord(selection: RecordSelection?, id: String, label: String, content: @Composable () -> Unit) {
    Column {
        if (selection?.active == true) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(id in selection.selected, { selection.toggle(id) }, enabled = id.isNotBlank())
            Text("选择 $label", fontSize = 12.sp, color = GreenDark)
        }
        Box {
            Box(if (selection?.active == true) Modifier.clearAndSetSemantics {} else Modifier) { content() }
            if (selection?.active == true) Box(Modifier.matchParentSize().clickable(enabled = id.isNotBlank()) { selection.toggle(id) })
        }
    }
}

@Composable
internal fun BatchDialog(progress: BatchProgress?, onConfirm: () -> Unit, onClose: () -> Unit, onRetry: () -> Unit) {
    if (progress == null) return
    val request = progress.request
    AlertDialog(
        onDismissRequest = { if (!progress.running) onClose() },
        properties = DialogProperties(dismissOnBackPress = !progress.running, dismissOnClickOutside = false, securePolicy = if (request.commands.any { it.kind == BatchKind.MERCHANT_KEY }) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit),
        title = { Text(if (!progress.started) "确认${request.title}" else "${request.title}结果") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!progress.started) {
                Text("已选 ${request.commands.size} 条 · 可执行 ${request.commands.count { it.skipReason.isBlank() }} 条")
                Text(request.warning, color = Warning, fontSize = 12.sp)
                LazyColumn(Modifier.heightIn(max = 300.dp)) { items(request.commands, key = { it.id }) { command ->
                    Column(Modifier.padding(vertical = 7.dp)) { Text(command.label, fontSize = 13.sp); if (command.skipReason.isNotBlank()) Text("将跳过：${command.skipReason}", color = Muted, fontSize = 12.sp) }
                } }
            } else {
                val success = progress.results.count { it.status == BatchOutcomeStatus.SUCCESS }
                Text("已处理 ${progress.results.size}/${request.commands.size} · 成功 $success", fontSize = 13.sp)
                if (progress.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (progress.results.any { it.newKey.isNotBlank() }) Text("关闭或重试前请复制新密钥，本页面不会保存密钥。", color = Warning, fontSize = 12.sp)
                if (progress.results.any { it.status == BatchOutcomeStatus.UNCERTAIN }) Text("有记录结果未确认，请先核对后台；该记录不能直接重试。", color = Warning, fontSize = 12.sp)
                LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(progress.results, key = { it.command.id }) { outcome ->
                    Column {
                        Text(outcome.command.label, fontSize = 13.sp)
                        Text("${outcome.status.label} · ${outcome.message}", color = if (outcome.status == BatchOutcomeStatus.SUCCESS) GreenDark else Warning, fontSize = 12.sp)
                        if (outcome.newKey.isNotBlank()) KeyResult(outcome.command.id, outcome.newKey)
                        HorizontalDivider(Modifier.padding(top = 8.dp), color = Line)
                    }
                } }
            }
        } },
        confirmButton = {
            if (!progress.started) com.futoonpay.admin.ui.ChatButton(onClick = onConfirm, enabled = request.commands.any { it.skipReason.isBlank() }) { Text("确认执行") }
            else if (!progress.running) com.futoonpay.admin.ui.ChatTextButton(onClick = onClose) { Text("关闭") }
            else Text("正在逐条执行…", color = Muted)
        },
        dismissButton = {
            if (!progress.started) com.futoonpay.admin.ui.ChatTextButton(onClick = onClose) { Text("取消") }
            else if (!progress.running && progress.retryable.isNotEmpty()) com.futoonpay.admin.ui.ChatTextButton(onClick = onRetry) { Text("重试失败/未执行项") }
        },
    )
}

@Composable
private fun KeyResult(uid: String, key: String) {
    val context = LocalContext.current
    val navigation = com.futoonpay.admin.LocalPanelNavigation.current
    var visible by remember(uid, key) { mutableStateOf(false) }
    var copied by remember(uid, key) { mutableStateOf(false) }
    Text(if (visible) key else "••••••••••••••••••••••••••••••••", fontSize = 12.sp)
    Row {
        com.futoonpay.admin.ui.ChatTextButton(onClick = { visible = !visible }) { Text(if (visible) "隐藏密钥" else "查看密钥", fontSize = 12.sp) }
        com.futoonpay.admin.ui.ChatTextButton(onClick = {
            val clip = ClipData.newPlainText("商户 $uid 新密钥", key)
            val sensitiveExtra = if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE"
            if (Build.VERSION.SDK_INT >= 24) {
                clip.description.extras = PersistableBundle().apply { putBoolean(sensitiveExtra, true) }
            }
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            copied = true
        }) { Text(if (copied) "已复制" else "复制新密钥", fontSize = 12.sp) }
    }
}
