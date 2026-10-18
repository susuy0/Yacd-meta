package com.futoonpay.admin

import android.content.Context
import android.widget.FrameLayout
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

/** Telegram fragment/sheet owns this view explicitly, just like the native business panel. */
class ChatToolsView @JvmOverloads constructor(context: Context, private val bridge: ChatToolsBridge, picker: Boolean, initialPage: String = "tags") : FrameLayout(context), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, OnBackPressedDispatcherOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    private val compose = ComposeView(context)
    private val composition = PanelComposition(compose, registry)
    private var closed = false
    private var panelHeight = 0
    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry get() = controller.savedStateRegistry
    override val onBackPressedDispatcher = OnBackPressedDispatcher { bridge.close() }
    init {
        controller.performAttach(); controller.performRestore(null); registry.currentState = Lifecycle.State.CREATED
        setViewTreeLifecycleOwner(this); setViewTreeViewModelStoreOwner(this); setViewTreeSavedStateRegistryOwner(this)
        compose.id = android.view.View.generateViewId()
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        compose.setContent { CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides this) { FutoonTheme {
            Box(if (picker) Modifier.fillMaxSize().background(Mist) else Modifier.fillMaxSize().background(Mist).statusBarsPadding().navigationBarsPadding()) { PanelOverlayHost { ChatToolsScreen(bridge, picker, initialPage) } }
        } } }
        addView(compose, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!closed) composition.attach()
    }
    fun onShown() { if (!closed) registry.currentState = Lifecycle.State.RESUMED }
    fun handleBack(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
    fun setPanelHeight(pixels: Int) { panelHeight = pixels.coerceAtLeast(0); requestLayout() }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val bounded = if (panelHeight > 0) MeasureSpec.makeMeasureSpec(if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) panelHeight else minOf(panelHeight, MeasureSpec.getSize(heightMeasureSpec)), MeasureSpec.EXACTLY) else heightMeasureSpec
        super.onMeasure(widthMeasureSpec, bounded)
    }
    fun onHidden() { if (!closed) registry.currentState = Lifecycle.State.CREATED }
    fun close() { if (closed) return; closed = true; registry.currentState = Lifecycle.State.DESTROYED; viewModelStore.clear(); composition.close() }
}

private data class ChatEditor(val kind: String, val id: String = "", val first: String = "", val second: String = "")

@Composable internal fun ChatToolsScreen(bridge: ChatToolsBridge, picker: Boolean, initialPage: String = "tags") {
    var config by remember { mutableStateOf<ChatToolsConfig?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var page by rememberSaveable { mutableStateOf(initialPage) }
    var tabId by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf("") }
    var editor by remember { mutableStateOf<ChatEditor?>(null) }
    var deletion by remember { mutableStateOf<Pair<String, ChatToolsConfig>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(refresh) {
        loading = true; error = null
        try { config = withContext(Dispatchers.IO) { ChatToolsConfig.parse(bridge.load()) } }
        catch (e: Exception) { error = e.message ?: "无法读取设置" }
        finally { loading = false }
    }
    fun save(next: ChatToolsConfig, done: () -> Unit = {}) {
        if (saving) return
        saving = true; error = null
        scope.launch {
            try { withContext(Dispatchers.IO) { bridge.save(next.json()) }; config = next; done() }
            catch (e: Exception) { error = e.message ?: "保存失败，请重试" }
            finally { saving = false }
        }
    }
    val data = config
    val tab = data?.tabs?.find { it.id == tabId } ?: data?.tabs?.firstOrNull()
    val category = tab?.categories?.find { it.id == categoryId } ?: tab?.categories?.firstOrNull()
    Column(Modifier.fillMaxSize().background(Mist)) {
        PayTopBar(if (picker) "快速回复" else "FutoonPay 设置", bridge::close)
        if (!picker) PayPills(listOf("tags" to "会话 Tag", "replies" to "快速回复"), page) { page = it }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (editor == null && deletion == null) error?.let { Text(it, color = Danger, modifier = Modifier.padding(12.dp), fontSize = 12.sp) }
        if (data == null && !loading) TextButton(onClick = { refresh++ }) { Text("重新读取设置") }
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("chat-tools-list"), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (data != null && !picker && page == "tags") {
                item { Text("按对方 Telegram ID 匹配，标记显示在聊天列表和聊天页顶部。工单话题使用末尾识别出的 TGID。", color = Muted, fontSize = 12.sp) }
                item { Button(enabled = !saving, onClick = { editor = ChatEditor("tag") }) { Text("新增 Tag") } }
                items(data.tags, key = { it.id }) { tag -> PayCard {
                    Text(tag.text, color = GreenDark)
                    Text("Telegram ID：${tag.telegramIds.joinToString(", ")}", fontSize = 12.sp)
                    Row {
                        TextButton(enabled = !saving, onClick = { editor = ChatEditor("tag", tag.id, tag.text, tag.telegramIds.joinToString(", ")) }) { Text("编辑 Tag") }
                        TextButton(enabled = !saving, onClick = { deletion = "删除 Tag「${tag.text}」？" to data.copy(tags = data.tags.filterNot { it.id == tag.id }) }) { Text("删除 Tag", color = Danger) }
                    }
                } }
                if (data.tags.isEmpty()) item { Text("暂无自定义 Tag", color = Muted) }
            } else if (data != null) {
                if (data.tabs.isNotEmpty()) item { PayPills(data.tabs.map { it.id to it.title }, tab?.id.orEmpty()) { tabId = it; categoryId = "" } }
                if (!picker) item { Row {
                    TextButton(enabled = !saving, onClick = { editor = ChatEditor("tab") }) { Text("新增 Tab") }
                    tab?.let { current ->
                        TextButton(enabled = !saving, onClick = { editor = ChatEditor("tab", current.id, current.title) }) { Text("编辑 Tab") }
                        TextButton(enabled = !saving, onClick = { deletion = "删除 Tab「${current.title}」及其全部分类和文案？" to data.copy(tabs = data.tabs.filterNot { it.id == current.id }) }) { Text("删除 Tab", color = Danger) }
                    }
                } }
                if (tab != null) {
                    if (tab.categories.isNotEmpty()) item { PayPills(tab.categories.map { it.id to it.title }, category?.id.orEmpty()) { categoryId = it } }
                    if (!picker) item { Row {
                        TextButton(enabled = !saving, onClick = { editor = ChatEditor("category") }) { Text("新增分类") }
                        category?.let { current ->
                            TextButton(enabled = !saving, onClick = { editor = ChatEditor("category", current.id, current.title) }) { Text("编辑分类") }
                            TextButton(enabled = !saving, onClick = { deletion = "删除分类「${current.title}」及其全部文案？" to data.copy(tabs = data.tabs.map { if (it.id == tab.id) it.copy(categories = it.categories.filterNot { c -> c.id == current.id }) else it }) }) { Text("删除分类", color = Danger) }
                        }
                    } }
                    if (category != null) {
                        if (!picker) item { Button(enabled = !saving, onClick = { editor = ChatEditor("reply") }) { Text("新增文案") } }
                        items(category.replies, key = { it.id }) { reply -> PayCard {
                            Column(if (picker) Modifier.fillMaxWidth().clickable { bridge.selectReply(reply.body) }.padding(6.dp) else Modifier) {
                                Text(reply.title, color = GreenDark)
                                Text(reply.body, modifier = Modifier.padding(top = 6.dp), fontSize = 14.sp)
                                if (picker) Text("点击填入输入框", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                            }
                            if (!picker) Row {
                                TextButton(enabled = !saving, onClick = { editor = ChatEditor("reply", reply.id, reply.title, reply.body) }) { Text("编辑文案") }
                                TextButton(enabled = !saving, onClick = { deletion = "删除文案「${reply.title}」？" to data.copy(tabs = data.tabs.map { if (it.id == tab.id) it.copy(categories = it.categories.map { c -> if (c.id == category.id) c.copy(replies = c.replies.filterNot { r -> r.id == reply.id }) else c }) else it }) }) { Text("删除文案", color = Danger) }
                            }
                        } }
                        if (category.replies.isEmpty()) item { Text("当前分类暂无文案", color = Muted) }
                    } else item { Text("当前 Tab 暂无分类", color = Muted) }
                } else item { Text("暂无快速回复，请先添加 Tab、分类和文案。", color = Muted) }
            }
        }
        if (picker) Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = bridge::manage, modifier = Modifier.weight(1f)) { Text("管理快速回复") }
            TextButton(onClick = bridge::originalSendOptions, modifier = Modifier.weight(1f)) { Text("原发送选项") }
        }
    }
    if (data != null) editor?.let { current ->
        var first by remember(current) { mutableStateOf(current.first) }
        var second by remember(current) { mutableStateOf(current.second) }
        var inputError by remember(current) { mutableStateOf<String?>(null) }
        val kind = when (current.kind) { "tag" -> "Tag"; "tab" -> "Tab"; "category" -> "分类"; else -> "文案" }
        PaySheet((if (current.id.isBlank()) "新增" else "编辑") + kind, { if (!saving) editor = null }) {
            Column(Modifier.fillMaxSize().padding(16.dp).testTag("chat-tools-editor"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(first, { first = it.take(64) }, label = { Text(if (current.kind == "tag") "Tag 内容" else "名称") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !saving)
                if (current.kind == "tag" || current.kind == "reply") OutlinedTextField(second, { second = it.take(if (current.kind == "tag") 2000 else 4096) }, label = { Text(if (current.kind == "tag") "生效 Telegram ID（多个用逗号分隔）" else "回复内容") }, modifier = Modifier.fillMaxWidth(), minLines = if (current.kind == "reply") 3 else 1, maxLines = 6, enabled = !saving)
                (inputError ?: error)?.let { Text(it, color = Danger, fontSize = 12.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !saving, onClick = { editor = null }, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(enabled = !saving, modifier = Modifier.weight(1f), onClick = {
                        try {
                            require(first.isNotBlank()) { "请填写名称" }
                            fun <T> replace(list: List<T>, id: (T) -> String, value: T) = if (current.id.isBlank()) list + value else list.map { if (id(it) == current.id) value else it }
                            val next = when (current.kind) {
                                "tag" -> data.copy(tags = replace(data.tags, { it.id }, ChatTag(current.id.ifBlank { java.util.UUID.randomUUID().toString() }, first.trim(), ChatToolsConfig.parseIds(second))))
                                "tab" -> data.copy(tabs = replace(data.tabs, { it.id }, ReplyTab(current.id.ifBlank { java.util.UUID.randomUUID().toString() }, first.trim(), data.tabs.find { it.id == current.id }?.categories.orEmpty())))
                                "category" -> data.copy(tabs = data.tabs.map { t -> if (t.id == tab?.id) t.copy(categories = replace(t.categories, { it.id }, ReplyCategory(current.id.ifBlank { java.util.UUID.randomUUID().toString() }, first.trim(), t.categories.find { it.id == current.id }?.replies.orEmpty()))) else t })
                                else -> { require(second.isNotBlank()) { "请填写回复内容" }; data.copy(tabs = data.tabs.map { t -> if (t.id == tab?.id) t.copy(categories = t.categories.map { c -> if (c.id == category?.id) c.copy(replies = replace(c.replies, { it.id }, QuickReply(current.id.ifBlank { java.util.UUID.randomUUID().toString() }, first.trim(), second))) else c }) else t }) }
                            }
                            save(next) { editor = null }
                        } catch (e: IllegalArgumentException) { inputError = e.message }
                    }) { Text(if (saving) "保存中…" else "保存") }
                }
            }
        }
    }
    deletion?.let { (message, next) -> PaySheet("确认删除", { if (!saving) deletion = null }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message)
            error?.let { Text(it, color = Danger, fontSize = 12.sp) }
            Row { TextButton(enabled = !saving, onClick = { deletion = null }) { Text("取消") }; TextButton(enabled = !saving, onClick = { save(next) { deletion = null } }) { Text("确认删除", color = Danger) } }
        }
    } }
}
