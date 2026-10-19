package com.futoonpay.admin

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.futoonpay.admin.ui.theme.FutoonTheme
import java.io.File
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExtensionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val host = ExtensionHostFixture()
    private fun root(page: String = "home") {
        compose.setContent { FutoonTheme { Box(Modifier.width(390.dp).height(760.dp).testTag("extensions-ui")) { PanelOverlayHost { ChatToolsScreen(host, false, page) } } } }
        compose.waitUntil(5000) { host.title.isNotBlank() && compose.onAllNodesWithText(if (page == "home") "会话 Tag" else if (page == "panel") "默认高度：85%" else if (page == "automation") "新增群规则" else if (page == "businessReplies") "新增业务回复模板" else "跟随 Telegram").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun screenshot(name: String) {
        val bounds = compose.onNodeWithTag("extensions-ui").fetchSemanticsNode().boundsInRoot
        compose.runOnUiThread {
            val view = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
            val bitmap = Bitmap.createBitmap(bounds.width.toInt(), bounds.height.toInt(), Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply { translate(-bounds.left, -bounds.top); view.draw(this) }
            File("build/ui-screenshots/$name.png").apply { parentFile.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    @Test fun nativeHostOwnsTheOnlySettingsHeaderAndReceivesChildTitles() {
        root(); assertEquals("FutoonPay 设置", host.title)
        compose.onNodeWithText("FutoonPay 设置").assertDoesNotExist()
        compose.onNodeWithText("悬浮窗自定义").performScrollTo().performClick()
        compose.waitUntil(5000) { host.title == "悬浮窗自定义" }
        compose.onNodeWithText("默认高度：85%").assertIsDisplayed()
        screenshot("37-native-settings-header-content")
    }
    @Test fun panelMemorySwitchAndDefaultTabPersistWithoutLosingQuickReplies() {
        host.saved = ChatToolsConfig(tabs = listOf(ReplyTab(title = "保留的分类"))).json(); root("panel")
        compose.onNodeWithText("记住页面、筛选和未完成操作").performClick()
        compose.onNode(hasText("识别") and isSelectable()).performClick()
        compose.onNodeWithText("保存设置").performScrollTo().performClick()
        compose.waitUntil(5000) { host.writes == 1 }
        val config = ChatToolsConfig.parse(host.saved)
        assertFalse(config.extensions.panel.remember); assertEquals("recognition", config.extensions.panel.defaultTab)
        assertEquals("保留的分类", config.tabs.single().title)
    }
    @Test fun appearanceStartsWithoutDimAndSavesExplicitOptIn() {
        root("appearance")
        compose.onNodeWithText("背景遮罩").performScrollTo()
        compose.onNode(isToggleable()).assertIsOff()
        compose.onNodeWithText("背景遮罩").performClick()
        compose.onNodeWithText("遮罩深度：25%").assertExists()
        screenshot("38-panel-appearance-dim-options")
        compose.onNodeWithText("保存设置").performScrollTo().performClick()
        compose.waitUntil(5000) { host.writes == 1 }
        assertEquals(25, ChatToolsConfig.parse(host.saved).extensions.appearance.dim)
    }
    @Test fun aNewGroupRuleIsSavedDisabledWithExactTelegramIdsAndButtonText() {
        root("automation"); compose.onNodeWithText("新增群规则").performClick()
        compose.onNode(hasSetTextAction() and hasText("客户端群 ID", substring = true)).performScrollTo().performTextReplacement("-42")
        compose.onNode(hasSetTextAction() and hasText("指定机器人／发送者 Telegram ID")).performScrollTo().performTextReplacement("5824870757")
        compose.onNodeWithText("确定规则").performScrollTo().performClick()
        assertEquals(0, host.writes)
        compose.onNodeWithText("保存设置").performScrollTo().performClick()
        compose.waitUntil(5000) { host.writes == 1 }
        val rule = ChatToolsConfig.parse(host.saved).extensions.rules.single()
        assertEquals(-42L, rule.dialog); assertEquals(5824870757L, rule.sender); assertFalse(rule.enabled)
        assertEquals("补单案例", rule.button); assertEquals(0L, rule.topic)
        screenshot("39-group-automation-disabled-rule")
    }
    @Test fun unknownTemplateVariableIsRejectedAndItsDraftRemainsEditable() {
        root("businessReplies"); compose.onNodeWithText("新增业务回复模板").performClick()
        compose.onNode(hasSetTextAction() and hasText("模板名称")).performTextReplacement("我的模板")
        compose.onNode(hasSetTextAction() and hasText("模板正文")).performTextReplacement("订单：{错误变量}")
        compose.onNodeWithText("确定").performScrollTo().performClick()
        compose.onNodeWithText("未知变量：错误变量").assertExists()
        compose.onNode(hasSetTextAction() and hasText("订单：{错误变量}")).assertExists()
        assertEquals(0, host.writes)
    }
}

private class ExtensionHostFixture : ChatToolsHostBridge {
    var saved = ChatToolsConfig().json()
    var writes = 0
    var title = ""
    override fun load() = saved
    override fun save(json: String) { saved = json; writes++ }
    override fun settingsTitle(title: String) { this.title = title }
    override fun accountLabel() = "合成测试账号"
    override fun selectedTab() = ""
    override fun selectedCategory() = ""
    override fun rememberSelection(tab: String, category: String) {}
    override fun manageAt(tab: String, category: String) {}
    override fun selectReply(text: String) {}
    override fun manage() {}
    override fun originalSendOptions() {}
    override fun close() {}
    override fun pickEntryImage() {}
    override fun pickAutomationGroup() {}
    override fun selectedGroup() = ""
    override fun automationLog() = "暂无执行记录"
    override fun resetEntryPosition() {}
}
