package com.futoonpay.admin

import com.futoonpay.admin.data.*
import org.junit.Assert.*
import org.junit.Test

/** Pure rules behind the fixes from the real-device review; no UI needed. */
class ReviewFixesTest {
    private fun settlement(status: Int) = SettlementSummary("42", "18", "fixture", "5", "2", "fixture-address", "335.30", "47.56", "", "", "", "", status, true, false, "")

    @Test fun onlyACompletedSettlementIsCalledActuallyArrived() {
        assertEquals("实际到账", settlement(1).actualAmountLabel())
        assertEquals("预计到账", settlement(7).actualAmountLabel())
        assertEquals("预计到账", settlement(0).actualAmountLabel())
        assertEquals("预计到账", settlement(3).actualAmountLabel())
        assertEquals("作废前折算金额", settlement(6).actualAmountLabel())
        assertFalse(settlement(1).actualAmountText().contains("作废"))
        assertTrue(settlement(6).actualAmountText().endsWith("已作废，不代表已到账"))
        assertEquals(settlement(7).displayActualAmount(), settlement(7).actualAmountText())
    }

    @Test fun aPercentageWithoutAKnownSampleIsNeverPresentedAsVerified() {
        val unknownSample = monitorRateDisplay(MonitorWindowMetrics(0.0, null, null, null))
        assertEquals("0%", unknownSample.text); assertFalse(unknownSample.verified); assertTrue(unknownSample.note.contains("无法核对"))
        val empty = monitorRateDisplay(MonitorWindowMetrics(100.0, 0, 0, 0))
        assertEquals("暂无样本", empty.text); assertFalse(empty.verified)
        val noRate = monitorRateDisplay(MonitorWindowMetrics(null, 3, 4, 0))
        assertEquals("暂无统计", noRate.text); assertFalse(noRate.verified)
        val real = monitorRateDisplay(MonitorWindowMetrics(75.0, 3, 4, 1))
        assertEquals("75%", real.text); assertTrue(real.verified); assertTrue(real.note.contains("3/4"))
        val zeroOfTwelve = monitorRateDisplay(MonitorWindowMetrics(0.0, 0, 12, 0))
        assertTrue(zeroOfTwelve.verified); assertTrue(zeroOfTwelve.note.contains("0/12"))
    }

    @Test fun listPagesAlwaysSayWhichMerchantTheyAreLimitedTo() {
        val all = panelScopeLabel(PanelRoute("orders"), "", "")!!
        assertEquals("范围：全部商户", all.text); assertNull(all.switchMerchantId)
        val withCurrent = panelScopeLabel(PanelRoute("settlements"), "1962", "刷课")!!
        assertTrue(withCurrent.text.startsWith("范围：全部商户")); assertEquals("1962", withCurrent.switchMerchantId)
        val locked = panelScopeLabel(PanelRoute("orders", merchantId = "1962"), "1962", "刷课")!!
        assertTrue(locked.text.contains("当前商户")); assertTrue(locked.text.contains("PID 1962")); assertNull(locked.switchMerchantId)
        val other = panelScopeLabel(PanelRoute("domains", merchantId = "77"), "1962", "刷课")!!
        assertTrue(other.text.contains("不是当前会话商户"))
        assertNull(panelScopeLabel(PanelRoute("recognition"), "1962", "x"))
        assertNull(panelScopeLabel(PanelRoute("orders", orderId = "S1"), "1962", "x"))
        assertNull(panelScopeLabel(PanelRoute("orders", module = "quick-actions"), "1962", "x"))
    }

    @Test fun authorizedDomainsAreNormalisedAndOddInputIsRejected() {
        assertEquals("pay.example.com", parseAuthorizedDomain("  HTTPS://Pay.Example.com/ ").domain)
        assertEquals("仅此域名", parseAuthorizedDomain("pay.example.com").scope)
        val wildcard = parseAuthorizedDomain("*.example.com")
        assertEquals("*.example.com", wildcard.domain); assertTrue(wildcard.scope.contains("example.com"))
        listOf("", "example", "a b.com", "pay.example.com/path", "pay.example.com:8080", "exa!mple.com", "*example.com", "-a.com", "a..com").forEach {
            assertNull("should reject '$it'", parseAuthorizedDomain(it).domain)
        }
    }

    @Test fun revokingAnApprovedDomainSaysItStopsWorkingNow() {
        assertTrue(domainActionImpact(1, 2, false).contains("立即失效"))
        assertTrue(domainActionImpact(0, 2, false).contains("不会被授权"))
        assertTrue(domainActionImpact(1, null, true).contains("不可恢复"))
        assertTrue(domainActionImpact(0, 1, false).contains("可用于"))
    }

    @Test fun machineStatusCodesReadAsWordsAndUnknownTextIsKept() {
        assertEquals("事故隔离中", readableChannelStatus("incident_quarantined"))
        assertEquals("正常", readableChannelStatus("正常"))
        assertEquals("1", readableChannelStatus("1"))
        assertEquals("some new state", readableChannelStatus("some_new_state"))
    }

    @Test fun theStripCallsRememberedChannelsAssociatedWithTheGroup() {
        assertEquals("已关联本群", channelSourceLabel("已记住"))
        assertEquals("仅本次", channelSourceLabel("仅本次"))
        assertEquals("来自订单", channelSourceLabel("来自订单"))
    }
}
