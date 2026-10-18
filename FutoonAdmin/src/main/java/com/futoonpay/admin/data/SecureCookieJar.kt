package com.futoonpay.admin.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

interface AdminCookieStore : CookieJar {
    fun has(name: String): Boolean
    fun clear()
}

class SecureCookieJar(context: Context, private val baseUrl: HttpUrl) : AdminCookieStore {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "futoon_admin_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    private val cookies = linkedMapOf<String, Cookie>()

    init {
        preferences.all.values.filterIsInstance<String>().forEach { raw ->
            Cookie.parse(baseUrl, raw)?.let { cookies[key(it)] = it }
        }
        removeExpired()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            val key = key(cookie)
            if (cookie.expiresAt <= System.currentTimeMillis()) {
                this.cookies.remove(key)
                preferences.edit().remove(key).apply()
            } else {
                this.cookies[key] = cookie
                preferences.edit().putString(key, cookie.toString()).apply()
            }
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        removeExpired()
        return cookies.values.filter { it.matches(url) }
    }

    @Synchronized
    fun snapshot(): List<Cookie> {
        removeExpired()
        return cookies.values.toList()
    }

    @Synchronized
    override fun has(name: String): Boolean = snapshot().any { it.name == name }

    @Synchronized
    override fun clear() {
        cookies.clear()
        preferences.edit().clear().apply()
    }

    private fun key(cookie: Cookie) = "${cookie.name}|${cookie.domain}|${cookie.path}"

    private fun removeExpired() {
        val expired = cookies.filterValues { it.expiresAt <= System.currentTimeMillis() }.keys
        if (expired.isNotEmpty()) {
            val editor = preferences.edit()
            expired.forEach { cookies.remove(it); editor.remove(it) }
            editor.apply()
        }
    }
}
