package com.futoonpay.admin

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.Spanned
import android.text.StaticLayout
import android.text.Layout
import android.text.TextPaint
import android.text.style.ReplacementSpan
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatToolsConfigTest {
    @Test fun tagsMatchTheWholeNumericTelegramIdAndKeepLargeIdsExact() {
        val config = ChatToolsConfig(tags = listOf(ChatTag(text = "商户", telegramIds = listOf("5824870757")), ChatTag(text = "VIP", telegramIds = listOf("9223372036854775807"))))
        assertEquals("商户", config.tagFor("5824870757")); assertEquals("", config.tagFor("582487075"))
        assertEquals("VIP", ChatToolsConfig.parse(config.json()).tagFor("9223372036854775807"))
    }
    @Test fun idInputCanonicalizesAndDeduplicatesButRejectsInvalidOrOverflowingIds() {
        assertEquals(listOf("5824870757", "123"), ChatToolsConfig.parseIds("005824870757，123\n5824870757"))
        listOf("", "0", "-100", "5824abc", "9223372036854775808", "1,wrong").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { ChatToolsConfig.parseIds(value) }
        }
    }
    @Test fun tabCategoryAndReplyHierarchyRoundTripsWithoutLosingFormatting() {
        val text = "  请提供付款凭证\n订单号：🙂\n"
        val data = ChatToolsConfig(tabs = listOf(ReplyTab(title = "查单", categories = listOf(ReplyCategory(title = "付款", replies = listOf(QuickReply(title = "凭证", body = text)))))))
        assertEquals(data, ChatToolsConfig.parse(data.json()))
        assertEquals(text, ChatToolsConfig.parse(data.json()).tabs.single().categories.single().replies.single().body)
    }
    @Test fun sameDisplayNamesNeverOverwriteDifferentTabsOrReplies() {
        val one = ReplyTab(title = "查单"); val two = ReplyTab(title = "查单")
        val data = ChatToolsConfig(tabs = listOf(one, two))
        assertNotEquals(one.id, two.id); assertEquals(2, ChatToolsConfig.parse(data.json()).tabs.size)
    }
    @Test fun invalidOrNewerConfigurationCannotBeSilentlyReplacedWithEmptyPreferences() {
        assertThrows(Exception::class.java) { ChatToolsConfig.parse("{bad") }
        assertThrows(IllegalArgumentException::class.java) { ChatToolsConfig.parse("{\"version\":2}") }
        val same = ReplyTab(id = "same", title = "x")
        assertThrows(IllegalArgumentException::class.java) { ChatToolsConfig.parse(ChatToolsConfig(tabs = listOf(same, same)).json()) }
    }
    @Test fun badgesRemainVisibleAndWithinTheLineForLongChineseAndRtlNames() {
        val paint = TextPaint().apply { textSize = 32f; isAntiAlias = true }
        for (title in listOf("这是一个非常长的会话名称".repeat(8), "محادثة طويلة جدا".repeat(8))) for (dark in listOf(false, true)) {
            val decorated = ChatTagRenderer.decorate(title, "商户 VIP".repeat(10), paint, 300, 2f, dark)!!
            assertTrue(decorated is Spanned)
            assertEquals(1, (decorated as Spanned).getSpans(0, decorated.length, ReplacementSpan::class.java).size)
            val layout = StaticLayout.Builder.obtain(decorated, 0, decorated.length, paint, 300).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            assertEquals(1, layout.lineCount)
            assertTrue(layout.getLineWidth(0) <= 301f)
            val bitmap = Bitmap.createBitmap(320, 100, Bitmap.Config.ARGB_8888)
            layout.draw(Canvas(bitmap))
            val pixels = IntArray(320 * 100); bitmap.getPixels(pixels, 0, 320, 0, 0, 320, 100)
            assertTrue(pixels.any { it != 0 }); bitmap.recycle()
        }
    }
    @Test fun absentTagsDoNotAlterTheOriginalNameOrAddEmptyBadges() {
        val paint = TextPaint().apply { textSize = 16f }
        assertEquals("张三", ChatTagRenderer.decorate("张三", "", paint, 300, 1f, false))
        assertEquals("张三", ChatTagRenderer.decorate("张三", "Tag", paint, 0, 1f, false))
    }
}
