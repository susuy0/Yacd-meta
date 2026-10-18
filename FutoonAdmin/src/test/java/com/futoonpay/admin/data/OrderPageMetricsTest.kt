package com.futoonpay.admin.data

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class OrderPageMetricsTest {
    private fun order(amount: String, currency: String, status: Int = 1) = OrderSummary("S1", "", "1", "", "", amount, "", "", "", "", "", "", "", status, 0, currency)
    @Test fun separatesCurrenciesAndUsesDecimalArithmetic() {
        val result = orderPageMetrics(listOf(order("0.10", "CNY"), order("0.20", "CNY"), order("500.00", "USDT", 0)))
        assertEquals(BigDecimal("0.30"), result.amounts["CNY"])
        assertEquals(BigDecimal("500.00"), result.amounts["USDT"])
        assertEquals(3, result.count); assertEquals(2, result.successful)
        assertEquals(66.666, result.rate!!, 0.001)
        assertEquals("500.00 USDT", displayOrderAmount("500", "USDT"))
    }
    @Test fun emptyPageHasNoSuccessRate() { assertNull(orderPageMetrics(emptyList()).rate) }
}
