package com.futoonpay.admin

import com.futoonpay.admin.data.FutoonRepository
import kotlinx.coroutines.flow.StateFlow

/** The original screens use the standalone app or an embedded Telegram account environment. */
interface FutoonEnvironment {
    val sessionExpired: StateFlow<Boolean>
    val adminBaseUrl: String
    val hasConfiguredEndpoint: Boolean
    val repository: FutoonRepository
    fun markAuthenticated()
    fun configureEndpoint(raw: String): Result<String>
}
