package com.futoonpay.admin.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchFiltersTest {
    @Test
    fun orderSearchMatchesOfficialFormParameters() {
        val request = OrderSearchFilters(
            column = "out_trade_no",
            value = "M-100",
            merchantId = "1001",
            paymentType = "2",
            channelId = "8",
            startDate = "2026-08-01",
            endDate = "2026-08-29",
            status = 3,
        ).toRequest()

        assertEquals("out_trade_no", request["column"])
        assertEquals("M-100", request["value"])
        assertEquals("1001", request["uid"])
        assertEquals("2", request["type"])
        assertEquals("8", request["channel"])
        assertEquals("2026-08-01", request["starttime"])
        assertEquals("2026-08-29", request["endtime"])
        assertEquals("3", request["dstatus"])
    }

    @Test
    fun merchantSearchMatchesOfficialFormParameters() {
        val request = MerchantSearchFilters(
            column = "telegram_id",
            value = "998877",
            groupId = "2",
            status = "pay_2",
        ).toRequest()

        assertEquals(
            mapOf("column" to "telegram_id", "value" to "998877", "gid" to "2", "dstatus" to "pay_2"),
            request,
        )
    }

    @Test
    fun settlementSearchMatchesOfficialFormParameters() {
        val request = SettlementSearchFilters(
            value = "Alice",
            merchantId = "1001",
            settlementType = "5",
            status = 2,
            usdtRate = "7.25",
        ).toRequest()

        assertEquals(
            mapOf(
                "value" to "Alice",
                "uid" to "1001",
                "type" to "5",
                "dstatus" to "2",
                "usdt_rate" to "7.25",
            ),
            request,
        )
    }

    @Test
    fun officialStatusMappingsAreExact() {
        assertEquals("待结算", settlementStatusLabel(0))
        assertEquals("已完成", settlementStatusLabel(1))
        assertEquals("正在结算", settlementStatusLabel(2))
        assertEquals("结算失败", settlementStatusLabel(3))
        assertEquals("未审核", merchantPaymentStatusLabel(2))
        assertEquals("已通过", domainAuditStatusLabel(1))
        assertEquals("已拒绝", domainAuditStatusLabel(2))
        assertEquals("checked", SETTLEMENT_CHECKED_RESULT)
    }
}
