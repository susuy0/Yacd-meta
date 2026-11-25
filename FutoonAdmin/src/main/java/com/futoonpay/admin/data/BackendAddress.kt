package com.futoonpay.admin.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class BackendAddress(val origin: String, val path: String) {
    companion object {
        fun fromUrl(value: String): BackendAddress {
            val url = value.toHttpUrlOrNull() ?: return BackendAddress("", "/admin")
            return BackendAddress(url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString().trimEnd('/'), url.encodedPath.trimEnd('/').ifBlank { "/" })
        }
    }
}

fun composeBackendAddress(origin: String, path: String): String {
    val base = origin.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("请输入完整官网地址，例如 https://pay.example.com")
    require(base.isHttps) { "官网地址必须使用 HTTPS" }
    require(base.username.isBlank() && base.password.isBlank()) { "官网地址不能包含账号或密码" }
    require(base.encodedPath == "/" && base.query == null && base.fragment == null) { "官网地址只填写域名，请把管理目录填在后台路径中" }
    val directory = path.trim()
    require(directory.startsWith('/') && !directory.startsWith("//") && '?' !in directory && '#' !in directory && '\\' !in directory) { "后台路径必须以 / 开头，且不能包含查询参数" }
    require(directory.split('/').none { it == "." || it == ".." }) { "后台路径不能包含相对目录" }
    return normalizeAdminBaseUrl(base.toString().trimEnd('/') + directory).toString()
}
