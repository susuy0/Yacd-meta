package com.futoonpay.admin.data

import java.math.BigDecimal
import java.math.RoundingMode

data class OrderPageMetrics(val count: Int, val successful: Int, val rate: Double?, val amounts: Map<String, BigDecimal>)

fun orderPageMetrics(orders: List<OrderSummary>): OrderPageMetrics {
    val amounts = linkedMapOf<String, BigDecimal>()
    orders.forEach { order ->
        val code = order.currency.trim().uppercase().ifBlank { "CNY" }
        val amount = order.amount.replace(",", "").toBigDecimalOrNull()
        if (amount != null) amounts[code] = (amounts[code] ?: BigDecimal.ZERO).add(amount)
    }
    val successful = orders.count { it.status == 1 }
    return OrderPageMetrics(orders.size, successful, if (orders.isEmpty()) null else successful * 100.0 / orders.size, amounts)
}

fun displayOrderAmount(amount: String, currency: String): String {
    val code = currency.trim().uppercase().ifBlank { "CNY" }
    val formatted = amount.replace(",", "").toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)?.toPlainString() ?: return "—"
    return if (code == "CNY") "¥$formatted" else "$formatted $code"
}
