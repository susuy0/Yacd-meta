package com.futoonpay.admin.ui

import com.futoonpay.admin.ui.theme.CardColor
import com.futoonpay.admin.ui.theme.ButtonText
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.text.selection.SelectionContainer
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.futoonpay.admin.PanelDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import com.futoonpay.admin.data.MerchantSummary
import com.futoonpay.admin.data.MerchantFlowSummary
import com.futoonpay.admin.data.MerchantSearchFilters
import com.futoonpay.admin.data.OrderSummary
import com.futoonpay.admin.data.OrderDetail
import com.futoonpay.admin.data.orderPageMetrics
import com.futoonpay.admin.data.displayOrderAmount
import com.futoonpay.admin.data.OrderChannelStat
import com.futoonpay.admin.data.ChannelRateSource
import com.futoonpay.admin.data.OrderSearchFilters
import com.futoonpay.admin.data.SettlementSearchFilters
import com.futoonpay.admin.data.SettlementSummary
import com.futoonpay.admin.data.SETTLEMENT_CHECKED_RESULT
import com.futoonpay.admin.data.merchantAccountStatusLabel
import com.futoonpay.admin.data.merchantPaymentStatusLabel
import com.futoonpay.admin.data.orderSettlementStatusLabel
import com.futoonpay.admin.data.orderStatusLabel
import com.futoonpay.admin.data.activeChips
import com.futoonpay.admin.data.withoutChip
import com.futoonpay.admin.data.withPreset
import com.futoonpay.admin.data.DatePreset
import com.futoonpay.admin.data.validRefundAmount
import com.futoonpay.admin.data.displayAmount
import com.futoonpay.admin.data.displayFee
import com.futoonpay.admin.data.settlementStatusLabel
import com.futoonpay.admin.ui.theme.Danger
import com.futoonpay.admin.ui.theme.Green
import com.futoonpay.admin.ui.theme.GreenDark
import com.futoonpay.admin.ui.theme.Ink
import com.futoonpay.admin.ui.theme.Line
import com.futoonpay.admin.ui.theme.Muted
import com.futoonpay.admin.ui.theme.Warning
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.lifecycle.repeatOnLifecycle

@Composable
internal fun OrdersScreen(state: AppUiState, viewModel: AppViewModel, searchSignal: Int = 0, showSearchEntry: Boolean = true) {
    val selection = rememberRecordSelection(listOf(state.adminUrl, state.orderPage, state.orderSearch), state.orders.map { it.tradeNo })
    var filters by remember { mutableStateOf(state.orderSearch) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingOrderAction?>(null) }
    var selectedChannelId by rememberSaveable { mutableStateOf("") }
    var actionLoading by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(searchSignal) { if (searchSignal > 0) advanced = true }
    LaunchedEffect(state.orderSearch) { filters = state.orderSearch }
    val panelLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(panelLifecycle) {
        panelLifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (isActive) { delay(30_000); viewModel.loadOrderChannelStats() }
        }
    }
    Column {
        StatusFilters(listOf(-1 to "全部", 0 to "待支付", 1 to "已支付", 2 to "已退款", 3 to "已冻结"), state.orderSearch.status) {
            viewModel.loadOrders(1, state.orderSearch.copy(status = it))
        }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            if (showSearchEntry) Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { advanced = true }) { Icon(Icons.Default.Search, null, Modifier.size(16.dp)); Text("完整搜索", fontSize = 12.sp) }
                Spacer(Modifier.weight(1f))
                if (state.orderSearch != OrderSearchFilters(status = state.orderSearch.status)) TextButton(onClick = { viewModel.loadOrders(1, OrderSearchFilters(status = state.orderSearch.status)) }) { Text("重置") }
            }
            AppliedChips(state.orderSearch.activeChips(),
                { viewModel.loadOrders(1, state.orderSearch.withoutChip(it)) },
                { viewModel.loadOrders(1, OrderSearchFilters(status = state.orderSearch.status)) })
        }
        item { CurrentOrderMetrics(state.orders, state.orderPage) }
        item { MultiSelectBar(selection, state.orders.map { it.tradeNo }, com.futoonpay.admin.data.orderBatchOptions(state.orders.filter { it.tradeNo in selection.selected }), state.refreshing || state.ordersLoading, viewModel::offerBatch) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("共 ${state.orderTotal} 笔 · 服务器查询", color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(if (state.orderChannelStatsLoading) "成功率更新中…" else "30秒自动刷新", color = Muted, fontSize = 12.sp)
            }
        }
        state.orderChannelStatsMessage?.let { item { Text(it, color = Warning, fontSize = 12.sp) } }
        item { ListFeedback(state.ordersLoading && state.orders.isEmpty(), state.ordersError, !state.ordersLoading && state.orders.isEmpty(), { viewModel.loadOrders(state.orderPage) }) }
        itemsIndexed(state.orders, key = { index, item -> "${item.tradeNo}:$index" }) { _, order ->
            SelectableRecord(selection, order.tradeNo, order.tradeNo) {
            OrderCard(order, state.orderChannelStats[order.channelId.normalizedId()], state.orderChannelStatsLoading,
                { viewModel.loadOrderDetail(order.tradeNo) }, { selectedChannelId = order.channelId.normalizedId() }) { pending = it }
            }
        }
        item { PaginationBar(state.orderPage, state.orderTotal, state.orders.size) { viewModel.loadOrders(it) } }
    }
    }
    if (advanced) PaySheet("完整搜索", { advanced = false }) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 480.dp), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ChoicePicker("订单状态", filters.status.toString(), listOf("-1" to "全部", "0" to "待支付", "1" to "已支付", "2" to "已退款", "3" to "已冻结")) { filters = filters.copy(status = it.toIntOrNull() ?: -1) } }
            item { ChoicePicker("搜索字段", filters.column, ORDER_SEARCH_COLUMNS) { filters = filters.copy(column = it) } }
            item { OutlinedTextField(filters.value, { filters = filters.copy(value = it) }, label = { Text("搜索内容") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { OutlinedTextField(filters.merchantId, { filters = filters.copy(merchantId = it) }, label = { Text("商户 UID") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { ChoicePicker("支付方式", filters.paymentType, ORDER_PAYMENT_TYPES) { filters = filters.copy(paymentType = it) } }
            item { OutlinedTextField(filters.channelId, { filters = filters.copy(channelId = it) }, label = { Text("通道 ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { DatePreset.entries.forEach { preset ->
                OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = { filters = filters.withPreset(preset, java.time.LocalDate.now()) }, modifier = Modifier.weight(1f)) { Text(preset.label, fontSize = 12.sp) }
            } } }
            item { OutlinedTextField(filters.startDate, { filters = filters.copy(startDate = it) }, label = { Text("开始日期 YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { OutlinedTextField(filters.endDate, { filters = filters.copy(endDate = it) }, label = { Text("结束日期 YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = { filters = OrderSearchFilters(status = filters.status) }, modifier = Modifier.weight(1f)) { Text("重置") }
                Button(shape = RoundedCornerShape(10.dp), onClick = { viewModel.loadOrders(1, filters); advanced = false }, modifier = Modifier.weight(2f)) { Text("应用筛选") }
            }
    }
    pending?.let { action -> OrderActionDialog(action, actionLoading, actionError, { if (!actionLoading) { pending = null; actionError = null } }, uncertain = action.order.tradeNo in state.uncertainOrders, onVerify = { pending = null; actionError = null; viewModel.loadOrderDetail(action.order.tradeNo) }) { amount, password, remark ->
        actionLoading = true; actionError = null
        viewModel.runOrderAction(OrderCommand(action.action, action.order.tradeNo, action.status, amount, password, remark)) { result ->
            actionLoading = false
            if (result.success) { pending = null; actionError = null } else actionError = result.message
        }
    } }
    if (state.orderDetailTradeNo.isNotBlank()) OrderDetailDialog(state.orderDetailTradeNo, state.orderDetail, state.orderDetailLoading,
        state.orders.firstOrNull { it.tradeNo == state.orderDetailTradeNo }, viewModel::dismissOrderDetail,
        { viewModel.dismissOrderDetail(); pending = it })
    if (selectedChannelId.isNotBlank()) ChannelHealthDialog(selectedChannelId, state.orderChannelStats[selectedChannelId], state.orderChannelStatsLoading, { selectedChannelId = "" })
}

private data class PendingOrderAction(
    val label: String,
    val action: String,
    val order: OrderSummary,
    val warning: String,
    val status: Int? = null,
    val amountRequired: Boolean = false,
    val passwordRequired: Boolean = false,
)

@Composable
private fun CurrentOrderMetrics(orders: List<OrderSummary>, page: Int) {
    val successful = orders.count { it.status == 1 }
    val amounts = orderPageMetrics(orders).amounts
    val rate = if (orders.isEmpty()) null else successful * 100.0 / orders.size
    androidx.compose.material3.Surface(color = com.futoonpay.admin.ui.theme.GreenTint, shape = RoundedCornerShape(12.dp)) { Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("当前页 · 第 $page 页 (${orders.size} 条)", color = Muted, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("订单 ${orders.size}", fontSize = 13.sp); Text("成功 $successful", fontSize = 13.sp) }
            Column(Modifier.weight(1f)) { Text("成功率", color = Muted, fontSize = 12.sp); Text(rate?.let(::formatRate) ?: "—", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            Column(Modifier.weight(1.6f), horizontalAlignment = Alignment.End) { Text("订单金额", color = Muted, fontSize = 12.sp); Text(amounts.entries.joinToString("\n") { (currency, amount) -> displayOrderAmount(amount.toPlainString(), currency) }.ifBlank { "—" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
    } }
}

@Composable
private fun MetricText(label: String, value: String, modifier: Modifier) {
    Column(modifier) { Text(label, color = Muted, fontSize = 12.sp); Text(value, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun OrderCard(order: OrderSummary, channelStat: OrderChannelStat?, channelLoading: Boolean,
    onDetail: () -> Unit, onChannelStat: () -> Unit, onAction: (PendingOrderAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    PayCard(Modifier.clickable(onClick = onDetail), compact = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(order.tradeNo, Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
            StatusPill(orderStatusLabel(order.status), orderStatusColor(order.status))
        }
        if (order.merchantTradeNo.isNotBlank()) Text("商户单 ${order.merchantTradeNo}", color = Muted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${order.merchantName} UID ${order.merchantId}".trim(), color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(displayOrderAmount(order.amount, order.currency), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(order.paymentType, color = Muted, fontSize = 12.sp)
            Text(order.createdAt, color = Muted, fontSize = 12.sp)
        }
        if (order.channelId.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = Line)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${channelStat?.channelName?.ifBlank { "通道" } ?: "通道"} #${order.channelId}",
                    Modifier.weight(1f), color = Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.heightIn(min = 44.dp).clickable(onClick = onChannelStat).padding(start = 8.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Surface(shape = RoundedCornerShape(13.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line), color = CardColor) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(when { channelStat?.fiveMinuteRate != null -> formatRate(channelStat.fiveMinuteRate); channelLoading -> "更新中…"; else -> "暂无样本" },
                        color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(when {
                        channelStat == null -> "近5分钟"
                        channelStat.source == ChannelRateSource.CURRENT_PAGE -> "${channelStat.successful}/${channelStat.total} · 当前页样本"
                        channelStat.countsOfficial -> "${channelStat.successful}/${channelStat.total} · 近5分钟"
                        else -> "近5分钟 · 官方成功率"
                    },
                        color = Muted, fontSize = 12.sp)
                } } }
            }
        }
    }
}

@Composable
private fun ChannelRateCard(stat: OrderChannelStat?, loading: Boolean, onClick: () -> Unit) {
    val rate = stat?.fiveMinuteRate
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = rateColor(rate).copy(alpha = .07f)),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (loading && stat == null) Muted else rateColor(rate)))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        loading && stat == null -> "近5分钟成功率 · 读取中"
                        rate != null -> "近5分钟成功率 · ${formatRate(rate)}"
                        else -> "近5分钟成功率 · 暂无样本"
                    },
                    color = if (rate == null) Muted else rateColor(rate), fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                )
                Text(
                    when {
                        stat == null -> "等待官网通道监测"
                        stat.source == ChannelRateSource.OFFICIAL -> "官网监测 · 成功 ${stat.successful}/${stat.total} · 处理中 ${stat.processing}"
                        else -> "当前页降级样本 · 成功 ${stat.successful}/${stat.total} · 处理中 ${stat.processing}"
                    },
                    color = Muted, fontSize = 12.sp,
                )
            }
            Text("详情", color = GreenDark, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ChannelHealthDialog(
    channelId: String,
    stat: OrderChannelStat?,
    loading: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val navigation = com.futoonpay.admin.LocalPanelNavigation.current
    val rate = stat?.fiveMinuteRate
    PaySheet("${stat?.channelName?.ifBlank { "通道" } ?: "通道"} #$channelId", onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 580.dp)) {
            Text("近5分钟 · 30秒自动刷新", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
            when {
                loading && stat == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = CardColor), border = CardDefaults.outlinedCardBorder(), shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(17.dp)) {
                                Text(
                                    if (stat?.source == ChannelRateSource.OFFICIAL) "近5分钟 · 全通道统计" else "近5分钟 · 当前页样本",
                                    color = Muted, fontSize = 12.sp,
                                )
                                Text(rate?.let(::formatRate) ?: "暂无样本", color = GreenDark, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                                Text(stat?.note ?: "官网暂未返回该通道数据", color = Muted, fontSize = 12.sp)
                                Spacer(Modifier.height(13.dp))
                                Text(if (stat?.countsOfficial == true) "全通道订单数量" else "以下数量为当前页近5分钟样本", color = Muted, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    HealthHeroMetric("成功", stat?.successful, Modifier.weight(1f))
                                    HealthHeroMetric("总订单", stat?.total, Modifier.weight(1f))
                                    if (stat?.processingAvailable != false) HealthHeroMetric("处理中", stat?.processing, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    if (stat?.source == ChannelRateSource.OFFICIAL) item {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            CompactMetric("5分钟", stat.fiveMinuteRate?.let(::formatRate) ?: "—", Modifier.weight(1f), rateColor(stat.fiveMinuteRate))
                            CompactMetric("30分钟", stat.thirtyMinuteRate?.let(::formatRate) ?: "—", Modifier.weight(1f), rateColor(stat.thirtyMinuteRate))
                            CompactMetric("1小时", stat.oneHourRate?.let(::formatRate) ?: "—", Modifier.weight(1f), rateColor(stat.oneHourRate))
                            CompactMetric("24小时", stat.oneDayRate?.let(::formatRate) ?: "—", Modifier.weight(1f), rateColor(stat.oneDayRate))
                        }
                    }
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = CardColor), border = CardDefaults.outlinedCardBorder()) {
                            Column(Modifier.padding(14.dp)) {
                                Text("当前页样本 · 每分钟趋势", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(Modifier.height(10.dp))
                                MerchantSparkline(stat?.minuteBuckets?.reversed()?.map(Int::toDouble).orEmpty(), Modifier.fillMaxWidth().height(105.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    listOf("-4m", "-3m", "-2m", "-1m", "现在").forEach { Text(it, color = Muted, fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = CardColor), border = CardDefaults.outlinedCardBorder()) {
                            Column(Modifier.padding(14.dp)) {
                                Text("最近订单与状态明细", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(Modifier.height(7.dp))
                                if (stat?.recentOrders.isNullOrEmpty()) {
                                    Text("当前页最近5分钟没有该通道订单。", color = Muted, fontSize = 12.sp)
                                } else stat?.recentOrders.orEmpty().forEachIndexed { index, order ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(order.tradeNo, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(order.createdAt, color = Muted, fontSize = 12.sp)
                                        }
                                        Text(money(order.amount), fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                        Spacer(Modifier.width(7.dp))
                                        StatusPill(orderStatusLabel(order.status), orderStatusColor(order.status))
                                    }
                                    if (index < stat.recentOrders.lastIndex) HorizontalDivider(color = Line)
                                }
                            }
                        }
                    }
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = CardColor), border = CardDefaults.outlinedCardBorder()) {
                            Column(Modifier.padding(14.dp)) {
                                Text("业务关联", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    item {
                                        TextButton(onClick = {
                                            navigation.orders(channelId = channelId)
                                        }) { Text("该通道订单") }
                                    }
                                    item {
                                        TextButton(onClick = {
                                            navigation.module("channels", channelId = channelId)
                                        }) { Text("通道详情") }
                                    }
                                    item {
                                        TextButton(onClick = {
                                            navigation.module("channel_monitor")
                                        }) { Text("通道监测") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthHeroMetric(label: String, value: Int?, modifier: Modifier) {
    Column(modifier.padding(8.dp)) {
        Text(label, color = Muted, fontSize = 12.sp, maxLines = 1)
        Text(value?.toString() ?: "—", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}


@Composable
private fun OrderDetailDialog(tradeNo: String, detail: OrderDetail?, loading: Boolean,
    summary: OrderSummary?, onDismiss: () -> Unit, onAction: (PendingOrderAction) -> Unit) {
    val context = LocalContext.current
    val navigation = com.futoonpay.admin.LocalPanelNavigation.current
    val workflows = com.futoonpay.admin.LocalPanelWorkflows.current
    if (detail != null) workflows?.rememberDetail(detail)
    val channelFound = com.futoonpay.admin.LocalChannelOrderFound.current
    LaunchedEffect(detail?.tradeNo, detail?.channelId) { detail?.channelId?.takeIf { it.isNotBlank() }?.let { channelFound?.invoke(it) } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            PayTopBar("订单详情", onDismiss)
            when {
                loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 2.dp) }
                detail == null -> Box(Modifier.weight(1f)) { EmptyState("订单详情读取失败") }
                else -> {
                    val actions = orderActions(summary ?: detail.toSummary())
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { DetailBlock("订单标识", listOf("系统订单号" to detail.tradeNo, "商户订单号" to detail.merchantTradeNo, "接口订单号" to detail.apiTradeNo)) }
                        item { PayCard {
                            Text("支付结果", color = Muted, fontSize = 12.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(displayOrderAmount(detail.amount, detail.currency), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                StatusPill(orderStatusLabel(detail.status), orderStatusColor(detail.status))
                            }
                            HorizontalDivider(color = Line)
                            DataRow("实际支付", displayOrderAmount(detail.paidAmount, detail.currency)); DataRow("商户分成", displayOrderAmount(detail.merchantIncome, detail.currency))
                            DataRow("支付方式", detail.paymentType)
                            val settlement = orderSettlementStatusLabel(detail.settlementStatus)
                            if (settlement.isNotBlank()) DataRow("结算状态", settlement)
                        } }
                        item { PayCard {
                            Text("商户与通道", color = Muted, fontSize = 12.sp)
                            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable {
                                navigation.merchants(merchantId = detail.merchantId)
                            }, verticalAlignment = Alignment.CenterVertically) { Text("商户", color = Muted, fontSize = 12.sp, modifier = Modifier.width(78.dp)); Text("UID ${detail.merchantId} ›", color = GreenDark, fontSize = 13.sp) }
                            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable {
                                navigation.module("channels", channelId = detail.channelId)
                            }, verticalAlignment = Alignment.CenterVertically) { Text("通道", color = Muted, fontSize = 12.sp, modifier = Modifier.width(78.dp)); Text("${detail.channelName.ifBlank { "通道" }} #${detail.channelId} ›", color = GreenDark, fontSize = 13.sp) }
                            DataRow("支付插件", detail.plugin)
                        } }
                        item { DetailBlock("时间 · 通知", listOf("创建时间" to detail.createdAt, "支付时间" to detail.completedAt, "回调通知" to notifyLabel(detail))) }
                        item { DetailBlock("请求信息", listOf("商品信息" to detail.name, "支付账号" to detail.buyer, "网站域名" to detail.domain, "请求 IP" to detail.ip, "附加参数" to detail.parameter)) }
                        item { OutlinedButton(onClick = { navigation.evidence(summary ?: detail.toSummary()) }, modifier = Modifier.fillMaxWidth()) { Text("选择付款凭证") } }
                        item { com.futoonpay.admin.OrderWorkflowTools(detail) }
                        item { Text("其他操作", color = Muted, fontSize = 12.sp) }
                        items(actions.filterNot { it.action == "notify" || it.action == "refund" }) { action ->
                            OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = { onAction(action) }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text(action.label) }
                        }
                    }
                    if (detail.status == 1 || detail.status == 2 || detail.status == 3) Row(
                        Modifier.fillMaxWidth().background(CardColor).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        actions.firstOrNull { it.action == "notify" }?.let { action -> OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = { onAction(action) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("重新通知") } }
                        actions.firstOrNull { it.action == "refund" }?.let { action -> Button(shape = RoundedCornerShape(10.dp), onClick = { onAction(action) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("退款") } }
                    }
                }
            }
        }
    }
}

private fun OrderDetail.toSummary() = OrderSummary(
    tradeNo, merchantTradeNo, merchantId, domain, name, amount, paidAmount, merchantIncome,
    paymentType, channelId, plugin, createdAt, completedAt, status, settlementStatus, currency,
)

private fun notifyLabel(detail: OrderDetail) = when {
    detail.status == 0 -> "等待通知"
    detail.notifyCount == 0 -> "通知成功"
    else -> "通知失败 ${detail.notifyCount} 次"
}

@Composable
private fun TimelineStep(title: String, subtitle: String, complete: Boolean, modifier: Modifier, failed: Boolean = false) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = CardColor), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(10.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (failed) Danger else if (complete) GreenDark else Line))
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
            Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CompactMetric(label: String, value: String, modifier: Modifier, valueColor: Color = Ink) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = CardColor), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(11.dp)) {
            Text(label, color = Muted, fontSize = 12.sp)
            Text(value, color = valueColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DetailBlock(title: String, rows: List<Pair<String, String>>) {
    Card(colors = CardDefaults.cardColors(containerColor = CardColor), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            rows.forEachIndexed { index, (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.width(92.dp))
                    SelectionContainer(Modifier.weight(1f)) { Text(value.ifBlank { "—" }, color = Ink, fontSize = 13.sp) }
                }
                if (index < rows.lastIndex) HorizontalDivider(color = Line)
            }
        }
    }
}

private fun orderActions(order: OrderSummary): List<PendingOrderAction> {
    val common = mutableListOf<PendingOrderAction>()
    when (order.status) {
        0 -> common += listOf(
            PendingOrderAction("查询上游订单", "queryChannelOrder", order, "将向支付通道查询最新支付状态。"),
            PendingOrderAction("改为已支付", "setStatus", order, "只修改状态，不自动核验真实到账，请确认款项已收到。", status = 1),
            PendingOrderAction("手动补单", "fillorder", order, "会直接改为已支付并给商户分成，请确认真实款项已经到账。"),
        )
        1 -> common += listOf(
            PendingOrderAction("改为未支付", "setStatus", order, "将已支付订单改回未支付，可能影响商户资金。", status = 0),
            PendingOrderAction("重新通知商户", "notify", order, "将再次向商户通知地址推送支付结果。"),
            PendingOrderAction("请求通道退款", "requestRefund", order, "将向支付通道提交退款请求。"),
            PendingOrderAction("手动退款", "refund", order, "将修改订单退款与商户资金记录。", amountRequired = true),
            PendingOrderAction("API 原路退款", "apirefund", order, "将向支付通道发起不可逆的原路退款。", amountRequired = true, passwordRequired = true),
            PendingOrderAction("冻结订单", "freeze", order, "冻结后该笔资金不可正常结算。"),
        )
        2 -> common += listOf(
            PendingOrderAction("改为未支付", "setStatus", order, "将退款订单改为未支付，请确认账务状态。", status = 0),
            PendingOrderAction("改为已支付", "setStatus", order, "将退款订单重新改为已支付，请确认账务状态。", status = 1),
            PendingOrderAction("重新通知商户", "notify", order, "将再次推送当前订单结果。"),
        )
        3 -> common += listOf(
            PendingOrderAction("解冻订单", "unfreeze", order, "解冻后订单恢复正常资金状态。"),
            PendingOrderAction("重新通知商户", "notify", order, "将再次推送当前订单结果。"),
        )
    }
    return common
}

@Composable
private fun OrderActionDialog(
    pending: PendingOrderAction,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    uncertain: Boolean = false,
    onVerify: () -> Unit = {},
    onConfirm: (String, String, String) -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf(pending.order.amount) }
    var paymentPassword by remember { mutableStateOf("") }
    var remark by rememberSaveable { mutableStateOf("") }
    PaySheet(pending.label, onDismiss) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(pending.warning, color = Muted)
                Text("订单 ${pending.order.tradeNo}", fontSize = 12.sp, color = Muted)
                if (pending.amountRequired) OutlinedTextField(
                    value = amount, onValueChange = { amount = it }, label = { Text("退款金额") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                )
                if (pending.passwordRequired) OutlinedTextField(
                    value = paymentPassword, onValueChange = { paymentPassword = it }, label = { Text("支付密码") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                )
                if (pending.amountRequired) OutlinedTextField(remark, { remark = it }, label = { Text("退款说明（可选）") }, modifier = Modifier.fillMaxWidth())
                if (pending.amountRequired && !validRefundAmount(amount, pending.order.amount)) Text("请输入不超过订单金额的正数，最多两位小数", color = Danger, fontSize = 12.sp)
                error?.let { Text(it, color = Danger, fontSize = 12.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = onDismiss, enabled = !loading, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("取消") }
                    Button(shape = RoundedCornerShape(10.dp), onClick = { if (uncertain) onVerify() else onConfirm(amount, paymentPassword, remark) },
                        enabled = !loading && (!pending.amountRequired || validRefundAmount(amount, pending.order.amount)) && (!pending.passwordRequired || paymentPassword.isNotBlank()),
                        modifier = Modifier.weight(2f).heightIn(min = 48.dp)) { Text(if (loading) "提交中…" else if (uncertain) "刷新并核对订单" else if (error != null) "重试" else "确认") }
                }
            }
    }
}

@Composable
internal fun SettlementsScreen(state: AppUiState, viewModel: AppViewModel, searchSignal: Int = 0) {
    val selection = rememberRecordSelection(listOf(state.adminUrl, state.settlementPage, state.settlementSearch), state.settlements.map { it.id })
    var filters by remember { mutableStateOf(state.settlementSearch) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingSettlementAction?>(null) }
    var detail by remember { mutableStateOf<SettlementSummary?>(null) }
    var actionLoading by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(searchSignal) { if (searchSignal > 0) advanced = true }
    LaunchedEffect(state.settlementSearch) { filters = state.settlementSearch }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { StatusFilters(listOf(-1 to "全部", 0 to "待结算", 2 to "正在结算", 1 to "已完成", 3 to "结算失败"), state.settlementSearch.status) { viewModel.loadSettlements(1, state.settlementSearch.copy(status = it)) } }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("共 ${state.settlementTotal} 笔 · 服务器查询", color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { advanced = true }) { Text("完整搜索") }
        } }
        if (state.settlementSearch.value.isNotBlank() || state.settlementSearch.merchantId.isNotBlank() || state.settlementSearch.settlementType != "0" || state.settlementSearch.usdtRate.isNotBlank()) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(listOfNotNull(state.settlementSearch.value.takeIf(String::isNotBlank), state.settlementSearch.merchantId.takeIf(String::isNotBlank)?.let { "商户 #$it" },
                    state.settlementSearch.settlementType.takeIf { it != "0" }?.let { "方式 $it" }, state.settlementSearch.usdtRate.takeIf(String::isNotBlank)?.let { "汇率 $it" }).joinToString(" · "), color = GreenDark, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.loadSettlements(1, SettlementSearchFilters(status = state.settlementSearch.status)) }) { Text("重置") }
            }
        }
        item { ListFeedback(state.settlementsLoading && state.settlements.isEmpty(), state.settlementsError, !state.settlementsLoading && state.settlements.isEmpty(), { viewModel.loadSettlements(state.settlementPage) }) }
        item { MultiSelectBar(selection, state.settlements.map { it.id }, com.futoonpay.admin.data.settlementBatchOptions(state.settlements.filter { it.id in selection.selected }), state.refreshing || state.settlementsLoading, viewModel::offerBatch) }
        items(state.settlements, key = { it.id }) { row -> SelectableRecord(selection, row.id, "结算 #${row.id}") { SettlementCard(row, { detail = row }) { pending = it } } }
        item { PaginationBar(state.settlementPage, state.settlementTotal, state.settlements.size) { viewModel.loadSettlements(it) } }
    }
    if (advanced) PaySheet("结算完整搜索", { advanced = false }) {
        LazyColumn(Modifier.heightIn(max = 540.dp), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OutlinedTextField(filters.value, { filters = filters.copy(value = it) }, label = { Text("结算账号或姓名") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(filters.merchantId, { filters = filters.copy(merchantId = it) }, label = { Text("商户 UID") }, modifier = Modifier.fillMaxWidth()) }
            item { ChoicePicker("结算方式", filters.settlementType, SETTLEMENT_TYPES) { filters = filters.copy(settlementType = it) } }
            item { OutlinedTextField(filters.usdtRate, { filters = filters.copy(usdtRate = it) }, label = { Text("USDT 汇率") }, modifier = Modifier.fillMaxWidth()) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = { filters = SettlementSearchFilters(status = filters.status) }, modifier = Modifier.weight(1f)) { Text("重置") }
                Button(shape = RoundedCornerShape(10.dp), onClick = { viewModel.loadSettlements(1, filters); advanced = false }, modifier = Modifier.weight(2f)) { Text("应用筛选") }
            } }
        }
    }
    detail?.let { row -> PaySheet("结算 #${row.id}", { detail = null }) {
        LazyColumn(Modifier.heightIn(max = 580.dp), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { DetailBlock("结算信息", listOf("状态" to settlementStatusLabel(row.status), "商户" to "${row.username} · UID ${row.merchantId}", "申请金额" to money(row.amount), "实际金额" to money(row.actualAmount), "USDT" to row.usdtAmount, "汇率" to row.usdtRate)) }
            item { DetailBlock("收款资料", listOf("结算方式" to row.chain.ifBlank { row.payType }, "账号 / 地址" to row.account, "申请时间" to row.createdAt, "完成时间" to row.completedAt, "处理结果" to row.result)) }
            items(settlementActions(row)) { action -> OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = { detail = null; pending = action }, modifier = Modifier.fillMaxWidth()) { Text(action.label) } }
        }
    } }
    pending?.let { action -> SettlementActionDialog(action, actionLoading, actionError, { if (!actionLoading) { pending = null; actionError = null } }, uncertain = action.item.id in state.uncertainSettlements, onVerify = { pending = null; actionError = null; viewModel.loadSettlements(state.settlementPage) }) { result, type, account, name ->
        actionLoading = true; actionError = null
        viewModel.runSettlementAction(SettlementCommand(action.action, action.item.id, result, type, account, name, action.item.merchantId, action.item.usdtRate, action.item.usdtAmount, status = action.status)) { response ->
            actionLoading = false
            if (response.success) { pending = null; actionError = null } else actionError = response.message
            if (response.partial) pending = action.copy(action = "settle_setresult", status = null, resultInput = true)
        }
    } }
}

private data class PendingSettlementAction(
    val label: String,
    val action: String,
    val item: SettlementSummary,
    val status: Int? = null,
    val resultInput: Boolean = false,
    val accountInput: Boolean = false,
    val resultPreset: String = "",
)

@Composable
private fun SettlementCard(item: SettlementSummary, onDetail: () -> Unit, onAction: (PendingSettlementAction) -> Unit) {
    val context = LocalContext.current
    val navigation = com.futoonpay.admin.LocalPanelNavigation.current
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onDetail), colors = CardDefaults.cardColors(containerColor = CardColor), border = CardDefaults.outlinedCardBorder(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("结算 #${item.id}", fontWeight = FontWeight.SemiBold)
                    Text("商户 ${item.merchantId} ${item.username}", color = Muted, fontSize = 12.sp)
                }
                StatusPill(settlementStatusLabel(item.status), settlementStatusColor(item.status))
                Box {
                    IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.MoreVert, "结算操作") }
                    DropdownMenu(expanded, { expanded = false }) {
                        settlementActions(item).forEach { action ->
                            DropdownMenuItem(text = { Text(action.label) }, onClick = { expanded = false; onAction(action) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(item.displayAmount(), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text("手续费 ${item.displayFee()} ${item.currency} · 实际到账 ${displayOrderAmount(item.actualAmount, item.currency)}", color = Muted, fontSize = 12.sp)
            if (item.usdtAmount.isNotBlank()) Text("${item.usdtAmount} USDT  ·  汇率 ${item.usdtRate}", color = GreenDark, fontSize = 13.sp)
            Spacer(Modifier.height(9.dp))
            Text("${item.chain.ifBlank { item.payType }}  ·  ${maskAccount(item.account)}", color = Muted, fontSize = 12.sp)
            Text("申请 ${item.createdAt}", color = Muted, fontSize = 12.sp)
            if (item.result.isNotBlank()) Text("处理结果：${item.result}", color = if (item.status == 3) Danger else Muted, fontSize = 12.sp)
            if (item.merchantId.isNotBlank()) {
                TextButton(onClick = {
                    navigation.merchants(merchantId = item.merchantId)
                }) { Text("查看关联商户", fontSize = 12.sp) }
            }
        }
    }
}

private fun settlementActions(item: SettlementSummary) = buildList {
    if (item.status == 3) add(PendingSettlementAction("检查完成", "settle_setresult", item, resultPreset = SETTLEMENT_CHECKED_RESULT))
    add(PendingSettlementAction("查询结算结果", "settle_result", item))
    add(PendingSettlementAction("设为待结算", "setStatus", item, status = 0))
    add(PendingSettlementAction("设为已完成", "setStatus", item, status = 1))
    add(PendingSettlementAction("设为正在结算", "setStatus", item, status = 2))
    add(PendingSettlementAction("设为结算失败", "setStatus", item, status = 3, resultInput = true))
    add(PendingSettlementAction("填写处理结果", "settle_setresult", item, resultInput = true))
    add(PendingSettlementAction("修改收款资料", "settle_save", item, accountInput = true))
}

@Composable
private fun SettlementActionDialog(
    pending: PendingSettlementAction,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    uncertain: Boolean = false,
    onVerify: () -> Unit = {},
    onConfirm: (String, String, String, String) -> Unit,
) {
    var result by rememberSaveable { mutableStateOf(pending.resultPreset.ifBlank { if (pending.resultInput) pending.item.result else pending.status?.toString() ?: pending.item.result }) }
    var payType by rememberSaveable { mutableStateOf(pending.item.chain.ifBlank { pending.item.payType }) }
    var account by rememberSaveable { mutableStateOf(pending.item.account) }
    var name by rememberSaveable { mutableStateOf("") }
    PaySheet(pending.label, onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 460.dp), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("结算 #${pending.item.id} · ${pending.item.displayAmount()}", color = Muted) }
            error?.let { item { Text(it, color = Danger, fontSize = 12.sp) } }
            if (pending.resultInput || pending.resultPreset.isNotBlank()) item {
                OutlinedTextField(result, { result = it }, label = { Text("处理结果") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
            if ((pending.item.status == 3 || pending.status == 3) && (pending.resultInput || pending.resultPreset.isNotBlank())) item {
                OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = { result = SETTLEMENT_CHECKED_RESULT }) { Text("检查完成 · 填写 checked") }
            }
            if (pending.accountInput) {
                item { OutlinedTextField(payType, { payType = it }, label = { Text("支付类型/链") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(account, { account = it }, label = { Text("收款账号/地址") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(name, { name = it }, label = { Text("收款人姓名") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            }
            if (!pending.resultInput && !pending.accountInput && pending.resultPreset.isBlank()) item {
                Text("该操作会更新结算状态，请确认链上或通道实际结果。")
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = onDismiss, enabled = !loading, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("取消") }
            Button(shape = RoundedCornerShape(10.dp), onClick = { if (uncertain) onVerify() else onConfirm(result, payType, account, name) }, enabled = !loading && (!(pending.resultInput || pending.resultPreset.isNotBlank()) || result.isNotBlank()), modifier = Modifier.weight(2f).heightIn(min = 48.dp)) { Text(if (loading) "提交中…" else if (uncertain) "刷新并核对结算" else if (error != null) "重试" else "确认") }
        }
    }
}

@Composable
internal fun MerchantsScreen(state: AppUiState, viewModel: AppViewModel, searchSignal: Int = 0) {
    val selection = rememberRecordSelection(listOf(state.adminUrl, state.merchantPage, state.merchantSearch), state.merchants.map { it.uid })
    var column by rememberSaveable { mutableStateOf(state.merchantSearch.column) }
    var query by rememberSaveable { mutableStateOf(state.merchantSearch.value) }
    var groupId by rememberSaveable { mutableStateOf(state.merchantSearch.groupId) }
    var status by rememberSaveable { mutableStateOf(state.merchantSearch.status) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf<MerchantAction?>(null) }
    var balanceError by remember { mutableStateOf<String?>(null) }
    var selectedMerchant by remember { mutableStateOf<MerchantSummary?>(null) }
    var detailDays by rememberSaveable { mutableStateOf(7) }
    LaunchedEffect(searchSignal) { if (searchSignal > 0) advanced = true }
    LaunchedEffect(state.merchantSearch) {
        column = state.merchantSearch.column
        query = state.merchantSearch.value
        groupId = state.merchantSearch.groupId
        status = state.merchantSearch.status
    }
    fun search(page: Int = 1) = viewModel.loadMerchants(page, MerchantSearchFilters(column, query, groupId, status))
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${state.merchantTotal} 个商户 · 服务器查询", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { advanced = true }) { Text("完整搜索") }
        } }
        if (state.merchantSearch.value.isNotBlank() || state.merchantSearch.groupId.isNotBlank() || state.merchantSearch.status != "0") item {
            Text("已筛选：${state.merchantSearch.value} · 用户组 ${state.merchantSearch.groupId.ifBlank { "全部" }} · ${MERCHANT_STATUSES.firstOrNull { it.first == state.merchantSearch.status }?.second ?: state.merchantSearch.status}", color = GreenDark, fontSize = 12.sp)
        }
        item { ListFeedback(state.merchantsLoading && state.merchants.isEmpty(), state.merchantsError, !state.merchantsLoading && state.merchants.isEmpty(), { viewModel.loadMerchants(state.merchantPage) }) }
        item { MerchantMetrics(state) }
        item { MultiSelectBar(selection, state.merchants.map { it.uid }, com.futoonpay.admin.data.merchantBatchOptions(state.merchants.filter { it.uid in selection.selected }), state.refreshing || state.merchantsLoading, viewModel::offerBatch) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${state.merchantTotal} 个商户", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(if (state.merchantFlowLoading) "正在统计7日流水" else "余额、权限与7日流水", color = Muted, fontSize = 12.sp)
                }
                TextButton(onClick = { viewModel.loadMerchantFlows() }, enabled = !state.merchantFlowLoading) { Text("刷新流水") }
            }
        }
        state.merchantFlowError?.let { error -> item { Text(error, color = Danger, fontSize = 12.sp) } }
        itemsIndexed(state.merchants, key = { index, it -> "${it.uid}:$index" }) { _, merchant ->
            SelectableRecord(selection, merchant.uid, "商户 ${merchant.uid}") {
            MerchantCard(
                merchant = merchant,
                flow = state.merchantFlows[merchant.uid],
                flowLoading = state.merchantFlowLoading,
                onDetail = { selectedMerchant = merchant; detailDays = 7; viewModel.loadMerchantDetailFlow(merchant.uid, 7) },
            ) { when (it.action) { "edit" -> viewModel.openMerchantEditor(it.merchant.uid); "resetKey" -> viewModel.offerBatch(com.futoonpay.admin.data.merchantKeyRequest(listOf(it.merchant))); else -> { balanceError = null; pending = it } } }
            }
        }
        item { PaginationBar(state.merchantPage, state.merchantTotal, state.merchants.size) { search(it) } }
    }
    if (advanced) PaySheet("商户完整搜索", { advanced = false }) {
        LazyColumn(Modifier.heightIn(max = 540.dp), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ChoicePicker("搜索字段", column, MERCHANT_SEARCH_COLUMNS) { column = it } }
            item { OutlinedTextField(query, { query = it }, label = { Text("搜索内容") }, modifier = Modifier.fillMaxWidth()) }
            item { ChoicePicker("用户组", groupId, MERCHANT_GROUPS) { groupId = it } }
            item { ChoicePicker("用户状态", status, MERCHANT_STATUSES) { status = it } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = { column = "uid"; query = ""; groupId = ""; status = "0" }, modifier = Modifier.weight(1f)) { Text("重置") }
                Button(shape = RoundedCornerShape(10.dp), onClick = { search(); advanced = false }, modifier = Modifier.weight(2f)) { Text("应用筛选") }
            } }
        }
    }
    pending?.let { action ->
        MerchantActionDialog(action, { pending = null }, state.merchantBalanceUpdating, balanceError, uncertain = action.merchant.uid in state.uncertainBalances, onVerify = { pending = null; viewModel.loadMerchants(1, MerchantSearchFilters(column = "uid", value = action.merchant.uid)) }) { amount, gid, endTime ->
            if (action.action in listOf("add", "reduce")) {
                balanceError = null
                viewModel.rechargeMerchant(action.merchant.uid, action.action, amount) { result ->
                    if (result.success) pending = null else balanceError = result.message
                }
                return@MerchantActionDialog
            }
            when (action.action) {
                                "toggleAccount" -> viewModel.setMerchantStatus(action.merchant.uid, "status", action.merchant.status != 1)
                                "togglePay" -> viewModel.setMerchantStatus(action.merchant.uid, "pay", !action.merchant.paymentEnabled)
                "toggleSettle" -> viewModel.setMerchantStatus(action.merchant.uid, "settle", !action.merchant.settlementEnabled)
                "group" -> viewModel.setMerchantGroup(action.merchant.uid, gid, endTime)
            }
            pending = null
        }
    }
    selectedMerchant?.let { merchant ->
        MerchantDetailDialog(
            merchant = merchant,
            flow = state.merchantDetailFlow,
            loading = state.merchantDetailLoading,
            onDismiss = { selectedMerchant = null },
            days = detailDays,
            error = state.merchantDetailError,
            onDays = { detailDays = it; viewModel.loadMerchantDetailFlow(merchant.uid, it) },
            onEdit = { selectedMerchant = null; viewModel.openMerchantEditor(merchant.uid) },
            onResetKey = { selectedMerchant = null; viewModel.offerBatch(com.futoonpay.admin.data.merchantKeyRequest(listOf(merchant))) },
        )
    }
    if (state.merchantEditorUid.isNotBlank()) MerchantProfileDialog(state, viewModel::closeMerchantEditor, viewModel::editMerchantField, viewModel::saveMerchantEditor, { viewModel.openMerchantEditor(state.merchantEditorUid) })
}

internal data class MerchantAction(val label: String, val action: String, val merchant: MerchantSummary)

@Composable
private fun MerchantCard(
    merchant: MerchantSummary,
    flow: MerchantFlowSummary?,
    flowLoading: Boolean,
    onDetail: () -> Unit,
    onAction: (MerchantAction) -> Unit,
) {
    val context = LocalContext.current
    val navigation = com.futoonpay.admin.LocalPanelNavigation.current
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onDetail),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        border = CardDefaults.outlinedCardBorder(), shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) {
                    Text(merchant.username.take(1).ifBlank { "商" }, color = Green, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(merchant.username.ifBlank { "商户 ${merchant.uid}" }, fontWeight = FontWeight.SemiBold)
                    Text("UID ${merchant.uid} · ${merchant.groupName}", color = Muted, fontSize = 12.sp)
                }
                StatusPill(merchantAccountStatusLabel(merchant.status), if (merchant.status == 1) GreenDark else Danger)
                Box {
                    IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.MoreVert, "商户操作") }
                    DropdownMenu(expanded, { expanded = false }) {
                        listOf(
                            MerchantAction(if (merchant.status == 1) "封禁账号" else "解封账号", "toggleAccount", merchant),
                            MerchantAction(
                                when (merchant.paymentStatus) { 2 -> "审核通过"; 1 -> "关闭支付"; else -> "开启支付" },
                                "togglePay",
                                merchant,
                            ),
                            MerchantAction(if (merchant.settlementEnabled) "关闭结算" else "开启结算", "toggleSettle", merchant),
                            MerchantAction("增加 CNY 余额", "add", merchant),
                            MerchantAction("扣除 CNY 余额", "reduce", merchant),
                            MerchantAction("修改用户组", "group", merchant),
                            MerchantAction("重置密钥", "resetKey", merchant),
                        ).forEach { action ->
                            DropdownMenuItem(text = { Text(action.label) }, onClick = { expanded = false; onAction(action) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabelValue("CNY 余额", money(merchant.balance))
                LabelValue("CNY 冻结", money(merchant.frozenBalance))
                LabelValue("支付", merchantPaymentStatusLabel(merchant.paymentStatus))
                LabelValue("结算", if (merchant.settlementEnabled) "正常" else "关闭")
            }
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))) {
                Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("近7日流水", color = Muted, fontSize = 12.sp)
                        Text(
                            when { flow != null -> money(flow.total); flowLoading -> "统计中…"; else -> "暂无统计" },
                            color = GreenDark, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    MerchantSparkline(flow?.points?.map { it.total }.orEmpty(), Modifier.fillMaxWidth().height(46.dp))
                }
            }
            HorizontalDivider(Modifier.padding(top = 11.dp), color = Line)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { TextButton(onClick = { onAction(MerchantAction("编辑商户", "edit", merchant)) }) { Text("编辑商户", fontSize = 12.sp) } }
                item { TextButton(onClick = onDetail) { Text("运营概览", fontSize = 12.sp) } }
                item {
                    TextButton(onClick = {
                        navigation.orders(merchantId = merchant.uid)
                    }) { Text("订单", fontSize = 12.sp) }
                }
                item {
                    TextButton(onClick = {
                        navigation.settlements(merchantId = merchant.uid)
                    }) { Text("结算", fontSize = 12.sp) }
                }
                item {
                    TextButton(onClick = {
                        navigation.domains(merchantId = merchant.uid)
                    }) { Text("域名", fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun MerchantMetrics(state: AppUiState) {
    val balance = state.merchants.sumOf { currencyValue(it.balance) }
    val frozen = state.merchants.sumOf { currencyValue(it.frozenBalance) }
    val flow = state.merchants.sumOf { state.merchantFlows[it.uid]?.total ?: 0.0 }
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        CompactMetric("本页 CNY 余额", money(balance), Modifier.weight(1f), GreenDark)
        CompactMetric("冻结资金", money(frozen), Modifier.weight(1f), if (frozen > 0) Danger else Ink)
        CompactMetric("7日流水", if (state.merchantFlowLoading && state.merchantFlows.isEmpty()) "统计中…" else money(flow), Modifier.weight(1f), GreenDark)
    }
}

@Composable
private fun MerchantSparkline(values: List<Double>, modifier: Modifier = Modifier) {
    if (values.isEmpty()) { Box(modifier, contentAlignment = Alignment.Center) { Text("暂无统计", color = Muted, fontSize = 12.sp) }; return }
    val points = values.map { if (it.isFinite()) it.coerceAtLeast(0.0) else 0.0 }
    val accent = GreenDark
    val fill = Green
    Canvas(modifier) {
        val max = points.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        if (points.size == 1) {
            val y = size.height - (points.single() / max * (size.height * .82f)).toFloat() - size.height * .08f
            drawCircle(accent, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width / 2, y))
            return@Canvas
        }
        val step = if (points.size <= 1) size.width else size.width / (points.size - 1)
        val line = Path()
        val area = Path()
        points.forEachIndexed { index, value ->
            val x = index * step
            val y = size.height - (value / max * (size.height * .82f)).toFloat() - size.height * .08f
            if (index == 0) {
                line.moveTo(x, y)
                area.moveTo(x, size.height)
                area.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                area.lineTo(x, y)
            }
        }
        area.lineTo(size.width, size.height)
        area.close()
        drawPath(area, fill.copy(alpha = .14f))
        drawPath(line, accent, style = Stroke(width = 2.dp.toPx()))
        points.forEachIndexed { index, value ->
            val x = index * step
            val y = size.height - (value / max * (size.height * .82f)).toFloat() - size.height * .08f
            drawCircle(accent, radius = 2.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
        }
    }
}

@Composable
internal fun MerchantDetailDialog(
    merchant: MerchantSummary,
    flow: MerchantFlowSummary?,
    loading: Boolean,
    onDismiss: () -> Unit,
    days: Int,
    error: String?,
    onDays: (Int) -> Unit,
    onEdit: (() -> Unit)? = null,
    onResetKey: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val navigation = com.futoonpay.admin.LocalPanelNavigation.current
    var currency by rememberSaveable(merchant.uid) { mutableStateOf("CNY") }
    val amounts = flow?.currencyPoints?.ifEmpty { mapOf("CNY" to flow.points) }.orEmpty()
    val points = amounts[currency].orEmpty()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            PayTopBar("商户详情", onDismiss)
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                item { SectionLabel("基本资料 · 状态") }
                item { PayCard {
                    DataRow("商户", "${merchant.username} · UID ${merchant.uid}"); DataRow("用户组", merchant.groupName)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(merchantAccountStatusLabel(merchant.status), if (merchant.status == 1) GreenDark else Danger)
                        StatusPill(merchantPaymentStatusLabel(merchant.paymentStatus), if (merchant.paymentEnabled) GreenDark else Danger)
                        StatusPill(if (merchant.settlementEnabled) "结算正常" else "结算关闭", if (merchant.settlementEnabled) GreenDark else Danger)
                    }
                } }
                item { SectionLabel("资金摘要（币种分开）") }
                item { PayCard { merchant.balances.ifEmpty { mapOf("CNY" to merchant.balance) }.forEach { (code, amount) -> DataRow("$code 余额", displayOrderAmount(amount, code)) } } }
                item { SectionLabel("经营数据 · 流水") }
                item { PayTabs(listOf("1" to "今日", "7" to "近7天", "30" to "30天"), days.toString()) { onDays(it.toInt()) } }
                item { PayTabs((listOf("CNY") + amounts.keys).distinct().map { it to it }, currency) { currency = it } }
                error?.let { item { Text(it, color = Danger); TextButton(onClick = { onDays(days) }) { Text("重试") } } }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = CardColor), border = CardDefaults.outlinedCardBorder()) {
                        Column(Modifier.padding(14.dp)) {
                            Text("${if (days == 1) "今日" else "近${days}天"}流水趋势 · $currency", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(Modifier.height(12.dp))
                            if (loading) Text("统计中…", color = Muted) else MerchantSparkline(points.map { it.total }, Modifier.fillMaxWidth().height(125.dp))
                            DataRow("合计", if (points.isEmpty()) "—" else displayOrderAmount("%.2f".format(Locale.US, points.sumOf { it.total }), currency))
                            Spacer(Modifier.height(9.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(points) { point ->
                                    Column(
                                        Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)).padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(point.date.takeLast(5), color = Muted, fontSize = 12.sp)
                                        Text("%.2f".format(Locale.US, point.total), fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    DetailBlock(
                        "资金与权限",
                        listOf(
                            "商户余额" to money(merchant.balance), "冻结资金" to money(merchant.frozenBalance),
                            "支付权限" to merchantPaymentStatusLabel(merchant.paymentStatus),
                            "结算权限" to if (merchant.settlementEnabled) "正常" else "关闭",
                        ),
                    )
                }
                item {
                    DetailBlock(
                        "商户资料",
                        listOf(
                            "结算账号" to merchant.account, "备注" to merchant.remark,
                            "Telegram" to merchant.telegramId, "邀请码" to merchant.inviteCode,
                            "推荐人" to merchant.recommenderId,
                            "分成比例" to merchant.recommenderPercent.takeIf(String::isNotBlank)?.let { "$it%" }.orEmpty(),
                            "网站域名" to merchant.website, "创建时间" to merchant.createdAt,
                        ),
                    )
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = CardColor), border = CardDefaults.outlinedCardBorder()) {
                        Column(Modifier.padding(14.dp)) {
                            Text("业务关联", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                if (onEdit != null) item { TextButton(onClick = onEdit) { Text("编辑商户") } }
                                if (onResetKey != null) item { TextButton(onClick = onResetKey) { Text("重置密钥") } }
                                item {
                                    TextButton(onClick = {
                                        navigation.orders(merchantId = merchant.uid)
                                    }) { Text("商户订单") }
                                }
                                item {
                                    TextButton(onClick = {
                                        navigation.settlements(merchantId = merchant.uid)
                                    }) { Text("结算记录") }
                                }
                                item {
                                    TextButton(onClick = {
                                        navigation.domains(merchantId = merchant.uid)
                                    }) { Text("域名审核") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowHeroMetric(label: String, value: Double, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(9.dp)).background(ButtonText.copy(alpha = .09f)).padding(9.dp)) {
        Text(label, color = ButtonText.copy(alpha = .80f), fontSize = 12.sp)
        Text(money(value), color = ButtonText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
internal fun MerchantActionDialog(
    pending: MerchantAction,
    onDismiss: () -> Unit,
    busy: Boolean = false,
    error: String? = null,
    uncertain: Boolean = false,
    onVerify: () -> Unit = {},
    onConfirm: (String, String, String) -> Unit,
) {
    var amount by rememberSaveable(pending.merchant.uid, pending.action) { mutableStateOf("") }
    var gid by rememberSaveable(pending.merchant.uid, pending.action) { mutableStateOf("") }
    var endTime by rememberSaveable(pending.merchant.uid, pending.action) { mutableStateOf("") }
    val operation = com.futoonpay.admin.data.MerchantBalanceOperation.fromAction(pending.action)
    val isBalance = operation != null
    val parsedAmount = com.futoonpay.admin.data.merchantBalanceAmount(amount)
    AlertDialog(shape = RoundedCornerShape(20.dp), 
        onDismissRequest = { if (!busy) onDismiss() }, title = { Text(pending.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("商户 UID ${pending.merchant.uid}", color = Muted)
                if (isBalance) {
                    Text("当前 CNY 余额 ${pending.merchant.balance.ifBlank { "—" }}", color = Muted)
                    OutlinedTextField(
                    amount, { amount = it }, label = { Text("金额（正数）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    enabled = !busy, isError = amount.isNotBlank() && parsedAmount == null,
                    supportingText = { Text("大于 0，最多两位小数；方向由所选操作决定") },
                )
                    parsedAmount?.let {
                        Text("本次${if (operation == com.futoonpay.admin.data.MerchantBalanceOperation.ADD) "增加" else "扣除"} ${operation!!.sign}¥${it.setScale(2).toPlainString()}",
                            color = if (operation == com.futoonpay.admin.data.MerchantBalanceOperation.ADD) GreenDark else Danger,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
                if (pending.action == "group") {
                    OutlinedTextField(gid, { gid = it }, label = { Text("用户组 GID") }, singleLine = true)
                    OutlinedTextField(endTime, { endTime = it }, label = { Text("到期时间（可留空）") }, singleLine = true)
                }
                if (!isBalance && pending.action != "group") Text("此操作会立即改变商户业务权限。")
                error?.let { Text(it, color = Danger) }
            }
        },
        confirmButton = {
            Button(shape = RoundedCornerShape(10.dp), onClick = { if (uncertain) onVerify() else onConfirm(amount, gid, endTime) }, enabled = !busy && (!isBalance || parsedAmount != null),
                colors = ButtonDefaults.buttonColors(containerColor = if (operation == com.futoonpay.admin.data.MerchantBalanceOperation.DEDUCT) Danger else Green)) {
                Text(if (busy) "提交中…" else if (uncertain) "重新读取商户" else when (operation) {
                    com.futoonpay.admin.data.MerchantBalanceOperation.ADD -> "确认增加"
                    com.futoonpay.admin.data.MerchantBalanceOperation.DEDUCT -> "确认扣除"
                    else -> "确认"
                })
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } },
    )
}

@Composable
internal fun MoreScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val navigation = com.futoonpay.admin.LocalPanelNavigation.current
    val groups = listOf(
        "运营与账务" to listOf(
            ModuleLink("资金明细", "funds", Icons.Default.Assessment),
            ModuleLink("商户统计", "merchant_stats", Icons.Default.History),
            ModuleLink("域名审核", "domain_audit", Icons.Default.AdminPanelSettings),
            ModuleLink("风控记录", "risk", Icons.Default.Security),
            ModuleLink("支付黑名单", "blacklist", Icons.Default.Block),
            ModuleLink("5 日无流水商户", "inactive", Icons.Default.Groups),
        ),
        "支付与分账" to listOf(
            ModuleLink("支付通道", "channels", Icons.Default.AddBusiness),
            ModuleLink("支付方式", "payment_types", Icons.Default.Payments),
            ModuleLink("通道轮询", "rolls", Icons.Default.SwapHoriz),
            ModuleLink("通道监测", "channel_monitor", Icons.Default.Assessment),
            ModuleLink("中转代理", "proxies", Icons.Default.AddBusiness),
            ModuleLink("分账接收方", "profit_receivers", Icons.Default.Share),
            ModuleLink("分账记录", "profit_orders", Icons.Default.ReceiptLong),
            ModuleLink("企业付款", "transfer", Icons.Default.AccountBalance),
        ),
        "系统管理" to listOf(
            ModuleLink("公告管理", "announcements", Icons.Default.Campaign),
            ModuleLink("支付与结算设置", "payment_settings", Icons.Default.Settings),
            ModuleLink("站点与账号设置", "site_settings", Icons.Default.AdminPanelSettings),
            ModuleLink("公众号与证书", "integrations", Icons.Default.AddBusiness),
            ModuleLink("登录日志", "login_logs", Icons.Default.History),
        ),
    )
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                BrandMark(42.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("FUTOON PAY", fontWeight = FontWeight.SemiBold)
                    Text("全部模块均使用原生界面", color = Muted, fontSize = 12.sp)
                }
            }
        }
        groups.forEach { (title, links) ->
            item { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)) }
            items(links) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (entry.id == "domain_audit") {
                            navigation.domains()
                        } else {
                            navigation.module(entry.id)
                        }
                    },
                    colors = CardDefaults.cardColors(containerColor = CardColor),
                    border = CardDefaults.outlinedCardBorder(), shape = RoundedCornerShape(12.dp),
                ) {
                    Row(Modifier.padding(horizontal = 15.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(entry.icon, null, tint = GreenDark, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(13.dp))
                        Text(entry.title, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted)
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(10.dp)) {
                Text("退出登录", color = Danger)
            }
        }
    }
}

private data class ModuleLink(val title: String, val id: String, val icon: ImageVector)

private val ORDER_SEARCH_COLUMNS = listOf(
    "trade_no" to "订单号", "out_trade_no" to "商户订单号", "api_trade_no" to "接口订单号",
    "name" to "商品名称", "money" to "订单金额", "realmoney" to "实付金额",
    "getmoney" to "分成金额", "domain" to "网站域名", "buyer" to "支付账号", "ip" to "支付 IP",
)

private val ORDER_PAYMENT_TYPES = listOf(
    "0" to "所有支付方式", "1" to "支付宝", "2" to "微信支付", "3" to "QQ 钱包",
)

private val SETTLEMENT_TYPES = listOf(
    "0" to "全部结算方式", "1" to "支付宝", "2" to "微信支付", "3" to "QQ 钱包",
    "4" to "USDT-TRON", "5" to "USDT-Polygon", "6" to "USDT-BEP20",
)

private val MERCHANT_SEARCH_COLUMNS = listOf(
    "uid" to "商户号", "key" to "密钥", "account" to "结算账号", "username" to "备注",
    "url" to "域名", "email" to "邮箱", "recommender_id" to "邀请人商户号", "telegram_id" to "Telegram",
)

private val MERCHANT_GROUPS = listOf("" to "全部用户组", "0" to "默认用户组", "2" to "测试用户组")

private val MERCHANT_STATUSES = listOf(
    "0" to "全部用户", "pay_2" to "待审核用户", "status_1" to "用户状态正常", "status_0" to "用户状态封禁",
    "pay_1" to "支付状态正常", "pay_0" to "支付状态关闭", "settle_1" to "结算状态正常", "settle_0" to "结算状态关闭",
)

@Composable
private fun ChoicePicker(label: String, value: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, color = Muted, fontSize = 12.sp)
                Text(options.firstOrNull { it.first == value }?.second ?: value.ifBlank { "请选择" }, color = Ink)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option.second) }, onClick = { onSelect(option.first); expanded = false })
            }
        }
    }
}

@Composable
private fun SearchField(value: String, placeholder: String, onValueChange: (String) -> Unit, onSearch: () -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) }, leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = { IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "搜索") } },
        singleLine = true, shape = RoundedCornerShape(10.dp),
    )
}

@Composable
private fun StatusFilters(items: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) {
    PayTabs(items.map { it.first.toString() to it.second }, selected.toString()) { onSelect(it.toInt()) }
}

@Composable
private fun PaginationBar(page: Int, total: Int, currentSize: Int, onPage: (Int) -> Unit) {
    val hasNext = page * 30 < total && currentSize > 0
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = { onPage(page - 1) }, enabled = page > 1) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null); Text("上一页")
        }
        Text("第 $page 页", color = Muted, fontSize = 12.sp)
        OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = { onPage(page + 1) }, enabled = hasNext) {
            Text("下一页"); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun ResultHeader(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Column {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(value, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = .10f)).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { Text(text, color = Muted) }
}

@Composable
private fun orderStatusColor(status: Int) = when (status) { 1 -> GreenDark; 2, 3 -> Danger; else -> Warning }
@Composable
private fun settlementStatusColor(status: Int) = when (status) { 1 -> GreenDark; 3 -> Danger; else -> Warning }
private fun maskAccount(value: String) = if (value.length <= 12) value else value.take(5) + "••••" + value.takeLast(5)
private fun currencyValue(value: String): Double = value.replace(",", "").replace(Regex("[^0-9.-]"), "").toDoubleOrNull() ?: 0.0
private fun money(value: Double): String = "¥" + String.format(Locale.US, "%.2f", value)
private fun money(value: String): String = value.takeIf(String::isNotBlank)?.let { money(currencyValue(it)) } ?: "—"
private fun formatRate(value: Double): String = String.format(Locale.US, "%.1f%%", value)
@Composable
private fun rateColor(value: Double?): Color = when {
    value == null -> Muted
    value < 70.0 -> Danger
    value < 90.0 -> Warning
    else -> GreenDark
}
private fun String.normalizedId(): String = Regex("\\d+").find(this)?.value?.toLongOrNull()?.toString().orEmpty()
