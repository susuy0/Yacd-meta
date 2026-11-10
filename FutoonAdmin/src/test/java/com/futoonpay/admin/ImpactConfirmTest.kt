package com.futoonpay.admin

import com.futoonpay.admin.ui.*
import org.junit.Assert.*
import org.junit.Test

class ImpactConfirmTest {
    @Test fun typedStateFollowsTheDesignWording() {
        assertEquals(TypedState.EMPTY, typedState("", "10482"));assertEquals(TypedState.EMPTY, typedState("  ", "10482"))
        assertEquals(TypedState.MISMATCH, typedState("1048", "10482"));assertEquals(TypedState.MATCHED, typedState(" 10482 ", "10482"))
        assertEquals(TypedState.MISMATCH, typedState("代收-A", "代收-A2"));assertEquals(TypedState.MATCHED, typedState("代收-A2", "代收-A2"))
    }
    @Test fun onlyTheRiskyDirectionOfEachMerchantActionNeedsTheTypedPid() {
        assertTrue(requiresTypedPid("toggleAccount", true, true, true));assertFalse(requiresTypedPid("toggleAccount", false, true, true))
        assertTrue(requiresTypedPid("togglePay", true, true, true));assertFalse(requiresTypedPid("togglePay", true, false, true))
        assertTrue(requiresTypedPid("toggleSettle", true, true, true));assertFalse(requiresTypedPid("toggleSettle", true, true, false))
        assertTrue(requiresTypedPid("group", false, false, false));assertFalse(requiresTypedPid("edit", true, true, true))
    }
    @Test fun confirmButtonLabelFollowsSubmitCooldownAndReady() {
        assertEquals("正在暂停…", confirmLabel("确认暂停", "正在暂停…", true, 0, true))
        assertEquals("3 秒后可确认", confirmLabel("确认暂停", "正在暂停…", false, 3, true))
        assertEquals("确认暂停", confirmLabel("确认暂停", "正在暂停…", false, 3, false))
        assertEquals("确认暂停", confirmLabel("确认暂停", "正在暂停…", false, 0, true))
    }
    @Test fun impactTextOnlyStatesWhatTheBackendDoes() {
        assertTrue(merchantImpact("toggleAccount", "星河电商", "386210.44").contains("¥386210.44"))
        assertFalse(merchantImpact("toggleAccount", "星河电商", "").contains("¥"))
        for (a in listOf("togglePay", "toggleSettle", "group")) assertTrue(merchantImpact(a, "星河电商", "").startsWith("星河电商"))
    }
}
