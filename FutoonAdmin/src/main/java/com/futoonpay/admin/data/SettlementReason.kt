package com.futoonpay.admin.data

import org.json.JSONObject

/** The approval token stays in native memory; it is never a displayed field or a persisted form draft. */
class SettlementReason internal constructor(
    val statusLabel: String,
    val sections: List<Pair<String, String>>,
    val reviews: List<SettlementReview>,
    val canApproveFull: Boolean,
    val canRecheck: Boolean,
    val explorerUrl: String,
    private val token: String,
) {
    internal fun approvalFields(): Map<String, String> = mapOf("token" to token)
    internal fun reviewFields(review: SettlementReview): Map<String, String> = mapOf("token" to token, "category" to review.category, "fingerprint" to review.fingerprint)
    internal fun recheckFields(): Map<String, String> = mapOf("token" to token, "category" to "recheck", "fingerprint" to "0".repeat(64))
}

class SettlementReview internal constructor(
    val title: String,
    val state: String,
    val reason: String,
    val records: List<String>,
    val note: String,
    val canApprove: Boolean,
    internal val category: String,
    internal val fingerprint: String,
    val counts: String = "",
)

internal fun parseSettlementReason(data: JSONObject): SettlementReason {
    val token = data.text("approval_token")
    val validToken = Regex("[0-9a-f]{64}").matches(token)
    val completion = data.optJSONObject("completion")
    val approval = data.optJSONObject("full_approval")
    val sections = buildList {
        fun line(label: String, value: String) { if (value.isNotBlank()) add(label to value) }
        line("当前状态", data.text("status_label"))
        val reviews = data.optJSONArray("reviews")
        if (approval != null && (reviews?.length() ?: 0) == 0) line("结算审核", if (approval.optBoolean("allowed")) "可审核放行本笔次数限制，进入付款队列；付款资格、余额和防重复检查仍会执行。" else approval.text("reason"))
        if (completion != null) {
            line("处理方式", completion.text("label"))
            line("结算金额", listOf(data.text("amount"), data.text("asset")).filter(String::isNotBlank).joinToString(" "))
            if (completion.text("method") == "blockchain") {
                line("网络", data.text("chain")); line("交易哈希", completion.text("tx_id"))
            } else if (completion.text("method") != "manual") line("交易哈希", "未记录交易哈希")
        } else {
            line("原因", data.text("reason"))
            line("本笔应付金额", listOf(data.text("amount"), data.text("asset"), data.text("chain")).filter(String::isNotBlank).joinToString(" · "))
            line("付款钱包余额", data.text("balance_note")); line("次数限制", data.text("current_rule")); line("说明", data.text("rule_note"))
            line("最近检查时间", data.text("last_checked_at").ifBlank { "尚无可确认的检查时间" } + data.text("timezone").takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty())
            line("下次链上核对时间", data.text("next_check_at")); line("下一步", data.text("next_step"))
        }
    }
    val reviews = data.optJSONArray("reviews")?.let { array -> (0 until array.length()).mapNotNull { i ->
        val row = array.optJSONObject(i) ?: return@mapNotNull null
        val category = row.text("category"); val fingerprint = row.text("fingerprint")
        SettlementReview(row.text("label"), when (row.text("state")) { "pending" -> "待审核"; "approved" -> "已审核"; "resolved" -> "当前未命中"; else -> "需核实" }, row.text("reason"),
            row.optJSONArray("records")?.let { records -> (0 until records.length()).mapNotNull { j -> records.optJSONObject(j)?.let { record ->
                val reasons = record.optJSONArray("reasons")?.let { r -> (0 until r.length()).joinToString("；") { r.optString(it) } }.orEmpty()
                "流水 #${record.text("id")} · ${record.text("money")} 元 · ${record.text("type")}\n$reasons\n来源编号：${record.text("trade_no").ifBlank { "未记录" }}"
            } } }.orEmpty(), row.text("approval_note"), row.optBoolean("can_approve") && validToken && category in setOf("frequency", "address", "ledger") && Regex("[0-9a-f]{64}").matches(fingerprint), category, fingerprint, if (!row.isNull("count") && !row.isNull("limit")) "近 24 小时 ${row.text("count")} 笔；限制 ${row.text("limit")} 笔" else "")
    } }.orEmpty()
    val explorerUrl = completion?.text("explorer_url").orEmpty().takeIf { Regex("^https://(?:tronscan\\.org/#/transaction/[a-fA-F0-9]{64}|(?:polygonscan\\.com|bscscan\\.com)/tx/0x[a-fA-F0-9]{64})$").matches(it) }.orEmpty()
    return SettlementReason(data.text("status_label"), sections, reviews, completion == null && validToken && approval?.optBoolean("allowed") == true, completion == null && validToken && data.optBoolean("review_can_recheck"), explorerUrl, token)
}

/** Existing recipient data must be loaded before showing an editable settlement form. */
data class SettlementAccountInfo(val payType: String, val account: String, val name: String, val types: List<NativeOption>)

internal fun parseSettlementAccountInfo(response: JSONObject): SettlementAccountInfo {
    check(response.optInt("code", -1) == 0) { response.optString("msg", "收款资料读取失败") }
    val document = org.jsoup.Jsoup.parseBodyFragment(response.optString("data"))
    fun input(name: String): String = document.selectFirst("[name=$name],#$name")?.attr("value")
        ?: error("后台未返回完整收款资料，请刷新重试")
    val select = document.selectFirst("[name=pay_type],#pay_type")
    val options = select?.select("option")?.map { NativeOption(it.attr("value"), it.text()) }.orEmpty()
    val payType = response.text("pay_type").ifBlank { select?.selectFirst("option[selected]")?.attr("value").orEmpty() }
    check(payType.isNotBlank() && options.any { it.value == payType }) { "后台未返回有效结算方式，请刷新重试" }
    return SettlementAccountInfo(payType, input("pay_account"), input("pay_name"), options)
}
