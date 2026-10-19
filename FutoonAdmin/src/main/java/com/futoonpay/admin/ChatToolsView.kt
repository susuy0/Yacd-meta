package com.futoonpay.admin

import android.content.Context
import android.widget.FrameLayout
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.*
import com.futoonpay.admin.ui.*
import com.futoonpay.admin.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface ChatToolsBridge {
    @Throws(Exception::class) fun load(): String
    @Throws(Exception::class) fun save(json: String)
    fun selectReply(text: String)
    fun manage()
    fun originalSendOptions()
    fun close()
}

/** Optional host navigation without changing the existing bridge contract or local JSON. */
interface ChatToolsNavigationBridge : ChatToolsBridge {
    fun accountLabel(): String
    fun selectedTab(): String
    fun selectedCategory(): String
    fun rememberSelection(tab: String, category: String)
    fun manageAt(tab: String, category: String)
}

/** Telegram fragment/sheet owns this view explicitly, just like the native business panel. */
class ChatToolsView @JvmOverloads constructor(context: Context, private val bridge: ChatToolsBridge, picker: Boolean, initialPage: String = "home") : FrameLayout(context), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, OnBackPressedDispatcherOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    private val compose = ComposeView(context)
    private val composition = PanelComposition(compose, registry)
    private var closed = false
    private var panelHeight = 0
    private var palette by mutableStateOf(FutoonPalette())
    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry get() = controller.savedStateRegistry
    override val onBackPressedDispatcher = OnBackPressedDispatcher { bridge.close() }
    init {
        controller.performAttach(); controller.performRestore(null); registry.currentState = Lifecycle.State.CREATED
        setViewTreeLifecycleOwner(this); setViewTreeViewModelStoreOwner(this); setViewTreeSavedStateRegistryOwner(this)
        compose.id = android.view.View.generateViewId()
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        compose.setContent { CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides this) { FutoonTheme(palette) {
            Box(if (picker) Modifier.fillMaxSize().background(Mist) else Modifier.fillMaxSize().background(Mist).statusBarsPadding().navigationBarsPadding()) { PanelOverlayHost { ChatToolsScreen(bridge, picker, initialPage) } }
        } } }
        addView(compose, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!closed) composition.prepare()
    }
    fun setThemeColors(json: String) { if (!closed) { palette = FutoonPalette.parse(json); setBackgroundColor(palette.surface.toArgb()) } }
    fun onShown() { if (!closed) registry.currentState = Lifecycle.State.RESUMED }
    fun handleBack(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
    fun setPanelHeight(pixels: Int) { panelHeight = pixels.coerceAtLeast(0); requestLayout() }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (!closed) composition.prepare()
        val bounded = if (panelHeight > 0) MeasureSpec.makeMeasureSpec(if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) panelHeight else minOf(panelHeight, MeasureSpec.getSize(heightMeasureSpec)), MeasureSpec.EXACTLY) else heightMeasureSpec
        super.onMeasure(widthMeasureSpec, bounded)
    }
    fun onHidden() { if (!closed) registry.currentState = Lifecycle.State.CREATED }
    fun close() { if (closed) return; closed = true; registry.currentState = Lifecycle.State.DESTROYED; viewModelStore.clear(); composition.close() }
}

internal const val QUICK_REPLY_LIMIT = 4096
private data class ChatEditor(val kind: String, val id: String = "", val first: String = "", val second: String = "")

@Composable internal fun ChatToolsScreen(bridge: ChatToolsBridge, picker: Boolean, initialPage: String = "home") {
    val navigation = bridge as? ChatToolsNavigationBridge
    var config by remember { mutableStateOf<ChatToolsConfig?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var page by rememberSaveable { mutableStateOf(initialPage) }
    var tabId by rememberSaveable { mutableStateOf(navigation?.selectedTab().orEmpty()) }
    var categoryId by rememberSaveable { mutableStateOf(navigation?.selectedCategory().orEmpty()) }
    var editor by remember { mutableStateOf<ChatEditor?>(null) }
    var deletion by remember { mutableStateOf<Pair<String, ChatToolsConfig>?>(null) }
    var preview by remember { mutableStateOf<QuickReply?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(refresh) {
        loading = true; error = null
        try {
            val loaded = withContext(Dispatchers.IO) { ChatToolsConfig.parse(bridge.load()) }
            config = loaded
            if (!picker && initialPage == "replies" && refresh == 0 && tabId.isNotBlank()) {
                page = if (categoryId.isNotBlank() && loaded.tabs.any { t -> t.id == tabId && t.categories.any { it.id == categoryId } }) "replyList" else "categories"
            }
        } catch (e: Exception) { error = e.message ?: "无法读取本机设置，已有内容没有被改动" }
        finally { loading = false }
    }
    fun save(next: ChatToolsConfig, done: () -> Unit = {}) {
        if (saving) return
        saving = true; error = null
        scope.launch {
            try { withContext(Dispatchers.IO) { bridge.save(next.json()) }; config = next; notice = "已保存"; done() }
            catch (e: Exception) { error = e.message ?: "保存失败，请重试" }
            finally { saving = false }
        }
    }
    val data = config
    val tab = data?.tabs?.find { it.id == tabId } ?: if (picker) data?.tabs?.firstOrNull() else null
    val category = tab?.categories?.find { it.id == categoryId } ?: if (picker) tab?.categories?.firstOrNull() else null
    LaunchedEffect(tab?.id, category?.id, data) {
        if (picker && data != null) {
            if ((tabId.isNotBlank() && tabId != tab?.id) || (categoryId.isNotBlank() && categoryId != category?.id)) notice = "原分类已删除，已切换到可用分类"
            tabId = tab?.id.orEmpty(); categoryId = category?.id.orEmpty()
        }
        navigation?.rememberSelection(tab?.id.orEmpty(), category?.id.orEmpty())
    }
    fun manage() { if (navigation != null) navigation.manageAt(tab?.id.orEmpty(), category?.id.orEmpty()) else bridge.manage() }
    fun back() {
        when {
            preview != null -> preview = null
            page == "replyList" -> page = "categories"
            page == "categories" -> page = "replies"
            page == "replies" && initialPage == "replies" -> bridge.close()
            page != "home" -> page = "home"
            else -> bridge.close()
        }
    }
    BackHandler(enabled = !picker || preview != null) { back() }
    val currentPreview = preview
    if (picker) {
        if (currentPreview != null) {
            Column(Modifier.fillMaxSize().background(CardColor)) {
                PayTopBar("文案预览", { preview = null }) { IconButton(onClick = bridge::close) { Icon(Icons.Outlined.Close, "关闭一键回复") } }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(currentPreview.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("${tab?.title.orEmpty()} › ${category?.title.orEmpty()} · ${currentPreview.body.length} 字", fontSize = 12.sp, color = Muted)
                    Text(currentPreview.body, fontSize = 16.sp, lineHeight = 24.sp)
                    if (currentPreview.body.length > QUICK_REPLY_LIMIT) Text("文案超过 $QUICK_REPLY_LIMIT 字，请在管理中修改后再填入。", color = Danger)
                }
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(shape = RoundedCornerShape(10.dp), onClick = { preview = null }, modifier = Modifier.weight(1f)) { Text("返回列表") }
                    Button(shape = RoundedCornerShape(10.dp), enabled = currentPreview.body.length <= QUICK_REPLY_LIMIT, onClick = { bridge.selectReply(currentPreview.body) }, modifier = Modifier.weight(1f)) { Text("填入输入框") }
                }
                TextButton(shape = RoundedCornerShape(10.dp), onClick = bridge::originalSendOptions, modifier = Modifier.fillMaxWidth()) { Text("原发送选项") }
            }
        } else BoxWithConstraints(Modifier.fillMaxSize().background(CardColor)) {
            val compact = maxHeight < 400.dp
            Row(Modifier.fillMaxSize()) {
                if (compact) Column(Modifier.width(104.dp).fillMaxHeight().background(Mist)) {
                    Text("一键回复", Modifier.padding(12.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    LazyColumn(Modifier.weight(1f)) { items(data?.tabs.orEmpty(), key = { it.id }) { item -> TextButton(shape = RoundedCornerShape(10.dp), onClick = { tabId = item.id; categoryId = "" }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(item.title, color = if (tab?.id == item.id) GreenDark else Muted) } } }
                    TextButton(shape = RoundedCornerShape(10.dp), onClick = ::manage) { Text("管理") }
                    TextButton(shape = RoundedCornerShape(10.dp), onClick = bridge::originalSendOptions) { Text("原发送选项", fontSize = 12.sp) }
                    TextButton(shape = RoundedCornerShape(10.dp), onClick = bridge::close) { Text("关闭") }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (!compact) PayTopBar("一键回复") {
                        TextButton(shape = RoundedCornerShape(10.dp), onClick = ::manage) { Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("管理") }
                        IconButton(onClick = bridge::close, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Close, "关闭一键回复") }
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (!compact && data?.tabs?.isNotEmpty() == true) PayTabs(data.tabs.map { it.id to it.title }, tab?.id.orEmpty()) { tabId = it; categoryId = "" }
                    if (tab?.categories?.isNotEmpty() == true) Box(Modifier.padding(horizontal = 12.dp)) { PayPills(tab.categories.map { it.id to it.title }, category?.id.orEmpty()) { categoryId = it } }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("chat-tools-list")) {
                        error?.let { message -> item { Column(Modifier.padding(16.dp)) { Text(message, color = Danger); TextButton(shape = RoundedCornerShape(10.dp), onClick = { refresh++ }) { Text("重新读取设置") } } } }
                        notice?.let { item { Text(it, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) } }
                        if (!loading && data != null && category?.replies.isNullOrEmpty()) item {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(when { tab == null -> "还没有一键回复"; category == null -> "当前 Tab 暂无分类"; else -> "当前分类暂无文案" })
                                TextButton(shape = RoundedCornerShape(10.dp), onClick = ::manage) { Text(when { tab == null -> "去添加 Tab"; category == null -> "去添加分类"; else -> "去添加文案" }) }
                            }
                        }
                        items(category?.replies.orEmpty(), key = { it.id }) { reply ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 80.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).clickable {
                                    if (reply.body.length > QUICK_REPLY_LIMIT) preview = reply else bridge.selectReply(reply.body)
                                }.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
                                    Text(reply.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(reply.body, color = Muted, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                }
                                IconButton(onClick = { preview = reply }, modifier = Modifier.width(56.dp).heightIn(min = 72.dp)) { Icon(Icons.Outlined.Visibility, "预览「${reply.title}」", tint = GreenDark) }
                            }
                            HorizontalDivider(Modifier.padding(start = 16.dp), color = Line)
                        }
                    }
                    if (!compact) Column(Modifier.fillMaxWidth().background(Mist).padding(horizontal = 12.dp)) {
                        TextButton(shape = RoundedCornerShape(10.dp), onClick = bridge::originalSendOptions, modifier = Modifier.fillMaxWidth()) { Text("原发送选项") }
                        Text("点选只填入输入框，可修改后手动发送", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp).align(Alignment.CenterHorizontally))
                    }
                }
            }
        }
    } else Column(Modifier.fillMaxSize().background(Mist)) {
        PayTopBar(when (page) { "tags" -> "会话 Tag"; "replies", "categories", "replyList" -> "一键回复"; "opening" -> "开户文案"; else -> "FutoonPay 设置" }, ::back)
        if (loading || saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (editor == null && deletion == null) error?.let { Text(it, color = Danger, modifier = Modifier.padding(12.dp), fontSize = 13.sp); TextButton(shape = RoundedCornerShape(10.dp), onClick = { refresh++ }) { Text("重新读取设置") } }
        if (editor == null) notice?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = Muted, fontSize = 12.sp) }
        if (page in setOf("categories", "replyList")) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            TextButton(shape = RoundedCornerShape(10.dp), onClick = { page = "replies" }) { Text("全部 Tab") }
            Text("›", Modifier.align(Alignment.CenterVertically), color = Muted)
            TextButton(shape = RoundedCornerShape(10.dp), onClick = { page = "categories" }, modifier = Modifier.weight(1f)) { Text(tab?.title ?: "Tab 已删除", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            if (page == "replyList") Text(category?.title.orEmpty(), Modifier.weight(1f).align(Alignment.CenterVertically), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("chat-tools-list"), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (page == "home") {
                item { SectionLabel("本机自定义配置") }
                item { PayCard {
                    Text(navigation?.accountLabel()?.ifBlank { "当前 Telegram 账号" } ?: "当前 Telegram 账号", fontWeight = FontWeight.Medium)
                    Text("配置跟随当前 Telegram 账号保存在本机。", color = Muted, fontSize = 13.sp)
                } }
                item {
                    val enabled = data != null && !loading
                    Column(Modifier.background(CardColor)) {
                        ChatSettingRow("会话 Tag", if (data == null) "—" else "${data.tags.size} 个 Tag · ${data.tags.flatMap { it.telegramIds }.distinct().size} 个 Telegram ID", Icons.Outlined.Label, enabled) { page = "tags" }
                        ChatSettingRow("一键回复", if (data == null) "—" else "${data.tabs.size} 个 Tab · ${data.tabs.sumOf { it.categories.size }} 个分类 · ${data.tabs.sumOf { t -> t.categories.sumOf { it.replies.size } }} 条文案", Icons.Outlined.Quickreply, enabled) { page = "replies" }
                        ChatSettingRow("开户文案", if (data == null) "—" else "${if (data.openingTemplate == defaultOpeningTemplate) "默认模板" else "已自定义"} · ${data.openingTemplate.length} 字 · 6 个变量", Icons.Outlined.Description, enabled) { page = "opening" }
                    }
                }
                item { Text("Telegram 账号管理使用原版设置；付款后台连接、退出登录在悬浮窗「更多 › 后台设置」中操作。", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(4.dp)) }
            } else if (data != null && page == "tags") {
                item { Text("按 Telegram ID 匹配，显示在聊天列表和聊天页顶部。工单话题使用末尾完整 TGID。", color = Muted, fontSize = 13.sp) }
                item { TextButton(shape = RoundedCornerShape(10.dp), enabled = !saving, onClick = { editor = ChatEditor("tag") }) { Icon(Icons.Outlined.Add, null); Text("新增 Tag") } }
                items(data.tags, key = { it.id }) { tag ->
                    ChatManageRow(tag.text, "${tag.telegramIds.size} 个 ID · ${tag.telegramIds.joinToString(", ")}", "Tag", { editor = ChatEditor("tag", tag.id, tag.text, tag.telegramIds.joinToString(", ")) }, { deletion = "删除 Tag「${tag.text}」？" to data.copy(tags = data.tags.filterNot { it.id == tag.id }) })
                }
                if (data.tags.isEmpty()) item { Text("暂无自定义 Tag", color = Muted) }
            } else if (data != null && page == "opening") {
                item { Text("开户后填入当前会话输入框，由你校对后手动发送。D0 开启结算，D1 不开启结算。", color = Muted, fontSize = 13.sp) }
                item { Button(shape = RoundedCornerShape(10.dp), enabled = !saving, onClick = { editor = ChatEditor("opening", "opening", second = data.openingTemplate) }) { Text("编辑开户文案") } }
                item { PayCard { Text(data.openingTemplate, fontSize = 14.sp) } }
            } else if (data != null && page == "replies") {
                item { Text("选择 Tab 管理分类，再进入分类管理文案。", color = Muted, fontSize = 13.sp) }
                item { TextButton(shape = RoundedCornerShape(10.dp), enabled = !saving, onClick = { editor = ChatEditor("tab") }) { Icon(Icons.Outlined.Add, null); Text("新增 Tab") } }
                items(data.tabs, key = { it.id }) { item ->
                    ChatManageRow(item.title, "${item.categories.size} 个分类 · ${item.categories.sumOf { it.replies.size }} 条文案", "Tab", { tabId = item.id; categoryId = ""; page = "categories" }, { deletion = "删除 Tab「${item.title}」及其 ${item.categories.size} 个分类和 ${item.categories.sumOf { it.replies.size }} 条文案？" to data.copy(tabs = data.tabs.filterNot { it.id == item.id }) }, { editor = ChatEditor("tab", item.id, item.title) })
                }
                if (data.tabs.isEmpty()) item { Text("暂无 Tab，先添加一个。", color = Muted) }
            } else if (data != null && page == "categories") {
                item { TextButton(shape = RoundedCornerShape(10.dp), enabled = tab != null && !saving, onClick = { editor = ChatEditor("category") }) { Icon(Icons.Outlined.Add, null); Text("新增分类") } }
                items(tab?.categories.orEmpty(), key = { it.id }) { item ->
                    ChatManageRow(item.title, "${item.replies.size} 条文案", "分类", { categoryId = item.id; page = "replyList" }, { deletion = "删除分类「${item.title}」及其 ${item.replies.size} 条文案？" to data.copy(tabs = data.tabs.map { if (it.id == tab?.id) it.copy(categories = it.categories.filterNot { c -> c.id == item.id }) else it }) }, { editor = ChatEditor("category", item.id, item.title) })
                }
                if (tab?.categories.isNullOrEmpty()) item { Text("当前 Tab 暂无分类", color = Muted) }
            } else if (data != null && page == "replyList") {
                item { TextButton(shape = RoundedCornerShape(10.dp), enabled = category != null && !saving, onClick = { editor = ChatEditor("reply") }) { Icon(Icons.Outlined.Add, null); Text("新增文案") } }
                items(category?.replies.orEmpty(), key = { it.id }) { item ->
                    ChatManageRow(item.title, item.body, "文案", { editor = ChatEditor("reply", item.id, item.title, item.body) }, { deletion = "删除文案「${item.title}」？" to data.copy(tabs = data.tabs.map { if (it.id == tab?.id) it.copy(categories = it.categories.map { c -> if (c.id == category?.id) c.copy(replies = c.replies.filterNot { r -> r.id == item.id }) else c }) else it }) })
                }
                if (category?.replies.isNullOrEmpty()) item { Text("当前分类暂无文案", color = Muted) }
            }
        }
    }
    if (data != null) editor?.let { current ->
        ChatEditorSheet(current, saving, error, { editor = null }, { first, second ->
            val id = current.id.ifBlank { java.util.UUID.randomUUID().toString() }
            fun <T> replace(list: List<T>, key: (T) -> String, value: T) = if (current.id.isBlank()) list + value else list.map { if (key(it) == current.id) value else it }
            val next = when (current.kind) {
                "opening" -> data.copy(openingTemplate = second)
                "tag" -> data.copy(tags = replace(data.tags, { it.id }, ChatTag(id, first.trim(), ChatToolsConfig.parseIds(second))))
                "tab" -> data.copy(tabs = replace(data.tabs, { it.id }, ReplyTab(id, first.trim(), data.tabs.find { it.id == id }?.categories.orEmpty())))
                "category" -> data.copy(tabs = data.tabs.map { t -> if (t.id == tab?.id) t.copy(categories = replace(t.categories, { it.id }, ReplyCategory(id, first.trim(), t.categories.find { it.id == id }?.replies.orEmpty()))) else t })
                else -> data.copy(tabs = data.tabs.map { t -> if (t.id == tab?.id) t.copy(categories = t.categories.map { c -> if (c.id == category?.id) c.copy(replies = replace(c.replies, { it.id }, QuickReply(id, first.trim(), second))) else c }) else t })
            }
            save(next) {
                editor = null
                if (current.kind == "tab" && current.id.isBlank()) { tabId = id; page = "categories" }
                if (current.kind == "category" && current.id.isBlank()) { categoryId = id; page = "replyList" }
            }
        })
    }
    deletion?.let { (message, next) -> PaySheet("确认删除", { if (!saving) deletion = null }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message)
            Text("此操作只删除本机配置，无法撤销。", color = Muted, fontSize = 13.sp)
            error?.let { Text(it, color = Danger, fontSize = 13.sp) }
            Row { TextButton(shape = RoundedCornerShape(10.dp), enabled = !saving, onClick = { deletion = null }) { Text("取消") }; TextButton(shape = RoundedCornerShape(10.dp), enabled = !saving, onClick = { save(next) { deletion = null } }) { Text("确认删除", color = Danger) } }
        }
    } }
}

@Composable private fun ChatSettingRow(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 84.dp).clickable(enabled = enabled, onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (enabled) GreenDark else Muted, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) { Text(title, fontSize = 16.sp); Text(detail, fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 4.dp)) }
        Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
    }
    HorizontalDivider(Modifier.padding(start = 56.dp), color = Line)
}

@Composable private fun ChatManageRow(title: String, summary: String, kind: String, onClick: () -> Unit, onDelete: () -> Unit, onEdit: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    Surface(color = CardColor) {
        Row(Modifier.fillMaxWidth().heightIn(min = 80.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick = onClick).padding(14.dp)) { Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium); Text(summary, color = Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.MoreVert, "${title}更多操作") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("编辑 $kind") }, onClick = { menu = false; (onEdit ?: onClick)() })
                    DropdownMenuItem(text = { Text("删除 $kind", color = Danger) }, onClick = { menu = false; onDelete() })
                }
            }
            if (onEdit != null) IconButton(onClick = onClick) { Icon(Icons.Outlined.ChevronRight, "进入$title") }
        }
    }
}

@Composable private fun ChatEditorSheet(current: ChatEditor, saving: Boolean, error: String?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var first by remember(current) { mutableStateOf(current.first) }
    var second by remember(current) { mutableStateOf(TextFieldValue(current.second, TextRange(current.second.length))) }
    var inputError by remember(current) { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val variableColor = GreenDark
    val variableBackground = GreenTint
    val errorColor = Danger
    val templateTransformation = VisualTransformation { text ->
        val spans = AnnotatedString.Builder(text.text)
        openingVariables(text.text).forEach { variable -> spans.addStyle(SpanStyle(color = if (variable.known) variableColor else errorColor, background = if (variable.known) variableBackground else errorColor.copy(alpha = .10f)), variable.start, variable.end) }
        TransformedText(spans.toAnnotatedString(), OffsetMapping.Identity)
    }
    val dirty = first != current.first || second.text != current.second
    fun close() { if (!saving) { if (dirty) discard = true else onDismiss() } }
    val kind = when (current.kind) { "tag" -> "Tag"; "tab" -> "Tab"; "category" -> "分类"; "opening" -> "开户文案"; else -> "文案" }
    val validation = runCatching {
        if (current.kind != "opening") { require(first.isNotBlank()) { "请填写名称" }; require(first.length <= 64) { "名称最多 64 个字符" } }
        when (current.kind) {
            "tag" -> { require(second.text.length <= 2000) { "Telegram ID 输入最多 2000 个字符" }; ChatToolsConfig.parseIds(second.text) }
            "reply" -> { require(second.text.isNotBlank()) { "请填写回复内容" }; require(second.text.length <= QUICK_REPLY_LIMIT) { "回复内容最多 $QUICK_REPLY_LIMIT 个字符" } }
            "opening" -> validateOpeningTemplate(second.text)
        }
    }.exceptionOrNull()?.message
    PaySheet((if (current.id.isBlank()) "新增" else "编辑") + kind, ::close) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("chat-tools-editor"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(shape = RoundedCornerShape(10.dp), enabled = !saving, onClick = ::close) { Text("取消") }
                Spacer(Modifier.weight(1f))
                Button(shape = RoundedCornerShape(10.dp), enabled = !saving && dirty && (current.kind != "opening" || validation == null), onClick = {
                    if (validation != null) inputError = validation else try { onSave(first, second.text) } catch (e: IllegalArgumentException) { inputError = e.message }
                }) { Text(if (saving) "保存中…" else "保存") }
            }
            if (current.kind in setOf("reply", "opening")) PayPills(listOf("edit" to "编辑", "preview" to "预览"), if (preview) "preview" else "edit") { preview = it == "preview" }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!preview) {
                    if (current.kind != "opening") OutlinedTextField(first, { first = it; inputError = null }, label = { Text(if (current.kind == "tag") "Tag 内容" else "名称") }, singleLine = true, isError = first.length > 64, supportingText = { Text("${first.length} / 64") }, modifier = Modifier.fillMaxWidth(), enabled = !saving)
                    if (current.kind in setOf("tag", "reply", "opening")) OutlinedTextField(second, { second = it; inputError = null }, label = { Text(when (current.kind) { "tag" -> "生效 Telegram ID（多个用逗号分隔）"; "opening" -> "开户文案模板"; else -> "回复内容" }) }, modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus), visualTransformation = if (current.kind == "opening") templateTransformation else VisualTransformation.None, minLines = if (current.kind == "opening") 8 else if (current.kind == "reply") 5 else 2, isError = validation != null && second.text.isNotEmpty(), supportingText = { Text("${second.text.length} / ${if (current.kind == "tag") 2000 else QUICK_REPLY_LIMIT}") }, enabled = !saving)
                } else PayCard {
                    if (current.kind == "reply") { Text(first, fontWeight = FontWeight.SemiBold); Text(second.text, fontSize = 16.sp) }
                    else {
                        Text("合成数据预览，不会开户或发送消息", color = Muted, fontSize = 12.sp)
                        val rendered = runCatching { openingMessage(second.text, "2806", "SAMPLE_KEY_FOR_PREVIEW_ONLY", mapOf("url" to "example.com", "username" to "@example", "settle" to "0"), "https://t.me/example_bot?start=sample_bind") }
                        Text(rendered.getOrElse { it.message ?: "模板不合法" }, fontSize = 14.sp)
                    }
                }
                if (current.kind == "tag") {
                    val ids = runCatching { ChatToolsConfig.parseIds(second.text) }.getOrDefault(emptyList())
                    if (ids.isNotEmpty()) PayCard {
                        Text("预览 · 合成会话", color = Muted, fontSize = 12.sp)
                        Text("示例联系人  [${first.ifBlank { "Tag" }}]", fontWeight = FontWeight.Medium)
                        Text("聊天列表与会话顶部都显示 · ${ids.size} 个唯一 ID", color = Muted, fontSize = 13.sp)
                        Text(ids.joinToString(" · "), fontSize = 13.sp)
                    }
                }
                if (current.kind == "opening") {
                    if (!preview) openingVariables(second.text).filterNot { it.known }.forEach { variable -> TextButton(shape = RoundedCornerShape(10.dp), onClick = { second = second.copy(selection = TextRange(variable.start, variable.end)); fieldFocus.requestFocus() }) { Text("定位 {${variable.name}}", color = Danger) } }
                    TextButton(shape = RoundedCornerShape(10.dp), enabled = !saving && !preview, onClick = { second = TextFieldValue(defaultOpeningTemplate, TextRange(defaultOpeningTemplate.length)) }) { Text("恢复默认模板") }
                    if (second.text.length > QUICK_REPLY_LIMIT - 300) Text("建议给变量展开留出 300 字以上余量；超长时不会截断密钥或链接。", color = Warning, fontSize = 13.sp)
                }
                (inputError ?: error ?: if (second.text.isNotEmpty() || current.kind == "opening") validation else null)?.let { Text(it, color = Danger, fontSize = 13.sp) }
                Spacer(Modifier.height(12.dp))
            }
            if (current.kind == "opening" && !preview) {
                Text("点选变量插入光标处", color = Muted, fontSize = 12.sp)
                PayPills(openingPlaceholders.map { it to "{$it}" }, "") { variable ->
                    val start = minOf(second.selection.start, second.selection.end)
                    val end = maxOf(second.selection.start, second.selection.end)
                    val insert = "{$variable}"
                    second = TextFieldValue(second.text.replaceRange(start, end, insert), TextRange(start + insert.length))
                }
            }
        }
    }
    if (discard) PaySheet("放弃修改？", { discard = false }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("未保存的内容将被丢弃。")
            Row { TextButton(shape = RoundedCornerShape(10.dp), onClick = { discard = false }) { Text("继续编辑") }; TextButton(shape = RoundedCornerShape(10.dp), onClick = { discard = false; onDismiss() }) { Text("放弃修改", color = Danger) } }
        }
    }
}
