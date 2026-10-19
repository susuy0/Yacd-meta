package com.futoonpay.admin.data

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

internal class TestCookies : AdminCookieStore {
    var cleared = false
    override fun clear() { cleared = true }
    override fun has(name: String) = !cleared
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
    override fun loadForRequest(url: HttpUrl) = emptyList<Cookie>()
}

internal fun fixtureClient(handler: (Request) -> Pair<Int, String>, expired: () -> Unit = {}, cookies: TestCookies = TestCookies()): FutoonApiClient {
    val transport = OkHttpClient.Builder().addInterceptor { chain ->
        val (code, body) = handler(chain.request())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }.build()
    return FutoonApiClient(cookies, "https://fixture.example/private-admin/".toHttpUrl(), expired, transport)
}

class ApiTransportRegressionTest {
    @Test fun reasonNetworkFailureRetainsPartialSuccessForReasonOnlyRetry() = runBlocking {
        var statusCalls = 0; var reasonCalls = 0
        val repository = FutoonRepository(fixtureClient({ request ->
            if (request.url.queryParameter("act") == "setSettleStatus") { statusCalls++; 200 to "{\"code\":0}" }
            else { reasonCalls++; throw IOException("connection reset") }
        }))
        val result = repository.failSettlementWithReason("42", "checked", "18", "", "")
        assertFalse(result.success); assertTrue(result.partial); assertEquals(1, statusCalls); assertEquals(1, reasonCalls)
    }
    @Test fun failureReasonIsNotWrittenWhenSettlementStatusWasRejected() = runBlocking {
        var calls = 0
        val repository = FutoonRepository(fixtureClient({ calls++; 200 to "{\"code\":-1,\"msg\":\"状态修改被拒绝\"}" }))
        val result = repository.failSettlementWithReason("42", "checked", "18", "", "")
        assertFalse(result.success); assertFalse(result.partial); assertEquals(1, calls)
    }
    @Test fun requestsUseConfiguredPathAndPreserveEncodedSearchConditions() = runBlocking {
        var recorded: Request? = null
        val client = fixtureClient({ recorded = it; 200 to "{\"total\":0,\"rows\":[]}" })
        client.page("ajax_order.php?act=orderList", "order.php", 3, 20, mapOf("uid" to "18", "channel" to "12", "value" to "支付 & #1"))
        val request = recorded!!
        assertEquals("/private-admin/ajax_order.php", request.url.encodedPath)
        assertEquals("https://fixture.example/private-admin/order.php", request.header("Referer"))
        val body = request.body as FormBody
        val fields = (0 until body.size).associate { body.name(it) to body.value(it) }
        assertEquals("支付 & #1", fields["value"]); assertEquals("18", fields["uid"]); assertEquals("12", fields["channel"])
    }
    @Test fun httpUnauthorizedClearsCookiesAndNotifiesSessionOwner() = runBlocking {
        val cookies = TestCookies(); var notified = false
        val client = fixtureClient({ 401 to "{}" }, { notified = true }, cookies)
        try { client.jsonGet("ajax.php", "order.php"); fail() } catch (_: SessionExpiredException) { }
        assertTrue(cookies.cleared); assertTrue(notified)
    }
    @Test fun permissionFailureDoesNotLogOutTheAccount() = runBlocking {
        val cookies = TestCookies(); var notified = false
        val client = fixtureClient({ 403 to "{}" }, { notified = true }, cookies)
        try { client.jsonGet("ajax.php", "order.php"); fail() } catch (_: PermissionDeniedException) { }
        assertFalse(cookies.cleared); assertFalse(notified)
    }
    @Test fun expiredHtmlDuringSaveCannotBeReportedAsSuccess() = runBlocking {
        val cookies = TestCookies()
        val client = fixtureClient({ 200 to "<html><form action='login.php'><input name='user'><input name='pass'></form></html>" }, cookies = cookies)
        try { client.submitForm("save.php", "POST", "order.php", emptyMap()); fail() } catch (_: SessionExpiredException) { }
        assertTrue(cookies.cleared)
    }
    @Test fun malformedResponseCannotBeDisplayedAsEmptyList() = runBlocking {
        val client = fixtureClient({ 200 to "upstream timeout" })
        try { client.jsonGet("ajax.php", "order.php"); fail() } catch (error: IOException) { assertTrue(error.message!!.contains("无法解析")) }
    }
    @Test fun createReturnsRecordIdNeededForAccountAndRouteConfiguration() = runBlocking {
        val client = fixtureClient({ 200 to "{\"code\":0,\"data\":{\"id\":42}}" })
        assertEquals("42", client.postAction("save.php", "order.php", emptyMap()).recordId)
        assertEquals("42", client.submitForm("save.php", "POST", "order.php", emptyMap()).recordId)
    }
    @Test fun transportErrorIsPropagatedForRetryAndCannotReportSuccess() = runBlocking {
        val client = fixtureClient({ throw IOException("connection reset") })
        try { client.postAction("save.php", "order.php", emptyMap()); fail() } catch (error: IOException) { assertEquals("connection reset", error.message) }
    }
    @Test fun invalidRoutingIsRejectedBeforeNetworkMutation() = runBlocking {
        var calls = 0
        val repository = FutoonRepository(fixtureClient({ if (it.method == "POST") calls++; 200 to "{\"code\":0}" }))
        val form = NativeForm("路由", "ajax_pay.php?act=saveRollInfo&id=7", "POST", emptyList())
        assertFalse(repository.submitPaymentForm("rolls", form, mapOf("list[0][channel]" to "12", "list[0][weight]" to "-1")).success)
        assertFalse(repository.submitPaymentForm("rolls", form, mapOf("list[0][channel]" to "12", "list[0][weight]" to "1", "list[1][channel]" to "12", "list[1][weight]" to "2")).success)
        assertFalse(repository.submitPaymentForm("rolls", form, emptyMap()).success)
        assertEquals(0, calls)
    }
    @Test fun removedMembersAreOmittedFromRealRoutePost() = runBlocking {
        var sent = emptyMap<String, String>()
        val repository = FutoonRepository(fixtureClient({ request ->
            val body = request.body as FormBody
            sent = (0 until body.size).associate { body.name(it) to body.value(it) }
            200 to "{\"code\":0}"
        }))
        val form = NativeForm("路由", "ajax_pay.php?act=saveRollInfo&id=7", "POST", emptyList())
        assertTrue(repository.submitPaymentForm("rolls", form, mapOf("list[0][channel]" to "12", "list[0][weight]" to "1", "list[1][channel]" to "", "list[1][weight]" to "30")).success)
        assertEquals(mapOf("list[0][channel]" to "12", "list[0][weight]" to "1"), sent)
    }
    @Test fun unconfirmedSaveResponseIsNotARetryableExplicitRejection() = runBlocking {
        val client = fixtureClient({ 200 to "<html>upstream response missing</html>" })
        try { client.submitForm("save.php", "POST", "order.php", emptyMap()); fail() }
        catch (error: IOException) { assertTrue(error.message!!.contains("核对")) }
    }
    @Test fun nativeActionPathsKeepFieldsEncodedAndOnlyEditorsNavigate() {
        val edit = NativeAction("编辑公告", "gonggao.php?my=edit", fields = mapOf("title" to "a & b"))
        assertTrue(edit.opensNativeDocument())
        assertTrue(edit.documentPath().contains("a+%26+b"))
        assertFalse(NativeAction("删除", "ajax.php?act=delete&id=1", destructive = true).opensNativeDocument())
        assertFalse(NativeAction("配置", "ajax.php?act=setChannel&id=1").opensNativeDocument())
    }

}
