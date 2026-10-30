package com.futoonpay.admin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.futoonpay.admin.ui.theme.*

internal class PayPanelChrome(val showBack: Boolean, val actions: @Composable RowScope.() -> Unit, val context: @Composable () -> Unit)
internal val LocalPayPanelChrome = staticCompositionLocalOf<PayPanelChrome?> { null }

@Composable
internal fun PayCard(modifier: Modifier = Modifier, compact: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = CardColor) {
        Column(Modifier.padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 10.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 10.dp), content = content)
    }
}

/** A ledger record has one flat surface and separator, rather than another nested card. */
@Composable
internal fun LedgerRecord(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(CardColor)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp), content = content)
        HorizontalDivider(color = Line, thickness = .5.dp)
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
internal fun PayBadge(text: String, color: Color = GreenDark) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = .09f)) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
internal fun FinanceOptionPicker(label: String, selected: String, options: List<Pair<String, String>>, enabled: Boolean = true, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ChatOutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, color = Muted, fontSize = 12.sp)
                Text(options.firstOrNull { it.first == selected }?.second ?: selected, color = Ink, fontSize = 14.sp)
            }
            Icon(Icons.Default.ExpandMore, null, tint = Muted, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (value, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { expanded = false; onSelect(value) }) }
        }
    }
}

@Composable
internal fun FinanceSearchBar(value: String, placeholder: String, onValueChange: (String) -> Unit, onSearch: () -> Unit, onFilters: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value, onValueChange, modifier = Modifier.weight(1f), singleLine = true,
            placeholder = { Text(placeholder, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            shape = RoundedCornerShape(24.dp), colors = ChatFieldColors())
        IconButton(onClick = onFilters, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.FilterList, "完整搜索", tint = Ink) }
    }
}

/** Page-level navigation is distinct from both filter chips and the bottom destinations. */
@Composable
internal fun PayTabs(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    ChatFilterTabs(options, selected, onSelect = onSelect)
}

@Composable
internal fun PayPills(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    ChatFilterTabs(options, selected, onSelect = onSelect)
}

@Composable
internal fun ChatFilterTabs(options: List<Pair<String, String>>, selected: String, enabled: Boolean = true, onSelect: (String) -> Unit) {
    val scroll = rememberLazyListState()
    LaunchedEffect(selected, options) {
        val index = options.indexOfFirst { it.first == selected }
        if (index >= 0) scroll.animateScrollToItem(index)
    }
    LazyRow(state = scroll, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.fillMaxWidth().testTag("chat-filter-list").selectableGroup().background(CardColor), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        items(options, key = { it.first }) { (value, label) ->
            val active = selected == value
            Box(Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp, max = 240.dp).testTag("chat-filter-$value").selectable(active, enabled = enabled, role = Role.Tab, onClick = { onSelect(value) }), contentAlignment = Alignment.Center) {
                    Text(label, color = if (active) GreenDark else Muted, fontSize = 14.sp, lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp))
                    if (active) Box(Modifier.align(Alignment.BottomCenter).width(32.dp).height(2.dp).background(GreenDark))
            }
        }
    }
}

internal data class ChatNavItem(val id: String, val title: String, val icon: ImageVector, val selectedIcon: ImageVector = icon)

/** Used by every business navigation bar, including the embedded panel's configurable tabs. */
@Composable
internal fun ChatNavigationBar(items: List<ChatNavItem>, selected: String?, onSelect: (String) -> Unit) {
    val scale = LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
    Column(Modifier.fillMaxWidth().background(CardColor)) {
        HorizontalDivider(color = Line, thickness = .5.dp)
        Row(Modifier.fillMaxWidth().heightIn(min = (com.futoonpay.admin.appearance.ChatLook.NAV_HEIGHT * scale).dp).selectableGroup()) {
            items.forEach { item ->
                val active = selected == item.id
                Box(Modifier.weight(1f).heightIn(min = (com.futoonpay.admin.appearance.ChatLook.NAV_HEIGHT * scale).dp).testTag("chat-nav-${item.id}")
                    .selectable(active, role = Role.Tab, onClick = { onSelect(item.id) }), contentAlignment = Alignment.Center) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                    Box(Modifier.widthIn(max = 64.dp).fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
                        Icon(if (active) item.selectedIcon else item.icon, null, Modifier.size(22.dp), tint = if (active) GreenDark else Muted)
                    }
                    Text(item.title, color = if (active) GreenDark else Muted, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (active) Box(Modifier.align(Alignment.BottomCenter).width(24.dp).height(2.dp).background(GreenDark))
                }
            }
        }
    }
}

@Composable internal fun ChatFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = CardColor, unfocusedContainerColor = CardColor,
    disabledContainerColor = FieldColor, focusedBorderColor = Green,
    unfocusedBorderColor = Line, disabledBorderColor = Line,
    focusedTextColor = Ink, unfocusedTextColor = Ink, cursorColor = Green,
    focusedLabelColor = GreenDark, unfocusedLabelColor = Muted)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PayTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val chrome = LocalPayPanelChrome.current
    val visibleBack = onBack.takeIf { chrome == null || chrome.showBack }
    Column(Modifier.fillMaxWidth().background(HeaderSurface)) {
        CompositionLocalProvider(LocalContentColor provides HeaderInk) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = if (visibleBack == null) 16.dp else 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            visibleBack?.let { IconButton(onClick = it, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = HeaderInk, modifier = Modifier.size(24.dp)) } }
            Text(title, fontSize = if (chrome == null) 20.sp else 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), color = HeaderInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            actions()
            chrome?.actions?.invoke(this)
        }
        }
        chrome?.context?.invoke()
    }
}

@Composable
internal fun PayBottomNav(selected: MainTab?, onSelect: (MainTab) -> Unit) {
    val items = MainTab.entries.map { tab -> ChatNavItem(tab.name, tab.title, when(tab) {
        MainTab.DASHBOARD -> Icons.Outlined.BarChart
        MainTab.ORDERS -> Icons.Outlined.ReceiptLong
        MainTab.SETTLEMENTS -> Icons.Outlined.AccountBalanceWallet
        MainTab.MERCHANTS -> Icons.Outlined.Storefront
        MainTab.MORE -> Icons.Outlined.MoreHoriz
    }) }
    ChatNavigationBar(items, selected?.name) { id -> MainTab.entries.find { it.name == id }?.let(onSelect) }
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
        item { com.futoonpay.admin.ui.ChatTextButton(onClick = onReset) { Text("重置", fontSize = 13.sp) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaySheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    com.futoonpay.admin.PanelDialog(onDismiss) {
        Surface(Modifier.fillMaxSize(), color = CardColor, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().background(HeaderSurface).heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), color = HeaderInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭", tint = HeaderInk) }
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
                error != null -> { Text(if (error.contains("权限")) "没有查看权限" else "加载失败", fontWeight = FontWeight.SemiBold); Text(error, color = Muted, fontSize = 13.sp); com.futoonpay.admin.ui.ChatOutlinedButton(shape = RoundedCornerShape(8.dp), onClick = onRetry) { Text("重试") } }
                else -> { Text("没有找到记录", fontWeight = FontWeight.SemiBold); Text("调整筛选条件后再试", color = Muted, fontSize = 13.sp) }
            }
        }
    }
}
