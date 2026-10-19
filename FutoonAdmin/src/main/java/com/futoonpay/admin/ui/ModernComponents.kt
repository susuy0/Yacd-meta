package com.futoonpay.admin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.futoonpay.admin.ui.theme.*

internal class PayPanelChrome(val showBack: Boolean, val actions: @Composable RowScope.() -> Unit, val context: @Composable () -> Unit)
internal val LocalPayPanelChrome = staticCompositionLocalOf<PayPanelChrome?> { null }

@Composable
internal fun PayCard(modifier: Modifier = Modifier, compact: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = CardColor,
        border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 10.dp), content = content)
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
}

@Composable
internal fun PayBadge(text: String, color: Color = GreenDark) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = .09f)) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
internal fun PayTabs(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val scroll = rememberLazyListState()
    LaunchedEffect(selected) {
        val index = options.indexOfFirst { it.first == selected }
        if (index >= 0) scroll.animateScrollToItem(index)
    }
    LazyRow(state = scroll, modifier = Modifier.fillMaxWidth().height((48f * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)).dp).background(CardColor), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        items(options, key = { it.first }) { (value, label) ->
            val active = selected == value
            Box(modifier = Modifier.fillMaxHeight().clickable { onSelect(value) }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    Text(label, color = if (active) GreenDark else Muted, fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                    if (active) Box(Modifier.matchParentSize(), contentAlignment = Alignment.BottomCenter) { Box(Modifier.fillMaxWidth().height(3.dp).background(GreenDark, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))) }
            }
        }
    }
}

@Composable
internal fun PayPills(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val scroll = rememberLazyListState()
    LaunchedEffect(selected) {
        val index = options.indexOfFirst { it.first == selected }
        if (index >= 0) scroll.scrollToItem(index)
    }
    LazyRow(state = scroll, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        items(options, key = { it.first }) { (value, label) ->
            Box(Modifier.heightIn(min = 48.dp).clickable { onSelect(value) }, contentAlignment = Alignment.Center) {
                Surface(color = if (value == selected) GreenDark else CardColor, shape = RoundedCornerShape(16.dp), border = if (value == selected) null else BorderStroke(1.dp, Line)) {
                    Text(label, color = if (value == selected) ButtonText else Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PayTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val chrome = LocalPayPanelChrome.current
    val visibleBack = onBack.takeIf { chrome == null || chrome.showBack }
    Column(Modifier.fillMaxWidth().background(CardColor)) {
        Row(Modifier.fillMaxWidth().heightIn(min = if (chrome == null) 56.dp else 48.dp).padding(start = if (visibleBack == null) 16.dp else 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            visibleBack?.let { IconButton(onClick = it, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Ink, modifier = Modifier.size(24.dp)) } }
            Text(title, fontSize = if (chrome == null) 18.sp else 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            actions()
            chrome?.actions?.invoke(this)
        }
        HorizontalDivider(color = Line)
        chrome?.context?.invoke()
    }
}

@Composable
internal fun PayBottomNav(selected: MainTab?, onSelect: (MainTab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(CardColor)) {
        HorizontalDivider(color = Line)
        Row(Modifier.fillMaxWidth().height(64.dp)) {
            MainTab.entries.forEach { tab ->
                val color = if (selected == tab) GreenDark else Muted
                val icon = when (tab) {
                    MainTab.DASHBOARD -> androidx.compose.material.icons.Icons.Outlined.BarChart
                    MainTab.ORDERS -> androidx.compose.material.icons.Icons.Outlined.ReceiptLong
                    MainTab.SETTLEMENTS -> androidx.compose.material.icons.Icons.Outlined.AccountBalanceWallet
                    MainTab.MERCHANTS -> androidx.compose.material.icons.Icons.Outlined.Storefront
                    MainTab.MORE -> androidx.compose.material.icons.Icons.Outlined.GridView
                }
                Column(Modifier.weight(1f).fillMaxHeight().clickable { onSelect(tab) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                    Icon(icon, tab.title, Modifier.size(24.dp), tint = color)
                    Text(tab.title, color = color, fontSize = 12.sp, fontWeight = if (selected == tab) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
internal fun AppliedChips(chips: List<Pair<String, String>>, onRemove: (String) -> Unit, onReset: () -> Unit) {
    if (chips.isNotEmpty()) LazyRow(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chips, key = { it.first }) { (key, label) ->
            Box(Modifier.heightIn(min = 48.dp).clickable { onRemove(key) }, contentAlignment = Alignment.Center) {
                Surface(color = GreenTint, shape = RoundedCornerShape(15.dp)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(label, color = GreenDark, fontSize = 12.sp)
                        Icon(Icons.Default.Close, "移除 $label", tint = GreenDark, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        item { TextButton(onClick = onReset) { Text("重置", fontSize = 13.sp) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaySheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    com.futoonpay.admin.PanelDialog(onDismiss) {
        Surface(Modifier.fillMaxSize(), color = Mist, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭") }
                }
                Column(Modifier.fillMaxWidth().weight(1f).imePadding(), content = content)
            }
        }
    }
}

@Composable
internal fun ListFeedback(loading: Boolean, error: String?, empty: Boolean, onRetry: () -> Unit) {
    if (loading || error != null || empty) PayCard {
        Column(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                loading -> { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp); Text("正在加载…", color = Muted) }
                error != null -> { Text(if (error.contains("权限")) "没有查看权限" else "加载失败", fontWeight = FontWeight.SemiBold); Text(error, color = Muted, fontSize = 13.sp); OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = onRetry) { Text("重试") } }
                else -> { Text("没有找到记录", fontWeight = FontWeight.SemiBold); Text("调整筛选条件后再试", color = Muted, fontSize = 13.sp) }
            }
        }
    }
}
