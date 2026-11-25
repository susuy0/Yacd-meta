package com.futoonpay.admin

import android.os.Bundle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import org.json.JSONObject

/** A detached panel retains data and saveable UI state, never an Activity or its View tree. */
interface NativePanelOptionsBridge : NativePanelBridge { fun settingsJson(): String }

internal class PanelBridgeRelay(private var target: NativePanelBridge?) : NativePanelBridge {
    private var session = requireNotNull(target).session()
    private var address = requireNotNull(target).address()
    private val scope = requireNotNull(target).accountScope()
    private var username = requireNotNull(target).username()
    fun attach(bridge: NativePanelBridge) { check(bridge.address() == address && bridge.accountScope() == scope); target = bridge }
    fun detach() { target?.let { address = it.address(); username = it.username(); session = it.session() }; target = null }
    private fun active(): NativePanelBridge = target ?: error("面板已关闭，重新打开后继续操作")
    override fun address() = target?.address() ?: address
    override fun accountScope() = scope
    override fun username() = target?.username() ?: username
    override fun session() = target?.session() ?: session
    override fun hasSession() = target?.hasSession() ?: false
    override fun configure(address: String, username: String) = active().configure(address, username)
    override fun call(operation: String, params: String) = active().call(operation, params)
    override fun collapse() { target?.collapse() }
    override fun copy(text: String) { target?.copy(text) }
    override fun selectEvidence(order: String, caption: String) = active().selectEvidence(order, caption)
    override fun pickEvidenceFromChat() = active().pickEvidenceFromChat()
    override fun openGallery() = active().openGallery()
    override fun evidencePaths() = target?.evidencePaths() ?: emptyArray()
    override fun evidencePreview(path: String) = target?.evidencePreview(path)
    override fun editEvidence(index: Int) = active().editEvidence(index)
    override fun removeEvidence(index: Int) = active().removeEvidence(index)
    override fun sendEvidence(order: String, caption: String) = active().sendEvidence(order, caption)
}

internal class PanelMemory(val key: String, host: NativePanelBridge, options: ExtensionOptions) {
    val bridge = PanelBridgeRelay(host)
    val environment = EmbeddedEnvironment(bridge)
    val store = ViewModelStore()
    val navigator = PanelNavigator().apply { select(options.panel.defaultTab) }
    val routeModels = PanelRouteModels()
    val sessionModels = PanelRouteModels()
    val initializedRoutes = mutableSetOf<String>()
    val merchantActions = mutableMapOf<String, CurrentMerchantActions>()
    var saved: Bundle? = null
    var query: RecognizedQuery? = null
    var attached = true
    var hasLiveState = false
    fun clear() { store.clear(); routeModels.clear(); sessionModels.clear(); merchantActions.clear(); bridge.detach(); saved = null; query = null }
}

internal object PanelMemories {
    private val entries = linkedMapOf<String, PanelMemory>()
    fun acquire(key: String, bridge: NativePanelBridge, options: ExtensionOptions): PanelMemory {
        if (options.panel.remember && key.isNotBlank()) {
            val old = entries[key]
            if (old != null && !old.attached) { entries.remove(key); entries[key] = old; old.attached = true; old.bridge.attach(bridge); return old }
        }
        val memory = PanelMemory(key, bridge, options)
        if (options.panel.remember && key.isNotBlank() && entries[key]?.attached != true) {
            while (entries.size >= 4) { val candidate = entries.entries.firstOrNull { !it.value.attached } ?: break; entries.remove(candidate.key)?.clear() }
            if (entries.size < 4) entries[key] = memory
        }
        return memory
    }
    fun release(memory: PanelMemory, remember: Boolean) {
        memory.hasLiveState = true; memory.attached = false; memory.bridge.detach()
        if (!remember || entries[memory.key] !== memory) { if (entries[memory.key] === memory) entries.remove(memory.key); memory.clear() }
    }
    fun clearAccount(scope: String) { entries.entries.filter { it.key.startsWith("$scope|") && !it.value.attached }.map { it.key }.forEach { entries.remove(it)?.clear() } }
}
