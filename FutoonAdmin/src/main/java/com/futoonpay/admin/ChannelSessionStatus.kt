package com.futoonpay.admin

import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Read the shared persisted session, rather than the legacy Java client's cookie snapshot.
 * The new `/svc_<hash>/` backend does not promise the old `admin_token` name, so any live cookie
 * other than the pre-login PHP session id (set when the captcha is fetched) counts as signed in.
 */
object ChannelSessionStatus {
    @JvmStatic fun hasSession(session: PanelSession): Boolean = runCatching {
        val base = session.address().toHttpUrl()
        PanelCookieJar(session, base).loadForRequest(base).any { it.value.isNotBlank() && (it.name == "admin_token" || !it.name.equals("PHPSESSID", ignoreCase = true)) }
    }.getOrDefault(false)
}
