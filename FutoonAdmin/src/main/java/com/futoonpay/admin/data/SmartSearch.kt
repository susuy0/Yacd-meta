package com.futoonpay.admin.data

/** One search box for the order list: the typed text decides the search column, so nobody has to pick a field first. */
internal object SmartSearch {
    private val systemTradeNo = Regex("^20\\d{15,17}$")
    private val merchantUid = Regex("^\\d{1,8}$")
    private val amount = Regex("^\\d+\\.\\d{1,2}$")
    private val domain = Regex("^[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$")
    private val merchantTradeNo = Regex("^\\d{9,}$|^[A-Za-z0-9_-]{8,}$")

    private val telegramId = Regex("^\\d{9,13}$")
    private val apiKey = Regex("^[0-9a-fA-F]{32}$")
    private val walletAddress = Regex("^T[A-Za-z0-9]{33}$|^0x[0-9a-fA-F]{40}$")

    /** Merchant list: id, Telegram ID, email, key, wallet address, domain, otherwise the remark/name. */
    fun merchants(raw: String, current: MerchantSearchFilters): MerchantSearchFilters {
        val text = raw.trim()
        val base = current.copy(column = "uid", value = "")
        return when {
            text.isEmpty() -> base
            merchantUid.matches(text) -> base.copy(value = text)
            telegramId.matches(text) -> base.copy(column = "telegram_id", value = text)
            text.contains('@') && text.contains('.') -> base.copy(column = "email", value = text)
            apiKey.matches(text) -> base.copy(column = "key", value = text)
            walletAddress.matches(text) -> base.copy(column = "account", value = text)
            domain.matches(text) -> base.copy(column = "url", value = text)
            else -> base.copy(column = "username", value = text)
        }
    }

    /** Settlement list: a short number is a merchant id, anything else (batch, account, id) goes to the value field. */
    fun settlements(raw: String, current: SettlementSearchFilters): SettlementSearchFilters {
        val text = raw.trim()
        val base = current.copy(value = "", merchantId = "")
        return when {
            text.isEmpty() -> base
            merchantUid.matches(text) -> base.copy(merchantId = text)
            else -> base.copy(value = text)
        }
    }

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
