package com.futoonpay.admin

import androidx.compose.runtime.*
import java.util.UUID

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
    val current: PanelRoute get() = stacks[tab]?.lastOrNull() ?: roots.getValue(tab)
    fun select(tab: String) { require(tab in roots); this.tab = tab }
    fun pop(): Boolean { val stack = stacks[tab].orEmpty(); if (stack.isEmpty()) return false; stacks[tab] = stack.dropLast(1); return true }
    fun push(route: PanelRoute) { stacks[tab] = stacks[tab].orEmpty() + route }
    override fun orders(channelId: String, merchantId: String) = push(PanelRoute("orders", channelId = channelId, merchantId = merchantId))
    override fun settlements(merchantId: String) = push(PanelRoute("settlements", merchantId = merchantId))
    override fun merchants(merchantId: String) = push(PanelRoute("merchants", merchantId = merchantId))
    override fun domains(merchantId: String) = push(PanelRoute("domains", merchantId = merchantId))
    override fun module(id: String, channelId: String, channelType: String) = push(PanelRoute("module", module = id, channelId = channelId, channelType = channelType))
    override fun evidence(order: com.futoonpay.admin.data.OrderSummary) = Unit
}
