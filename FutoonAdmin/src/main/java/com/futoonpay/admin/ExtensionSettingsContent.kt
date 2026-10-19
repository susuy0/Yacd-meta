package com.futoonpay.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.futoonpay.admin.ui.*
import com.futoonpay.admin.ui.theme.*
import org.json.JSONObject
import java.util.UUID

@Composable internal fun ExtensionSettingsContent(page: String, options: ExtensionOptions, host: ChatToolsHostBridge?, revision: Int,
    saving: Boolean, saveError: String?, modifier: Modifier, save: (ExtensionOptions, () -> Unit) -> Unit) {
    var working by remember(page) { mutableStateOf(options) }
    var validation by remember(page) { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    var editTemplate by remember { mutableStateOf<BusinessReplyTemplate?>(null) }
    var editRule by remember { mutableStateOf<GroupAutomationRule?>(null) }
    var deleteTemplate by remember { mutableStateOf<BusinessReplyTemplate?>(null) }
    var deleteRule by remember { mutableStateOf<GroupAutomationRule?>(null) }
    var log by remember { mutableStateOf<String?>(null) }
    val dirty = working != options
    LaunchedEffect(options.panel.image) { working = working.copy(panel = working.panel.copy(image = options.panel.image)) }
    BackHandler(enabled = dirty && editRule == null && editTemplate == null) { discard = true }
    fun persist(next: ExtensionOptions, done: () -> Unit = {}) {
        try { ExtensionOptions.parse(next.json()); validation = null; save(next) { working = next; done() } }
        catch (e: Exception) { validation = e.message ?: "设置内容不正确" }
    }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { (validation ?: saveError)?.let { Text(it, color = Danger, fontSize = 13.sp) } }
        if (page == "panel") {
            item { PayCard {
                SectionLabel("展开与状态")
                ExtensionSlider("默认高度", working.panel.height, 50..100, "%") { working = working.copy(panel = working.panel.copy(height = it)) }
                ExtensionToggle("默认全屏展开", working.panel.full) { working = working.copy(panel = working.panel.copy(full = it)) }
                ExtensionToggle("记住页面、筛选和未完成操作", working.panel.remember) { working = working.copy(panel = working.panel.copy(remember = it)) }
                Text("收起后继续当前任务；同一账号、后台和会话隔离保存。应用重启后恢复导航及可保存的表单，业务结果重新读取。", color = Muted, fontSize = 13.sp)
                Text("默认 Tab", fontWeight = FontWeight.Medium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    panelTabIds.forEach { id -> FilterChip(selected = working.panel.defaultTab == id, onClick = { working = working.copy(panel = working.panel.copy(defaultTab = id)) }, label = { Text(panelTabNames.getValue(id), fontSize = 12.sp) }) }
                }
            } }
            item { PayCard {
                SectionLabel("业务 Tab 顺序")
                working.panel.tabOrder.forEachIndexed { i, id ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(panelTabNames.getValue(id), Modifier.weight(1f))
                        TextButton(enabled = i > 0 && !saving, onClick = { val order = working.panel.tabOrder.toMutableList(); java.util.Collections.swap(order, i, i - 1); working = working.copy(panel = working.panel.copy(tabOrder = order)) }) { Text("上移") }
                        TextButton(enabled = i < 4 && !saving, onClick = { val order = working.panel.tabOrder.toMutableList(); java.util.Collections.swap(order, i, i + 1); working = working.copy(panel = working.panel.copy(tabOrder = order)) }) { Text("下移") }
                    }
                }
            } }
            item { PayCard {
                SectionLabel("悬浮窗入口")
                Text(if (working.panel.image.isBlank()) "当前使用默认图标" else "当前使用自定义图片")
                val image = remember(working.panel.image) { if (working.panel.image.isBlank()) null else runCatching { android.graphics.BitmapFactory.decodeFile(working.panel.image, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }) }.getOrNull() }
                image?.let { Image(it.asImageBitmap(), "入口图片预览", Modifier.size(72.dp)) }
                Button(enabled = !saving && host != null, onClick = { host?.pickEntryImage() }) { Text("从相册选择并裁剪图片") }
                ExtensionSlider("入口大小", working.panel.entrySize, 40..96, "dp") { working = working.copy(panel = working.panel.copy(entrySize = it)) }
                ExtensionSlider("入口圆角", working.panel.entryRadius, 0..48, "dp") { working = working.copy(panel = working.panel.copy(entryRadius = it)) }
                ExtensionSlider("入口不透明度", working.panel.entryOpacity, 25..100, "%") { working = working.copy(panel = working.panel.copy(entryOpacity = it)) }
                TextButton(enabled = !saving, onClick = { working = working.copy(panel = working.panel.copy(image = "", entrySize = 52, entryRadius = 24, entryOpacity = 100)); host?.resetEntryPosition() }) { Text("恢复默认入口与位置") }
            } }
        } else if (page == "appearance") {
            item { PayCard {
                SectionLabel("Futoon 扩展区域主题")
                listOf("telegram" to "跟随 Telegram", "light" to "浅色", "dark" to "深色").forEach { (id, title) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { working = working.copy(appearance = working.appearance.copy(theme = id)) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(working.appearance.theme == id, { working = working.copy(appearance = working.appearance.copy(theme = id)) }); Text(title)
                    }
                }
                OutlinedTextField(working.appearance.accent, { working = working.copy(appearance = working.appearance.copy(accent = it)) }, label = { Text("强调色，例如 #2481CC；留空跟随主题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("#2481CC", "#087A4B", "#7954C4", "#D16B32").forEach { color ->
                    Box(Modifier.size(44.dp).background(Color(android.graphics.Color.parseColor(color)), androidx.compose.foundation.shape.CircleShape).clickable { working = working.copy(appearance = working.appearance.copy(accent = color)) })
                } }
                ExtensionSlider("字号比例", working.appearance.font, 80..150, "%") { working = working.copy(appearance = working.appearance.copy(font = it)) }
                ExtensionSlider("面板圆角", working.appearance.radius, 0..36, "dp") { working = working.copy(appearance = working.appearance.copy(radius = it)) }
                ExtensionSlider("面板不透明度", working.appearance.opacity, 50..100, "%") { working = working.copy(appearance = working.appearance.copy(opacity = it)) }
                ExtensionToggle("背景遮罩", working.appearance.dim > 0) { working = working.copy(appearance = working.appearance.copy(dim = if (it) 25 else 0)) }
                if (working.appearance.dim > 0) ExtensionSlider("遮罩深度", working.appearance.dim, 1..60, "%") { working = working.copy(appearance = working.appearance.copy(dim = it)) }
                Text("遮罩默认关闭。字号比例在系统字号基础上调整。", color = Muted, fontSize = 13.sp)
                TextButton(enabled = !saving, onClick = { working = working.copy(appearance = AppearanceOptions()) }) { Text("恢复默认外观") }
            } }
        } else if (page == "businessReplies") {
            item { Text("订单和商户信息可生成可编辑草稿，填入当前聊天后由你手动发送。", color = Muted, fontSize = 13.sp) }
            item { Button(enabled = !saving, onClick = { editTemplate = BusinessReplyTemplate(title = "", body = "") }) { Text("新增业务回复模板") } }
            items(working.templates, key = { it.id }) { template -> PayCard {
                Text(template.title, fontWeight = FontWeight.Medium); Text(template.body, color = Muted, maxLines = 3, fontSize = 13.sp)
                Row { TextButton(enabled = !saving, onClick = { editTemplate = template }) { Text("编辑") }; TextButton(enabled = !saving, onClick = { deleteTemplate = template }) { Text("删除", color = Danger) } }
            } }
            item { TextButton(enabled = !saving, onClick = { working = working.copy(templates = (working.templates + defaultBusinessTemplates.filter { d -> working.templates.none { it.id == d.id } })) }) { Text("补回缺少的默认模板") } }
        } else if (page == "automation") {
            item { Text("每个群单独开启；可再限定话题与发送者。只处理启用后的新消息及其按钮更新，同一动作最多执行一次。", color = Muted, fontSize = 13.sp) }
            item { Row { Button(enabled = !saving, onClick = { editRule = GroupAutomationRule(dialog = -1, sender = 0) }) { Text("新增群规则") }; TextButton(onClick = { log = host?.automationLog() ?: "暂无执行记录" }) { Text("执行记录") } } }
            working.rules.groupBy { it.dialog }.forEach { (_, rules) ->
                item { PayCard {
                    Text(rules.first().groupName.ifBlank { "群 ${rules.first().dialog}" }, fontWeight = FontWeight.Medium)
                    ExtensionToggle("此群自动化", rules.any { it.enabled }) { enabled -> val ids = rules.map { it.id }.toSet(); val now = System.currentTimeMillis() / 1000
                        working = working.copy(rules = working.rules.map { if (it.id in ids) it.copy(enabled = enabled, since = if (enabled) now else it.since) else it }) }
                    rules.forEach { rule ->
                        HorizontalDivider(color = Line)
                        ExtensionToggle(rule.title, rule.enabled) { enabled -> val now = System.currentTimeMillis() / 1000; working = working.copy(rules = working.rules.map { if (it.id == rule.id) it.copy(enabled = enabled, since = if (enabled) now else it.since) else it }) }
                        Text("发送者 ${rule.sender} · ${if (rule.topic == 0L) "全部话题" else "话题 ${rule.topic}"}\n${if (rule.action == "click") "点击「${rule.button}」" else "生成回复草稿"}", color = Muted, fontSize = 12.sp)
                        Row { TextButton(onClick = { editRule = rule }) { Text("编辑规则") }; TextButton(onClick = { deleteRule = rule }) { Text("删除", color = Danger) } }
                    }
                } }
            }
            if (working.rules.isEmpty()) item { Text("还没有群规则。选择群组后填写实际机器人 ID 和按钮全名。", color = Muted) }
            item { Text("应用在线并收到消息时执行。按钮动作仅支持机器人回调按钮；超时或结果不明不自动重试。", color = Muted, fontSize = 12.sp) }
        }
        item { Button(enabled = dirty && !saving, onClick = { persist(working) }, modifier = Modifier.fillMaxWidth()) { Text(if (saving) "正在保存…" else "保存设置") } }
    }
    if (discard) PaySheet("尚有未保存的设置", { discard = false }) { Column(Modifier.padding(16.dp)) {
        Text("保存后再离开，或放弃本页的修改。")
        Row { TextButton(onClick = { discard = false }) { Text("继续编辑") }; TextButton(enabled = !saving, onClick = { working = options; discard = false }) { Text("放弃修改") }; TextButton(enabled = !saving, onClick = { persist(working) { discard = false } }) { Text("保存") } }
    } }
    editTemplate?.let { template -> BusinessTemplateEditor(template, { editTemplate = null }) { edited ->
        working = working.copy(templates = if (working.templates.any { it.id == edited.id }) working.templates.map { if (it.id == edited.id) edited else it } else working.templates + edited); editTemplate = null
    } }
    editRule?.let { rule -> AutomationRuleEditor(rule, host, revision, { editRule = null }) { edited ->
        working = working.copy(rules = if (working.rules.any { it.id == edited.id }) working.rules.map { if (it.id == edited.id) edited else it } else working.rules + edited); editRule = null
    } }
    deleteTemplate?.let { template -> ConfirmExtensionDelete(template.title, { deleteTemplate = null }) { working = working.copy(templates = working.templates.filterNot { it.id == template.id }); deleteTemplate = null } }
    deleteRule?.let { rule -> ConfirmExtensionDelete(rule.title, { deleteRule = null }) { working = working.copy(rules = working.rules.filterNot { it.id == rule.id }); deleteRule = null } }
    log?.let { text -> PaySheet("自动化执行记录", { log = null }) { Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) { Text(text.ifBlank { "暂无执行记录" }, fontSize = 13.sp) } } }
}

@Composable private fun ExtensionSlider(title: String, value: Int, range: IntRange, unit: String, change: (Int) -> Unit) {
    Text("$title：$value$unit", fontSize = 14.sp)
    Slider(value.toFloat(), { change(it.toInt().coerceIn(range)) }, valueRange = range.first.toFloat()..range.last.toFloat())
}
@Composable private fun ExtensionToggle(title: String, value: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { change(!value) }, verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), fontSize = 14.sp); Switch(value, change) }
}
@Composable private fun ConfirmExtensionDelete(title: String, close: () -> Unit, confirm: () -> Unit) {
    PaySheet("确认删除", close) { Column(Modifier.padding(16.dp)) { Text("删除「$title」？保存设置后生效。"); Row { TextButton(onClick = close) { Text("取消") }; TextButton(onClick = confirm) { Text("删除", color = Danger) } } } }
}

@Composable private fun BusinessTemplateEditor(initial: BusinessReplyTemplate, close: () -> Unit, save: (BusinessReplyTemplate) -> Unit) {
    var title by rememberSaveable(initial.id) { mutableStateOf(initial.title) }
    var body by rememberSaveable(initial.id) { mutableStateOf(initial.body) }
    var error by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    fun back() { if (title != initial.title || body != initial.body) discard = true else close() }
    PaySheet("业务回复模板", ::back) { Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(title, { title = it }, label = { Text("模板名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(body, { body = it }, label = { Text("模板正文") }, minLines = 6, supportingText = { Text("${body.length} / 4096") }, modifier = Modifier.fillMaxWidth())
        Text("插入变量", fontWeight = FontWeight.Medium)
        businessReplyVariables.chunked(3).forEach { row -> Row { row.forEach { variable -> TextButton(onClick = { body += "{$variable}" }) { Text(variable, fontSize = 12.sp) } } } }
        Text("变量在生成草稿时替换；缺失信息显示为“未提供”。", color = Muted, fontSize = 12.sp)
        error?.let { Text(it, color = Danger) }
        Row { TextButton(onClick = ::back) { Text("取消") }; Button(onClick = { try { val next = initial.copy(title = title.trim(), body = body); validateBusinessTemplate(next); save(next) } catch (e: Exception) { error = e.message } }) { Text("确定") } }
    } }
    if (discard) PaySheet("放弃修改？", { discard = false }) { Row(Modifier.padding(16.dp)) { TextButton(onClick = { discard = false }) { Text("继续编辑") }; TextButton(onClick = close) { Text("放弃修改") } } }
}

@Composable private fun AutomationRuleEditor(initial: GroupAutomationRule, host: ChatToolsHostBridge?, revision: Int, close: () -> Unit, save: (GroupAutomationRule) -> Unit) {
    var title by rememberSaveable(initial.id) { mutableStateOf(initial.title) }
    var dialog by rememberSaveable(initial.id) { mutableStateOf(if (initial.dialog == -1L) "" else initial.dialog.toString()) }
    var groupName by rememberSaveable(initial.id) { mutableStateOf(initial.groupName) }
    var topic by rememberSaveable(initial.id) { mutableStateOf(initial.topic.toString()) }
    var sender by rememberSaveable(initial.id) { mutableStateOf(if (initial.sender <= 0) "" else initial.sender.toString()) }
    var keyword by rememberSaveable(initial.id) { mutableStateOf(initial.keyword) }
    var button by rememberSaveable(initial.id) { mutableStateOf(initial.button) }
    var action by rememberSaveable(initial.id) { mutableStateOf(initial.action) }
    var draft by rememberSaveable(initial.id) { mutableStateOf(initial.draft) }
    var cooldown by rememberSaveable(initial.id) { mutableStateOf(initial.cooldown.toString()) }
    var awaitingPick by rememberSaveable(initial.id) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    val dirty = title != initial.title || dialog != (if (initial.dialog == -1L) "" else initial.dialog.toString()) || groupName != initial.groupName || topic != initial.topic.toString() || sender != (if (initial.sender <= 0) "" else initial.sender.toString()) || keyword != initial.keyword || button != initial.button || action != initial.action || draft != initial.draft || cooldown != initial.cooldown.toString()
    fun back() { if (dirty) discard = true else close() }
    LaunchedEffect(revision) { if (awaitingPick) { val picked = runCatching { JSONObject(host?.selectedGroup().orEmpty()) }.getOrNull(); if (picked != null && picked.optLong("dialog") < 0) { dialog = picked.optString("dialog"); groupName = picked.optString("title"); topic = picked.optString("topic", "0"); awaitingPick = false } } }
    PaySheet("群自动化规则", ::back) { Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(title, { title = it }, label = { Text("规则名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(enabled = host != null, onClick = { awaitingPick = true; host?.pickAutomationGroup() }) { Text("选择群组") }
        Text(groupName.ifBlank { "尚未选择群组" }, color = Muted)
        OutlinedTextField(dialog, { dialog = it }, label = { Text("客户端群 ID（选择群组自动填写）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(topic, { topic = it }, label = { Text("话题 ID；0 表示全部话题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(sender, { sender = it }, label = { Text("指定机器人／发送者 Telegram ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(keyword, { keyword = it }, label = { Text("消息包含文字") }, modifier = Modifier.fillMaxWidth())
        Row { FilterChip(action == "click", { action = "click" }, label = { Text("点击机器人按钮") }); Spacer(Modifier.width(8.dp)); FilterChip(action == "draft", { action = "draft" }, label = { Text("生成回复草稿") }) }
        if (action == "click") OutlinedTextField(button, { button = it }, label = { Text("按钮完整文字，精确匹配") }, modifier = Modifier.fillMaxWidth())
        else { OutlinedTextField(draft, { draft = it }, label = { Text("草稿文案") }, minLines = 3, modifier = Modifier.fillMaxWidth()); Text("可用 {消息文字}、{群名称}、{发送者ID}；已有聊天草稿时跳过，不覆盖。", color = Muted, fontSize = 12.sp) }
        OutlinedTextField(cooldown, { cooldown = it }, label = { Text("执行间隔（2～3600 秒）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("新增规则默认关闭，保存规则后再按群开启。点击只执行指定按钮，不延伸到后续确认。", color = Muted, fontSize = 12.sp)
        error?.let { Text(it, color = Danger) }
        Row { TextButton(onClick = ::back) { Text("取消") }; Button(onClick = { try {
            val edited = initial.copy(title = title.trim(), dialog = dialog.trim().toLong(), groupName = groupName, topic = topic.trim().toLong(), sender = sender.trim().toLong(), keyword = keyword, button = button, action = action, draft = draft, cooldown = cooldown.trim().toInt(), since = if (initial.enabled) System.currentTimeMillis() / 1000 else initial.since)
            ExtensionOptions.parse(ExtensionOptions(rules = listOf(edited)).json()); save(edited)
        } catch (e: Exception) { error = e.message ?: "请检查群 ID、发送者 ID 和规则内容" } }) { Text("确定规则") } }
    } }
    if (discard) PaySheet("放弃规则修改？", { discard = false }) { Row(Modifier.padding(16.dp)) { TextButton(onClick = { discard = false }) { Text("继续编辑") }; TextButton(onClick = close) { Text("放弃修改") } } }
}
