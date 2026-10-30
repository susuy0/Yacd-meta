package com.futoonpay.admin

import androidx.compose.runtime.mutableStateOf

/** In-memory editing state owned by the Telegram fragment, not its disposable composition. */
class ChatToolsSessionState(initialPage: String) {
    internal val page = mutableStateOf(initialPage)
    internal var automation: ExtensionSettingsDraft? = null
}

internal class ExtensionSettingsDraft(options: ExtensionOptions) {
    val working = mutableStateOf(options)
    val editRule = mutableStateOf<GroupAutomationRule?>(null)
    var ruleEditor: AutomationRuleDraft? = null
}

internal class AutomationRuleDraft(initial: GroupAutomationRule) {
    val title = mutableStateOf(initial.title)
    val dialog = mutableStateOf(if (initial.dialog == -1L) "" else initial.dialog.toString())
    val groupName = mutableStateOf(initial.groupName)
    val topic = mutableStateOf(initial.topic.toString())
    val additionalGroups = mutableStateOf(initial.groups.drop(1))
    val sender = mutableStateOf(if (initial.sender <= 0) "" else initial.sender.toString())
    val keyword = mutableStateOf(initial.keyword)
    val button = mutableStateOf(initial.button)
    val action = mutableStateOf(initial.action)
    val draft = mutableStateOf(initial.draft)
    val cooldown = mutableStateOf(initial.cooldown.toString())
    val awaitingPick = mutableStateOf(false)
    fun selection(): List<AutomationGroup> = listOfNotNull(dialog.value.trim().toLongOrNull()?.takeIf { it < 0 }?.let { AutomationGroup(it, groupName.value, topic.value.trim().toLongOrNull() ?: 0) }) + additionalGroups.value
    fun applySelection(picked: List<AutomationGroup>) {
        if (picked.isEmpty()) return
        val old = selection().associateBy { it.dialog }
        val groups = picked.distinctBy { it.dialog }.map { old[it.dialog]?.copy(title = it.title) ?: it }
        dialog.value = groups.first().dialog.toString(); groupName.value = groups.first().title; topic.value = groups.first().topic.toString()
        additionalGroups.value = groups.drop(1)
    }
    fun build(initial: GroupAutomationRule): GroupAutomationRule {
        val did = requireNotNull(dialog.value.trim().toLongOrNull()?.takeIf { it < 0 }) { "请先选择至少一个群组" }
        val tid = requireNotNull(topic.value.trim().ifBlank { "0" }.toLongOrNull()?.takeIf { it >= 0 }) { "话题 ID 应为 0 或正整数" }
        val sid = requireNotNull(sender.value.trim().ifBlank { "0" }.toLongOrNull()?.takeIf { it >= 0 }) { "发送者 ID 可留空；填写时应为正整数" }
        val seconds = requireNotNull(cooldown.value.trim().toIntOrNull()?.takeIf { it in 2..3600 }) { "执行间隔应为 2～3600 秒" }
        val targets = listOf(AutomationGroup(did, groupName.value, tid)) + additionalGroups.value
        return initial.copy(title = title.value.trim(), dialog = did, groupName = groupName.value, topic = tid, sender = sid,
            keyword = keyword.value, button = button.value, action = action.value, draft = draft.value, cooldown = seconds,
            groups = if (targets.size > 1 || initial.groups.isNotEmpty()) targets else emptyList(),
            since = if (initial.enabled) System.currentTimeMillis() / 1000 else initial.since).also {
            ExtensionOptions.parse(ExtensionOptions(rules = listOf(it)).json())
        }
    }
}
