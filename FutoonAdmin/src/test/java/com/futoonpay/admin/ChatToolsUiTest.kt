package com.futoonpay.admin

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
class ChatToolsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val bridge = ChatToolsFixture()
    private fun root(picker: Boolean = false, short: Boolean = false) {
        compose.setContent { FutoonTheme { PanelOverlayHost { Box(Modifier.width(390.dp).height(if (short) 350.dp else 650.dp).testTag("chat-tools-panel")) { ChatToolsScreen(bridge, picker) } } } }
        compose.waitUntil(5000) { compose.onAllNodesWithText(if (picker) "查单" else "新增 Tag").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun screenshot(name: String) {
        val bounds = compose.onNodeWithTag("chat-tools-panel").fetchSemanticsNode().boundsInRoot
        compose.runOnUiThread {
            val view = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
            val bitmap = Bitmap.createBitmap(bounds.width.toInt(), bounds.height.toInt(), Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply { translate(-bounds.left, -bounds.top); view.draw(this) }
            File("build/ui-screenshots/$name.png").apply { parentFile.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    private fun save() { compose.onNodeWithText("保存", useUnmergedTree = true).performClick(); compose.waitUntil(5000) { compose.onAllNodesWithTag("chat-tools-editor").fetchSemanticsNodes().isEmpty() } }
    @Test fun settingsAddsEditsAndDeletesAnExactTgidTag() {
        root()
        compose.onNodeWithText("新增 Tag").performClick()
        compose.onNode(hasSetTextAction() and hasText("Tag 内容")).performTextInput("商户")
        compose.onNode(hasSetTextAction() and hasText("生效 Telegram ID", substring = true)).performTextInput("5824870757")
        save(); assertEquals("商户", ChatToolsConfig.parse(bridge.saved).tagFor("5824870757"))
        compose.onNodeWithText("编辑 Tag").performClick()
        compose.onNode(hasSetTextAction() and hasText("Tag 内容")).performTextReplacement("VIP")
        save(); assertEquals("VIP", ChatToolsConfig.parse(bridge.saved).tagFor("5824870757"))
        screenshot("26-futoon-chat-tag-settings")
        compose.onNodeWithText("删除 Tag").performClick()
        compose.onAllNodesWithText("确认删除").onLast().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("暂无自定义 Tag").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("", ChatToolsConfig.parse(bridge.saved).tagFor("5824870757"))
    }
    @Test fun settingsCreatesTabCategoryAndEditableReplyWithoutSending() {
        root()
        compose.onNodeWithText("快速回复").performClick(); compose.onNodeWithText("新增 Tab").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("查单"); save()
        compose.onNodeWithText("新增分类").performClick(); compose.onNode(hasSetTextAction()).performTextInput("付款凭证"); save()
        compose.onNodeWithText("新增文案").performClick()
        compose.onNode(hasSetTextAction() and hasText("名称")).performTextInput("索要凭证")
        compose.onNode(hasSetTextAction() and hasText("回复内容")).performTextInput("请提供付款凭证\n谢谢")
        save()
        assertEquals("请提供付款凭证\n谢谢", ChatToolsConfig.parse(bridge.saved).tabs.single().categories.single().replies.single().body)
        assertEquals("", bridge.inserted)
        screenshot("27-futoon-quick-reply-settings")
    }
    @Test fun pickerSwitchesTabsAndCategoriesAndOnlyReturnsEditableDraftText() {
        bridge.saved = ChatToolsConfig(tabs = listOf(
            ReplyTab(title = "查单", categories = listOf(ReplyCategory(title = "付款", replies = listOf(QuickReply(title = "凭证", body = "请提供付款凭证"))))),
            ReplyTab(title = "售后", categories = listOf(ReplyCategory(title = "退款", replies = listOf(QuickReply(title = "核实", body = "请提供退款订单号")))))
        )).json()
        root(picker = true)
        compose.onNodeWithText("售后").performClick(); compose.onNodeWithText("退款").assertIsDisplayed()
        compose.onNodeWithText("核实").performClick()
        assertEquals("请提供退款订单号", bridge.inserted); assertEquals(0, bridge.writes)
        compose.onNodeWithText("管理快速回复").assertIsDisplayed()
        compose.onNodeWithText("原发送选项").assertIsDisplayed()
        screenshot("28-futoon-long-press-quick-replies")
    }
    @Test fun keyboardSizedEditorKeepsSaveVisibleAndRejectsBadIds() {
        root(short = true); compose.onNodeWithText("新增 Tag").performClick()
        compose.onNode(hasSetTextAction() and hasText("Tag 内容")).performTextInput("测试")
        compose.onNode(hasSetTextAction() and hasText("生效 Telegram ID", substring = true)).performTextInput("0")
        compose.onNodeWithText("保存", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.onNodeWithText("Telegram ID 必须是完整的正整数").assertExists()
        assertEquals(0, bridge.writes)
    }
    @Test fun failedSaveKeepsFormAndExistingConfiguration() {
        bridge.fail = true
        root(); compose.onNodeWithText("新增 Tag").performClick()
        compose.onNode(hasSetTextAction() and hasText("Tag 内容")).performTextInput("测试")
        compose.onNode(hasSetTextAction() and hasText("生效 Telegram ID", substring = true)).performTextInput("5824870757")
        compose.onNodeWithText("保存", useUnmergedTree = true).performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("fixture save failure").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("保存", useUnmergedTree = true).assertExists()
        compose.onNode(hasSetTextAction() and hasText("测试")).assertExists()
        assertEquals("", bridge.saved)
    }
    @Test fun actualSheetViewBoundsItsHeightAndOwnsItsLifecycle() {
        var panel: ChatToolsView? = null
        compose.setContent { AndroidView(factory = { context -> ChatToolsView(context, bridge, true).also { panel = it; it.setPanelHeight(350); it.onShown() } }, modifier = Modifier.fillMaxWidth()) }
        compose.onNodeWithText("管理快速回复").assertIsDisplayed()
        compose.runOnUiThread {
            assertTrue(panel!!.measuredHeight in 1..350)
            assertEquals(androidx.lifecycle.Lifecycle.State.RESUMED, panel!!.lifecycle.currentState)
            panel!!.onHidden(); assertEquals(androidx.lifecycle.Lifecycle.State.CREATED, panel!!.lifecycle.currentState)
            panel!!.onShown(); panel!!.close(); assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, panel!!.lifecycle.currentState)
        }
    }
}
internal class ChatToolsFixture : ChatToolsBridge {
    @Volatile var saved = ""
    var fail = false
    var inserted = ""
    var writes = 0
    override fun load() = saved
    override fun save(json: String) { if (fail) error("fixture save failure"); saved = json; writes++ }
    override fun selectReply(text: String) { inserted = text }
    override fun manage() {}
    override fun originalSendOptions() {}
    override fun close() {}
}
