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
    private fun pill(fieldHeightDp: Float, frameHeightDp: Float = 48f): RectF {
        // field sits 8dp above a bottom aligned frame
        val frameBottom = frameHeightDp * density
        val fieldBottom = frameBottom - 8f * density
        return RectF().also { ChatComposerPill.pillRect(0f, 300f, fieldBottom - fieldHeightDp * density, fieldBottom, density, it) }
    }

    @Test fun oneLinePillSharesTheCentreLineOfThe48dpControlRow() {
        val p = pill(32f)
        assertEquals(32f * density, p.height(), 0.01f)
        // plus/send buttons are 48dp tall and bottom aligned: their centre is 24dp above the frame bottom
        assertEquals(48f * density - 24f * density, (p.top + p.bottom) / 2f, 0.01f)
    }

    @Test fun extraTextLinesGrowThePillUpwardOnly() {
        val one = pill(32f, 48f)
        val two = pill(54f, 70f)
        assertEquals(48f * density - one.bottom, 70f * density - two.bottom, 0.01f)
        assertEquals(22f * density, two.height() - one.height(), 0.01f)
    }

    @Test fun aShortFieldStillGetsAFullPill() {
        assertEquals(32f * density, pill(20f).height(), 0.01f)
    }
}
