package com.futoonpay.admin.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class AdminEndpointStore(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "futoon_admin_endpoint",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun load(): HttpUrl? = preferences.getString(KEY_BASE_URL, null)
        ?.let { runCatching { normalizeAdminBaseUrl(it) }.getOrNull() }

    fun save(baseUrl: HttpUrl) {
        preferences.edit().putString(KEY_BASE_URL, baseUrl.toString()).apply()
    }

    private companion object { const val KEY_BASE_URL = "base_url" }
}

/**
 * 将用户输入规范化为管理后台根目录。后台路径必须保留，查询参数和片段则不能成为根地址的一部分。
 */
internal fun normalizeAdminBaseUrl(raw: String): HttpUrl {
    val input = raw.trim()
    require(input.isNotBlank()) { "请输入后台地址" }
    val withScheme = if ("://" in input) input else "https://$input"
    val parsed = withScheme.toHttpUrlOrNull() ?: throw IllegalArgumentException("后台地址格式不正确")
    require(parsed.isHttps) { "后台地址必须使用 HTTPS" }
    require(parsed.username.isEmpty() && parsed.password.isEmpty()) { "后台地址不能包含账号或密码" }
    val path = parsed.encodedPath.let { if (it.endsWith('/')) it else "$it/" }
    return parsed.newBuilder()
        .encodedPath(path)
        .query(null)
        .fragment(null)
        .build()
}
