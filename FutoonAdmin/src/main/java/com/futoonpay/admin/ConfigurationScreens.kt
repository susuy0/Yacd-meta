package com.futoonpay.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.*
import com.futoonpay.admin.ui.theme.*

internal fun configurationRowId(headers: List<String>, row: List<String>, actions: List<NativeAction>): String {
    val index = headers.indexOfFirst { it.equals("id", true) || it == "编号" }
    if (index >= 0) return row.getOrNull(index).orEmpty().trim()
    return actions.firstNotNullOfOrNull { Regex("[?&]id=([0-9]+)(?:&|$)").find(it.endpoint)?.groupValues?.get(1) ?: it.fields["id"] }.orEmpty()
}

internal fun configurationAssociations(module: String, id: String, channels: List<ConfigurationChannel>) =
    if (id.isBlank()) emptyList() else channels.filter { if (module == "proxies") it.proxyId == id else it.typeId == id }

internal fun configurationActionAssociations(state: PaymentAdminState, action: NativeAction): String {
    if (state.spec.id !in setOf("proxies", "payment_types")) return ""
    val id = Regex("[?&]id=([0-9]+)(?:&|$)").find(action.endpoint)?.groupValues?.get(1) ?: action.fields["id"].orEmpty()
    val channels = configurationAssociations(state.spec.id, id, state.configurationChannels)
    return if (channels.isNotEmpty()) "\n关联通道（${channels.size}）：" + channels.joinToString("、") { "${it.name} #${it.id}" }
        else if (!state.configurationLoaded || state.configurationError != null || (state.spec.id == "proxies" && state.configurationChannels.any { it.proxyId == null })) "\n关联关系尚未完整读取，请先刷新核对。" else "\n暂无关联通道。"
}

@Composable
internal fun ConfigurationListScreen(state: PaymentAdminState, modifier: Modifier, query: String, onQuery: (String) -> Unit,
    onAction: (NativeAction) -> Unit, onReloadContext: () -> Unit, onRetry: () -> Unit, onBatch: (BatchRequest) -> Unit = {}) {
    val module = state.spec.id
    val visible = state.document?.tables.orEmpty().flatMap { table -> table.rows.mapIndexedNotNull { index, row ->
        if (query.isNotBlank() && row.none { it.contains(query.trim(), true) }) null else {
            val actions = table.rowActions.getOrNull(index).orEmpty()
            val id = configurationRowId(table.headers, row, actions)
            PaymentBatchRow(id, "${row.getOrNull(table.headers.indexOfFirst { it.contains("名称") || it.equals("name", true) }).orEmpty()} #$id", actions)
        }
    } }
    val selection = rememberRecordSelection(listOf(module, state.page, query), visible.map { it.id })
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OutlinedTextField(query, onQuery, placeholder = { Text(if (module == "proxies") "搜索代理名称、ID、地址" else "搜索支付方式、调用值、ID") }, singleLine = true, modifier = Modifier.fillMaxWidth() , colors = com.futoonpay.admin.ui.ChatFieldColors()) }
        item { MultiSelectBar(selection, visible.map { it.id }, paymentBatchOptions(module, visible.filter { it.id in selection.selected }), state.loading, onBatch) }
        if (state.configurationLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.configurationError?.let { error -> item { PayCard { Text(error, color = Danger); com.futoonpay.admin.ui.ChatTextButton(onClick = onReloadContext) { Text("重新加载关联通道") } } } }
        state.document?.tables.orEmpty().forEach { table ->
            itemsIndexed(table.rows) { index, row ->
                if (query.isBlank() || row.any { it.contains(query.trim(), true) }) {
                    val actions = table.rowActions.getOrNull(index).orEmpty()
                    val id = configurationRowId(table.headers, row, actions)
                    val fields = table.headers.mapIndexed { i, label -> label to row.getOrNull(i).orEmpty() }
                    val name = fields.firstOrNull { it.first.contains("名称") || it.first.equals("name", true) }?.second.orEmpty()
                    val channels = configurationAssociations(module, id, state.configurationChannels)
                    SelectableRecord(selection, id, "${state.spec.title} $id") {
                    PayCard {
                        val disable = actions.firstOrNull { it.label in setOf("停用", "禁用", "关闭") }
                        val enable = actions.firstOrNull { it.label in setOf("启用", "开启") }
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("${name.ifBlank { state.spec.title }} #$id", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (disable != null || enable != null) Switch(checked = disable != null, onCheckedChange = { (if (it) enable else disable)?.let(onAction) }, enabled = !state.loading)
                        }
                        fields.filter { (label, value) -> value.isNotBlank() && label !in setOf("ID", "id", "名称", "操作") }.forEach { (label, value) -> DataRow(label, nativeVisibleValue(label, value)) }
                        HorizontalDivider(color = Line)
                        Text("关联通道 · ${if (state.configurationLoading) "加载中" else channels.size}", fontSize = 12.sp, color = Muted)
                        AssociationLinks(channels, { channelId -> onAction(NativeAction("通道详情", "futoon://channel-detail?id=$channelId")) })
                        if (channels.isEmpty()) Text(when {
                            state.configurationError != null || !state.configurationLoaded -> "关联通道尚未读取"
                            module == "proxies" && state.configurationChannels.any { it.proxyId == null } -> "后台未提供完整代理关联字段"
                            else -> "暂无关联通道"
                        }, color = Muted, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            actions.filterNot { it.endpoint.startsWith("futoon://channels") || it == enable || it == disable }.forEach { action ->
                                com.futoonpay.admin.ui.ChatTextButton(onClick = { onAction(action) }, enabled = !state.loading) { Text(action.label, color = if (action.destructive) Danger else GreenDark, fontSize = 12.sp) }
                            }
                        }
                    }
                    }
                }
            }
        }
        item { ListFeedback(state.loading && state.document == null, state.loadError, !state.loading && state.document?.tables.orEmpty().flatMap { it.rows }.isEmpty(), onRetry) }
    }
}

@Composable
private fun AssociationLinks(channels: List<ConfigurationChannel>, onOpen: (String) -> Unit) {
    channels.forEach { channel -> com.futoonpay.admin.ui.ChatTextButton(onClick = { onOpen(channel.id) }, contentPadding = PaddingValues(0.dp)) { Text("${channel.name} #${channel.id} →", fontSize = 12.sp) } }
}

@Composable
internal fun ConfigurationEditorFields(form: NativeForm, module: String, channels: List<ConfigurationChannel>, busy: Boolean,
    error: String?, onField: (String, String) -> Unit, onOpenChannel: (String) -> Unit, onRetry: () -> Unit) {
    val fee = form.methodFeeField().takeIf { module == "payment_types" }
    val bindings = form.methodBindingField().takeIf { module == "payment_types" }
    val id = form.fields.firstOrNull { it.name == "id" }?.value.orEmpty()
    val selected = selectedChannelIds(bindings?.value.orEmpty())
    var query by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("基本信息")
        PayCard {
            form.fields.filter { it.type != "hidden" && it != fee && it != bindings }.forEach { field ->
                NativeFieldEditor(field.copy(readOnly = field.readOnly || busy), field.value) { onField(field.name, it) }
                if (module == "payment_types" && field.name == "name" && field.readOnly) Text("类型标识创建后不可修改", color = Muted, fontSize = 12.sp)
            }
        }
        if (module == "payment_types") {
            SectionLabel("支付方式费率")
            PayCard {
                if (fee != null) NativeFieldEditor(fee.copy(label = "向商户收取的费率 (%)", type = "decimal", readOnly = fee.readOnly || busy), fee.value) { onField(fee.name, it) }
                else Text("当前后台表单未提供独立支付方式费率，需后台支持后配置。", color = Muted, fontSize = 12.sp)
                Text("通道成本费率在通道内单独配置，这里不会修改。", color = Muted, fontSize = 12.sp)
            }
        }
        SectionLabel(if (bindings != null) "关联通道 · 已选 ${selected.size} 个" else "关联通道")
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) PayCard { Text(error, color = Danger); com.futoonpay.admin.ui.ChatTextButton(onClick = onRetry) { Text("重新加载关联通道") } }
        if (bindings != null) {
            OutlinedTextField(query, { query = it }, placeholder = { Text("搜索通道名称或 ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true , colors = com.futoonpay.admin.ui.ChatFieldColors())
            val options = (channels.map { NativeOption(it.id, it.name) } + bindings.options + selected.map { NativeOption(it, "原有通道 #$it") }).distinctBy { it.value }
            options.filter { query.isBlank() || "${it.label} ${it.value}".contains(query.trim(), true) }.forEach { option ->
                val channel = channels.firstOrNull { it.id == option.value }
                val checked = option.value in selected
                val compatible = channel != null && channel.typeId == id
                PayCard {
                    Row {
                        Checkbox(checked, { next -> onField(bindings.name, (if (next) selected + option.value else selected - option.value).joinToString(",")) }, enabled = !busy && !bindings.readOnly && (compatible || checked))
                        Column(Modifier.weight(1f)) {
                            Text("${option.label} #${option.value}")
                            Text(when { channel == null -> "通道已失效，取消勾选可移除"; !compatible -> "支付方式不匹配，不可选"; else -> "成本 ${channel.cost.ifBlank { "—" }}% · 限额 ${channel.minimum.ifBlank { "—" }}–${channel.maximum.ifBlank { "—" }}" }, color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            val associated = configurationAssociations(module, id, channels)
            PayCard {
                AssociationLinks(associated, onOpenChannel)
                if (associated.isEmpty()) Text(if (error != null || busy) "关联通道尚未读取" else if (module == "proxies" && channels.any { it.proxyId == null }) "后台未提供完整代理关联字段" else "暂无关联通道", color = Muted)
                if (module == "payment_types") Text("当前后台未提供多通道绑定字段；现有关联按通道的支付方式展示。", color = Muted, fontSize = 12.sp)
            }
        }
    }
}
