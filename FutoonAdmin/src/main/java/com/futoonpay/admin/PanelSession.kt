package com.futoonpay.admin

import com.futoonpay.admin.data.AdminCookieStore
import okhttp3.Cookie
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Session belongs to the current Telegram user and backend, supplied by the host. */
interface PanelSession {
    fun isActive(): Boolean
    fun address(): String
    fun loadCookies(): String
    fun saveCookies(json: String)
    fun clearCookies()
}

/** Separate capability: old hosts may read data, but cannot submit modern balance mutations. */
interface PanelBalanceSession : PanelSession {
    fun loadBalancePending(uid: String): String
    fun saveBalancePending(uid: String, json: String)
}

internal class SessionBalancePendingStore(private val session: PanelSession) : com.futoonpay.admin.data.BalancePendingStore {
    private val address = session.address()
    private fun active(): PanelBalanceSession {
        check(session.isActive() && session.address() == address) { "账号或后台已变化，请重新打开余额管理" }
        return session as? PanelBalanceSession ?: error("当前宿主不支持余额操作记录，请更新客户端")
    }
    override fun read(uid: String) = active().loadBalancePending(uid)
    override fun write(uid: String, value: String) = active().saveBalancePending(uid, value)
}
internal class PanelCookieJar(private val session: PanelSession, private val base: HttpUrl, private val onChanged: () -> Unit = {}) : AdminCookieStore {
    private fun checkScope() {
        if (!session.isActive() || session.address() != base.toString()) throw IOException("Telegram 账号或后台已变化，请重新打开面板")
    }
    @Synchronized private fun read(): LinkedHashMap<String, Cookie> {
        checkScope()
        val result = linkedMapOf<String, Cookie>()
        val raw = session.loadCookies()
        val rows = if (raw.isBlank()) JSONArray() else JSONArray(raw)
        for (i in 0 until rows.length()) {
            val row=rows.getJSONObject(i)
            val expiry=row.optLong("expiry",-1)
            if (expiry>=0 && expiry<=System.currentTimeMillis()) continue
            val domain=row.getString("domain").trimStart('.')
            if (domain!=base.host) continue
            val b=Cookie.Builder().name(row.getString("name")).value(row.getString("value")).hostOnlyDomain(domain).path(row.optString("path","/"))
            if (expiry>=0) b.expiresAt(expiry)
            if (row.optBoolean("secure")) b.secure()
            if (row.optBoolean("httpOnly")) b.httpOnly()
            val c=b.build(); result[key(c)]=c
        }
        return result
    }
    override fun loadForRequest(url: HttpUrl): List<Cookie> = read().values.filter { it.matches(url) }
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        checkScope()
        if (url.scheme!=base.scheme || url.host!=base.host || url.port!=base.port) throw IOException("后台来源不一致")
        val all=read()
        for(c in cookies) if(c.domain==base.host) { if(c.expiresAt<=System.currentTimeMillis()) all.remove(key(c)) else all[key(c)]=c }
        val rows=JSONArray()
        for(c in all.values) rows.put(JSONObject().put("name",c.name).put("value",c.value).put("domain",c.domain).put("path",c.path).put("secure",c.secure).put("httpOnly",c.httpOnly).put("expiry",if(c.persistent)c.expiresAt else -1))
        session.saveCookies(rows.toString())
        onChanged()
    }
    override fun has(name: String)=read().values.any { it.name==name }
    override fun clear() { checkScope(); session.clearCookies(); onChanged() }
    private fun key(c: Cookie)="${c.name}|${c.domain}|${c.path}"
}
