package com.futoonpay.admin

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.*
import com.futoonpay.admin.ui.theme.FutoonTheme
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OfficialBackendUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun model(handler: (Request) -> String): AppViewModel {
        val transport = OkHttpClient.Builder().addInterceptor { chain -> Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
            .body(handler(chain.request()).toResponseBody("application/json".toMediaType())).build() }.build()
        val repo = FutoonRepository(FutoonApiClient(TestCookies(), "https://fixture.example/svc_aabbcc/".toHttpUrl(), transport = transport))
        return AppViewModel(TestEnvironment(repo), loadOnStart = false)
    }
    private fun row(status: Int = 7) = SettlementSummary("42", "18", "Fixture", "5", "2", "fixture-address", "335.30", "47.56", "", "", "", "", status, true, false, "")
    private fun screenshot(name: String) = compose.runOnUiThread {
        val view = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap)); File("build/ui-screenshots/$name.png").also { it.parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun officialMenuOpensWeixinAndDomainViaTheirCorrectNativeRoutes() {
        var module = ""; var domains = 0
        compose.setContent { FutoonTheme { Box(Modifier.width(390.dp).height(650.dp)) { ModernManage({ module = it }, { domains++ }, {}) } } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("公众号小程序"))
        compose.onNodeWithText("公众号小程序").performClick(); assertEquals("weixin_apps", module)
        screenshot("40-official-management-menu")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("授权域名"))
        compose.onNodeWithText("授权域名").performClick(); assertEquals(1, domains)
    }
    @Test fun processingSettlementNeverOffersManualStatusWritesAndShowsActualUsdt() {
        val writes = AtomicInteger(); val vm = model { if (it.method != "GET") writes.incrementAndGet(); "{}" }
        val state = AppUiState(settlements = listOf(row(2)), settlementTotal = 1, settlementsLoading = false)
        compose.setContent { FutoonTheme { Box(Modifier.width(390.dp).height(650.dp)) { PanelOverlayHost { SettlementsScreen(state, vm) } } } }
        compose.onNodeWithText("实际到账 47.56 USDT").assertExists()
        compose.onNodeWithText("手续费", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("结算操作").performClick()
        compose.onNodeWithText("标记已完成").assertDoesNotExist(); compose.onNodeWithText("作废结算").assertDoesNotExist()
        compose.onNodeWithText("设为正在结算").assertDoesNotExist()
        compose.onNodeWithText("原因与处理建议").assertExists(); assertEquals(0, writes.get())
        screenshot("41-processing-settlement-actions")
    }
    @Test fun reviewDetailsReadOnlyUntilAnExplicitReviewIntentAndNeverDisplayToken() {
        val writes = AtomicInteger(); val token = "a".repeat(64); var action = ""; var payload = emptyMap<String, String>()
        val vm = model { request -> if (request.method != "GET") writes.incrementAndGet(); """{"code":0,"detail":{"status_label":"待审核","reason":"测试核验原因","amount":"47.56","asset":"USDT","approval_token":"$token","full_approval":{"allowed":true}}}""" }
        compose.setContent { FutoonTheme { Box(Modifier.width(390.dp).height(650.dp)) { OfficialSettlementDetails(row(), vm) { _, requested, fields, _ -> action = requested; payload = fields } } } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("审核通过并入队").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, writes.get()); compose.onNodeWithText(token).assertDoesNotExist()
        compose.onNodeWithText("审核通过并入队").performClick()
        assertEquals("approve_full_settlement", action); assertEquals(token, payload["token"]); assertEquals(0, writes.get())
        screenshot("42-official-settlement-reason")
    }
    @Test fun recipientFormLoadsServerValuesAndCancelNeverSubmitsAChange() {
        val writes = AtomicInteger()
        val html = """<select id="pay_type" name="pay_type"><option value="2">微信</option><option value="5">USDT-Polygon</option></select><input id="pay_account" value="fixture-address"><input id="pay_name" value="Fixture recipient">"""
        val vm = model { request ->
            if (request.method != "GET") writes.incrementAndGet()
            org.json.JSONObject().put("code",0).put("pay_type",5).put("data",html).toString()
        }
        val state = AppUiState(settlements = listOf(row()), settlementTotal = 1, settlementsLoading = false)
        compose.setContent { FutoonTheme { Box(Modifier.width(390.dp).height(650.dp)) { PanelOverlayHost { SettlementsScreen(state, vm) } } } }
        compose.onNodeWithContentDescription("结算操作").performClick()
        compose.onNodeWithText("修改收款资料").performClick()
        compose.waitUntil(5000) { compose.onAllNodes(hasSetTextAction() and hasText("Fixture recipient")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("USDT-Polygon").assertExists()
        compose.onNodeWithText("支付类型/链").assertDoesNotExist()
        assertEquals(0, writes.get()); compose.onNodeWithText("取消").performClick(); assertEquals(0, writes.get())
    }

    @Test fun settlementReasonIsVisibleOnOpeningWithoutScrollingPastInternalFields() {
        val vm = model { """{"code":0,"detail":{"status_label":"待审核","reason":"请核验本笔收款地址","amount":"47.56","asset":"USDT"}}""" }
        val state = AppUiState(settlements = listOf(row().copy(result = "risk_review", transferDate = "0000-00-00 00:00:00")), settlementTotal = 1, settlementsLoading = false)
        compose.setContent { FutoonTheme { Box(Modifier.width(390.dp).height(650.dp)) { PanelOverlayHost { SettlementsScreen(state, vm) } } } }
        compose.onNodeWithContentDescription("结算操作").performClick()
        compose.onNodeWithText("原因与处理建议").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("请核验本笔收款地址").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("请核验本笔收款地址").assertIsDisplayed()
        compose.onNodeWithText("risk_review", substring = true).assertDoesNotExist()
        screenshot("43-settlement-reason-first")
    }
    @Test fun categoryReviewRequiresNewHumanEvidenceAndCannotUseLegacyCheckedShortcut() {
        val writes = AtomicInteger(); val token = "a".repeat(64); val fingerprint = "b".repeat(64)
        val vm = model { r -> if (r.method != "GET") writes.incrementAndGet()
            """{"code":0,"detail":{"status_label":"待审核","reason":"地址需核实","approval_token":"$token","reviews":[{"label":"地址审核","category":"address","state":"pending","reason":"核实地址","can_approve":true,"fingerprint":"$fingerprint"}]}}"""
        }
        val state = AppUiState(settlements = listOf(row(3).copy(dispatchState = "held", result = "risk_review")), settlementTotal = 1, settlementsLoading = false)
        compose.setContent { FutoonTheme { Box(Modifier.width(390.dp).height(650.dp)) { PanelOverlayHost { SettlementsScreen(state, vm) } } } }
        compose.onNodeWithContentDescription("结算操作").performClick(); compose.onNodeWithText("原因与处理建议").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("通过地址审核").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("通过地址审核").performScrollTo().performClick()
        compose.onNodeWithText("核实依据（最多 500 字）").assertExists()
        compose.onNodeWithText("risk_review", substring = true).assertDoesNotExist()
        compose.onNodeWithText("检查完成 · 填写 checked").assertDoesNotExist()
        compose.onNodeWithText("确认").assertIsNotEnabled()
        compose.onNodeWithText("取消").performClick(); assertEquals(0, writes.get())
        screenshot("44-review-evidence-empty")
    }
    @Test fun managementSearchPreservesRoutesAndRecordDetailsRemainReachable() {
        var module = ""
        compose.setContent { FutoonTheme { Box(Modifier.width(390.dp).height(650.dp)) { ModernManage({ module = it }, {}, {}) } } }
        compose.onNodeWithText("查找管理功能").performTextInput("公众号")
        compose.onNodeWithText("支付通道").assertDoesNotExist()
        compose.onNodeWithText("公众号小程序").assertIsDisplayed().performClick()
        assertEquals("weixin_apps", module)
        screenshot("45-management-search")
    }
    @Test fun statisticsQuerySendsSelectedDayAndMethodAndUsesServerColumnLabels() {
        val requests = mutableListOf<Map<String, String>>()
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request(); val body = req.body as FormBody
            requests += (0 until body.size).associate { body.name(it) to body.value(it) }
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body("""{"code":0,"columns":{"uid":"商户号","channel_900":"示例新通道","total":"总计"},"data":[{"uid":"18","channel_900":125.50,"total":125.50}]}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val repo = FutoonRepository(FutoonApiClient(TestCookies(), "https://fixture.example/svc_aabbcc/".toHttpUrl(), transport = transport))
        val vm = NativeModuleViewModel(repo, ModuleCatalog.find("merchant_stats"))
        compose.setContent { FutoonTheme { Box(Modifier.width(390.dp).height(650.dp)) { NativeModuleRoot(vm, {}) } } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("示例新通道").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("敏感字段不会从网页回填；留空表示不修改").assertDoesNotExist()
        compose.onNodeWithText("查询日期").performTextReplacement("2026-10-29")
        compose.onNodeWithText("按支付通道").performClick(); compose.onNodeWithText("查询", useUnmergedTree = true).performClick()
        compose.waitUntil(5000) { requests.size >= 2 && compose.onAllNodesWithText("2026-10-29 · 1 个商户").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(mapOf("day" to "2026-10-29", "method" to "channel"), requests.last())
        screenshot("46-native-merchant-statistics")
    }

    @Test fun longRecordsExposeSummaryFirstAndNeverLoseTheirFullValues() {
        compose.setContent { FutoonTheme { NativeTableRow(listOf("ID", "名称", "状态", "到账地址"), listOf("8", "示例用户组", "启用", "fixture-complete-address")) } }
        compose.onNodeWithText("fixture-complete-address").assertDoesNotExist()
        compose.onNodeWithText("展开详情").performClick()
        compose.onNodeWithText("fixture-complete-address").assertIsDisplayed()
        compose.onNodeWithText("收起详情").performClick()
        compose.onNodeWithText("fixture-complete-address").assertDoesNotExist()
    }
    @Test fun permissionFailureStaysVisibleAndNeverPretendsThePageHasNoData() {
        val transport = OkHttpClient.Builder().addInterceptor { chain -> Response.Builder().request(chain.request())
            .protocol(Protocol.HTTP_1_1).code(403).message("Forbidden").body("denied".toResponseBody()).build() }.build()
        val repo = FutoonRepository(FutoonApiClient(TestCookies(), "https://fixture.example/svc_aabbcc/".toHttpUrl(), transport = transport))
        val vm = NativeModuleViewModel(repo, ModuleCatalog.find("account_settings"))
        compose.setContent { FutoonTheme { Box(Modifier.width(390.dp).height(650.dp)) { NativeModuleRoot(vm, {}) } } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("没有查看或操作权限，请联系管理员开通").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("没有查看或操作权限，请联系管理员开通").assertIsDisplayed()
        compose.onNodeWithText("此页面暂时没有可显示的数据或表单").assertDoesNotExist()
        compose.onNodeWithText("重新加载").assertIsDisplayed()
    }

    @Test fun inactiveSelectionCannotFreezeAnAlreadyFrozenMerchantOrSubmitWithoutConfirmation() {
        var frozen = emptySet<String>(); var scanned: InactiveMerchantFilters? = null
        val state = NativeModuleState(ModuleCatalog.find("inactive"), loading = false, inactiveScan = InactiveMerchantScan(listOf(
            InactiveMerchant("18", "Fixture normal", "2026-01-01", "", "0", 1, "正常", "否"),
            InactiveMerchant("19", "Fixture frozen", "2026-01-01", "", "0", 0, "已冻结", "否")), 2, "2026-10-26"))
        compose.setContent { FutoonTheme { Box(Modifier.width(390.dp).height(650.dp)) { PanelOverlayHost { InactiveMerchantsScreen(state, { scanned = it }, { frozen = it }) } } } }
        compose.onNodeWithText("扫描商户").performClick(); assertEquals(InactiveMerchantFilters(), scanned)
        compose.onNodeWithContentDescription("选择商户 #18").performClick()
        assertTrue(frozen.isEmpty())
        compose.onNodeWithContentDescription("选择商户 #19").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("冻结所选 1 个商户").performClick()
        assertTrue(frozen.isEmpty()); compose.onNodeWithText("#18 · Fixture normal").assertExists()
        compose.onNodeWithText("取消").performClick(); assertTrue(frozen.isEmpty())
        compose.onNodeWithText("冻结所选 1 个商户").performClick()
        compose.onNodeWithText("确认冻结").performClick(); assertEquals(setOf("18"), frozen)
    }

    @Test fun refreshingInactiveScanClearsSelectionEvenWhenTheReturnedRowsAreIdentical() {
        val scans = AtomicInteger()
        val transport = OkHttpClient.Builder().addInterceptor { chain -> scans.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body("""{"code":0,"total":1,"window_start":"2026-10-26","rows":[{"uid":"18","merchant_name":"Fixture","status":1,"recent_order_count":0}]}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val repo = FutoonRepository(FutoonApiClient(TestCookies(), "https://fixture.example/svc_aabbcc/".toHttpUrl(), transport = transport))
        val vm = NativeModuleViewModel(repo, ModuleCatalog.find("inactive"))
        compose.setContent { FutoonTheme { Box(Modifier.width(390.dp).height(650.dp)) { PanelOverlayHost { NativeModuleRoot(vm, {}) } } } }
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("选择商户 #18").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("选择商户 #18").performClick()
        compose.onNodeWithText("冻结所选 1 个商户").assertExists()
        compose.onNodeWithText("扫描商户").performClick()
        compose.waitUntil(5000) { scans.get() >= 2 && compose.onAllNodesWithText("冻结所选 1 个商户").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("冻结所选 1 个商户").assertDoesNotExist()
    }

}
