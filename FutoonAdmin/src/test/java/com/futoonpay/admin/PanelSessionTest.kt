package com.futoonpay.admin

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PanelSessionTest {
    private class Session: PanelSession {
        var active=true;var endpoint="https://fixture.example/admin/";var json=""
        override fun isActive()=active
        override fun address()=endpoint
        override fun loadCookies()=json
        override fun saveCookies(json: String){this.json=json}
        override fun clearCookies(){json=""}
    }
    @Test fun hostAndOriginalClientShareSameCookieFormat() {
        val s=Session();val base=s.endpoint.toHttpUrl();val jar=PanelCookieJar(s,base)
        jar.saveFromResponse(base,listOf(Cookie.Builder().name("admin_token").value("fixture").hostOnlyDomain(base.host).path("/admin/").httpOnly().secure().build()))
        assertEquals(-1,JSONArray(s.json).getJSONObject(0).getLong("expiry"))
        assertTrue(jar.has("admin_token"));assertEquals("fixture",jar.loadForRequest(base).single().value)
        assertTrue(jar.loadForRequest("https://fixture.example/other/".toHttpUrl()).isEmpty())
        jar.clear();assertFalse(jar.has("admin_token"))
    }
    @Test fun cookiesReloadAfterHostLoginAndExpiredCookiesAreIgnored() {
        val s=Session();val jar=PanelCookieJar(s,s.endpoint.toHttpUrl())
        assertFalse(jar.has("admin_token"))
        s.json="""[{"name":"admin_token","value":"fixture","domain":"fixture.example","path":"/admin/","expiry":-1}]"""
        assertTrue(jar.has("admin_token"))
        s.json=s.json.replace("\"expiry\":-1","\"expiry\":1")
        assertFalse(jar.has("admin_token"))
    }
    @Test fun ownerOrBackendSwitchBlocksOldClient() {
        val s=Session();val jar=PanelCookieJar(s,s.endpoint.toHttpUrl())
        s.active=false
        try {jar.has("admin_token");fail()}catch(_:IOException){}
        s.active=true;s.endpoint="https://other.example/admin/"
        try {jar.clear();fail()}catch(_:IOException){}
    }
}
