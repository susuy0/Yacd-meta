package com.futoonpay.admin.data

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ApiBoundaryRegressionTest {
    private data class Reply(val code: Int, val body: String = "", val location: String? = null)

    private fun client(handler: (Request) -> Reply): FutoonApiClient {
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            val reply = handler(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(reply.code).message("fixture").apply {
                    reply.location?.let { header("Location", it) }
                }.body(reply.body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return FutoonApiClient(TestCookies(), "https://fixture.example/private-admin/".toHttpUrl(), transport = transport)
    }

    @Test fun readingFollowsValidatedRelativeHops() = runBlocking {
        val seen = mutableListOf<String>()
        val api = client { request ->
            seen += request.url.encodedPath
            when (seen.size) {
                1 -> Reply(302, location = "pages/entry.php")
                2 -> Reply(307, location = "../ajax.php?act=list")
                else -> Reply(200, "{\"rows\":[],\"total\":0}")
            }
        }
        assertEquals(0, api.jsonGet("index.php", "").getInt("total"))
        assertEquals(listOf("/private-admin/index.php", "/private-admin/pages/entry.php", "/private-admin/ajax.php"), seen)
    }

    @Test fun checksEveryHopInsteadOfOnlyTheFirstRedirect() = runBlocking {
        var calls = 0
        val api = client {
            calls++
            if (calls == 1) Reply(302, location = "nested/entry.php")
            else Reply(302, location = "../../outside.php")
        }
        try { api.document("index.php"); fail("Expected a stopped redirect") }
        catch (error: IOException) { assertTrue(error.message!!.contains("跳转")) }
        assertEquals(2, calls)
    }

    @Test fun unsafeRedirectsStopBeforeTheDestinationIsRequested() = runBlocking {
        listOf(
            "http://fixture.example/private-admin/next.php",
            "https://fixture.example:8443/private-admin/next.php",
            "https://other.example/private-admin/next.php",
            "//other.example/private-admin/next.php",
            "/private-admin-escape/next.php",
            "/private-admin/%252e%252e/outside.php",
        ).forEach { location ->
            var calls = 0
            val api = client { calls++; Reply(302, location = location) }
            try { api.document("index.php"); fail(location) }
            catch (error: IOException) { assertTrue(location, error.message!!.contains("跳转")) }
            assertEquals(location, 1, calls)
        }
    }

    @Test fun boundsRedirectLoops() = runBlocking {
        var calls = 0
        val api = client { calls++; Reply(302, location = "loop.php") }
        try { api.document("loop.php"); fail("Expected bounded redirect loop") }
        catch (error: IOException) { assertTrue(error.message!!.contains("跳转次数")) }
        assertEquals(9, calls)
    }

    @Test fun preservesLegacyLoginPostThenGetRedirect() = runBlocking {
        val requests = mutableListOf<Request>()
        val api = client { request ->
            requests += request
            if (requests.size == 1) Reply(302, location = "index.php")
            else Reply(200, "登陆管理中心成功")
        }
        assertTrue(api.login("admin", "password", "1234").success)
        assertEquals(listOf("POST", "GET"), requests.map { it.method })
        assertEquals("/private-admin/index.php", requests.last().url.encodedPath)
        assertNull(requests.last().body)
        assertNull(requests.last().header("Content-Type"))
    }

    @Test fun loginDoesNotReplayItsPasswordOn307Or308() = runBlocking {
        listOf(307, 308).forEach { status ->
            var calls = 0
            val api = client { calls++; Reply(status, location = "next.php") }
            assertFalse(api.login("admin", "password", "1234").success)
            assertEquals(1, calls)
        }
    }

    @Test fun captchaHtmlCannotIntroduceAnOutOfScopeImageRequest() = runBlocking {
        var calls = 0
        val api = client { calls++; Reply(200, "<img src='https://other.example/code.php'>") }
        try { api.loadCaptcha(); fail("Expected scoped captcha URL") }
        catch (_: IllegalArgumentException) { }
        assertEquals(1, calls)
    }

    @Test fun unsafeRefererOrFormActionIsRejectedBeforeMutation() = runBlocking {
        var calls = 0
        val api = client { calls++; Reply(200, "{\"code\":0}") }
        try { api.submitForm("../save.php", "GET", "index.php", emptyMap()); fail() }
        catch (_: IllegalArgumentException) { }
        try { api.submitForm("save.php", "POST", "https://fixture.example:8443/private-admin/index.php", emptyMap()); fail() }
        catch (_: IllegalArgumentException) { }
        assertEquals(0, calls)
    }
}
