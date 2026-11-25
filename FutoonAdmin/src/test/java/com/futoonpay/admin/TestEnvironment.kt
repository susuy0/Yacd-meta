package com.futoonpay.admin

import com.futoonpay.admin.data.FutoonRepository
import kotlinx.coroutines.flow.MutableStateFlow

internal class TestEnvironment(private val supplied: FutoonRepository? = null) : PanelEnvironment {
    override val repository get() = supplied ?: error("No live repository in fixture environment")
    override val adminBaseUrl = if (supplied == null) "" else supplied.backendAddress
    override val hasConfiguredEndpoint get() = supplied != null
    override val sessionExpired = MutableStateFlow(false)
    override fun configureEndpoint(raw: String): Result<String> = Result.failure(IllegalStateException("Fixtures cannot configure live backends"))
    override fun markAuthenticated() { sessionExpired.value = false }
}
