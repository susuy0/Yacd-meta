package com.futoonpay.admin

import com.futoonpay.admin.appearance.ChatScale
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatScaleTest {
    @Test fun phoneShortSideBecomesThe390PointCanvas() {
        assertEquals(443, ChatScale.densityDpi(1080, 2.625f))
        assertEquals(369, ChatScale.densityDpi(900, 2.0f))
    }

    @Test fun tabletsAndUnknownSizesKeepTheSystemDensity() {
        assertEquals(0, ChatScale.densityDpi(1600, 2.0f))
        assertEquals(0, ChatScale.densityDpi(0, 2.625f))
        assertEquals(0, ChatScale.densityDpi(1080, 0f))
    }
}
