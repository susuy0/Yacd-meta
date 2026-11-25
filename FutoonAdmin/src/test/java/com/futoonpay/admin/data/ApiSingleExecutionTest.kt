package com.futoonpay.admin.data

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.security.KeyStore
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Real loopback HTTPS exchanges cover follow-ups hidden below application interceptors. */
class ApiSingleExecutionTest {
    private class Fixture : AutoCloseable {
        val server = MockWebServer()
        val transport: OkHttpClient

        init {
            // Public, test-only localhost identity. Nothing is read from the machine's key stores.
            val password = "fixture".toCharArray()
            val identity = KeyStore.getInstance("PKCS12").apply {
                requireNotNull(ApiSingleExecutionTest::class.java.getResourceAsStream("/fixtures/localhost-test.p12.base64")).use { input ->
                    load(Base64.getMimeDecoder().wrap(input), password)
                }
            }
            val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(identity, password) }
            val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(identity) }
            val manager = trust.trustManagers.filterIsInstance<X509TrustManager>().single()
            val tls = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, arrayOf(manager), null) }
            server.useHttps(tls.socketFactory, false)
            server.start()
            transport = OkHttpClient.Builder().sslSocketFactory(tls.socketFactory, manager).build()
        }

        fun client(transport: OkHttpClient = this.transport, cookies: AdminCookieStore = TestCookies()): FutoonApiClient =
            FutoonApiClient(cookies, server.url("/private-admin/"), transport = transport)

        override fun close() {
            transport.dispatcher.executorService.shutdown()
            transport.connectionPool.evictAll()
            server.shutdown()
        }
    }

    @Test fun realRedirectToAnotherPortStopsBeforeTheDestinationGetsASession() = runBlocking {
        Fixture().use { source ->
            Fixture().use { destination ->
                source.server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", destination.server.url("/private-admin/stolen.php")))
                destination.server.enqueue(MockResponse().setBody("{}"))
                try { source.client().jsonGet("ajax.php", "index.php"); fail("Expected stopped cross-port redirect") }
                catch (error: IOException) { assertTrue(error.message!!.contains("跳转")) }
                assertEquals(1, source.server.requestCount)
                assertEquals(0, destination.server.requestCount)
            }
        }
    }

    @Test fun legacyLoginKeepsCookiesAcrossAValidatedRedirect() = runBlocking {
        Fixture().use { fixture ->
            val cookies = object : AdminCookieStore {
                var saved: List<Cookie> = emptyList()
                override fun clear() { saved = emptyList() }
                override fun has(name: String) = saved.any { it.name == name }
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { saved = cookies }
                override fun loadForRequest(url: HttpUrl) = saved.filter { it.matches(url) }
            }
            fixture.server.enqueue(MockResponse().setResponseCode(302)
                .setHeader("Set-Cookie", "admin_token=fixture; Path=/private-admin/; Secure; HttpOnly")
                .setHeader("Location", "index.php"))
            fixture.server.enqueue(MockResponse().setBody("<html>dashboard</html>"))
            assertTrue(fixture.client(cookies = cookies).login("admin", "password", "1234").success)
            val login = fixture.server.takeRequest(1, TimeUnit.SECONDS)!!
            val landing = fixture.server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("POST", login.method)
            assertEquals("GET", landing.method)
            assertEquals("admin_token=fixture", landing.getHeader("Cookie"))
            assertEquals(0L, landing.bodySize)
            assertEquals(2, fixture.server.requestCount)
        }
    }

    @Test fun getFormMutationDoesNotReplayEvenOnRetryAfterZero503() = runBlocking {
        Fixture().use { fixture ->
            fixture.server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            fixture.server.enqueue(MockResponse().setBody("{\"code\":0}"))
            try {
                fixture.client().submitForm("save.php?existing=1", "GET", "edit.php", mapOf("value" to "a & b"))
                fail("Expected an unconfirmed mutation")
            } catch (error: MutationOutcomeUnknownException) {
                assertTrue(error.message!!.contains("核对"))
            }
            assertEquals(1, fixture.server.requestCount)
            val request = fixture.server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("GET", request.method)
            assertEquals("/private-admin/save.php", request.requestUrl!!.encodedPath)
            assertEquals("1", request.requestUrl!!.queryParameter("existing"))
            assertEquals("a & b", request.requestUrl!!.queryParameter("value"))
            assertEquals(0L, request.bodySize)
        }
    }

    @Test fun getAndPostFormMutationsNeverFollowRedirects() = runBlocking {
        listOf("GET", "POST").forEach { method ->
            listOf(301, 302, 303, 307, 308).forEach { status ->
                Fixture().use { fixture ->
                    fixture.server.enqueue(MockResponse().setResponseCode(status).setHeader("Location", "save-again.php"))
                    fixture.server.enqueue(MockResponse().setBody("{\"code\":0}"))
                    try {
                        fixture.client().submitForm("save.php", method, "edit.php", mapOf("id" to "42"))
                        fail("Expected unconfirmed $method / $status")
                    } catch (_: MutationOutcomeUnknownException) { }
                    assertEquals("$method / $status", 1, fixture.server.requestCount)
                }
            }
        }
    }

    @Test fun disconnectedGetMutationIsUncertainAndSentOnce() = runBlocking {
        Fixture().use { fixture ->
            fixture.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            fixture.server.enqueue(MockResponse().setBody("{\"code\":0}"))
            try {
                fixture.client().submitForm("save.php", "GET", "edit.php", mapOf("id" to "42"))
                fail("Expected uncertain result after disconnect")
            } catch (error: MutationOutcomeUnknownException) {
                assertNotNull(error.cause)
            }
            assertEquals(1, fixture.server.requestCount)
        }
    }

    @Test fun singleExecutionDoesNotInvokeAnInjectedAuthenticator() = runBlocking {
        Fixture().use { fixture ->
            var authenticationCalls = 0
            val authenticated = fixture.transport.newBuilder().authenticator(Authenticator { _, response ->
                authenticationCalls++
                response.request.newBuilder().header("Authorization", "fixture").build()
            }).build()
            fixture.server.enqueue(MockResponse().setResponseCode(401).setHeader("WWW-Authenticate", "Basic realm=fixture"))
            val cookies = TestCookies()
            try {
                fixture.client(authenticated, cookies).submitForm("save.php", "GET", "edit.php", emptyMap())
                fail("Expected expired session")
            } catch (_: SessionExpiredException) { }
            assertEquals(0, authenticationCalls)
            assertEquals(1, fixture.server.requestCount)
            assertTrue(cookies.cleared)
        }
    }

    @Test fun onlyAnExplicitResponseBecomesAnApiResult() = runBlocking {
        Fixture().use { fixture ->
            val api = fixture.client()
            fixture.server.enqueue(MockResponse().setBody("{\"code\":0,\"msg\":\"saved\",\"id\":42}"))
            val saved = api.submitForm("save.php", "GET", "edit.php", emptyMap())
            assertTrue(saved.success)
            assertFalse(saved.uncertain)
            assertEquals("42", saved.recordId)
            fixture.server.enqueue(MockResponse().setBody("{\"code\":-1,\"msg\":\"rejected\"}"))
            val rejected = api.submitForm("save.php", "GET", "edit.php", emptyMap())
            assertFalse(rejected.success)
            assertFalse(rejected.uncertain)
            fixture.server.enqueue(MockResponse().setBody("<html>response missing</html>"))
            try { api.submitForm("save.php", "GET", "edit.php", emptyMap()); fail("Expected uncertain response") }
            catch (_: MutationOutcomeUnknownException) { }
            assertEquals(3, fixture.server.requestCount)
        }
    }

    @Test fun malformedStatusIsUncertainRatherThanAnExplicitRejection() = runBlocking {
        Fixture().use { fixture ->
            val api = fixture.client()
            listOf("null", "\"unexpected\"", "true", "{}", "0.5").forEach { status ->
                fixture.server.enqueue(MockResponse().setBody("{\"code\":$status}"))
                try { api.submitForm("save.php", "GET", "edit.php", emptyMap()); fail(status) }
                catch (_: MutationOutcomeUnknownException) { }
            }
            fixture.server.enqueue(MockResponse().setBody("{\"code\":\"0\"}"))
            assertTrue(api.submitForm("save.php", "GET", "edit.php", emptyMap()).success)
            assertEquals(6, fixture.server.requestCount)
        }
    }
}
