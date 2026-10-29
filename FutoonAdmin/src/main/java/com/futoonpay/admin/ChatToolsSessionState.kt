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
    val sender = mutableStateOf(if (initial.sender <= 0) "" else initial.sender.toString())
    val keyword = mutableStateOf(initial.keyword)
    val button = mutableStateOf(initial.button)
    val action = mutableStateOf(initial.action)
    val draft = mutableStateOf(initial.draft)
    val cooldown = mutableStateOf(initial.cooldown.toString())
    val awaitingPick = mutableStateOf(false)
}
