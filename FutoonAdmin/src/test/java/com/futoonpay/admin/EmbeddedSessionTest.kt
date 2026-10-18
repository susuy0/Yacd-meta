package com.futoonpay.admin

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class EmbeddedSessionTest {
    private val base = "https://example.test/admin/".toHttpUrl()
    private class Store : FutoonIntegration.Store {
        var active = true
        var url = "https://example.test/admin/"
        var raw = ""
        override fun isActive() = active
        override fun address() = url
        override fun setAddress(address: String) { url = address }
        override fun loadCookies(address: String) = raw
        override fun saveCookies(address: String, cookies: String) { raw = cookies }
        override fun clearCookies(address: String) { raw = "" }
        override fun prepareEvidence(tradeNo: String, merchantTradeNo: String, merchantId: String, amount: String, currency: String) {}
    }
    @Test fun hostLoginAndNativeLoginShareCookieFormat() {
        val store = Store()
        store.raw = """[{"name":"admin_token","value":"host-token","domain":"example.test","path":"/admin/","secure":true,"httpOnly":true,"expiry":-1}]"""
        val jar = BridgedCookieJar(store, base)
        assertEquals("host-token", jar.loadForRequest(base).single().value)
        jar.saveFromResponse(base, listOf(Cookie.Builder().name("admin_token").value("native-token").hostOnlyDomain(base.host).path("/admin/").secure().httpOnly().build()))
        val row = JSONArray(store.raw).getJSONObject(0)
        assertEquals("native-token", row.getString("value"))
        assertEquals(-1L, row.getLong("expiry"))
        assertTrue(row.getBoolean("httpOnly"))
    }
    @Test fun logoutFromEitherSurfaceImmediatelyRemovesNativeCookies() {
        val store = Store(); val jar = BridgedCookieJar(store, base)
        jar.saveFromResponse(base,listOf(Cookie.Builder().name("admin_token").value("one").hostOnlyDomain(base.host).build()))
        assertTrue(jar.has("admin_token"))
        store.raw = ""
        assertFalse(jar.has("admin_token"))
    }
    @Test fun cookieStoresNeverCrossTelegramAccountsOrBackends() {
        val a = Store(); val b = Store()
        val jarA = BridgedCookieJar(a,base); val jarB = BridgedCookieJar(b,base)
        jarA.saveFromResponse(base,listOf(Cookie.Builder().name("admin_token").value("only-a").hostOnlyDomain(base.host).build()))
        assertFalse(jarB.has("admin_token"))
        a.active = false
        assertThrows(java.io.IOException::class.java) { jarA.loadForRequest(base) }
        b.url = "https://another.test/admin/"
        assertThrows(java.io.IOException::class.java) { jarB.loadForRequest(base) }
    }
    @Test fun expiredAndForeignCookiesAreExcluded() {
        val store = Store()
        store.raw = """[{"name":"expired","value":"x","domain":"example.test","path":"/","expiry":1},{"name":"foreign","value":"x","domain":"other.test","path":"/","expiry":-1}]"""
        assertTrue(BridgedCookieJar(store,base).loadForRequest(base).isEmpty())
    }
}
