package com.futoonpay.admin

import android.app.Application
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.URLSpan
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ChatOrderHintMarksTest {
    @Test fun verifiedOrderSurvivesEntityFormattingAndRepeatedLayoutRebuilds() {
        val owner = Any(); val raw = "订单号：202610301234567890"; val text = SpannableStringBuilder(raw)
        val order = URLSpan("futoon://order-actions")
        ChatOrderHintMarks.put(owner, raw, 4, raw.length, order)
        assertTrue(ChatOrderHintMarks.restore(owner, text))
        // Telegram removes overlapping URLSpans when reapplying bold/code entities.
        text.removeSpan(order); text.setSpan(StyleSpan(Typeface.BOLD), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        assertTrue(ChatOrderHintMarks.restore(owner, text))
        assertTrue(ChatOrderHintMarks.restore(owner, text))
        assertEquals(1, text.getSpans(0, text.length, URLSpan::class.java).size)
        assertEquals(4, text.getSpanStart(order)); assertEquals(1, text.getSpans(0,text.length,StyleSpan::class.java).size)
        ChatOrderHintMarks.clear(owner, text); assertFalse(ChatOrderHintMarks.restore(owner, text))
        assertEquals(0, text.getSpans(0,text.length,URLSpan::class.java).size)
    }
    @Test fun exactVerifiedOrderOverridesAutoPhoneLinkButKeepsExternalLinks() {
        val raw = "202610301234567890"; val owner = Any(); val text = SpannableStringBuilder(raw)
        text.setSpan(URLSpan("tel:$raw"), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val order = URLSpan("futoon://order-actions"); ChatOrderHintMarks.put(owner,raw,0,raw.length,order)
        assertTrue(ChatOrderHintMarks.restore(owner,text)); assertEquals(order,text.getSpans(0,text.length,URLSpan::class.java).single())
        ChatOrderHintMarks.clear(owner,text)
        assertEquals("tel:$raw",text.getSpans(0,text.length,URLSpan::class.java).single().url)
        text.getSpans(0,text.length,URLSpan::class.java).forEach { text.removeSpan(it) }
        val external = URLSpan("https://example.test/order"); text.setSpan(external,0,text.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ChatOrderHintMarks.put(owner,raw,0,raw.length,order)
        assertFalse(ChatOrderHintMarks.restore(owner,text)); assertEquals(external,text.getSpans(0,text.length,URLSpan::class.java).single())
        ChatOrderHintMarks.clear(owner,text)
    }
    @Test fun mediaCaptionUsesItsOwnDisplayTextAndClearsAlongsideTheBody() {
        val owner = Any(); val body = SpannableStringBuilder("Photo"); val caption = SpannableStringBuilder("订单号：202610301234567890")
        val order = URLSpan("futoon://order-actions"); ChatOrderHintMarks.put(owner,caption.toString(),4,caption.length,order)
        assertFalse(ChatOrderHintMarks.restore(owner,body)); assertTrue(ChatOrderHintMarks.restore(owner,caption))
        ChatOrderHintMarks.clear(owner,body,caption); assertEquals(-1,caption.getSpanStart(order))
        assertFalse(ChatOrderHintMarks.restore(owner,caption))
    }
    @Test fun editedTextAndAnotherMessageNeverReceiveStaleActions() {
        val owner = Any(); val text = SpannableStringBuilder("ORDER1234")
        ChatOrderHintMarks.put(owner,text.toString(),0,text.length,URLSpan("futoon://order-actions"))
        assertFalse(ChatOrderHintMarks.restore(Any(),text))
        text.replace(0,text.length,"CHANGED12"); assertFalse(ChatOrderHintMarks.restore(owner,text))
        assertEquals(0,text.getSpans(0,text.length,URLSpan::class.java).size)
        ChatOrderHintMarks.clear(owner,text)
    }
}
