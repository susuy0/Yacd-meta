package com.futoonpay.admin

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ExtensionOptionsTest {
    @Test fun oldSettingsKeepTagsRepliesAndOpeningWhileReceivingSafeDefaults() {
        val old = """{"version":1,"tags":[{"id":"tag","text":"VIP","telegramIds":["5824870757"]}],"tabs":[]}"""
        val config = ChatToolsConfig.parse(old)
        assertEquals("VIP", config.tagFor("5824870757"))
        assertEquals(0, config.extensions.appearance.dim)
        assertTrue(config.extensions.panel.remember)
        assertTrue(config.extensions.rules.isEmpty())
        assertEquals(6, config.extensions.templates.size)
        assertEquals(config, ChatToolsConfig.parse(config.json()))
    }
    @Test fun optionsRoundTripWithoutRoundingIdsOrDiscardingExistingFeatures() {
        val rule = GroupAutomationRule(dialog = -2147483900L, topic = 1922, sender = 5824870757, enabled = true, since = 100)
        val options = ExtensionOptions(PanelOptions(height = 70, defaultTab = "recognition", tabOrder = panelTabIds.reversed(), image = "/data/private/p.png", entrySize = 64), AppearanceOptions(theme = "dark", accent = "#7954C4", font = 125, dim = 0), rules = listOf(rule))
        val original = ChatToolsConfig(tabs = listOf(ReplyTab(title = "查单")), extensions = options)
        assertEquals(original, ChatToolsConfig.parse(original.json()))
        assertEquals(5824870757L, ChatToolsConfig.parse(original.json()).extensions.rules.single().sender)
    }
    @Test fun incompleteTabsInvalidColorsAndUnsafeNumericBoundsAreRejected() {
        listOf(
            ExtensionOptions(panel = PanelOptions(tabOrder = listOf("orders"))),
            ExtensionOptions(panel = PanelOptions(entryOpacity = 0)),
            ExtensionOptions(panel = PanelOptions(entrySize = 200)),
            ExtensionOptions(appearance = AppearanceOptions(accent = "garbage")),
            ExtensionOptions(appearance = AppearanceOptions(dim = 90)),
        ).forEach { assertThrows(IllegalArgumentException::class.java) { ExtensionOptions.parse(it.json()) } }
    }
    @Test fun rulesRequireAnExactGroupAndValidOptionalSenderAndExplicitAction() {
        listOf(GroupAutomationRule(dialog = 42, sender = 7), GroupAutomationRule(dialog = -42, sender = -7),
            GroupAutomationRule(dialog = -42, sender = 7, button = ""), GroupAutomationRule(dialog = -42, sender = 7, action = "draft", draft = ""),
            GroupAutomationRule(dialog = -42, sender = 7, cooldown = 0)).forEach { rule ->
            assertThrows(IllegalArgumentException::class.java) { ExtensionOptions.parse(ExtensionOptions(rules = listOf(rule)).json()) }
        }
    }
    @Test fun multipleGroupsKeepTheirOwnTopicsAndOptionalSenderAcrossPersistence() {
        val groups = listOf(AutomationGroup(-42, "普通群"), AutomationGroup(-73, "话题群", 77))
        val rule = GroupAutomationRule(dialog = -42, groupName = "普通群", sender = 0, groups = groups, enabled = true, since = 100)
        val restored = ExtensionOptions.parse(ExtensionOptions(rules = listOf(rule)).json()).rules.single()
        assertEquals(rule, restored)
        assertTrue(restored.acceptsEvent(-42, 0, 7, "补单案例", 105, 110))
        assertTrue(restored.acceptsEvent(-73, 77, 999, "补单案例", 105, 110))
        assertFalse(restored.acceptsEvent(-73, 78, 7, "补单案例", 105, 110))
        assertFalse(restored.acceptsEvent(-99, 77, 7, "补单案例", 105, 110))
        assertFalse(restored.copy(sender = 8).acceptsEvent(-42, 0, 7, "补单案例", 105, 110))
        assertThrows(IllegalArgumentException::class.java) { ExtensionOptions.parse(ExtensionOptions(rules = listOf(rule.copy(groups = groups + groups.first()))).json()) }
    }
    @Test fun blankSenderAndTopicSaveWithoutParsingErrorsAndInvalidValuesHaveFieldMessages() {
        val rule = GroupAutomationRule(dialog = -42, sender = 0)
        val draft = AutomationRuleDraft(rule); draft.sender.value = ""; draft.topic.value = ""
        assertEquals(0L, draft.build(rule).sender); assertEquals(0L, draft.build(rule).topic)
        draft.sender.value = "abc"
        val error = assertThrows(IllegalArgumentException::class.java) { draft.build(rule) }
        assertTrue(error.message!!.contains("发送者 ID")); assertFalse(error.message!!.contains("For input string"))
    }
    @Test fun selectingSeveralGroupsRetainsExistingTopicAndCancelDoesNotModifyRule() {
        val original = GroupAutomationRule(dialog = -42, topic = 77, sender = 0)
        val draft = AutomationRuleDraft(original); draft.keyword.value = "补单"
        draft.applySelection(listOf(AutomationGroup(-42,"第一群"),AutomationGroup(-73,"第二群")))
        assertEquals(77L,draft.selection().first().topic); assertEquals(2,draft.build(original).targets().size)
        assertEquals("补单",draft.keyword.value)
        draft.applySelection(emptyList()); assertEquals(2,draft.selection().size)
        assertTrue(original.groups.isEmpty())
        draft.applySelection(listOf(AutomationGroup(-73,"第二群")))
        assertEquals(-73L,draft.build(original).dialog); assertEquals(1,draft.selection().size)
    }
    private val rule = GroupAutomationRule(dialog = -42, topic = 12, sender = 5824870757, keyword = "补单案例", enabled = true, since = 100)
    @Test fun ruleOnlyMatchesItsEnabledGroupTopicSenderAndMessageText() {
        assertTrue(rule.acceptsEvent(-42, 12, 5824870757, "新的补单案例", 105, 110))
        assertFalse(rule.copy(enabled = false).acceptsEvent(-42, 12, 5824870757, "补单案例", 105, 110))
        assertFalse(rule.acceptsEvent(-43, 12, 5824870757, "补单案例", 105, 110))
        assertFalse(rule.acceptsEvent(-42, 13, 5824870757, "补单案例", 105, 110))
        assertFalse(rule.acceptsEvent(-42, 12, 5824870758, "补单案例", 105, 110))
        assertFalse(rule.acceptsEvent(-42, 12, 5824870757, "普通消息", 105, 110))
    }
    @Test fun ruleIgnoresHistoryOldMessagesAndFutureClockOutliers() {
        assertFalse(rule.acceptsEvent(-42, 12, 5824870757, "补单案例", 99, 110))
        assertFalse(rule.acceptsEvent(-42, 12, 5824870757, "补单案例", 105, 410))
        assertFalse(rule.acceptsEvent(-42, 12, 5824870757, "补单案例", 300, 110))
        assertTrue(rule.copy(topic = 0).acceptsEvent(-42, 999, 5824870757, "补单案例", 105, 110))
    }
    @Test fun templateExpandsLiteralDollarBackslashesAndMultilineValuesWithoutRegexReplacementHazards() {
        val result = renderBusinessReply("订单 {系统订单号}\n金额 {金额} {币种}", mapOf("系统订单号" to "a$1\\b", "金额" to "10.00", "币种" to "CNY"))
        assertEquals("订单 a$1\\b\n金额 10.00 CNY", result)
        assertEquals("未提供", renderBusinessReply("{商户名称}", emptyMap()))
        assertThrows(IllegalArgumentException::class.java) { validateBusinessTemplate(BusinessReplyTemplate(title = "x", body = "{未知变量}")) }
    }
    @Test fun memoryRetainsNavigationAndModelsInTheSameScopeButDoesNotRetainAnAttachedHost() {
        val one = FixtureBridge(); val options = ExtensionOptions()
        val first = PanelMemories.acquire("memory-test|backend|chat", one, options)
        first.navigator.select("recognition"); first.navigator.openOrder("T20261019")
        val modelOwner = first.routeModels.owner("order")
        PanelMemories.release(first, true)
        assertThrows(IllegalStateException::class.java) { first.bridge.call("local.read", "{}") }
        val second = PanelMemories.acquire("memory-test|backend|chat", FixtureBridge(), options)
        assertSame(first, second); assertSame(modelOwner, second.routeModels.owner("order"))
        assertEquals("T20261019", second.navigator.current.orderId)
        val other = PanelMemories.acquire("memory-test|backend|different-chat", FixtureBridge(), options)
        assertNotSame(second, other)
        PanelMemories.release(second, false); PanelMemories.release(other, false)
    }
    @Test fun disabledStateMemoryReleasesModelStoresAndStartsFromConfiguredTab() {
        val options = ExtensionOptions(panel = PanelOptions(remember = false, defaultTab = "merchants"))
        val first = PanelMemories.acquire("disabled-memory", FixtureBridge(), options)
        first.navigator.select("more"); PanelMemories.release(first, false)
        val second = PanelMemories.acquire("disabled-memory", FixtureBridge(), options)
        assertNotSame(first, second); assertEquals("merchants", second.navigator.tab)
        PanelMemories.release(second, false)
    }
    @Test fun navigationSnapshotKeepsRoutesAndSearchContextAcrossProcessRestart() {
        val first = PanelNavigator(); first.select("orders"); first.push(PanelRoute("orders", merchantId = "2806", queryColumn = "out_trade_no", queryValue = "M-123"))
        val second = PanelNavigator(); second.restore(first.snapshot())
        assertEquals(first.current, second.current); assertEquals("2806", second.current.merchantId)
        assertEquals("M-123", second.current.queryValue)
        val before = second.current; second.restore("{bad"); assertEquals(before, second.current)
    }
}
