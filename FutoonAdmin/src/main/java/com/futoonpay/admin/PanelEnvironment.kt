package com.futoonpay.admin

import com.futoonpay.admin.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.HttpUrl.Companion.toHttpUrl

/** The original AppViewModel's application dependency narrowed to a host-scoped session. */
interface PanelEnvironment {
    val repository: FutoonRepository
    val adminBaseUrl: String
    val hasConfiguredEndpoint: Boolean
    val sessionExpired: StateFlow<Boolean>
    fun configureEndpoint(raw: String): Result<String>
    fun markAuthenticated()
}

internal class EmbeddedEnvironment(private val bridge: NativePanelBridge) : PanelEnvironment {
    private var revision = 0
    private var activeRepository: FutoonRepository? = null
    private val expired = MutableStateFlow(false)
    override val sessionExpired: StateFlow<Boolean> = expired
    override var adminBaseUrl = bridge.address()
        private set
    override val hasConfiguredEndpoint get() = activeRepository != null
    override val repository get() = activeRepository ?: error("请先连接后台")
    init { if (adminBaseUrl.isNotBlank()) activate(adminBaseUrl) }
    override fun markAuthenticated() { expired.value = false }
    override fun configureEndpoint(raw: String): Result<String> = runCatching {
        val normalized = normalizeAdminBaseUrl(raw).toString()
        bridge.configure(normalized, bridge.username())
        if (normalized != adminBaseUrl || activeRepository == null) activate(normalized)
        expired.value = false
        normalized
    }
    private fun activate(address: String) {
        val current = ++revision
        adminBaseUrl = address
        val delegate = bridge.session()
        val scope = object : PanelSession by delegate {
            override fun isActive() = current == revision && delegate.isActive() && delegate.address() == address
        }
        val pending = object : BalancePendingStore {
            private val store = SessionBalancePendingStore(delegate)
            private fun checkScope() { check(scope.isActive()) { "账号或后台已变化" } }
            override fun read(uid: String): String { checkScope(); return store.read(uid) }
            override fun write(uid: String, value: String) { checkScope(); store.write(uid, value) }
        }
        activeRepository = FutoonRepository(FutoonApiClient(PanelCookieJar(scope, address.toHttpUrl()) { runCatching { bridge.call("ui.channel.refresh", "{}") } }, address.toHttpUrl(), onSessionExpired = { expired.value = true }), pending)
    }
}
