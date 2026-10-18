package com.futoonpay.admin.data

import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import org.junit.Assert.*
import org.junit.Test

internal fun profileFixture(uid: String = "1011", money: String = "20.00", status: String = "2") = """
<form onsubmit="return editUserChannelInfo(this, $uid)"><input name="key" value="PRIVATE-KEY"></form>
<form onsubmit="return editUser(this, $uid)" method="POST">
<div class="form-group"><label>邮箱</label><input name="email" value="test@example.com" required></div><input name="phone" value="">
<input name="username" value="原收款人"><input name="account" value="account-1">
<input name="money" value="$money"><input name="gid" value="4">
<select name="status" default="$status"><option value="0">停用</option><option value="1">正常</option></select>
<select name="pay" default="2"><option value="0">关闭</option><option value="1">开启</option><option value="2">待确认</option></select>
<select name="settle" default="0"><option value="1">开启</option><option value="0">关闭</option></select>
<input name="pwd" type="password" value="PASSWORD-HASH">
<input name="remark" value="旧备注">
</form>
""".trimIndent()

class ConfigurationFeaturesTest {
    private fun configurationClient(handler: (okhttp3.Request) -> Pair<Int, String>) = fixtureClient({ request ->
        val result = handler(request)
        if (result.second in setOf("<html/>", "<html></html>")) result.first to "<html><script>load({url:'ajax_pay.php?act=channelList'});</script></html>" else result
    })
    @Test fun merchantUsesOnlyTheCorrectUidFormAndMasksCredentials() {
        val form = merchantProfileForm(profileFixture(), "1011")
        assertEquals("ajax_user.php?act=editUser&uid=1011", form.action)
        assertFalse(form.fields.any { it.name == "key" })
        assertEquals("", form.fields.first { it.name == "pwd" }.value)
        assertTrue(form.fields.first { it.name == "money" }.readOnly)
    }
    @Test fun foreignUidAndAbsentProfileAreRejected() {
        for (html in listOf(profileFixture("99"), "<form><input name='money' value='1'></form>")) {
            try { merchantProfileForm(html, "1011"); fail("Wrong form accepted") } catch (_: IllegalStateException) { }
        }
    }
    @Test fun defaultStatusAndUnknownValueSurviveParsing() {
        val form = merchantProfileForm(profileFixture(), "1011")
        assertEquals("2", form.fields.first { it.name == "status" }.value)
        assertTrue(form.fields.first { it.name == "status" }.options.any { it.value == "2" })
        assertEquals("0", form.fields.first { it.name == "settle" }.value)
    }
    @Test fun onlyChangedProfileValuesMergeIntoLatestBalanceAndStates() {
        val original = merchantProfileForm(profileFixture(), "1011")
        val fresh = merchantProfileForm(profileFixture(money = "88.00", status = "1"), "1011")
        val values = original.fields.associate { it.name to it.value } + mapOf("email" to "changed@example.com", "money" to "-100", "key" to "EVIL", "uid" to "9")
        val merged = mergeMerchantProfile(original, fresh, values)
        assertEquals("88.00", merged["money"])
        assertEquals("1", merged["status"])
        assertEquals("2", merged["pay"]); assertEquals("0", merged["settle"])
        assertEquals("changed@example.com", merged["email"])
        assertEquals("account-1", merged["account"])
        assertFalse(merged.containsKey("key")); assertFalse(merged.containsKey("pwd"))
    }
    @Test fun blankRequiredAndInvalidStatusAreRejected() {
        val form = merchantProfileForm(profileFixture(), "1011")
        for (values in listOf(mapOf("email" to ""), mapOf("pay" to "999"))) {
            try { mergeMerchantProfile(form, form, values); fail() } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun realProfileSaveReloadsThenSendsCompleteFreshPayload() = runBlocking {
        var posted = emptyMap<String, String>(); val paths = mutableListOf<String>()
        val repository = FutoonRepository(configurationClient { request ->
            paths += request.url.encodedPath
            if (request.method == "POST") {
                val body = request.body as FormBody
                posted = (0 until body.size).associate { body.name(it) to body.value(it) }
                assertEquals("1011", request.url.queryParameter("uid"))
                200 to "{\"code\":0}"
            } else 200 to profileFixture(money = "30.00")
        })
        val original = merchantProfileForm(profileFixture(), "1011")
        assertTrue(repository.saveMerchantProfile("1011", original, mapOf("phone" to "13000000000")).success)
        assertEquals(listOf("/private-admin/uset.php", "/private-admin/ajax_user.php"), paths)
        assertEquals("30.00", posted["money"]); assertEquals("13000000000", posted["phone"])
        assertEquals("test@example.com", posted["email"]); assertEquals("2", posted["pay"])
    }
    private fun methodForm() = NativeForm("编辑支付方式", "ajax_pay.php?act=savePayType", "POST", listOf(
        NativeField("id", "ID", "2", "hidden"), NativeField("name", "类型标识", "wxpay", "text", readOnly = true),
        NativeField("showname", "名称", "微信", "text"), NativeField("device", "设备", "0", "select"),
        NativeField("rate", "费率", "3.00", "number"), NativeField("channels[]", "关联通道", "12", "multiselect", listOf(NativeOption("12", "蓝色微信"))),
    ))
    private val catalogue = """{"total":2,"rows":[{"id":12,"name":"蓝色微信","type":2,"rate":"2.50","proxy_id":7},{"id":15,"name":"支付宝","type":1,"rate":"2.00","proxy_id":0}]}"""
    @Test fun methodRateAndBindingAreReallyPostedAndReadBack() = runBlocking {
        var posted = emptyMap<String, String>()
        val repository = FutoonRepository(configurationClient { request -> when (request.url.queryParameter("act")) {
            "channelList" -> 200 to catalogue
            "savePayType" -> { val body = request.body as FormBody; posted = (0 until body.size).associate { body.name(it) to body.value(it) }; 200 to "{\"code\":0}" }
            "getPayType" -> 200 to """{"code":0,"data":{"id":2,"rate":"3.0","channels":[12]}}"""
            else -> 200 to "<html></html>"
        } })
        val form = methodForm()
        assertTrue(repository.submitPaymentForm("payment_types", form, form.fields.associate { it.name to it.value }).success)
        assertEquals("3.00", posted["rate"]); assertEquals("12", posted["channels[0]"]); assertEquals("wxpay", posted["name"])
        assertFalse(posted.containsKey("channels[]"))
    }
    @Test fun ignoredFeeOrMissingBindingCannotReportSuccess() = runBlocking {
        for (saved in listOf("""{"rate":"2.00","channels":[12]}""", """{"rate":"3.00"}""")) {
            val repository = FutoonRepository(configurationClient { request -> when (request.url.queryParameter("act")) {
                "channelList" -> 200 to catalogue
                "getPayType" -> 200 to "{\"code\":0,\"data\":$saved}"
                else -> 200 to "{\"code\":0}"
            } })
            val form = methodForm()
            assertFalse(repository.submitPaymentForm("payment_types", form, form.fields.associate { it.name to it.value }).success)
        }
    }
    @Test fun foreignOrMissingChannelCannotBeBound() = runBlocking {
        var saves = 0
        val repository = FutoonRepository(configurationClient { request ->
            if (request.url.queryParameter("act") == "savePayType") saves++
            if (request.url.queryParameter("act") == "channelList") 200 to catalogue else 200 to "{\"code\":0}"
        })
        val form = methodForm()
        for (selection in listOf("15", "9", "12,15")) assertFalse(repository.submitPaymentForm("payment_types", form, form.fields.associate { it.name to it.value } + ("channels[]" to selection)).success)
        assertEquals(0, saves)
    }
    @Test fun rateOutsideRangeCannotReachTheNetwork() = runBlocking {
        var calls = 0
        val repository = FutoonRepository(configurationClient { calls++; 200 to "{}" })
        val form = methodForm()
        for (rate in listOf("-1", "100.01", "abc", "")) assertFalse(repository.submitPaymentForm("payment_types", form, form.fields.associate { it.name to it.value } + ("rate" to rate)).success)
        assertEquals(0, calls)
    }
    @Test fun legacyMethodDoesNotInventRateOrBindingFields() {
        val form = methodForm().copy(fields = methodForm().fields.filterNot { it.name == "rate" || it.name == "channels[]" })
        assertNull(form.methodFeeField()); assertNull(form.methodBindingField())
    }
    @Test fun createdRecordWithFailedReadbackIsPartialAndMustNotBeCreatedAgain() = runBlocking {
        for (response in listOf("{\"code\":0}", "{\"code\":0,\"id\":5}")) {
            val repository = FutoonRepository(configurationClient { request ->
                if (request.url.queryParameter("act") == "savePayType") 200 to response else 200 to "{}"
            })
            val form = methodForm().copy(fields = methodForm().fields.filterNot { it.name == "channels[]" }.map { if (it.name == "id") it.copy(value = "") else it })
            val result = repository.submitPaymentForm("payment_types", form, form.fields.associate { it.name to it.value })
            assertFalse(result.success); assertTrue(result.partial)
        }
    }
    @Test fun proxyAssociationIsByIdAndAbsenceIsDistinctFromZero() = runBlocking {
        val repository = FutoonRepository(configurationClient { request -> if (request.url.queryParameter("act") == "channelList") 200 to catalogue else 200 to "<html/>" })
        val channels = repository.configurationChannels()
        assertEquals("7", channels.first().proxyId); assertEquals("0", channels.last().proxyId)
        assertEquals("2.50", channels.first().cost)
    }
    @Test fun failedChannelQueryMustNotBePresentedAsAnEmptyList() = runBlocking {
        val repository = FutoonRepository(configurationClient { request -> if (request.url.queryParameter("act") == "channelList") 200 to "{\"code\":-1,\"msg\":\"权限不足\"}" else 200 to "<html/>" })
        try { repository.configurationChannels(); fail() } catch (error: java.io.IOException) { assertEquals("权限不足", error.message) }
    }
    @Test fun unchangedIdentityNumberSurvivesWithoutExposingItInTheEditor() {
        val html = profileFixture().replace("<input name=\"remark\"", "<input name=\"certno\" value=\"TEST-ID-123\"><input name=\"remark\"")
        val form = merchantProfileForm(html, "1011")
        assertEquals("", form.fields.first { it.name == "certno" }.value)
        val submitted = merchantProfileSubmission(html, "1011", form, mapOf("email" to "new@example.com"))
        assertEquals("TEST-ID-123", submitted["certno"])
        assertFalse(submitted.containsKey("pwd"))
    }
    @Test fun checkboxBindingsKeepEveryCheckedMember() {
        val form = NativeDocumentParser.parse("""<form><label><input type="checkbox" name="channels[]" value="12" checked>蓝色</label><label><input type="checkbox" name="channels[]" value="13" checked>绿色</label><label><input type="checkbox" name="channels[]" value="15">支付宝</label></form>""", "pay_type.php").forms.single()
        val field = form.methodBindingField()!!
        assertEquals(setOf("12", "13"), selectedChannelIds(field.value))
        assertEquals(3, field.options.size)
    }
    @Test fun completeChannelCatalogueReadsSubsequentPages() = runBlocking {
        val offsets = mutableListOf<String>()
        val repository = FutoonRepository(configurationClient { request ->
            if (request.url.queryParameter("act") == "channelList") {
                val body = request.body as FormBody
                val fields = (0 until body.size).associate { body.name(it) to body.value(it) }
                offsets += fields.getValue("offset")
                val id = if (offsets.size == 1) 12 else 13
                200 to "{\"total\":2,\"rows\":[{\"id\":$id,\"name\":\"通道$id\",\"type\":2}]}"
            } else 200 to "<html/>"
        })
        assertEquals(listOf("12", "13"), repository.configurationChannels().map { it.id })
        assertEquals(listOf("0", "100"), offsets)
    }
    @Test fun legacyCatalogueUsesItsActualPaginationAndKnownDetailEndpoint() = runBlocking {
        val acts = java.util.Collections.synchronizedList(mutableListOf<String>())
        val repository = FutoonRepository(fixtureClient({ request ->
            val act = request.url.queryParameter("act").orEmpty()
            acts += act
            when {
                act == "getChannel" -> {
                    val id = request.url.queryParameter("id")!!
                    200 to "{\"code\":0,\"data\":{\"id\":$id,\"name\":\"通道$id\",\"type\":2,\"proxyid\":7,\"rate\":\"2.50\"}}"
                }
                request.url.encodedPath.endsWith("pay_channel.php") -> {
                    val second = request.url.queryParameter("page") == "2"
                    val id = if (second) 13 else 12
                    200 to "<table><thead><tr><th>ID</th><th>名称</th></tr></thead><tbody><tr><td>$id</td><td>通道$id</td></tr></tbody></table>" + if (second) "" else "<ul class='pagination'><li><a href='pay_channel.php?page=2'>下一页</a></li></ul>"
                }
                else -> 200 to "<html/>"
            }
        }))
        assertEquals(listOf("12", "13"), repository.configurationChannels().map { it.id })
        assertFalse(acts.contains("channelList"))
        assertEquals(2, acts.count { it == "getChannel" })
    }
}
