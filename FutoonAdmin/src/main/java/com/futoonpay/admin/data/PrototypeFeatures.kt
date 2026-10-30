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
// money and realmoney can be denominated in different assets. Never infer a fee by subtraction.
fun SettlementSummary.displayFee(): String = fee.ifBlank { "—" }
fun SettlementSummary.actualCurrency(): String = if (payType in setOf("4", "5", "6")) "USDT" else currency
fun SettlementSummary.displayActualAmount(): String = displayOrderAmount(actualAmount, actualCurrency())
fun SettlementSummary.displayStatus(): String = when {
    status == 3 && dispatchState == "held" -> "待审核"
    status == 3 && dispatchState == "unknown" -> "待核对"
    else -> settlementStatusLabel(status)
}
fun SettlementSummary.displayChain(): String = if (payType in setOf("4", "5", "6")) when (chain) {
    "1" -> "TRON (TRC20)"; "2" -> "Polygon (PoS)"; "3" -> "BNB Smart Chain (BEP20)"; else -> chain
} else ""
fun SettlementSummary.displayPayType(): String = when (payType) {
    "1" -> "支付宝"; "2" -> "微信"; "3" -> "QQ 钱包"; "4", "5", "6" -> "USDT"; else -> payType
}
fun SettlementSummary.canSetModernStatus(target: Int): Boolean = status != 2 && target in setOf(1, 6) && !(status == 1 && target == 6)
internal val MODERN_SETTLEMENT_FILTERS = listOf(-1 to "全部", -2 to "未完成", 7 to "待审核", 4 to "队列中", 0 to "待结算", 2 to "正在结算", 1 to "已完成", 5 to "链上确认失败", 3 to "结算失败", 6 to "作废")
internal fun settlementStatusConfirmation(status: Int): String = if (status == 1)
    "确认已核对付款结果并标记为已完成？此操作不转账、不扣款，该笔将停止后续自动派款。"
else "确认作废这笔结算？该笔将停止后续自动派款，余额不变；已广播的链上交易无法撤回，请核对交易记录。"

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
