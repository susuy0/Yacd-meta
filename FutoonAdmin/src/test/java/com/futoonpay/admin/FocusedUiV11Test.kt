package com.futoonpay.admin

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.futoonpay.admin.ui.theme.*
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
class FocusedUiV11Test {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val bridge = ChatToolsFixture()
    private val reply = QuickReply("reply", "索要付款凭证", "第一行，请提供付款凭证。\n第二行，包含完整订单号。\n第三行，原样保留。")
    private fun config(body: String = reply.body) = ChatToolsConfig(tags = listOf(ChatTag(text = "VIP", telegramIds = listOf("5824870757"))), tabs = listOf(ReplyTab("tab", "查单", listOf(ReplyCategory("cat", "付款", listOf(reply.copy(body = body)))))))
    private fun root(picker: Boolean = false, small: Boolean = false, palette: FutoonPalette = FutoonPalette()) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (small) 1.5f else 1f)) {
                FutoonTheme(palette) { Box(Modifier.width(if (small) 320.dp else 390.dp).height(if (small) 350.dp else 650.dp).testTag("focused-ui")) { PanelOverlayHost { ChatToolsScreen(bridge, picker) } } }
            }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText(if (picker) "查单" else "会话 Tag").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun screenshot(name: String) {
        val bounds = compose.onNodeWithTag("focused-ui").fetchSemanticsNode().boundsInRoot
        compose.runOnUiThread {
            val view = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
            val bitmap = Bitmap.createBitmap(bounds.width.toInt(), bounds.height.toInt(), Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply { translate(-bounds.left, -bounds.top); view.draw(this) }
            File("build/ui-screenshots/$name.png").apply { parentFile.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    @Test fun homeShowsSeparateEntriesAndActualAccountCounts() {
        bridge.saved = config().json(); root()
        compose.onNodeWithText("1 个 Tag · 1 个 Telegram ID").assertIsDisplayed()
        compose.onNodeWithText("1 个 Tab · 1 个分类 · 1 条文案").assertIsDisplayed()
        compose.onNodeWithText("开户文案").assertIsDisplayed()
        screenshot("33-custom-settings-home")
    }
    @Test fun previewDoesNotInsertUntilConfirmedAndPreservesEveryLine() {
        bridge.saved = config().json()
        root(picker = true, palette = FutoonPalette(dark = true, surface = Color(0xFF202B36), background = Color(0xFF17212B), text = Color(0xFFF0F4F7), secondaryText = Color(0xFFA3B5C3), accent = Color(0xFF64B5EF), accentText = Color(0xFF88C9FF), divider = Color(0xFF384757)))
        compose.onNodeWithContentDescription("预览「索要付款凭证」").performClick()
        assertEquals("", bridge.inserted); assertEquals(0, bridge.writes)
        compose.onNodeWithText(reply.body).assertIsDisplayed()
        compose.onNodeWithText("原发送选项").assertIsDisplayed()
        screenshot("34-reply-preview-dark")
        compose.onNodeWithText("填入输入框").performClick(); assertEquals(reply.body, bridge.inserted)
    }
    @Test fun narrowLargeFontPickerKeepsManagementAndOriginalOptionsReachable() {
        bridge.saved = config().json()
        root(picker = true, small = true, palette = FutoonPalette(surface = Color(0xFFFFF8EC), background = Color(0xFFF5ECDD), text = Color(0xFF30251D), secondaryText = Color(0xFF75614E), accent = Color(0xFFA64B37), accentText = Color(0xFF93412F), divider = Color(0xFFD7C7B3)))
        compose.onNodeWithText("原发送选项").assertIsDisplayed()
        compose.onNodeWithText("管理").assertIsDisplayed()
        compose.onNodeWithText(reply.title).assertIsDisplayed()
        screenshot("35-reply-compact-custom-large-font")
    }
    @Test fun legacyOverlongReplyIsPreviewedAndNeverSilentlyTruncated() {
        bridge.saved = config("长".repeat(4097)).json(); root(picker = true)
        compose.onNodeWithText(reply.title).performClick()
        compose.onNodeWithText("填入输入框").assertIsNotEnabled()
        compose.onNodeWithText("原发送选项").assertIsDisplayed()
        assertEquals("", bridge.inserted); assertEquals(0, bridge.writes)
    }
    @Test fun dirtyReplyCancelOffersToKeepEditingWithoutLosingText() {
        bridge.saved = config().json(); root()
        compose.onNodeWithText("一键回复").performClick()
        compose.onNodeWithText("查单").performClick()
        compose.onNodeWithText("付款").performClick()
        compose.onNodeWithText(reply.title).performClick()
        compose.onNode(hasSetTextAction() and hasText("回复内容")).performTextReplacement("新的文案\n尚未保存")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("放弃修改？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNode(hasSetTextAction() and hasText("新的文案\n尚未保存")).assertExists()
        assertEquals(0, bridge.writes)
    }
}
