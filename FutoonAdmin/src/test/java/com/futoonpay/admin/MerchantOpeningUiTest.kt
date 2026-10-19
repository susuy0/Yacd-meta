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
import com.futoonpay.admin.ui.theme.FutoonTheme
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MerchantOpeningUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val chat = ChatScope("测试会话", "5824870757", key = "fixture-chat", dialog = 5824870757)
    private val creates = AtomicInteger()
    private val inserted = AtomicReference("")
    private val permission = AtomicReference("")
    private val repository = FutoonRepository(fixtureClient({ request -> when (request.url.queryParameter("act")) {
        "addUser" -> { creates.incrementAndGet(); permission.set(request.openingFields()["settle"]); 200 to openingSuccess }
        "bind" -> 200 to JSONObject().put("code", 0).put("bind", fixtureBinding).toString()
        "getcount" -> 200 to """{"count1":1}"""
        "userList", "orderList" -> 200 to """{"total":0,"rows":[]}"""
        else -> 200 to openingFixture()
    } }))
    private fun root(short: Boolean = false): MerchantOpeningViewModel {
        val model = MerchantOpeningViewModel(repository, chat, { ChatToolsConfig() }, { text, _ -> inserted.set(text) })
        compose.setContent { FutoonTheme { Box(Modifier.width(390.dp).height(if (short) 350.dp else 650.dp).testTag("opening-panel")) { PanelOverlayHost {
            LaunchedEffect(model) { model.load() }
            MerchantOpeningScreen(model, chat, {}, {})
        } } } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("邮箱 / 登录账号（选填）").fetchSemanticsNodes().isNotEmpty() }
        return model
    }
    private fun screenshot(name: String) {
        val bounds = compose.onNodeWithTag("opening-panel").fetchSemanticsNode().boundsInRoot
        compose.runOnUiThread {
            val view = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
            val bitmap = Bitmap.createBitmap(bounds.width.toInt(), bounds.height.toInt(), Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply { translate(-bounds.left, -bounds.top); view.draw(this) }
            File("build/ui-screenshots/$name.png").apply { parentFile.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    private fun remark() {
        compose.onNodeWithTag("merchant-opening-list").performScrollToNode(hasText("备注 / 结算账号姓名 *"))
        compose.onNodeWithText("备注 / 结算账号姓名 *").performTextInput("@fixture(业务)")
    }
    private fun awaitDraft(model: MerchantOpeningViewModel) {
        try {
            // A semantics observation advances Robolectric's paused main loop after IO resumes.
            // Waiting only on an AtomicReference leaves the continuation queued forever.
            compose.waitUntil(10000) {
                compose.onAllNodesWithText("文案已填入输入框，请校对后手动发送。").fetchSemanticsNodes().isNotEmpty() && inserted.get().isNotBlank()
            }
        } catch (error: Throwable) {
            println("Opening phase=${model.state.value.stage}, error=${model.state.value.error}, creates=${creates.get()}, settle=${permission.get()}")
            println(compose.onRoot(useUnmergedTree = true).printToString())
            screenshot("failed-opening-draft")
            throw error
        }
    }
    @Test fun d0SelectionControlsReviewPostAndAutomaticallyPreparedDraft() {
        val model = root()
        compose.onNodeWithText("下一步 · 核对开户").assertIsNotEnabled()
        remark()
        compose.onNodeWithTag("merchant-opening-list").performScrollToNode(hasText("D1 · 不开启结算"))
        compose.onNodeWithText("D1 · 不开启结算").assertIsDisplayed()
        compose.onNodeWithText("D0 · 开启结算").performScrollTo().performClick()
        compose.onNodeWithText("D0 · 开启结算").assertIsDisplayed()
        screenshot("29-merchant-opening-d0-form")
        compose.onNodeWithText("下一步 · 核对开户").performClick()
        compose.onNodeWithText("确认开户").assertIsDisplayed()
        compose.onNodeWithText("请核对本次开户信息").assertIsDisplayed()
        screenshot("30-merchant-opening-confirmation")
        assertEquals(0, creates.get()); assertEquals("", inserted.get())
        compose.onNodeWithText("确认开户").performClick()
        awaitDraft(model)
        assertEquals("1", permission.get()); assertEquals(1, creates.get()); assertTrue(inserted.get().contains("结算周期: D0"))
        compose.onNodeWithText("🏡 开户成功").assertExists()
        screenshot("31-merchant-opening-result")
    }
    @Test fun d1DefaultPostsSettlementDisabledAndDraftIsNotSent() {
        val model = root(); remark()
        compose.onNodeWithText("下一步 · 核对开户").performClick()
        compose.onNodeWithText("确认开户").performClick()
        awaitDraft(model)
        assertEquals("0", permission.get()); assertTrue(inserted.get().contains("结算周期: D1")); assertEquals(1, creates.get())
    }
    @Test fun compactKeyboardSizedFormKeepsReviewAndConfirmationButtonsVisible() {
        val model = root(short = true)
        compose.runOnIdle { model.edit("username", "@fixture") }
        compose.onNodeWithText("下一步 · 核对开户").assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithText("返回修改").assertIsDisplayed()
        compose.onNodeWithText("确认开户").assertIsDisplayed()
        screenshot("32-merchant-opening-compact-confirmation")
        assertEquals(0, creates.get())
    }
    @Test fun merchantTabProvidesAnEntryToTheNativeOpeningForm() {
        val navigator = PanelNavigator().apply { select("merchants") }
        val bridge = object : FixtureBridge() {
            override fun address() = repository.backendAddress
            override fun call(operation: String, params: String) = when(operation) {
                "chat.tools.read" -> ChatToolsConfig().json()
                else -> super.call(operation, params)
            }
        }
        val env = TestEnvironment(repository)
        compose.setContent { FutoonTheme { Box(Modifier.width(390.dp).height(650.dp)) { EmbeddedPanelRoot(env, bridge, navigator, chat, null, 0) } } }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("商户开户").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("商户开户").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("邮箱 / 登录账号（选填）").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("下一步 · 核对开户").assertIsDisplayed(); assertEquals(0, creates.get())
    }
}
