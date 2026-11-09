package com.futoonpay.admin

/** What a list page is limited to. Shown on every order, settlement and domain page so nobody reads another merchant's rows as the current one. */
internal data class PanelScopeLabel(val text: String, val switchLabel: String? = null, val switchMerchantId: String? = null)

private val SCOPED_KINDS = setOf("orders", "settlements", "domains")

internal fun panelScopeLabel(route: PanelRoute, currentMerchantId: String, currentMerchantName: String): PanelScopeLabel? {
    if (route.kind !in SCOPED_KINDS || route.orderId.isNotBlank() || route.module == "quick-actions") return null
    val locked = route.merchantId.trim()
    val currentId = currentMerchantId.trim()
    val currentName = currentMerchantName.trim()
    fun named(id: String, name: String) = if (name.isBlank()) "PID $id" else "$name · PID $id"
    return when {
        locked.isNotEmpty() && locked == currentId -> PanelScopeLabel("范围：当前商户 · ${named(locked, currentName)}")
        locked.isNotEmpty() -> PanelScopeLabel("范围：商户 PID $locked（不是当前会话商户）")
        currentId.isNotEmpty() -> PanelScopeLabel("范围：全部商户（不限当前会话）", "只看当前商户", currentId)
        else -> PanelScopeLabel("范围：全部商户")
    }
}
