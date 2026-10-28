package com.futoonpay.admin

import android.graphics.RectF
import com.futoonpay.admin.appearance.ChatComposerPill
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=android.app.Application::class, qualifiers="w390dp-h844dp-mdpi")
class ChatComposerPillTest {
    private val density = 2.75f
    private fun rect(frameHeightDp: Float): RectF = RectF().also { ChatComposerPill.pillRect(0f, 0f, 300f, frameHeightDp * density, density, it) }

    @Test fun oneLinePillSharesTheCentreLineOfThe48dpControlRow() {
        val frame = 68f * density
        val pill = rect(68f)
        assertEquals(32f * density, pill.height(), 0.01f)
        // plus/send buttons are 48dp tall and bottom aligned, so their centre is 24dp above the frame bottom
        assertEquals(frame - 24f * density, (pill.top + pill.bottom) / 2f, 0.01f)
    }

    @Test fun extraTextLinesGrowThePillUpwardOnly() {
        val one = rect(68f)
        val two = rect(90f)
        // both frames are bottom aligned, so the gap under the pill must not change
        assertEquals(68f * density - one.bottom, 90f * density - two.bottom, 0.01f)
        assertEquals(22f * density, two.height() - one.height(), 0.01f)
    }
}
