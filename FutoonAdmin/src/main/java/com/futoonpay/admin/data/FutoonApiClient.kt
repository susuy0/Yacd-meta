package com.futoonpay.admin.data

import com.futoonpay.admin.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class FutoonApiClient(
    private val cookieJar: AdminCookieStore,
    val baseUrl: HttpUrl,
    private val onSessionExpired: () -> Unit = {},
    transport: OkHttpClient? = null,
) {
    private val client = (transport?.newBuilder() ?: OkHttpClient.Builder())
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "FutoonPay-Android/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/json,text/html;q=0.9,*/*;q=0.8")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()
            chain.proceed(request)
        }
        .build()

    suspend fun loadCaptcha(): ByteArray = withContext(Dispatchers.IO) {
        val loginHtml = getText("login.php", "login.php")
        val captchaPath = Regex("<img[^>]+src=[\"']([^\"']*code(?:\\.php)?(?=[?\"'])[^\"']*)", RegexOption.IGNORE_CASE)
            .find(loginHtml)?.groupValues?.getOrNull(1)
            ?: "code.php?r=${System.currentTimeMillis()}"
        execute(
            Request.Builder()
                .url(resolve(captchaPath))
                .header("Referer", resolve("login.php").toString())
                .build()
        ).use { response ->
            if (!response.isSuccessful) throw IOException("验证码加载失败（${response.code}）")
            response.body?.bytes() ?: throw IOException("验证码内容为空")
        }
    }

    suspend fun login(username: String, password: String, captcha: String): ApiResult = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("user", username)
            .add("pass", password)
            .add("code", captcha)
            .build()
        val html = execute(
            Request.Builder()
                .url(resolve("login.php"))
                .header("Referer", resolve("login.php").toString())
                .post(body)
                .build()
        ).use { response ->
            if (!response.isSuccessful) return@withContext ApiResult(false, "登录失败（${response.code}）")
            response.body?.string().orEmpty()
        }
        val success = cookieJar.has("admin_token") || html.contains("登陆管理中心成功")
        val message = Regex("alert\\(['\"]([^'\"]+)", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)
            ?: if (success) "登录成功" else "账号、密码或验证码不正确"
        ApiResult(success, message)
    }

    suspend fun sessionIsValid(): Boolean = runCatching {
        val json = getJson("ajax.php?act=getcount", "")
        json.optInt("code", 0) != 403 && json.has("count1")
    }.getOrDefault(false)

    suspend fun dashboard(forceRefresh: Boolean = false): JSONObject {
        val suffix = if (forceRefresh) "&getnew=1" else ""
        return getJson("ajax.php?act=getcount$suffix", "")
    }

    suspend fun page(
        endpoint: String,
        referer: String,
        page: Int,
        pageSize: Int,
        filters: Map<String, String>,
        method: String = "POST",
    ): JSONObject = withContext(Dispatchers.IO) {
        val builder = FormBody.Builder()
            .add("pageNumber", page.toString())
            .add("pageSize", pageSize.toString())
            .add("offset", ((page - 1) * pageSize).toString())
            .add("limit", pageSize.toString())
        filters.filterValues { it.isNotBlank() }.forEach { (key, value) -> builder.add(key, value) }
        val requestBuilder = Request.Builder().header("Referer", resolve(referer).toString())
        val request = if (method.equals("GET", true)) {
            val url = resolve(endpoint).newBuilder().apply {
                addQueryParameter("pageNumber", page.toString())
                addQueryParameter("pageSize", pageSize.toString())
                addQueryParameter("offset", ((page - 1) * pageSize).toString())
                addQueryParameter("limit", pageSize.toString())
                filters.filterValues { it.isNotBlank() }.forEach { (key, value) -> addQueryParameter(key, value) }
            }.build()
            requestBuilder.url(url).get().build()
        } else requestBuilder.url(resolve(endpoint)).post(builder.build()).build()
        executeJson(request).also { json ->
            if (json.has("code") && json.optInt("code", -1) !in setOf(0, 200)) throw IOException(json.optString("msg", "查询失败"))
        }
    }

    suspend fun postAction(
        endpoint: String,
        referer: String,
        fields: Map<String, String>,
    ): ApiResult = withContext(Dispatchers.IO) {
        val builder = FormBody.Builder()
        fields.forEach { (key, value) -> builder.add(key, value) }
        val json = executeJson(
            Request.Builder()
                .url(resolve(endpoint))
                .header("Referer", resolve(referer).toString())
                .post(builder.build())
                .build(), once = true
        )
        if (!json.has("code") || json.isNull("code")) throw IOException("后台未明确确认操作结果，请刷新核对")
        val code = json.optInt("code", -1)
        ApiResult(code == 0 || code == 200, json.optString("msg", if (code == 0 || code == 200) "操作成功" else "操作失败"), json.recordId())
    }

    suspend fun postActionMulti(
        endpoint: String,
        referer: String,
        fields: Map<String, List<String>>,
    ): ApiResult = withContext(Dispatchers.IO) {
        val builder = FormBody.Builder()
        fields.forEach { (key, values) -> values.forEach { value -> builder.add(key, value) } }
        val json = executeJson(
            Request.Builder().url(resolve(endpoint)).header("Referer", resolve(referer).toString())
                .post(builder.build()).build(), once = true
        )
        if (!json.has("code") || json.isNull("code")) throw IOException("后台未明确确认操作结果，请刷新核对")
        val code = json.optInt("code", -1)
        ApiResult(code == 0 || code == 200, json.optString("msg", if (code == 0 || code == 200) "操作成功" else "操作失败"), json.recordId())
    }

    suspend fun getAction(
        endpoint: String,
        referer: String,
        query: Map<String, String> = emptyMap(),
    ): ApiResult = withContext(Dispatchers.IO) {
        val url = resolve(endpoint).newBuilder().apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val json = executeJson(
            Request.Builder().url(url).header("Referer", resolve(referer).toString()).get().build(), once = true
        )
        if (!json.has("code") || json.isNull("code")) throw IOException("后台未明确确认操作结果，请刷新核对")
        val code = json.optInt("code", -1)
        ApiResult(code == 0 || code == 200, json.optString("msg", if (code == 0 || code == 200) "操作成功" else "操作失败"), json.recordId())
    }

    suspend fun jsonGet(
        endpoint: String,
        referer: String,
        query: Map<String, String> = emptyMap(),
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = resolve(endpoint).newBuilder().apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        executeJson(Request.Builder().url(url).header("Referer", resolve(referer).toString()).get().build())
    }

    suspend fun jsonPost(
        endpoint: String,
        referer: String,
        fields: Map<String, String>,
        once: Boolean = false,
    ): JSONObject = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build()
        val body = if (!once) form else object : okhttp3.RequestBody() {
            override fun contentType() = form.contentType()
            override fun contentLength() = form.contentLength()
            override fun isOneShot() = true
            override fun writeTo(sink: okio.BufferedSink) = form.writeTo(sink)
        }
        executeJson(
            Request.Builder().url(resolve(endpoint)).header("Referer", resolve(referer).toString())
                .post(body).build(), once
        )
    }

    suspend fun document(path: String): String = withContext(Dispatchers.IO) {
        val html = getText(path, path)
        if (looksLikeLoginPage(html)) expireSession()
        html
    }

    suspend fun submitForm(
        action: String,
        method: String,
        referer: String,
        fields: Map<String, String>,
    ): ApiResult = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build()
        val requestBuilder = Request.Builder().url(resolve(action)).header("Referer", resolve(referer).toString())
        val request = if (method.equals("GET", true)) {
            val url = resolve(action).newBuilder().apply { fields.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
            requestBuilder.url(url).get().build()
        } else requestBuilder.post(form).build()
        (if (method.equals("GET", true)) execute(request) else executeOnce(request)).use { response ->
            if (response.code == 401) expireSession()
            if (response.code == 403) throw PermissionDeniedException()
            if (!response.isSuccessful) throw IOException("保存结果待核对（${response.code}）")
            val raw = response.body?.string().orEmpty()
            if (looksLikeLoginPage(raw)) expireSession()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (json != null) {
                if (!json.has("code") || json.isNull("code")) throw IOException("后台未明确确认保存结果，请刷新核对")
                val code = json.optInt("code", -1)
                if (code == 403) expireSession()
                return@withContext ApiResult(code == 0 || code == 200, json.optString("msg", "操作完成"), json.recordId())
            }
            val message = Regex("alert\\(['\"]([^'\"]+)", RegexOption.IGNORE_CASE)
                .find(raw)?.groupValues?.getOrNull(1) ?: throw IOException("后台未确认保存结果，请刷新核对")
            val failed = message.contains("失败") || message.contains("错误") || message.contains("不能为空")
            if (!failed && !message.contains("成功") && !message.contains("完成")) throw IOException("后台未明确确认保存结果，请刷新核对")
            ApiResult(!failed, message)
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        runCatching { getText("login.php?logout", "") }
        cookieJar.clear()
    }

    private suspend fun getJson(path: String, referer: String): JSONObject = withContext(Dispatchers.IO) {
        executeJson(
            Request.Builder()
                .url(resolve(path))
                .header("Referer", resolve(referer).toString())
                .get()
                .build()
        )
    }

    private fun getText(path: String, referer: String): String {
        val request = Request.Builder()
            .url(resolve(path))
            .header("Referer", resolve(referer).toString())
            .get()
            .build()
        return execute(request).use { response ->
            if (response.code == 401) expireSession()
            if (response.code == 403) throw PermissionDeniedException()
            if (!response.isSuccessful) throw IOException("请求失败（${response.code}）")
            response.body?.string().orEmpty()
        }
    }

    private fun executeJson(request: Request, once: Boolean = false): JSONObject =
        (if (once) executeOnce(request) else execute(request)).use { response ->
        if (response.code == 401) expireSession()
        if (response.code == 403) throw PermissionDeniedException()
        if (!response.isSuccessful) throw IOException("请求失败（${response.code}）")
        val raw = response.body?.string().orEmpty()
        if (looksLikeLoginPage(raw)) expireSession()
        val json = runCatching { normalizeJson(raw) }
            .getOrElse { throw IOException("服务器返回了无法解析的数据") }
        if (json.optInt("code", 0) == 403) expireSession()
        json
    }

    private fun expireSession(): Nothing {
        cookieJar.clear()
        onSessionExpired()
        throw SessionExpiredException()
    }

    /**
     * 部分易支付 AJAX 列表直接返回顶层 JSON 数组，另一些列表返回
     * { total, rows }。在网络层统一成后者，避免各业务页面分别猜测返回结构。
     */
    private fun normalizeJson(raw: String): JSONObject {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) {
            val rows = JSONArray(trimmed)
            return JSONObject().put("total", rows.length()).put("rows", rows)
        }
        return JSONObject(trimmed)
    }

    private fun executeOnce(request: Request): Response = client.newBuilder().retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build().newCall(request).execute()

    private fun execute(request: Request): Response = client.newCall(request).execute()

    private fun looksLikeLoginPage(html: String): Boolean {
        if (!html.contains('<')) return false
        val document = org.jsoup.Jsoup.parse(html)
        return document.select("input[name=user]").isNotEmpty() && document.select("input[name=pass]").isNotEmpty() &&
            (html.contains("login", ignoreCase = true) || html.contains("登陆管理中心"))
    }

    private fun resolve(path: String): HttpUrl {
        val resolved = baseUrl.resolve(com.futoonpay.admin.BackendRoutes.route(baseUrl.encodedPath, path)) ?: throw IllegalArgumentException("无效接口路径")
        require(
            resolved.isHttps && resolved.host == baseUrl.host && resolved.encodedPath.startsWith(baseUrl.encodedPath)
        ) { "已阻止管理目录外的请求" }
        return resolved
    }
}

class SessionExpiredException : IOException("登录已过期，请重新登录")
class PermissionDeniedException : IOException("没有查看或操作权限，请联系管理员开通")

private fun JSONObject.recordId(): String = text("id").ifBlank { optJSONObject("data")?.text("id").orEmpty() }
