package com.futoonpay.admin

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.*
import com.futoonpay.admin.ui.theme.FutoonTheme
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkflowUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val requests = CopyOnWriteArrayList<okhttp3.Request>()
    private val bridge = WorkflowFixtureBridge { op, _ -> if (op == "recognize") """[{"kind":"SYSTEM_ORDER","value":"S1000001"}]""" else null }
    private val navigator = PanelNavigator()
    private var revision by mutableIntStateOf(0)
    private fun environment() = TestEnvironment(FutoonRepository(fixtureClient({ request ->
        requests += request
        when {
            request.url.encodedPath.endsWith("uset.php") -> 200 to profileFixture()
            request.url.queryParameter("act") == "editUser" -> 200 to """{"code":0,"msg":"保存成功"}"""
            request.url.queryParameter("act") == "getcount" -> 200 to """{"count1":1}"""
            request.url.queryParameter("act") == "order" -> 200 to JSONObject().put("code", 0).put("data", JSONObject(WorkflowFixtureBridge.order).put("api_trade_no", "C3000003")).toString()
            request.url.queryParameter("act") == "orderList" -> 200 to """{"total":1,"rows":[${WorkflowFixtureBridge.order}]}"""
            request.url.queryParameter("act") == "userList" -> 200 to """{"total":1,"rows":[{"uid":"1011","username":"测试商户","money":"20.00","pay":1,"status":1,"settle":1}]}"""
            else -> 200 to """{"total":0,"rows":[]}"""
        }
    })))
    private fun root(query: RecognizedQuery? = null) {
        val env = environment()
        if (query != null) navigator.showRecognition()
        compose.setContent { FutoonTheme { Box(Modifier.width(390.dp).height(650.dp).testTag("workflow-panel")) {
            EmbeddedPanelRoot(env, bridge, navigator, ChatScope("工单", "5824870757", "来自话题末尾 TGID", "-100:12627", -100, 12627, "12627", "游客"), query, revision)
        } } }
        compose.waitUntil(10000) { compose.onAllNodes(hasText("识别") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun screenshot(name: String) {
        val bounds = compose.onNodeWithTag("workflow-panel").fetchSemanticsNode().boundsInRoot
        compose.runOnUiThread {
            val view = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
            val bitmap = Bitmap.createBitmap(bounds.width.toInt(), bounds.height.toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap); canvas.translate(-bounds.left, -bounds.top); view.draw(canvas)
            File("build/ui-screenshots/$name.png").apply { parentFile.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    @Test fun allFiveTabsRemainVisibleAndRecognizedMerchantUsesMoreWithoutEditing() {
        root()
        listOf("识别", "订单", "商户", "结算", "更多").forEach { compose.onNode(hasText(it) and hasClickAction()).assertIsDisplayed() }
        compose.onNode(hasText("识别") and hasClickAction()).performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("当前商户更多操作").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("截图 OCR / 二维码").assertIsDisplayed()
        compose.onNodeWithText("编辑商户").assertDoesNotExist()
        compose.onNodeWithContentDescription("当前商户更多操作").performClick()
        compose.onNodeWithText("编辑商户").assertDoesNotExist()
        compose.onNodeWithText("测试商户 · PID 1011").assertExists()
        for (label in listOf("增加 CNY 余额", "扣除 CNY 余额", "修改用户组", "重置密钥", "商户订单", "商户结算", "域名审核")) compose.onNodeWithText(label).assertExists()
        compose.onNodeWithText("修改用户组").performScrollTo().performClick()
        compose.onNodeWithText("商户 UID 1011").assertIsDisplayed()
        compose.onNodeWithText("用户组 GID").performTextInput("3")
        compose.onNodeWithText("确认").assertIsEnabled()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("商户 UID 1011").assertDoesNotExist()
        assertEquals(0, requests.count { it.url.queryParameter("act") == "editUser" })
        assertFalse(bridge.calls.any { it.first in listOf("merchant.profile", "form.save") })
        screenshot("23-recognition-merchant-more")
    }
    @Test fun merchantTabHasBothCurrentConversationAndOriginalFullList() {
        root()
        compose.onNode(hasText("商户") and hasClickAction()).performClick()
        compose.onNodeWithText("当前会话").assertIsDisplayed()
        compose.onNodeWithText("全部商户").assertIsDisplayed().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("UID 1011", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("完整搜索").assertExists()
        compose.onNodeWithText("多选").assertExists()
        screenshot("24-full-original-merchants-tab")
    }
    @Test fun recognizedOrderOpensReceiptSelectionWithEditableInfoAndManualPreview() {
        root(RecognizedQuery("系统订单号 S1000001", "AUTO", source = MessageSource(-100, 12627, 19)))
        compose.waitUntil(5000) { compose.onAllNodesWithText("查询所选").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("查询所选").assertIsDisplayed().performClick()
        compose.waitUntil(5000) { bridge.calls.any { it.first == "orders.search" } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("停止未执行项").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("recognition-list").performScrollToNode(hasText("查看订单与选择付款凭证 ›"))
        compose.onNodeWithText("查看订单与选择付款凭证 ›").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("订单详情").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("选择付款凭证"))
        compose.onNodeWithText("选择付款凭证").performClick()
        compose.onNodeWithText("从聊天选图").assertIsDisplayed()
        compose.onNodeWithText("相册 / 文件").assertIsDisplayed()
        assertTrue(bridge.selectedCaption.contains("S1000001")); assertTrue(bridge.selectedCaption.contains("1011"))
        assertTrue(bridge.selectedCaption.contains("C3000003"))
        assertEquals(0, bridge.sent.get())
        compose.onNodeWithText("选择收件人并预览").assertIsNotEnabled()
        compose.onNodeWithTag("receipt-list").performScrollToNode(hasText("补单协助"))
        compose.onNodeWithText("补单协助").performClick()
        compose.onNodeWithTag("receipt-list").performScrollToNode(hasText("从聊天选图"))
        compose.onNodeWithText("从聊天选图").performClick()
        assertEquals(1, bridge.pickedChat)
        compose.runOnUiThread { bridge.files = arrayOf("/fixture/payment.jpg"); revision++ }
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("凭证 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("选择收件人并预览").assertIsEnabled()
        assertEquals(0, bridge.sent.get())
        compose.onNodeWithText("选择收件人并预览").performClick()
        assertEquals(1, bridge.sent.get())
        assertTrue(bridge.selectedCaption.startsWith("请协助补单"))
        assertTrue(requests.none { it.url.queryParameter("act") == "fillorder" || it.url.queryParameter("act") == "setStatus" })
        assertTrue(bridge.calls.none { it.first == "order.action" })
        screenshot("25-receipt-supplement-manual-preview")
    }
    @Test fun shortRecognitionPanelKeepsQueryActionsOnScreen() {
        val vm = PanelWorkflows(bridge)
        val actions = CurrentMerchantActions(AppViewModel(TestEnvironment(), loadOnStart = false))
        compose.setContent { FutoonTheme { PanelOverlayHost { Box(Modifier.width(390.dp).height(350.dp)) { RecognitionScreen(vm, actions, ChatScope(), navigator, bridge) } } } }
        compose.onNodeWithText("提取编号").assertIsDisplayed()
        compose.onNodeWithText("查询所选").assertIsDisplayed()
    }
}
