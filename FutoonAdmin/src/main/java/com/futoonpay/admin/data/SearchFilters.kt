package com.futoonpay.admin.data

internal const val SETTLEMENT_CHECKED_RESULT = "checked"

data class OrderSearchFilters(
    val column: String = "trade_no",
    val value: String = "",
    val merchantId: String = "",
    val paymentType: String = "0",
    val channelId: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val status: Int = -1,
) {
    fun toRequest(): Map<String, String> = buildMap {
        put("column", column)
        if (value.isNotBlank()) put("value", value.trim())
        if (merchantId.isNotBlank()) put("uid", merchantId.trim())
        put("type", paymentType)
        if (channelId.isNotBlank()) put("channel", channelId.trim())
        if (startDate.isNotBlank()) put("starttime", startDate.trim())
        if (endDate.isNotBlank()) put("endtime", endDate.trim())
        put("dstatus", status.toString())
    }
}

data class MerchantSearchFilters(
    val column: String = "uid",
    val value: String = "",
    val groupId: String = "",
    val status: String = "0",
) {
    fun toRequest(): Map<String, String> = buildMap {
        put("column", column)
        if (value.isNotBlank()) put("value", value.trim())
        if (groupId.isNotBlank()) put("gid", groupId)
        put("dstatus", status)
    }
}

data class SettlementSearchFilters(
    val value: String = "",
    val merchantId: String = "",
    val settlementType: String = "0",
    val status: Int = -1,
    val usdtRate: String = "",
) {
    fun toRequest(): Map<String, String> = buildMap {
        if (value.isNotBlank()) put("value", value.trim())
        if (merchantId.isNotBlank()) put("uid", merchantId.trim())
        put("type", settlementType)
        put("dstatus", status.toString())
        if (usdtRate.isNotBlank()) put("usdt_rate", usdtRate.trim())
    }
}

internal fun orderStatusLabel(status: Int): String = when (status) {
    1 -> "已支付"
    2 -> "已退款"
    3 -> "已冻结"
    0 -> "待支付"
    else -> "状态 $status"
}

internal fun orderSettlementStatusLabel(status: Int): String = when (status) {
    1 -> "待结算"
    2 -> "结算成功"
    3 -> "结算失败"
    else -> ""
}

internal fun settlementStatusLabel(status: Int): String = when (status) {
    0 -> "待结算"
    1 -> "已完成"
    2 -> "正在结算"
    3 -> "结算失败"
    else -> "状态 $status"
}

internal fun merchantPaymentStatusLabel(status: Int): String = when (status) {
    2 -> "未审核"
    1 -> "支付正常"
    else -> "支付关闭"
}

internal fun domainAuditStatusLabel(status: Int): String = when (status) {
    1 -> "已通过"
    2 -> "已拒绝"
    0 -> "待审核"
    else -> "状态 $status"
}
