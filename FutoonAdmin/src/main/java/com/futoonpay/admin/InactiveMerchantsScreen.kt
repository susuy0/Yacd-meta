package com.futoonpay.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.futoonpay.admin.data.InactiveMerchantFilters
import com.futoonpay.admin.ui.*
import com.futoonpay.admin.ui.theme.*

@Composable
internal fun InactiveMerchantsScreen(state: NativeModuleState, onScan: (InactiveMerchantFilters) -> Unit, onFreeze: (Set<String>) -> Unit) {
    var keyword by rememberSaveable { mutableStateOf(state.inactiveFilters.keyword) }
    var whitelist by rememberSaveable { mutableStateOf(state.inactiveFilters.whitelist) }
    var status by rememberSaveable { mutableStateOf(state.inactiveFilters.accountStatus) }
    var filters by rememberSaveable { mutableStateOf(false) }
    var selected by remember(state.inactiveScan, state.inactiveRevision) { mutableStateOf(emptySet<String>()) }
    var confirm by remember(state.inactiveScan, state.inactiveRevision) { mutableStateOf(false) }
    val rows = state.inactiveScan?.rows.orEmpty()
    val busy = state.loading || state.writeUncertain || state.loadError != null
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedTextField(keyword, { keyword = it }, label = { Text("商户号 / 名称") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = ChatFieldColors()) }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChatOutlinedButton(onClick = { filters = !filters }, modifier = Modifier.weight(1f)) { Text(if (filters) "收起筛选" else "筛选") }
                ChatButton(onClick = { onScan(InactiveMerchantFilters(whitelist, status, keyword)) }, enabled = !state.loading, modifier = Modifier.weight(1f)) { Text(if (state.loading) "扫描中…" else "扫描商户") }
            } }
            if (filters) {
                item { Text("白名单", color = Muted); PayPills(listOf("all" to "全部", "yes" to "仅白名单", "no" to "非白名单"), whitelist) { whitelist = it } }
                item { Text("账户状态", color = Muted); PayPills(listOf("all" to "全部", "normal" to "正常", "frozen" to "已冻结"), status) { status = it } }
            }
            state.inactiveScan?.let { scan ->
                item { Text("${scan.total} 个商户 · 自 ${scan.windowStart} 起共 5 个自然日", color = Muted) }
                if (rows.isEmpty()) item { Text("没有符合条件的商户", color = Muted) }
            }
            items(rows, key = { it.uid }) { merchant ->
                var expanded by remember(merchant.uid) { mutableStateOf(false) }
                PayCard(compact = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(modifier = Modifier.semantics { contentDescription = "选择商户 #${merchant.uid}" }, checked = merchant.uid in selected, enabled = !busy && merchant.status > 0, onCheckedChange = { checked -> selected = if (checked) selected + merchant.uid else selected - merchant.uid })
                        Column(Modifier.weight(1f)) { Text("${merchant.name.ifBlank { "商户" }} #${merchant.uid}"); Text(merchant.statusText, color = Muted) }
                        IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(48.dp)) { Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "收起商户详情" else "展开商户详情") }
                    }
                    DataRow("最近下单", merchant.lastOrder.ifBlank { "无记录" })
                    DataRow("近 5 日订单", merchant.recentOrders)
                    if (merchant.whitelistText.isNotBlank() && merchant.whitelistText != "—") PayBadge(merchant.whitelistText)
                    if (expanded) DataRow("注册时间", merchant.registered)
                }
            }
        }
        if (selected.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ChatTextButton(onClick = { selected = emptySet() }, enabled = !state.loading) { Text("取消选择") }
            ChatOutlinedButton(onClick = { confirm = true }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("冻结所选 ${selected.size} 个商户", color = Danger) }
        }
    }
    if (confirm) PaySheet("确认冻结商户", { confirm = false }) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("将冻结以下 ${selected.size} 个商户。请核对商户号和名称。", color = Danger) }
            items(rows.filter { it.uid in selected }) { Text("#${it.uid} · ${it.name}") }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChatOutlinedButton(onClick = { confirm = false }, modifier = Modifier.weight(1f)) { Text("取消") }
            ChatButton(onClick = { confirm = false; onFreeze(selected) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("确认冻结") }
        }
    }
}
