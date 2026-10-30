package com.futoonpay.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.futoonpay.admin.ui.ChatButton
import com.futoonpay.admin.ui.ChatFieldColors
import com.futoonpay.admin.ui.ChatFilterTabs
import com.futoonpay.admin.ui.theme.Muted

@Composable
internal fun MerchantStatisticsScreen(state: NativeModuleState, onQuery: (String, String) -> Unit) {
    var day by rememberSaveable { mutableStateOf(state.statisticsDay) }
    var method by rememberSaveable { mutableStateOf(state.statisticsMethod) }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ChatFilterTabs(listOf("type" to "按支付方式", "channel" to "按支付通道"), method, enabled = !state.loading) { value -> method = value; onQuery(day.trim(), value) } }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(day, { day = it }, singleLine = true, label = { Text("查询日期") }, modifier = Modifier.weight(1f), colors = ChatFieldColors())
            ChatButton(onClick = { onQuery(day.trim(), method) }, enabled = !state.loading) { Text(if (state.loading) "查询中…" else "查询") }
        } }
        state.statistics?.let { table ->
            item { Text("${state.statisticsDay} · ${if (state.statisticsMethod == "channel") "按支付通道" else "按支付方式"} · ${table.rows.size} 个商户", color = Muted) }
            items(table.rows) { row -> NativeTableRow(table.headers, row, summaryFields = 2, foldZeroDetails = true) }
            if (table.rows.isEmpty()) item { Text("当天没有交易记录", color = Muted) }
        }
    }
}
