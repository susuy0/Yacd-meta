package com.futoonpay.admin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.futoonpay.admin.PanelDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import com.futoonpay.admin.NativeFieldEditor
import com.futoonpay.admin.ui.theme.Danger
import com.futoonpay.admin.ui.theme.Muted

@Composable
internal fun MerchantProfileDialog(state: AppUiState, onClose: () -> Unit, onField: (String, String) -> Unit, onSave: () -> Unit, onRetry: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !state.merchantEditorSaving)) {
        MerchantProfileScreen(state, onClose, onField, onSave, onRetry)
    }
}

@Composable
internal fun MerchantProfileScreen(state: AppUiState, onClose: () -> Unit, onField: (String, String) -> Unit, onSave: () -> Unit, onRetry: () -> Unit) {
    val form = state.merchantEditor
    val busy = state.merchantEditorLoading || state.merchantEditorSaving
    var confirm by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val original = state.merchantEditorOriginal?.fields.orEmpty().associateBy { it.name }
    val changes = form?.fields.orEmpty().filter { field -> !field.readOnly && field.type != "hidden" && (!field.sensitive || field.value.isNotBlank()) && original[field.name]?.value != field.value }
    fun close() { if (!state.merchantEditorSaving) { if (changes.isNotEmpty() && original.isNotEmpty()) discard = true else onClose() } }
    BackHandler { close() }
    val valid = form != null && form.fields.none { it.required && !it.readOnly && !it.sensitive && it.value.isBlank() }
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), containerColor = MaterialTheme.colorScheme.background,
        topBar = { PayTopBar("编辑商户 · PID ${state.merchantEditorUid}", ::close) },
        bottomBar = { Surface(shadowElevation = 6.dp) { Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.futoonpay.admin.ui.ChatOutlinedButton(onClick = ::close, shape = RoundedCornerShape(8.dp), enabled = !state.merchantEditorSaving, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("取消") }
            com.futoonpay.admin.ui.ChatButton(onClick = { if (state.merchantEditorUncertain) onRetry() else confirm = true }, shape = RoundedCornerShape(8.dp), enabled = (valid || state.merchantEditorUncertain) && !busy, modifier = Modifier.weight(2f).heightIn(min = 48.dp)) { Text(if (state.merchantEditorSaving) "保存中…" else if (state.merchantEditorUncertain) "重新读取资料并对比" else "保存资料") }
        } } }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.merchantEditorLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.merchantEditorError?.let { error -> item { PayCard { Text(error, color = Danger); if (form == null) com.futoonpay.admin.ui.ChatTextButton(onClick = onRetry) { Text("重新加载资料") } } } }
            val groups = form?.fields.orEmpty().filter { it.type != "hidden" && it.name !in setOf("pwd", "key", "apikey") }.groupBy { field -> when (field.name) {
                "gid", "status", "pay", "settle", "mode", "open_code", "upid" -> "业务设置"
                "account", "username", "settle_id" -> "结算收款信息"
                "money", "freeze_money", "remain_money", "deposit" -> "资金信息"
                else -> "商户资料"
            } }
            groups.forEach { (title, fields) ->
                item { SectionLabel(title) }
                item { PayCard { fields.forEach { field -> NativeFieldEditor(field.copy(readOnly = field.readOnly || busy || state.merchantEditorUncertain), field.value) { onField(field.name, it) } } } }
            }
            item { Text("余额仅供查看；增加或扣除余额请使用商户资金操作。保存失败会保留本次输入。", color = Muted) }
        }
    }
    if (confirm) PaySheet("核对改动 · PID ${state.merchantEditorUid}", { confirm = false }) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("将立即更新此商户的资料与业务权限，请核对操作对象和变更。") }
                items(changes.size) { index ->
                    val field = changes[index]
                    val old = original[field.name]
                    PayCard {
                        Text(field.label)
                        DataRow("后台当前值", if (field.sensitive) "不显示" else old?.let { value -> value.options.firstOrNull { it.value == value.value }?.label ?: value.value }.orEmpty())
                        DataRow("本次填写", if (field.sensitive) "已设置新值" else field.options.firstOrNull { it.value == field.value }?.label ?: field.value)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.futoonpay.admin.ui.ChatOutlinedButton(onClick = { confirm = false }, modifier = Modifier.weight(1f)) { Text("返回修改") }
                com.futoonpay.admin.ui.ChatButton(onClick = { confirm = false; onSave() }, modifier = Modifier.weight(1f)) { Text("确认保存") }
            }
        }
    }
    if (discard) PaySheet("放弃修改？", { discard = false }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("未保存的商户资料将丢失。")
            Row { com.futoonpay.admin.ui.ChatTextButton(onClick = { discard = false }) { Text("继续编辑") }; com.futoonpay.admin.ui.ChatTextButton(onClick = { discard = false; onClose() }) { Text("放弃修改") } }
        }
    }

}
