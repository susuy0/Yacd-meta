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

}
