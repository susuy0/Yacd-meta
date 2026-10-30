package com.futoonpay.admin.data

import org.json.JSONObject

data class InactiveMerchantFilters(val whitelist: String = "all", val accountStatus: String = "all", val keyword: String = "") {
    fun fields(): Map<String, String> {
        require(whitelist in setOf("all", "yes", "no") && accountStatus in setOf("all", "normal", "frozen")) { "筛选条件不正确" }
        return mapOf("whitelist" to whitelist, "account_status" to accountStatus, "keyword" to keyword.trim())
    }
}

data class InactiveMerchant(val uid: String, val name: String, val registered: String, val lastOrder: String,
    val recentOrders: String, val status: Int, val statusText: String, val whitelistText: String)
data class InactiveMerchantScan(val rows: List<InactiveMerchant>, val total: Int, val windowStart: String)

internal fun parseInactiveMerchants(response: JSONObject): InactiveMerchantScan {
    check(response.optInt("code", -1) == 0) { response.optString("msg", "扫描失败") }
    val rows = response.optJSONArray("rows") ?: error("后台未返回扫描结果")
    val merchants = (0 until rows.length()).map { i ->
        val row = rows.getJSONObject(i)
        InactiveMerchant(row.text("uid"), row.text("merchant_name"), row.text("addtime"), row.text("last_order"),
            row.text("recent_order_count"), row.optInt("status", -1), row.text("status_text"), row.text("whitelist_text"))
    }
    return InactiveMerchantScan(merchants, response.optInt("total", merchants.size), response.text("window_start"))
}
