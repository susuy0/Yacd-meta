package com.futoonpay.admin.data

import java.math.BigDecimal

/** Legacy ajax_user.php recharge contract: 0 adds, 1 deducts; amount stays positive. */
enum class MerchantBalanceOperation(val action: String, val apiCode: String, val label: String, val sign: String) {
    ADD("add", "0", "增加余额", "+"),
    DEDUCT("reduce", "1", "扣除余额", "−");

    companion object {
        fun fromAction(action: String): MerchantBalanceOperation? = entries.firstOrNull { it.action == action }
    }
}

fun merchantBalanceAmount(input: String): BigDecimal? {
    val text = input.trim()
    if (!Regex("[0-9]+(?:\\.[0-9]{1,2})?").matches(text)) return null
    return text.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
}

internal fun merchantBalanceFields(uid: String, action: String, input: String): Map<String, String>? {
    if (!Regex("[0-9]+").matches(uid) || uid.toBigIntegerOrNull()?.signum() != 1) return null
    val operation = MerchantBalanceOperation.fromAction(action) ?: return null
    val amount = merchantBalanceAmount(input) ?: return null
    return mapOf("uid" to uid, "actdo" to operation.apiCode, "rmb" to amount.setScale(2).toPlainString())
}
