package com.futoonpay.admin

import okhttp3.HttpUrl.Companion.toHttpUrl

/** Read the shared persisted session, rather than the legacy Java client's cookie snapshot. */
object ChannelSessionStatus {
    @JvmStatic fun hasSession(session: PanelSession): Boolean = runCatching {
        val base = session.address().toHttpUrl()
        PanelCookieJar(session, base).loadForRequest(base).any { it.name == "admin_token" && it.value.isNotBlank() }
    }.getOrDefault(false)
}
