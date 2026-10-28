package com.futoonpay.admin

import com.futoonpay.admin.data.OrderSearchFilters
import com.futoonpay.admin.data.SmartSearch
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartSearchTest {
    private val start = OrderSearchFilters(status = 1, channelId = "42")

    @Test fun systemOrderNumbersSearchByTradeNo() {
        val f = SmartSearch.orders("2026102817405455274", start)
        assertEquals("trade_no" to "2026102817405455274", f.column to f.value)
    }

    @Test fun shortNumbersAreMerchantIds() {
        val f = SmartSearch.orders("2402", start)
        assertEquals("2402", f.merchantId); assertEquals("", f.value)
    }

    @Test fun decimalsSearchTheAmount() {
        val f = SmartSearch.orders("8.80", start)
        assertEquals("money" to "8.80", f.column to f.value)
    }

    @Test fun domainsAndMerchantOrderNumbersAndNames() {
        assertEquals("domain", SmartSearch.orders("api.sakura-cat.club", start).column)
        assertEquals("out_trade_no", SmartSearch.orders("2026102817104809357411246", start).column)
        assertEquals("out_trade_no", SmartSearch.orders("VE7A5CEED202610281739JTHL", start).column)
        assertEquals("name", SmartSearch.orders("VIP 月卡", start).column)
    }

    @Test fun statusAndOtherFiltersSurviveAndBlankClears() {
        val f = SmartSearch.orders("8.80", start)
        assertEquals(1, f.status); assertEquals("42", f.channelId)
        val cleared = SmartSearch.orders("  ", f)
        assertEquals("", cleared.value); assertEquals("", cleared.merchantId); assertEquals(1, cleared.status)
    }
}

class SmartSearchListsTest {
    private val m = com.futoonpay.admin.data.MerchantSearchFilters(groupId = "21", status = "status_1")
    private val s = com.futoonpay.admin.data.SettlementSearchFilters(status = 0, settlementType = "4")

    @Test fun merchantsPickTheRightColumn() {
        assertEquals("uid" to "2402", SmartSearch.merchants("2402", m).let { it.column to it.value })
        assertEquals("telegram_id", SmartSearch.merchants("6562055782", m).column)
        assertEquals("email", SmartSearch.merchants("a@b.com", m).column)
        assertEquals("key", SmartSearch.merchants("0123456789abcdef0123456789abcdef", m).column)
        assertEquals("account", SmartSearch.merchants("T" + "A".repeat(33), m).column)
        assertEquals("url", SmartSearch.merchants("api.sakura-cat.club", m).column)
        assertEquals("username", SmartSearch.merchants("健力宝", m).column)
    }

    @Test fun merchantFiltersSurviveAndBlankClears() {
        val f = SmartSearch.merchants("2402", m)
        assertEquals("21", f.groupId); assertEquals("status_1", f.status)
        assertEquals("", SmartSearch.merchants(" ", f).value)
    }

    @Test fun settlementsUseMerchantIdOrValue() {
        assertEquals("2402", SmartSearch.settlements("2402", s).merchantId)
        val v = SmartSearch.settlements("TXabc", s)
        assertEquals("TXabc", v.value); assertEquals("", v.merchantId)
        assertEquals(0, v.status); assertEquals("4", v.settlementType)
    }
}
