package com.futoonpay.admin

import org.junit.Assert.*
import org.junit.Test

class BusinessTemplateEditorTest {
    @Test fun everyVariableHasASampleSoThePreviewNeverShowsAGap() {
        assertEquals(businessReplyVariables.toSet(), businessReplySamples.keys)
        assertTrue(businessReplySamples.values.all { it.isNotBlank() })
    }
    @Test fun previewFillsKnownVariablesAndMarksUnknownOnes() {
        val text = renderBusinessReply("订单 {系统订单号}（{金额} {币种}）{乱写}", businessReplySamples)
        assertEquals("订单 P2611100093417（2,480.00 CNY）未提供", text)
    }
}
