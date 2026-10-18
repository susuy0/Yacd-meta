package com.futoonpay.admin

import android.app.Application
import com.futoonpay.admin.data.AdminEndpointStore
import com.futoonpay.admin.data.FutoonApiClient
import com.futoonpay.admin.data.FutoonRepository
import com.futoonpay.admin.data.SecureCookieJar
import com.futoonpay.admin.data.normalizeAdminBaseUrl
import okhttp3.HttpUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FutoonApplication : Application(), FutoonEnvironment {
    private lateinit var endpointStore: AdminEndpointStore
    private var cookieJar: SecureCookieJar? = null
    private var activeBaseUrl: HttpUrl? = null
    private var activeRepository: FutoonRepository? = null
    private val _sessionExpired = MutableStateFlow(false)
    override val sessionExpired = _sessionExpired.asStateFlow()
    override fun markAuthenticated() { _sessionExpired.value = false }

    override val adminBaseUrl: String get() = activeBaseUrl?.toString().orEmpty()
    override val hasConfiguredEndpoint: Boolean get() = activeRepository != null
    override val repository: FutoonRepository
        get() = activeRepository ?: throw IllegalStateException("请先配置后台地址")

    override fun onCreate() {
        super.onCreate()
        endpointStore = AdminEndpointStore(this)
        endpointStore.load()?.let(::activate)
    }

    override fun configureEndpoint(raw: String): Result<String> = runCatching {
        val normalized = normalizeAdminBaseUrl(raw)
        cookieJar?.clear()
        if (normalized != activeBaseUrl) {
            endpointStore.save(normalized)
            activate(normalized)
        }
        normalized.toString()
    }

    companion object {
        fun from(context: android.content.Context): FutoonEnvironment =
            (context.applicationContext as? FutoonApplication) ?: FutoonIntegration.environment(context)
    }

    private fun activate(baseUrl: HttpUrl) {
        val jar = SecureCookieJar(this, baseUrl)
        cookieJar = jar
        activeBaseUrl = baseUrl
        activeRepository = FutoonRepository(FutoonApiClient(jar, baseUrl, onSessionExpired = { _sessionExpired.value = true }))
    }
}
