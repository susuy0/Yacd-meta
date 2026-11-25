package com.futoonpay.admin.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** A session is scoped to one HTTPS origin and one directory, including redirect hops. */
class AdminRequestPolicy internal constructor(private val baseUrl: HttpUrl) {
    constructor(baseUrl: String) : this(baseUrl.toHttpUrl())

    init {
        require(baseUrl.isHttps && baseUrl.username.isEmpty() && baseUrl.password.isEmpty()) {
            "后台地址必须为不含账号密码的 HTTPS 地址"
        }
        require(baseUrl.query == null && baseUrl.fragment == null && baseUrl.encodedPath.endsWith('/')) {
            "后台地址必须为完整的管理目录"
        }
        requireUnambiguousPath(baseUrl)
    }

    /** String-based entry points keep the fallback Java host on the exact same URL rules. */
    fun resolveUrl(path: String): String = resolve(path).toString()

    fun requireAllowedUrl(url: String): String = requireAllowed(url.toHttpUrl()).toString()

    fun resolveRedirectUrl(currentUrl: String, location: String): String {
        val current = requireAllowed(currentUrl.toHttpUrl())
        return requireAllowed(current.resolve(location) ?: throw IllegalArgumentException("无效跳转路径")).toString()
    }

    internal fun resolve(path: String): HttpUrl = requireAllowed(
        baseUrl.resolve(path) ?: throw IllegalArgumentException("无效接口路径")
    )

    internal fun requireAllowed(url: HttpUrl): HttpUrl {
        require(
            url.scheme == baseUrl.scheme && url.host == baseUrl.host && url.port == baseUrl.port &&
                url.username.isEmpty() && url.password.isEmpty() && url.fragment == null &&
                url.encodedPath.startsWith(baseUrl.encodedPath)
        ) { "已阻止管理目录外的请求" }
        requireUnambiguousPath(url)
        return url
    }

    private fun requireUnambiguousPath(url: HttpUrl) {
        // A proxy/PHP server may decode escaped separators or a second %-encoding differently
        // from HttpUrl. Query values remain unrestricted; only the routing path is checked.
        require(url.pathSegments.none { segment ->
            segment == "." || segment == ".." || segment.any { char ->
                char == '/' || char == '\\' || char == '%' || char <= '\u001f' || char == '\u007f'
            }
        }) { "接口路径包含歧义目录" }
    }
}
