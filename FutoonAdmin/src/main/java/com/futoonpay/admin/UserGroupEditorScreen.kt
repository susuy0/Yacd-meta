package com.futoonpay.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.*
import com.futoonpay.admin.ui.theme.*

@Composable
internal fun UserGroupEditorScreen(form: NativeForm, onSubmit: (NativeForm, Map<String, String>) -> Unit, busy: Boolean, onCancel: () -> Unit, uncertain: Boolean = false, revision: Int = 0, draft: Map<String, String> = emptyMap(), onDraft: (Int, Map<String, String>) -> Unit = { _, _ -> }) {
    val values = remember(form, revision) { mutableStateMapOf<String, String>().apply { form.fields.forEach { put(it.name, it.value) }; putAll(draft) } }
    var confirm by remember(form, revision) { mutableStateOf(false) }
    val blocked = busy || uncertain
    val valid = values["name"].orEmpty().isNotBlank()
    val routeGroups = form.fields.filter { it.name.startsWith("info[") }.groupBy { it.name.substringBefore("][") + "]" }
    var section by androidx.compose.runtime.saveable.rememberSaveable(form, revision) { mutableStateOf("base") }
    var route by androidx.compose.runtime.saveable.rememberSaveable(form, revision) { mutableStateOf(routeGroups.keys.firstOrNull().orEmpty()) }
    fun change(field: NativeField, value: String) { updateGroupRoute(form, values, field.name, value); onDraft(revision, values.toMap()) }
    Column(Modifier.fillMaxSize()) {
        PayTabs(listOf("base" to "基础", "payment" to "支付配置", "settlement" to "结算"), section) { section = it }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (uncertain) item { Text(if (values["action"] == "add") "新增结果待核对，请返回列表刷新确认，不要重复新增。" else "保存结果待核对，请点右上角重新读取后核实。", color = Warning) }
            when (section) {
                "base" -> {
                    item { form.fields.firstOrNull { it.name == "name" }?.let { field -> NativeFieldEditor(field.copy(label = "显示名称", readOnly = field.readOnly || blocked), values[field.name].orEmpty()) { change(field, it) } } }
                    form.fields.filter { it.type != "hidden" && it.name != "name" && !it.name.startsWith("info[") && !it.name.startsWith("settle_") }.forEach { field ->
                        item { NativeFieldEditor(field.copy(readOnly = field.readOnly || blocked), values[field.name].orEmpty()) { change(field, it) } }
                    }
                }
                "payment" -> {
                    item { FinanceOptionPicker("支付方式", route, routeGroups.map { (key, fields) -> key to fields.first().label.substringBefore(" · ") }) { route = it } }
                    (routeGroups[route] ?: routeGroups.values.firstOrNull()).orEmpty().filter { it.type != "hidden" }.forEach { field ->
                        item { NativeFieldEditor(field.copy(label = field.label.substringAfter(" · ", field.label), readOnly = field.readOnly || blocked), values[field.name].orEmpty()) { change(field, it) } }
                    }
                }
                else -> form.fields.filter { it.name.startsWith("settle_") && it.type != "hidden" }.forEach { field ->
                    item { NativeFieldEditor(field.copy(readOnly = field.readOnly || blocked), values[field.name].orEmpty()) { change(field, it) } }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChatOutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.weight(1f)) { Text("取消编辑") }
            ChatButton(onClick = { confirm = true }, enabled = valid && !blocked, modifier = Modifier.weight(1f)) { Text(if (uncertain) "等待核对" else if (busy) "保存中…" else "保存用户组") }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("确认保存用户组") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(values["name"].orEmpty(), fontWeight = FontWeight.SemiBold)
            Text(values["gid"].orEmpty().takeIf(String::isNotBlank)?.let { "用户组 #$it" } ?: "新增用户组")
            Text("通道、分成比例与结算设置会影响该组商户，请核对后保存。", color = Muted)
        } },
        confirmButton = { ChatButton(enabled = valid && !blocked, onClick = { confirm = false; onSubmit(form, values.toMap()) }) { Text("确认保存") } },
        dismissButton = { ChatTextButton(onClick = { confirm = false }) { Text("取消") } })
}
