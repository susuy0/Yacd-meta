package com.futoonpay.admin

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.Lifecycle
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.theme.FutoonTheme
import com.futoonpay.admin.ui.AppViewModel
import com.futoonpay.admin.ui.AppUiState
import com.futoonpay.admin.ui.OrdersScreen
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EmbeddedPanelTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val bridge = FixtureBridge()
    private val navigator = PanelNavigator()
    private fun screen(content: @Composable () -> Unit) { compose.setContent { FutoonTheme { Box(Modifier.width(390.dp).height(650.dp).testTag("native-floating-panel")) { content() } } }; compose.waitForIdle() }
    private fun screenshot(name: String) {
        val bounds = compose.onNodeWithTag("native-floating-panel").fetchSemanticsNode().boundsInRoot
        compose.runOnUiThread {
            val parent = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            val view = parent.getChildAt(0)
            val bitmap = android.graphics.Bitmap.createBitmap(bounds.width.toInt(), bounds.height.toInt(), android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.translate(-bounds.left, -bounds.top)
            view.draw(canvas)
            java.io.File("build/ui-screenshots/$name.png").apply { parentFile.mkdirs() }.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private fun environment() = TestEnvironment(FutoonRepository(fixtureClient({ request ->
        when (request.url.queryParameter("act")) {
            "getcount" -> 200 to """{"count1":1}"""
            "orderList" -> 200 to """{"total":1,"rows":[{"trade_no":"S2610180001","uid":"1011","money":"10.00","realmoney":"10.00","status":1,"channel":"12","addtime":"2026-10-18 14:30:00","name":"测试订单"}]}"""
            "settleList" -> 200 to """{"total":1,"rows":[{"id":"42","uid":"1011","money":"88.00","status":0,"type":"支付宝","account":"fixture"}]}"""
            else -> 200 to """{"total":0,"rows":[]}"""
        }
    })))
    @Test fun originalOrderAndSettlementListsRemainInsideOnePanel() {
        val env = environment()
        screen { EmbeddedPanelRoot(env, bridge, navigator, ChatScope("私聊", "5824870757"), null, 0) }
        compose.waitUntil(10000) { compose.onAllNodesWithText("S2610180001").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("S2610180001").assertIsDisplayed()
        screenshot("20-embedded-orders-default")
        compose.onNode(hasText("结算") and hasClickAction()).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("88.00", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("settlements", navigator.current.kind)
        screenshot("21-embedded-settlements")
        assertEquals(0, bridge.sent.get())
        compose.onNode(hasText("更多") and hasClickAction()).performClick()
        compose.onNodeWithText("支付通道").assertExists()
        compose.onNodeWithText("轮询组").assertExists()
        screenshot("22-embedded-more")
        assertEquals(0, bridge.closed.get())
    }
    @Test fun viewOwnsLifecycleAndCanPauseReopenAndDestroy() {
        var panel: NativePanelView? = null
        screen { AndroidView(factory = { ctx -> NativePanelView(ctx, bridge).also { panel = it; it.onShown() } }, modifier = Modifier.fillMaxSize()) }
        compose.onNodeWithText("官网域名或基础地址").assertExists()
        compose.runOnUiThread {
            assertEquals(Lifecycle.State.RESUMED, panel!!.lifecycle.currentState)
            panel!!.onHidden(); assertEquals(Lifecycle.State.CREATED, panel!!.lifecycle.currentState)
            panel!!.onShown(); assertEquals(Lifecycle.State.RESUMED, panel!!.lifecycle.currentState)
            panel!!.close(); assertEquals(Lifecycle.State.DESTROYED, panel!!.lifecycle.currentState)
        }
    }
    @Test fun navigationReturnsToPreviousModuleAndKeepsEachMainTabStack() {
        navigator.module("rolls")
        val route = navigator.current
        navigator.module("channels", channelId = "12")
        assertEquals("12", navigator.current.channelId)
        navigator.select("settlements"); assertEquals("settlements", navigator.current.kind)
        navigator.select("orders"); assertEquals("12", navigator.current.channelId)
        assertTrue(navigator.pop()); assertEquals(route, navigator.current)
        assertTrue(navigator.pop()); assertEquals("orders", navigator.current.kind)
        assertFalse(navigator.pop())
    }
    @Test fun pluginConfirmationStaysVisibleAtKeyboardSizedPanelHeight() {
        compose.setContent { FutoonTheme { Box(Modifier.width(390.dp).height(350.dp)) { PanelOverlayHost {
            androidx.compose.foundation.layout.Column {
                NativeFieldEditor(NativeField("plugin", "支付插件", "", "select", (1..20).map { NativeOption("plugin$it", "插件 $it") }), "") {}
            }
        } } } }
        compose.onNodeWithText("请选择").performClick()
        compose.onNodeWithText("插件 1").performClick()
        compose.onNodeWithText("选择 插件 1").assertIsDisplayed()
    }
    @Test fun fullOrderSearchKeepsApplyActionVisibleInShortPanel() {
        val model = AppViewModel(TestEnvironment(), loadOnStart = false)
        compose.setContent { FutoonTheme { Box(Modifier.width(390.dp).height(350.dp)) { PanelOverlayHost {
            OrdersScreen(AppUiState(), model)
        } } } }
        compose.onNodeWithText("完整搜索").performClick()
        compose.onNodeWithText("应用筛选").assertIsDisplayed()
    }
}

internal open class FixtureBridge : NativePanelBridge {
    val sent = AtomicInteger()
    val closed = AtomicInteger()
    override fun address() = ""
    override fun username() = "fixture"
    override fun accountScope() = "fixture-account"
    override fun hasSession() = false
    override fun session(): PanelSession = object : PanelSession {
        override fun isActive() = true
        override fun address() = ""
        override fun loadCookies() = "[]"
        override fun saveCookies(json: String) {}
        override fun clearCookies() {}
    }
    override fun configure(address: String, username: String) { error("No live backend permitted in UI fixtures") }
    override fun call(operation: String, params: String): String = when(operation) {
        "local.read", "local.write", "delivery.read" -> "{}"
        "merchant.lookup" -> "{\"rows\":[]}"
        "recognize" -> "[]"
        else -> error("Unexpected bridge operation: $operation")
    }
    override fun collapse() { closed.incrementAndGet() }
    override fun copy(text: String) {}
    override fun selectEvidence(order: String, caption: String) {}
    override fun pickEvidenceFromChat() {}
    override fun openGallery() {}
    override fun evidencePaths() = emptyArray<String>()
    override fun evidencePreview(path: String): android.graphics.Bitmap? = null
    override fun editEvidence(index: Int) {}
    override fun removeEvidence(index: Int) {}
    override fun sendEvidence(order: String, caption: String) { sent.incrementAndGet() }
}
