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
