package com.futoonpay.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    fun change(field: NativeField, value: String) { updateGroupRoute(form, values, field.name, value); onDraft(revision, values.toMap()) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (uncertain) item { Text(if (values["action"] == "add") "新增结果待核对，请返回列表刷新确认，不要重复新增。" else "保存结果待核对，请点右上角重新读取后核实。", color = Warning) }
            item { form.fields.firstOrNull { it.name == "name" }?.let { field -> NativeFieldEditor(field.copy(label = "显示名称", readOnly = blocked), values[field.name].orEmpty()) { change(field, it) } } }
            routeGroups.forEach { (_, fields) -> item {
                val channel = fields.firstOrNull { it.name.endsWith("[channel]") }
                val summary = channel?.options?.firstOrNull { it.value == values[channel.name] }?.label.orEmpty()
                GroupEditorSection(fields.first().label.substringBefore(" · "), summary, form, fields, values, blocked) { field, value -> change(field, value) }
            } }
            item { GroupEditorSection("结算设置", "结算开关、手动结算与手续费", form, form.fields.filter { it.name.startsWith("settle_") }, values, blocked) { field, value -> change(field, value) } }
            val advanced = form.fields.filter { it.type != "hidden" && it.name != "name" && !it.name.startsWith("info[") && !it.name.startsWith("settle_") }
            if (advanced.isNotEmpty()) item { GroupEditorSection("高级设置", "用户变量", form, advanced, values, blocked) { field, value -> change(field, value) } }
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

@Composable
private fun GroupEditorSection(title: String, summary: String, form: NativeForm, fields: List<NativeField>, values: Map<String, String>, busy: Boolean, onChange: (NativeField, String) -> Unit) {
    var expanded by remember(form, title) { mutableStateOf(false) }
    PayCard(compact = true) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); if (summary.isNotBlank()) Text(summary, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "收起$title" else "展开$title")
        }
        if (expanded) fields.filter { it.type != "hidden" }.forEach { field ->
            NativeFieldEditor(field.copy(label = field.label.substringAfter(" · ", field.label), readOnly = field.readOnly || busy), values[field.name].orEmpty()) { onChange(field, it) }
        }
    }
}
