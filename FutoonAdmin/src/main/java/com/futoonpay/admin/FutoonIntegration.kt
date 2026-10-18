package com.futoonpay.admin

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.futoonpay.admin.data.AdminCookieStore
import com.futoonpay.admin.data.FutoonApiClient
import com.futoonpay.admin.data.FutoonRepository
import com.futoonpay.admin.data.normalizeAdminBaseUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Cookie
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.lang.ref.WeakReference

/** Only the host integration changes; screens and PHP repository come from version 1.3.4. */
object FutoonIntegration {
    const val EXTRA_SCOPE = "futoon_telegram_owner"
    interface Store {
        fun isActive(): Boolean
        fun address(): String
        fun setAddress(address: String)
        @Throws(Exception::class) fun loadCookies(address: String): String
        @Throws(Exception::class) fun saveCookies(address: String, cookies: String)
        @Throws(Exception::class) fun clearCookies(address: String)
        fun prepareEvidence(tradeNo: String, merchantTradeNo: String, merchantId: String, amount: String, currency: String)
    }
    fun interface Restorer { fun restore(scope: String): Store? }
    private val stores = mutableMapOf<String, Store>()
    private val environments = mutableMapOf<String, EmbeddedEnvironment>()
    private val activities = mutableMapOf<String, MutableList<WeakReference<Activity>>>()
    private var restorer: Restorer? = null
    @JvmStatic fun setRestorer(value: Restorer) { restorer = value }
    @JvmStatic @Synchronized fun register(scope: String, store: Store) {
        stores[scope] = store
        environments.remove(scope)
    }
    @Synchronized internal fun store(scope: String): Store? = stores[scope] ?: restorer?.restore(scope)?.also { stores[scope] = it }
    internal fun scope(context: Context): String? = when (context) {
        is ScopedAdminContext -> context.scope
        is Activity -> context.intent.getStringExtra(EXTRA_SCOPE)
        is ContextWrapper -> scope(context.baseContext)
        else -> null
    }
    fun isEmbedded(context: Context) = scope(context) != null
    internal fun track(activity: Activity) {
        val owner = scope(activity) ?: return
        activities.getOrPut(owner) { mutableListOf() }.add(WeakReference(activity))
    }
    internal fun untrack(activity: Activity) {
        activities.values.forEach { refs -> refs.removeAll { it.get() == null || it.get() === activity } }
        activities.entries.removeAll { it.value.isEmpty() }
    }
    @Synchronized fun environment(context: Context): FutoonEnvironment {
        val scope = scope(context) ?: error("请从 Telegram 中打开富通管理")
        val store = store(scope)?.takeIf { it.isActive() } ?: error("Telegram 账号已退出，请重新打开")
        return environments.getOrPut(scope) { EmbeddedEnvironment(store) }
    }
    fun prepareEvidence(context: Context, tradeNo: String, merchantTradeNo: String, merchantId: String, amount: String, currency: String) {
        val scope = scope(context) ?: return
        val target = store(scope)?.takeIf { it.isActive() } ?: return
        target.prepareEvidence(tradeNo, merchantTradeNo, merchantId, amount, currency)
        // Channel details can launch another native order page. Close that whole native stack.
        activities[scope]?.mapNotNull { it.get() }?.toList()?.forEach { it.finish() }
    }
}

/** All child activities inherit the originating Telegram identity, including after process recreation. */
open class EmbeddedAdminActivity : ComponentActivity() {
    // Activity attaches its base context before assigning its Intent. Read the owner when
    // preferences are actually requested, so recreation cannot silently lose account isolation.
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        val owner = intent?.getStringExtra(FutoonIntegration.EXTRA_SCOPE)
        return if (owner == null) super.getSharedPreferences(name, mode)
        else super.getSharedPreferences("futoon_native_" + owner.filter(Char::isDigit) + "_" + name, mode)
    }
    override fun getApplicationContext(): Context {
        val original = super.getApplicationContext()
        val owner = intent?.getStringExtra(FutoonIntegration.EXTRA_SCOPE)
        return if (owner == null) original else ScopedAdminContext(original, owner)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val owner = intent.getStringExtra(FutoonIntegration.EXTRA_SCOPE)
        if (owner != null && FutoonIntegration.store(owner)?.isActive() != true) finish()
        if (!isFinishing) FutoonIntegration.track(this)
    }
    override fun onResume() {
        super.onResume()
        val owner = intent.getStringExtra(FutoonIntegration.EXTRA_SCOPE)
        if (owner != null && FutoonIntegration.store(owner)?.isActive() != true) finish()
    }
    override fun onDestroy() {
        FutoonIntegration.untrack(this)
        super.onDestroy()
    }
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        if (intent.component?.className?.startsWith("com.futoonpay.admin.") == true) {
            this.intent.getStringExtra(FutoonIntegration.EXTRA_SCOPE)?.let { intent.putExtra(FutoonIntegration.EXTRA_SCOPE, it) }
        }
        super.startActivityForResult(intent, requestCode, options)
    }
}

private class ScopedAdminContext(base: Context, val scope: String) : ContextWrapper(base) {
    private val prefix = "futoon_native_" + scope.filter(Char::isDigit) + "_"
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = baseContext.getSharedPreferences(prefix + name, mode)
}

private class EmbeddedEnvironment(private val store: FutoonIntegration.Store) : FutoonEnvironment {
    private val expired = MutableStateFlow(false)
    override val sessionExpired = expired.asStateFlow()
    private var base: HttpUrl? = store.address().takeIf(String::isNotBlank)?.let { runCatching { normalizeAdminBaseUrl(it) }.getOrNull() }
    private var activeRepository: FutoonRepository? = base?.let(::createRepository)
    override val adminBaseUrl: String get() = base?.toString().orEmpty()
    override val hasConfiguredEndpoint: Boolean get() = activeRepository != null
    override val repository: FutoonRepository get() = activeRepository ?: error("请先配置后台地址")
    override fun markAuthenticated() { expired.value = false }
    override fun configureEndpoint(raw: String): Result<String> = runCatching {
        check(store.isActive()) { "Telegram 账号已退出" }
        val normalized = normalizeAdminBaseUrl(raw)
        base?.let { store.clearCookies(it.toString()) }
        store.setAddress(normalized.toString())
        base = normalized
        activeRepository = createRepository(normalized)
        expired.value = false
        normalized.toString()
    }
    private fun createRepository(url: HttpUrl) = FutoonRepository(FutoonApiClient(
        BridgedCookieJar(store, url), url, onSessionExpired = { expired.value = true },
    ))
}

/** Shares the host's encrypted session instead of introducing another login or credential store. */
internal class BridgedCookieJar(private val store: FutoonIntegration.Store, private val base: HttpUrl) : AdminCookieStore {
    private fun checkOwner() {
        if (!store.isActive() || runCatching { normalizeAdminBaseUrl(store.address()) }.getOrNull() != base) {
            throw IOException("Telegram 账号或后台已变化，请返回聊天重新打开")
        }
    }
    @Synchronized private fun read(): LinkedHashMap<String, Cookie> {
        checkOwner()
        val result = linkedMapOf<String, Cookie>()
        val raw = store.loadCookies(base.toString())
        val rows = if (raw.isBlank()) JSONArray() else JSONArray(raw)
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val expiry = row.optLong("expiry", -1)
            if (expiry >= 0 && expiry <= System.currentTimeMillis()) continue
            val domain = row.getString("domain").trimStart('.')
            if (domain != base.host) continue
            val builder = Cookie.Builder().name(row.getString("name")).value(row.getString("value"))
                .hostOnlyDomain(domain).path(row.optString("path", "/"))
            if (expiry >= 0) builder.expiresAt(expiry)
            if (row.optBoolean("secure")) builder.secure()
            if (row.optBoolean("httpOnly")) builder.httpOnly()
            val cookie = builder.build()
            result[key(cookie)] = cookie
        }
        return result
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> = read().values.filter { it.matches(url) }
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (url.scheme != base.scheme || url.host != base.host || url.port != base.port) throw IOException("后台来源不一致")
        val all = read()
        for (cookie in cookies) {
            if (cookie.domain != base.host) continue
            if (cookie.expiresAt <= System.currentTimeMillis()) all.remove(key(cookie)) else all[key(cookie)] = cookie
        }
        val rows = JSONArray()
        for (cookie in all.values) rows.put(JSONObject().put("name", cookie.name).put("value", cookie.value)
            .put("domain", cookie.domain).put("path", cookie.path).put("secure", cookie.secure)
            .put("httpOnly", cookie.httpOnly).put("expiry", if (cookie.persistent) cookie.expiresAt else -1))
        store.saveCookies(base.toString(), rows.toString())
    }
    @Synchronized override fun has(name: String): Boolean = read().values.any { it.name == name }
    @Synchronized override fun clear() { checkOwner(); store.clearCookies(base.toString()) }
    private fun key(cookie: Cookie) = "${cookie.name}|${cookie.domain}|${cookie.path}"
}
