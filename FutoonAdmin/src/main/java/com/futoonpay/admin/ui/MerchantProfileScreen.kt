package com.futoonpay.admin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
    val valid = form != null && form.fields.none { it.required && !it.readOnly && !it.sensitive && it.value.isBlank() }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = { PayTopBar("编辑商户 · UID ${state.merchantEditorUid}", onClose) },
        bottomBar = { Surface(shadowElevation = 6.dp) { Row(Modifier.navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onClose, shape = RoundedCornerShape(10.dp), enabled = !state.merchantEditorSaving, modifier = Modifier.weight(1f).height(48.dp)) { Text("取消") }
            Button(onClick = onSave, shape = RoundedCornerShape(10.dp), enabled = valid && !busy, modifier = Modifier.weight(2f).height(48.dp)) { Text(if (state.merchantEditorSaving) "保存中…" else "保存资料") }
        } } }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.merchantEditorLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.merchantEditorError?.let { error -> item { PayCard { Text(error, color = Danger); if (form == null) TextButton(onClick = onRetry) { Text("重新加载资料") } } } }
            val groups = form?.fields.orEmpty().filter { it.type != "hidden" && it.name !in setOf("pwd", "key", "apikey") }.groupBy { field -> when (field.name) {
                "gid", "status", "pay", "settle", "mode", "open_code", "upid" -> "业务设置"
                "account", "username", "settle_id" -> "结算收款信息"
                "money", "freeze_money", "remain_money", "deposit" -> "资金信息"
                else -> "商户资料"
            } }
            groups.forEach { (title, fields) ->
                item { SectionLabel(title) }
                item { PayCard { fields.forEach { field -> NativeFieldEditor(field.copy(readOnly = field.readOnly || busy), field.value) { onField(field.name, it) } } } }
            }
            item { Text("余额仅供查看；增加或扣除余额请使用商户资金操作。保存失败会保留本次输入。", color = Muted) }
        }
    }
}
