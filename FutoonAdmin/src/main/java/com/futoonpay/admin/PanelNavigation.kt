package com.futoonpay.admin

import androidx.compose.runtime.*
import java.util.UUID
import org.json.JSONObject
import org.json.JSONArray

internal data class PanelRoute(val kind: String, val module: String = "", val channelId: String = "", val channelType: String = "", val merchantId: String = "", val id: String = UUID.randomUUID().toString(), val orderId: String = "", val queryColumn: String = "", val queryValue: String = "")

internal interface PanelNavigation {
    fun orders(channelId: String = "", merchantId: String = "")
    fun settlements(merchantId: String = "")
    fun merchants(merchantId: String = "")
    fun domains(merchantId: String = "")
    fun module(id: String, channelId: String = "", channelType: String = "")
    fun evidence(order: com.futoonpay.admin.data.OrderSummary)
}

internal val LocalPanelNavigation = staticCompositionLocalOf<PanelNavigation> { object : PanelNavigation {
    override fun orders(channelId: String, merchantId: String) {}
    override fun settlements(merchantId: String) {}
    override fun merchants(merchantId: String) {}
    override fun domains(merchantId: String) {}
    override fun module(id: String, channelId: String, channelType: String) {}
    override fun evidence(order: com.futoonpay.admin.data.OrderSummary) {}
} }

internal class PanelNavigator : PanelNavigation {
    private val roots = mapOf("recognition" to PanelRoute("recognition"), "orders" to PanelRoute("orders"), "merchants" to PanelRoute("merchants"), "settlements" to PanelRoute("settlements"), "more" to PanelRoute("more"))
    var tab by mutableStateOf("orders")
        private set
    private val stacks = mutableStateMapOf<String, List<PanelRoute>>()
    var lastQueryId: String = ""
    fun showOrdersForQuery() { tab = "orders"; stacks["orders"] = emptyList() }
    fun showRecognition() { tab = "recognition"; stacks["recognition"] = emptyList() }
    fun openOrder(tradeNo: String) = push(PanelRoute("orders", orderId = tradeNo))
    fun merchantOpening() = push(PanelRoute("merchant_opening"))
    val current: PanelRoute get() = stacks[tab]?.lastOrNull() ?: roots.getValue(tab)
    fun select(tab: String) { require(tab in roots); this.tab = tab }
    fun snapshot(): String = JSONObject().put("tab", tab).put("stacks", JSONObject().also { result ->
        stacks.forEach { (key, routes) -> result.put(key, JSONArray(routes.takeLast(16).filterNot { it.kind == "merchant_opening" }.map { route ->
            JSONObject().put("kind", route.kind).put("module", route.module).put("channelId", route.channelId).put("channelType", route.channelType).put("merchantId", route.merchantId)
                .put("id", route.id).put("orderId", route.orderId).put("queryColumn", route.queryColumn).put("queryValue", route.queryValue)
        })) }
    }).toString()
    fun restore(raw: String) {
        if (raw.isBlank() || raw.length > 100000) return
        runCatching {
            val json = JSONObject(raw)
            val selected = json.optString("tab"); if (selected in roots) tab = selected
            val saved = json.optJSONObject("stacks") ?: return@runCatching
            roots.keys.forEach { key -> val rows = saved.optJSONArray(key) ?: return@forEach
                stacks[key] = (0 until minOf(rows.length(), 16)).mapNotNull { i -> val row = rows.getJSONObject(i); val kind = row.optString("kind")
                    if (kind !in setOf("recognition", "orders", "merchants", "settlements", "more", "module", "domains", "settings", "evidence", "merchant_records")) null
                    else PanelRoute(kind, row.optString("module"), row.optString("channelId"), row.optString("channelType"), row.optString("merchantId"), row.optString("id").ifBlank { UUID.randomUUID().toString() }, row.optString("orderId"), row.optString("queryColumn"), row.optString("queryValue"))
                }
            }
        }
    }
    val canPop: Boolean get() = stacks[tab].orEmpty().isNotEmpty()
    fun pop(): Boolean { val stack = stacks[tab].orEmpty(); if (stack.isEmpty()) return false; stacks[tab] = stack.dropLast(1); return true }
    fun push(route: PanelRoute) { stacks[tab] = stacks[tab].orEmpty() + route }
    override fun orders(channelId: String, merchantId: String) = push(PanelRoute("orders", channelId = channelId, merchantId = merchantId))
    override fun settlements(merchantId: String) = push(PanelRoute("settlements", merchantId = merchantId))
    override fun merchants(merchantId: String) = push(PanelRoute("merchants", merchantId = merchantId))
    override fun domains(merchantId: String) = push(PanelRoute("domains", merchantId = merchantId))
    override fun module(id: String, channelId: String, channelType: String) = push(PanelRoute("module", module = id, channelId = channelId, channelType = channelType))
    override fun evidence(order: com.futoonpay.admin.data.OrderSummary) = Unit
}
