package com.futoonpay.admin.data

import org.json.JSONObject

/** The official statistics response supplies its own payment-type or channel columns. */
internal fun parseMerchantStatistics(response: JSONObject): NativeTable {
    check(response.optInt("code", -1) in setOf(0, 200)) { response.optString("msg", "统计查询失败") }
    val columns = response.optJSONObject("columns") ?: error("后台未返回统计列，请重试")
    val keys = columns.keys().asSequence().toList().sortedWith(compareBy<String> { when (it) { "uid" -> 0; "total" -> 1; else -> 2 } }.thenBy { it })
    check(keys.isNotEmpty()) { "后台未返回统计列，请重试" }
    val rows = response.optJSONArray("data") ?: error("后台未返回统计数据，请重试")
    return NativeTable("商户统计", keys.map { columns.optString(it) }, (0 until rows.length()).map { index ->
        val row = rows.optJSONObject(index) ?: error("统计数据格式不正确")
        keys.map { key ->
            if (key == "uid") row.text(key)
            else if (row.isNull(key)) "0.00"
            else row.text(key).toBigDecimalOrNull()?.setScale(2, java.math.RoundingMode.HALF_UP)?.toPlainString() ?: row.text(key)
        }
    })
}
