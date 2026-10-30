package com.futoonpay.admin.data

import com.futoonpay.admin.OfficialBackendFeatures
import com.futoonpay.admin.PanelBusinessEngine
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OfficialBackendParityTest {
    private fun modern(handler: (Request) -> String): FutoonRepository = FutoonRepository(modernClient(handler))
    private fun modernClient(handler: (Request) -> String): FutoonApiClient {
        val transport = OkHttpClient.Builder().addInterceptor { chain -> Response.Builder().request(chain.request())
            .protocol(Protocol.HTTP_1_1).code(200).message("fixture").body(handler(chain.request()).toResponseBody("application/json".toMediaType())).build() }.build()
        return FutoonApiClient(TestCookies(), "https://fixture.example/svc_aabbcc/".toHttpUrl(), transport = transport)
    }
    private fun row(status: Int = 7, dispatch: String = "") = SettlementSummary("42", "18", "fixture", "5", "2", "fixture-address", "335.30", "47.56", "", "", "", "", status, true, false, "", dispatchState = dispatch)
    private fun fields(r: Request) = (r.body as FormBody).let { b -> (0 until b.size).associate { b.name(it) to b.value(it) } }

    @Test fun settlementStatusWritesUseNewPostAndNeverSendObsoleteExchangeFields() = runBlocking {
        val requests = mutableListOf<Request>(); val repo = modern { requests += it; "{\"code\":200}" }
        assertTrue(repo.setSettlementStatus("42", 6, "18", "7.1", "47.56").success)
        assertEquals("POST", requests.single().method)
        assertEquals("/svc_aabbcc/ajax_settle", requests.single().url.encodedPath)
        assertEquals("setSettleStatus", requests.single().url.queryParameter("act"))
        assertEquals(mapOf("id" to "42", "status" to "6"), fields(requests.single()))
        for (status in listOf(0, 2, 3, 4, 5, 7)) assertFalse(repo.setSettlementStatus("42", status, "", "", "").success)
        assertEquals(1, requests.size)
    }
    @Test fun newBatchOnlyOffersWebsiteStatesAndSkipsProcessingAndCompletedVoids() {
        val options = settlementBatchOptions(listOf(row(2), row(1).copy(id = "43"), row(7).copy(id = "44")), true)
        assertEquals(setOf("1", "6"), options.flatMap { it.commands }.map { it.fields["status"] }.toSet())
        assertTrue(options.all { it.commands[0].skipReason.isNotBlank() })
        val void = options.single { it.commands.first().fields["status"] == "6" }
        assertTrue(void.commands[1].skipReason.isNotBlank()); assertEquals("", void.commands[2].skipReason)
        assertTrue(void.warning.contains("余额不变"))
    }
    @Test fun officialStatusesAndUnfinishedFilterPreserveServerMeaning() {
        assertEquals("unfinished", SettlementSearchFilters(status = -2).toRequest()["dstatus"])
        assertEquals("待审核", row().displayStatus()); assertEquals("队列中", row(4).displayStatus())
        assertEquals("链上确认失败", row(5).displayStatus()); assertEquals("作废", row(6).displayStatus())
        assertEquals("待审核", row(3, "held").displayStatus()); assertEquals("待核对", row(3, "unknown").displayStatus())
        assertEquals("状态 99", row(99).displayStatus())
    }
    @Test fun usdtReceiptsNeverBecomeCnyFeesOrAnUnnamedChain() {
        assertEquals("—", row().displayFee()); assertEquals("USDT", row().actualCurrency())
        assertTrue(row().displayActualAmount().contains("USDT")); assertFalse(row().displayActualAmount().contains("¥"))
        assertEquals("Polygon (PoS)", row().displayChain()); assertEquals("0.20", row().copy(fee = "0.20").displayFee())
        assertEquals("CNY", row().copy(payType = "1").actualCurrency())
    }
    @Test fun plainRouteParserLoadsListsAndNativeActionsWithoutExpandingToExternalHosts() {
        val html = """<table><tr><th>ID</th><th>操作</th></tr><tr><td>8</td><td><button onclick="editframe(8)">编辑</button><button onclick="testweixin(8)">测试</button></td></tr></table>
        <form id="form-store"><input type="hidden" name="action"><input name="name"><input name="appid"><input name="appsecret"></form>
        <script>
        function editframe(id){ ${'$'}.ajax({type:'GET',url:'ajax_pay?act=getWeixin&id='+id}); }
        function testweixin(id){ ${'$'}.ajax({type:'POST',url:'ajax_pay?act=testweixin',data:{id:id}}); }
        function save(){ ${'$'}.ajax({type:'POST',url:'ajax_pay?act=saveWeixin',data:${'$'}('#form-store').serialize()}); }
        var list={url:'ajax_pay?act=channelMonitor'};
        var foreign={url:'https://other.example/ajax_pay?act=channelList'};
        </script>"""
        val doc = NativeDocumentParser.parse(html, "pay_weixin")
        val actions = doc.tables.single().rowActions.single()
        assertEquals("ajax_pay?act=getWeixin&id=8", actions.first { it.label == "编辑" }.endpoint)
        assertEquals(mapOf("id" to "8"), actions.first { it.label == "连通测试" }.fields)
        assertEquals("ajax_pay?act=saveWeixin", doc.forms.single().action)
        assertEquals("", doc.forms.single().fields.single { it.name == "appsecret" }.value)
        assertEquals(listOf("ajax_pay?act=getWeixin&id=", "ajax_pay?act=channelMonitor").toSet(), NativeDocumentParser.listEndpoints(html).toSet())
        assertTrue(NativeDocumentParser.parse("<a href='https://other.example/ajax_pay?act=delete' >删除</a>", "pay_weixin").actions.isEmpty())
    }
    @Test fun reasonDataRequiresLiveServerPermissionAndKeepsApprovalTokenOutOfDisplay() {
        val token = "a".repeat(64)
        val data = JSONObject("""{"status_label":"待审核","amount":"47.56","asset":"USDT","chain":"Polygon","reason":"需核验","next_step":"核实后审核","approval_token":"$token","full_approval":{"allowed":true},"reviews":[{"label":"频率审核","category":"frequency","state":"pending","reason":"超过限制","can_approve":true,"fingerprint":"${"b".repeat(64)}"}]}""")
        val detail = parseSettlementReason(data)
        assertTrue(detail.canApproveFull); assertTrue(detail.reviews.single().canApprove)
        assertTrue(detail.sections.any { it.second.contains("USDT") }); assertFalse(detail.sections.toString().contains(token))
        data.remove("approval_token"); val expired = parseSettlementReason(data)
        assertFalse(expired.canApproveFull); assertFalse(expired.reviews.single().canApprove)
    }
    @Test fun explorerLinksAreLimitedToOfficialTransactionUrls() {
        val tx = "0x" + "a".repeat(64)
        fun detail(url: String) = parseSettlementReason(JSONObject().put("completion", JSONObject().put("method", "blockchain").put("explorer_url", url)))
        assertEquals("https://polygonscan.com/tx/$tx", detail("https://polygonscan.com/tx/$tx").explorerUrl)
        assertEquals("", detail("https://polygonscan.com.evil.example/tx/$tx").explorerUrl)
        assertEquals("", detail("javascript:alert(1)").explorerUrl)
    }
    @Test fun retiredModulesAreBlockedBeforeAnyNewBackendNetworkRequest() {
        var calls = 0; val engine = PanelBusinessEngine(modernClient { calls++; "{}" })
        for (module in listOf("proxies", "announcements", "risk", "payment_settings", "site_settings", "login_logs")) {
            assertFalse(OfficialBackendFeatures.available(module))
            try { engine.execute("module.read", JSONObject().put("module", module)); fail(module) } catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, calls); assertTrue(OfficialBackendFeatures.available("weixin_apps")); assertTrue(OfficialBackendFeatures.available("account_settings"))
    }
    @Test fun weixinEditorNeverDisplaysSecretAndPreservesItOnlyForItsLoadedRecord() = runBlocking {
        val submissions = mutableListOf<Map<String, String>>()
        val repo = modern { r -> when (r.url.queryParameter("act")) {
            "getWeixin" -> """{"code":0,"data":{"id":"8","name":"Fixture","appid":"wx-fixture","appsecret":"fixture-secret","type":"0"}}"""
            "saveWeixin" -> { submissions += fields(r); "{\"code\":0}" }
            else -> "<html></html>"
        } }
        val form = NativeForm("公众号", "ajax_pay?act=saveWeixin", "POST", listOf(NativeField("action", "", "", "hidden"), NativeField("id", "", "", "hidden"), NativeField("name", "名称", "", "text"), NativeField("appid", "AppID", "", "text"), NativeField("appsecret", "AppSecret", "", "password", sensitive = true)))
        val loaded = repo.paymentEditor("weixin_apps", NativeAction("编辑", "ajax_pay?act=getWeixin&id=8"), NativeDocument("公众号", emptyList(), emptyList(), listOf(form)))
        assertEquals("", loaded.fields.single { it.name == "appsecret" }.value)
        val values = loaded.fields.filterNot { it.sensitive }.associate { it.name to it.value }
        assertTrue(repo.submitPaymentForm("weixin_apps", loaded, values).success)
        assertEquals("fixture-secret", submissions.single()["appsecret"])
        assertFalse(repo.submitPaymentForm("weixin_apps", loaded.copy(fields = loaded.fields.map { if(it.name=="id")it.copy(value="9") else it }), values).success)
        assertEquals(1, submissions.size)
    }
    @Test fun recipientEditLoadsOriginalNameAndPaymentTypeInsteadOfUsingChainCode() = runBlocking {
        var saved = emptyMap<String, String>()
        val html = """<select id="pay_type" name="pay_type"><option value="2">微信</option><option value="5">USDT-Polygon</option></select><input id="pay_account" value="fixture-address"><input id="pay_name" value="Fixture recipient">"""
        val repo = modern { request -> when (request.url.queryParameter("act")) {
            "settle_info" -> { assertEquals("GET", request.method); JSONObject().put("code",0).put("pay_type",5).put("data",html).toString() }
            "settle_save" -> { saved = fields(request); "{\"code\":0}" }
            else -> error("Unexpected call")
        } }
        val info = repo.settlementInfo("42")
        assertEquals("5", info.payType); assertEquals("Fixture recipient", info.name)
        assertTrue(repo.saveSettlementInfo("42", info.payType, info.account, info.name).success)
        assertEquals("5", saved["pay_type"]); assertNotEquals("2", saved["pay_type"])
        assertEquals("Fixture recipient", saved["pay_name"])
        assertFalse(repo.saveSettlementInfo("42", info.payType, info.account, "").success)
        try { parseSettlementAccountInfo(JSONObject().put("code",0).put("pay_type",5).put("data","<div>incomplete</div>")); fail() } catch (_: IllegalStateException) { }
    }

    @Test fun hiddenEditorTablesNeverLeakIntoTheVisibleListButFormsRemainEditable() {
        val html = """<div class="modal" aria-hidden="true"><form id="group-editor"><table><tr><th>支付方式</th></tr><tr><td><select name="type"><option value="1">支付宝</option></select></td></tr></table><input name="name"></form></div>
            <table><tr><th>GID</th><th>名称</th></tr><tr><td>12</td><td>真实用户组</td></tr></table>"""
        val doc = NativeDocumentParser.parse(html, "glist")
        assertEquals(1, doc.tables.size); assertEquals(listOf("12", "真实用户组"), doc.tables.single().rows.single())
        assertEquals("select", doc.forms.single().fields.single { it.name == "type" }.type)
    }
    @Test fun statisticsUseReturnedColumnsPreserveMerchantIdsAndRejectErrorResponses() {
        val result = parseMerchantStatistics(JSONObject("""{"code":0,"columns":{"uid":"商户号","channel_999":"新增通道","total":"合计"},"data":[{"uid":"10001","channel_999":null,"total":"20.125"}]}"""))
        assertEquals(listOf("商户号", "合计", "新增通道"), result.headers)
        assertEquals(mapOf("商户号" to "10001", "合计" to "20.13", "新增通道" to "0.00"), result.headers.zip(result.rows.single()).toMap())
        try { parseMerchantStatistics(JSONObject("""{"code":-1,"msg":"没有权限"}""")); fail() } catch (e: IllegalStateException) { assertEquals("没有权限", e.message) }
    }

    @Test fun inactiveScanUsesTheOfficialReadEndpointAndFreezeOnlyUsesExplicitIds() = runBlocking {
        val requests = mutableListOf<Request>()
        val repo = modern { request -> requests += request
            if (request.url.queryParameter("act") == "scanInactiveMerchants") """{"code":0,"total":1,"window_start":"2026-10-26","rows":[{"uid":"18","merchant_name":"Fixture","status":1,"recent_order_count":0}]}""" else """{"code":0,"msg":"完成"}"""
        }
        val scan = repo.inactiveMerchants(InactiveMerchantFilters("no", "normal", " Fixture "))
        assertEquals("scanInactiveMerchants", requests.single().url.queryParameter("act"))
        assertEquals(mapOf("whitelist" to "no", "account_status" to "normal", "keyword" to "Fixture"), fields(requests.single()))
        assertEquals("18", scan.rows.single().uid); assertEquals(1, scan.total)
        try { repo.freezeInactiveMerchants(listOf("")); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(1, requests.size)
        assertTrue(repo.freezeInactiveMerchants(listOf("18", "18")).success)
        assertEquals("batchFreezeInactiveMerchants", requests.last().url.queryParameter("act"))
        assertEquals(mapOf("uids" to "18"), fields(requests.last()))
    }

}
