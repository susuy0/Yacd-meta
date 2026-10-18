package com.futoonpay.admin.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class OrderChannelStatsTest {
    private val now = LocalDateTime.of(2026, 8, 30, 2, 0, 0)

    @Test
    fun buildsFiveMinuteCountsAndBucketsFromCurrentPageOnly() {
        val stats = currentPageChannelStats(
            listOf(
                order("A", "2", "2026-08-30 01:59:30", 1),
                order("B", "2", "2026-08-30 01:58:10", 0),
                order("C", "2", "2026-08-30 01:56:20", 2),
                order("OLD", "2", "2026-08-30 01:40:00", 1),
            ),
            now,
        ).getValue("2")

        assertEquals(3, stats.total)
        assertEquals(1, stats.successful)
        assertEquals(1, stats.processing)
        assertEquals(1, stats.refunded)
        assertEquals(33.333, stats.fiveMinuteRate!!, 0.01)
        assertEquals(listOf(1, 1, 0, 1, 0), stats.minuteBuckets)
        assertEquals(listOf("A", "B", "C"), stats.recentOrders.map { it.tradeNo })
    }

    @Test
    fun officialFiveMinuteRateWinsWhileCountsRemainPageSamples() {
        val payload = JSONObject().put(
            "data",
            JSONArray().put(
                JSONObject()
                    .put("channel_id", "2")
                    .put("channel_name", "富通支付宝")
                    .put("rate_5min", "88")
                    .put("rate_30min", "91")
                    .put("rate_1hour", "93.5")
                    .put("rate_24hour", "96"),
            ),
        )
        val page = currentPageChannelStats(listOf(order("A", "2", "2026-08-30 01:59:30", 1)), now)
        val result = mergeOrderChannelStats(listOf("2"), page, parseOfficialChannelRates(payload))
        val stat = result.stats.getValue("2")

        assertFalse(result.fallbackUsed)
        assertEquals(ChannelRateSource.OFFICIAL, stat.source)
        assertEquals(88.0, stat.fiveMinuteRate!!, 0.001)
        assertEquals(91.0, stat.thirtyMinuteRate!!, 0.001)
        assertEquals(1, stat.successful)
        assertFalse(stat.countsOfficial)
    }

    @Test
    fun officialCountsAreUsedOnlyWhenReturnedTogether() {
        val payload = JSONObject().put("data", JSONArray().put(JSONObject()
            .put("id", "2").put("success_rate_5min", "92")
            .put("success_count_5min", "46").put("total_count_5min", "50").put("processing_count_5min", "3")))
        val result = mergeOrderChannelStats(listOf("2"), emptyMap(), parseOfficialChannelRates(payload))
        val stat = result.stats.getValue("2")
        assertTrue(stat.countsOfficial)
        assertEquals(46, stat.successful)
        assertEquals(50, stat.total)
        assertEquals(3, stat.processing)
    }

    @Test
    fun missingOfficialChannelIsExplicitCurrentPageFallback() {
        val page = currentPageChannelStats(listOf(order("A", "7", "2026-08-30 01:59:30", 1)), now)
        val result = mergeOrderChannelStats(listOf("7"), page, emptyMap(), "官网接口失败")

        assertTrue(result.fallbackUsed)
        assertEquals(ChannelRateSource.CURRENT_PAGE, result.stats.getValue("7").source)
        assertEquals("官网接口失败", result.stats.getValue("7").note)
    }

    private fun order(tradeNo: String, channel: String, createdAt: String, status: Int) = OrderSummary(
        tradeNo = tradeNo,
        merchantTradeNo = "",
        merchantId = "1001",
        domain = "",
        name = "测试订单",
        amount = "10.00",
        paidAmount = if (status == 1) "10.00" else "",
        merchantIncome = "",
        paymentType = "支付宝",
        channelId = channel,
        plugin = "futong",
        createdAt = createdAt,
        completedAt = "",
        status = status,
        settlementStatus = 0,
    )
}
