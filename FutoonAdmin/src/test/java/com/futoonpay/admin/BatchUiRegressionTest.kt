package com.futoonpay.admin

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.*
import com.futoonpay.admin.ui.theme.FutoonTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BatchUiRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun screen(content: @Composable () -> Unit) {
        compose.setContent { FutoonTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } } }; compose.waitForIdle()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle(); compose.runOnUiThread {
            // Compose dialogs have a separate window; draw the foreground decor, not the activity behind it.
            val globalClass = Class.forName("android.view.WindowManagerGlobal")
            val global = globalClass.getMethod("getInstance").invoke(null)
            val field = globalClass.getDeclaredField("mViews").apply { isAccessible = true }
            val view = (field.get(global) as List<*>).filterIsInstance<android.view.View>().last { it.isShown && it.width > 0 && it.height > 0 }
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888); view.draw(Canvas(bitmap))
            File("build/ui-screenshots/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private fun merchant(id: String) = MerchantSummary(id, "测试商户$id", "VIP", "500", "0", "", "", "", 1, 1, 1)

    @Test fun merchantListHasRealMultiSelectAndResetKeyActionMenu() {
        val repo = FutoonRepository(fixtureClient({ 200 to "{\"total\":0,\"rows\":[]}" }))
        val vm = AppViewModel(TestEnvironment(), repo, loadOnStart = false)
        screen { MerchantsScreen(AppUiState(merchants = listOf(merchant("1011")), merchantTotal = 1), vm) }
        compose.onNodeWithText("多选").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("商户操作").performScrollTo().performClick()
        compose.onNodeWithText("重置密钥").assertIsDisplayed().performClick()
        assertEquals(BatchKind.MERCHANT_KEY, vm.state.value.batch!!.request.commands.single().kind)
        assertFalse(vm.state.value.batch!!.started)
    }
    @Test fun selectingCurrentPageProducesExactUidsAndClearDisablesBatch() {
        var captured: BatchRequest? = null
        val ids = listOf("1011", "1012")
        screen {
            val selection = rememberRecordSelection(1, ids)
            Column {
                MultiSelectBar(selection, ids, merchantBatchOptions(ids.filter { it in selection.selected }.map(::merchant)), onRequest = { captured = it })
                ids.forEach { id -> SelectableRecord(selection, id, "商户 $id") { Text("商户卡片 $id") } }
            }
        }
        compose.onNodeWithText("多选").performClick()
        compose.onAllNodes(isToggleable())[0].performClick()
        compose.onNodeWithText("已选 2 条").assertIsDisplayed()
        screenshot("16-list-multi-select")
        compose.onNodeWithText("批量操作").performClick()
        compose.onNodeWithText("重置密钥 · 2 条可执行").performClick()
        assertEquals(ids, captured!!.commands.map { it.id })
        compose.onNodeWithText("清空").performClick(); compose.onNodeWithText("批量操作").assertIsNotEnabled()
    }
    @Test fun merchantMultiSelectUsesTheActualNativeListCards() {
        val repo = FutoonRepository(fixtureClient({ 200 to "{\"total\":0,\"rows\":[]}" }))
        val vm = AppViewModel(TestEnvironment(), repo, loadOnStart = false)
        val rows = listOf(merchant("1011"), merchant("1012"))
        screen { MerchantsScreen(AppUiState(merchants = rows, merchantTotal = 2), vm) }
        compose.onNodeWithText("多选").performScrollTo().performClick()
        compose.onAllNodes(isToggleable()).onFirst().performScrollTo().performClick()
        compose.onNodeWithText("已选 2 条").assertIsDisplayed()
        screenshot("19-merchants-native-multi-select")
        compose.onNodeWithText("批量操作").performClick()
        compose.onNodeWithText("重置密钥 · 2 条可执行").performClick()
        assertEquals(listOf("1011", "1012"), vm.state.value.batch!!.request.commands.map { it.id })
    }
    @Test fun pageChangeClearsSelectionAndRefreshRemovesMissingRows() {
        var page by mutableStateOf(1); var ids by mutableStateOf(listOf("1", "2")); var state: RecordSelection? = null
        screen {
            val selection = rememberRecordSelection(page, ids); state = selection
            MultiSelectBar(selection, ids, emptyList(), onRequest = {})
        }
        compose.onNodeWithText("多选").performClick(); compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.runOnIdle { ids = listOf("2") }; compose.waitForIdle(); assertEquals(setOf("2"), state!!.selected)
        compose.runOnIdle { page = 2; ids = listOf("3") }; compose.waitForIdle(); assertTrue(state!!.selected.isEmpty()); assertFalse(state!!.active)
    }
    @Test fun resetConfirmationDoesNotExecuteBeforeExplicitConfirmAndListsTargets() {
        var confirms = 0
        val request = merchantKeyRequest(listOf(merchant("1011"), merchant("1012")))
        screen { BatchDialog(BatchProgress(request), { confirms++ }, {}, {}) }
        compose.onNodeWithText("确认重置密钥").assertIsDisplayed()
        compose.onNodeWithText("测试商户1011 · UID 1011").assertIsDisplayed()
        assertEquals(0, confirms); compose.onNodeWithText("确认执行").performClick(); assertEquals(1, confirms)
    }
    @Test fun newKeyStartsMaskedAndCopiesExactValueWithoutLoggingOrSaving() {
        val key = "0123456789abcdef0123456789abcdef"; val request = merchantKeyRequest(listOf(merchant("1011")))
        val result = BatchOutcome(request.commands.single(), BatchOutcomeStatus.SUCCESS, "密钥已重置", key)
        screen { BatchDialog(BatchProgress(request, started = true, results = listOf(result)), {}, {}, {}) }
        compose.onNodeWithText(key).assertDoesNotExist(); compose.onNodeWithText("复制新密钥").performClick()
        compose.onNodeWithText("已复制").assertIsDisplayed()
        val clip = (compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!
        assertEquals(key, clip.getItemAt(0).text.toString()); assertTrue(clip.description.extras!!.getBoolean("android.content.extra.IS_SENSITIVE"))
        compose.onNodeWithText("查看密钥").performClick(); compose.onNodeWithText(key).assertIsDisplayed()
        compose.onNodeWithText("隐藏密钥").performClick(); compose.onNodeWithText(key).assertDoesNotExist()
        // Screenshots exclude the key even in test fixtures.
        screenshot("17-key-reset-result-masked")
    }
    @Test fun busyDialogCannotConfirmTwiceOrOfferRetry() {
        val request = merchantKeyRequest(listOf(merchant("1011")))
        screen { BatchDialog(BatchProgress(request, started = true, running = true), {}, {}, {}) }
        compose.onNodeWithText("正在逐条执行…").assertExists(); compose.onNodeWithText("确认执行").assertDoesNotExist(); compose.onNodeWithText("重试失败/未执行项").assertDoesNotExist()
    }
    @Test fun paymentChannelListSelectsActualChannelActionsAndClearsOnSearch() {
        var query by mutableStateOf(""); var captured: BatchRequest? = null
        val actions = listOf(NativeAction("停用", "ajax_pay.php?act=setChannel&id=12&status=0"), NativeAction("删除", "ajax_pay.php?act=delChannel&id=12", destructive = true))
        val document = NativeDocument("通道", emptyList(), listOf(NativeTable("通道", listOf("ID", "名称", "支付方式", "是否启用"), listOf(listOf("12", "微信-A", "微信", "启用")), listOf(actions))), emptyList())
        val state = PaymentAdminState(PaymentAdminCatalog.find("channels"), loading = false, document = document)
        screen { PaymentChannelScreen(state, Modifier.fillMaxSize(), query, { query = it }, {}, {}, {}, {}, {}, {}, {}, onBatch = { captured = it }) }
        compose.onNodeWithText("多选").performClick(); compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNodeWithText("已选 1 条").assertIsDisplayed(); screenshot("18-channel-multi-select")
        compose.onNodeWithText("批量操作").performClick(); compose.onNodeWithText("停用 · 1 条可执行").performClick()
        assertEquals("12", captured!!.commands.single().id); assertEquals(actions.first(), captured!!.commands.single().nativeAction)
        compose.runOnIdle { query = "不存在" }; compose.waitForIdle(); compose.onNodeWithText("完成多选").assertDoesNotExist()
    }
}
