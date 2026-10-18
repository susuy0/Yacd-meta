package com.futoonpay.admin.data

import java.math.BigDecimal
import java.time.LocalDate
import org.json.JSONObject

enum class DatePreset(val label: String, val days: Long) {
    TODAY("今日", 0), YESTERDAY("昨日", 1), WEEK("近7天", 6)
}

fun OrderSearchFilters.withPreset(preset: DatePreset, today: LocalDate = LocalDate.now()): OrderSearchFilters {
    val end = if (preset == DatePreset.YESTERDAY) today.minusDays(1) else today
    return copy(startDate = today.minusDays(preset.days).toString(), endDate = end.toString())
}

fun OrderSearchFilters.activeChips(): List<Pair<String, String>> = buildList {
    if (value.isNotBlank()) add("value" to value)
    if (merchantId.isNotBlank()) add("merchant" to "商户 #$merchantId")
    if (channelId.isNotBlank()) add("channel" to "通道 #$channelId")
    if (paymentType != "0") add("type" to "支付方式 $paymentType")
    if (startDate.isNotBlank() || endDate.isNotBlank()) add("date" to "$startDate 至 ${endDate.ifBlank { "现在" }}")
}

fun OrderSearchFilters.withoutChip(key: String): OrderSearchFilters = when (key) {
    "value" -> copy(value = "")
    "merchant" -> copy(merchantId = "")
    "channel" -> copy(channelId = "")
    "type" -> copy(paymentType = "0")
    "date" -> copy(startDate = "", endDate = "")
    else -> this
}

fun validRefundAmount(input: String, maximum: String): Boolean {
    val amount = input.trim().toBigDecimalOrNull() ?: return false
    val max = maximum.replace(",", "").trim().toBigDecimalOrNull() ?: return false
    return amount > BigDecimal.ZERO && amount <= max && amount.stripTrailingZeros().scale() <= 2
}

fun SettlementSummary.displayAmount(): String = displayOrderAmount(amount, currency)
fun SettlementSummary.displayFee(): String = fee.ifBlank {
    val original = amount.replace(",", "").toBigDecimalOrNull()
    val actual = actualAmount.replace(",", "").toBigDecimalOrNull()
    if (original == null || actual == null || actual > original) "—" else original.subtract(actual).toPlainString()
}

internal fun JSONObject.currencyAmounts(key: String): Map<String, String> {
    val objectValue = optJSONObject(key) ?: return emptyMap()
    return objectValue.keys().asSequence().associateWith { objectValue.text(it) }
        .filterValues { it.isNotBlank() }
}

internal fun NativeField.canPersistDraft(): Boolean = !sensitive && !readOnly && type != "password" && type != "hidden" &&
    !Regex("pass|secret|token|api.?key|private.?key|credential", RegexOption.IGNORE_CASE).containsMatchIn(name)

internal fun NativeForm.draftValues(): Map<String, String> = fields.filter { it.canPersistDraft() }.associate { it.name to it.value }

internal fun NativeForm.restoreDraft(saved: Map<String, String>): NativeForm = copy(fields = fields.map { field ->
    val value = saved[field.name]
    when {
        !field.canPersistDraft() || value == null -> field
        field.type in setOf("select", "radio") && field.options.isNotEmpty() && field.options.none { it.value == value } -> field
        else -> field.copy(value = value)
    }
})

internal fun NativeForm.hasAccountInput(): Boolean = fields.any {
    it.type != "hidden" && it.value.isNotBlank() && (it.sensitive || it.name.contains("appid", true) || it.name.contains("merchant", true))
}
