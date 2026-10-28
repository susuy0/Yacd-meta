package com.futoonpay.admin.data

/** One search box for the order list: the typed text decides the search column, so nobody has to pick a field first. */
internal object SmartSearch {
    private val systemTradeNo = Regex("^20\\d{15,17}$")
    private val merchantUid = Regex("^\\d{1,8}$")
    private val amount = Regex("^\\d+\\.\\d{1,2}$")
    private val domain = Regex("^[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$")
    private val merchantTradeNo = Regex("^\\d{9,}$|^[A-Za-z0-9_-]{8,}$")

    /** Keeps the chosen status; everything else comes from [raw]. Blank text clears the query. */
    fun orders(raw: String, current: OrderSearchFilters): OrderSearchFilters {
        val text = raw.trim()
        val base = current.copy(column = "trade_no", value = "", merchantId = "")
        return when {
            text.isEmpty() -> base
            systemTradeNo.matches(text) -> base.copy(value = text)
            merchantUid.matches(text) -> base.copy(merchantId = text)
            amount.matches(text) -> base.copy(column = "money", value = text)
            domain.matches(text) -> base.copy(column = "domain", value = text)
            merchantTradeNo.matches(text) -> base.copy(column = "out_trade_no", value = text)
            else -> base.copy(column = "name", value = text)
        }
    }
}
