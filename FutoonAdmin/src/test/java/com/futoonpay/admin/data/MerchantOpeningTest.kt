package com.futoonpay.admin.data

import com.futoonpay.admin.*
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.URLDecoder

internal fun openingFixture(group: String = "4", token: String = "fresh") = """
<form onsubmit="return editUser(this, 999)"><input name="key" value="unrelated"></form>
<h3>添加商户</h3><form method="POST" onsubmit="return addUser(this)">
<input type="hidden" name="csrf" value="$token">
<div class="form-group"><label>邮箱(登录账号):</label><input name="email" placeholder="可留空"></div>
<div class="form-group"><label>登录密码:</label><input type="text" name="pwd" placeholder="留空则只能使用密钥登录"></div>
<div class="form-group"><label>用户组:</label><select name="gid"><option value="0">默认用户组</option><option value="$group">测试用户组</option></select></div>
<div class="form-group"><label>网站域名:</label><input name="url"></div>
<div class="form-group"><label>结算方式:</label><select name="settle_id"><option value="4">USDT-TRON地址</option><option value="5">USDT-Polygon地址</option><option value="6">USDT-BEP20地址</option></select></div>
<div class="form-group"><label>结算账号:</label><input name="account"></div>
<div class="form-group"><label>*备注:</label><input name="username" required></div>
<div class="form-group"><label>手续费扣除模式:</label><select name="mode"><option value="0">余额扣费</option><option value="1">订单加费</option></select></div>
<div class="form-group"><label>商户状态:</label><select name="status"><option value="1">1_正常</option><option value="0">0_封禁</option><option value="2">2_未审核</option></select></div>
<div class="form-group"><label>支付权限:</label><select name="pay"><option value="1">1_开启</option><option value="0">0_关闭</option></select></div>
<div class="form-group"><label>结算权限:</label><select name="settle"><option value="1">1_开启</option><option value="0">0_关闭</option></select></div>
<input type="submit" value="确定添加"></form>
""".trimIndent()
internal fun openingForm(vararg values: Pair<String, String>): NativeForm = merchantOpeningForm(openingFixture()).let { form ->
    val edits = mapOf("username" to "@fixture(业务)", *values)
    form.copy(fields = form.fields.map { field -> field.copy(value = edits[field.name] ?: field.value) })
}
internal fun Request.openingFields(): Map<String, String> {
    val buffer = Buffer(); body!!.writeTo(buffer)
    return buffer.readUtf8().split('&').associate { part -> val pair = part.split('=', limit = 2); URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8") }
}
internal val fixtureMerchantKey = "A1".repeat(16)
internal const val fixtureBinding = "https://t.me/FixtureBot?start=synthetic_token_bind"
internal val openingSuccess get() = JSONObject().put("code", 0).put("uid", "9001").put("key", fixtureMerchantKey).toString()

class MerchantOpeningTest {
    @Test fun onlyTheAddUserFormIsParsedAndPasswordIsMasked() {
        val form = merchantOpeningForm(openingFixture())
        assertEquals(MERCHANT_OPENING_ACTION, form.action)
        assertFalse(form.fields.any { it.name == "key" })
        assertTrue(form.fields.first { it.name == "pwd" }.sensitive)
        assertEquals("password", form.fields.first { it.name == "pwd" }.type)
        assertEquals("0", form.fields.first { it.name == "settle" }.value)
        assertEquals(listOf("1", "0"), form.fields.first { it.name == "settle" }.options.map { it.value })
    }
    @Test fun editingOrMissingRequiredOpeningFieldsCannotBeMistakenForCreation() {
        for (html in listOf(profileFixture(), openingFixture().replace("name=\"username\"", "name=\"wrong\""), openingFixture().replace("method=\"POST\"", "method=\"GET\""))) {
            assertTrue(runCatching { merchantOpeningForm(html) }.isFailure)
        }
    }
    @Test fun d0AndD1PostCorrectPermissionsAndKeepFeeModeIndependent() = runBlocking {
        for ((cycle, permission) in listOf("D0" to "1", "D1" to "0")) {
            var sent: Request? = null
            val repository = FutoonRepository(fixtureClient({ request ->
                if (request.url.queryParameter("act") == "addUser") { sent = request; 200 to openingSuccess } else 200 to openingFixture(token = "rotated")
            }))
            val form = openingForm("settle" to permission, "mode" to "1", "url" to "fixture.example", "pwd" to "synthetic password")
            assertEquals(OpeningOutcome.CREATED, repository.openMerchant(form).outcome)
            assertEquals("POST", sent!!.method); assertEquals("/private-admin/ajax_user.php", sent!!.url.encodedPath)
            assertEquals("https://fixture.example/private-admin/uset.php?my=add", sent!!.header("Referer"))
            assertTrue(sent!!.body!!.isOneShot())
            val values = sent!!.openingFields()
            assertEquals(permission, values["settle"]); assertEquals("1", values["mode"]); assertEquals("rotated", values["csrf"])
            assertEquals("synthetic password", values["pwd"]); assertEquals(cycle, settlementCycle(values.getValue("settle")))
        }
    }
    @Test fun blankRemarkAndRemovedGroupAreRejectedBeforePost() = runBlocking {
        var posts = 0
        val repository = FutoonRepository(fixtureClient({ request -> if (request.method == "POST") posts++; 200 to openingFixture(group = "99") }))
        for (form in listOf(openingForm("username" to ""), openingForm("gid" to "4"))) assertTrue(runCatching { repository.openMerchant(form) }.isFailure)
        assertEquals(0, posts)
    }
    @Test fun duplicateEmailPreventsAccountCreation() = runBlocking {
        var creations = 0
        val repository = FutoonRepository(fixtureClient({ request -> when (request.url.queryParameter("act")) {
            "userList" -> 200 to """{"total":1,"rows":[{"uid":"19","email":"Owner@fixture.example"}]}"""
            "addUser" -> { creations++; 200 to openingSuccess }
            else -> 200 to openingFixture()
        } }))
        val result = repository.openMerchant(openingForm("email" to "owner@fixture.example"))
        assertEquals(OpeningOutcome.REJECTED, result.outcome); assertTrue(result.message.contains("19")); assertEquals(0, creations)
    }
    @Test fun timeoutIsUncertainAndCannotReportFailureAsSafeToRepeat() = runBlocking {
        var writes = 0
        val repository = FutoonRepository(fixtureClient({ request -> if (request.url.queryParameter("act") == "addUser") { writes++; throw IOException("lost response") }; 200 to openingFixture() }))
        assertEquals(OpeningOutcome.UNCERTAIN, repository.openMerchant(openingForm()).outcome)
        assertEquals(1, writes)
    }
    @Test fun successfulCreationWithMissingKeyIsStillCreatedAndMissingIdIsUncertain() {
        assertEquals(OpeningOutcome.CREATED, merchantOpeningResult(JSONObject("""{"code":0,"uid":9001}""")).outcome)
        assertEquals(OpeningOutcome.UNCERTAIN, merchantOpeningResult(JSONObject("""{"code":0,"key":"unused"}""")).outcome)
        assertEquals(OpeningOutcome.UNCERTAIN, merchantOpeningResult(JSONObject("{}")).outcome)
        assertEquals(OpeningOutcome.REJECTED, merchantOpeningResult(JSONObject("""{"code":-1,"msg":"拒绝"}""")).outcome)
        assertFalse(merchantOpeningResult(JSONObject(openingSuccess)).toString().contains(fixtureMerchantKey))
    }
    @Test fun bindingUsesTheObservedEndpointAndBindField() = runBlocking {
        var seen: Request? = null
        val repository = FutoonRepository(fixtureClient({ request -> seen = request; 200 to JSONObject().put("code", 0).put("bind", fixtureBinding).toString() }))
        assertEquals(fixtureBinding, repository.merchantBinding("9001"))
        assertEquals("GET", seen!!.method); assertEquals("bind", seen!!.url.queryParameter("act")); assertEquals("9001", seen!!.url.queryParameter("uid"))
        assertEquals("https://fixture.example/private-admin/ulist.php", seen!!.header("Referer"))
    }
    @Test fun missingRejectedOrUntrustedBindingUrlsCannotEnterDrafts() {
        for (url in listOf("", "http://t.me/FixtureBot?start=token_bind", "https://other.example/?start=token_bind", "https://t.me/FixtureBot?start=token_bind&redirect=bad", "https://t.me/FixtureBot?start=login")) {
            assertTrue(runCatching { merchantBindingLink(JSONObject().put("code", 0).put("bind", url)) }.isFailure)
        }
        assertTrue(runCatching { merchantBindingLink(JSONObject().put("code", -1).put("bind", fixtureBinding)) }.isFailure)
    }
    @Test fun oldChatSettingsGetTheTemplateAndNewSettingsPreserveItAlongsideTagsAndReplies() {
        assertEquals(defaultOpeningTemplate, ChatToolsConfig.parse("""{"version":1,"tags":[],"tabs":[]}""").openingTemplate)
        val custom = "用户 {商户ID} 密钥 {密钥} {结算周期} {绑定链接}"
        val settings = ChatToolsConfig(tags = listOf(ChatTag(text = "VIP", telegramIds = listOf("5824870757"))), openingTemplate = custom)
        val roundTrip = ChatToolsConfig.parse(settings.json())
        assertEquals(custom, roundTrip.openingTemplate); assertEquals("VIP", roundTrip.tagFor("5824870757"))
    }
    @Test fun templateUsesReturnedValuesAndModeInsteadOfExampleCredentials() {
        for ((permission, cycle) in listOf("1" to "D0", "0" to "D1")) {
            val text = openingMessage(defaultOpeningTemplate, "9001", fixtureMerchantKey, mapOf("url" to "fixture.example", "username" to "@fixture", "settle" to permission), fixtureBinding)
            assertTrue(text.contains("用户 ID: 9001")); assertTrue(text.contains("密钥: $fixtureMerchantKey")); assertTrue(text.contains("结算周期: $cycle"))
            assertTrue(text.contains(fixtureBinding)); assertTrue(text.contains("结算手续费1.6% 汇率+0.25")); assertFalse(text.contains("2806"))
        }
    }
    @Test fun merchantTextCannotRecursivelyExpandSecretsAndOversizedDraftsAreNotTruncated() {
        val text = openingMessage("{网站域名} {商户ID}", "9001", fixtureMerchantKey, mapOf("url" to "{密钥}", "settle" to "0"), fixtureBinding)
        assertEquals("{密钥} 9001", text)
        assertTrue(runCatching { openingMessage("x".repeat(4090) + "{密钥}", "9001", fixtureMerchantKey, mapOf("settle" to "0"), fixtureBinding) }.isFailure)
        assertTrue(runCatching { validateOpeningTemplate("{拼错了}") }.isFailure)
    }
}
