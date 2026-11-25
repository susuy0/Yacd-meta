package com.futoonpay.admin

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
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
class ConfigurationUiRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val channels = listOf(
        ConfigurationChannel("12", "蓝色微信", "2", "2.50", "10", "5000", "7"),
        ConfigurationChannel("13", "绿色微信", "2", "2.30", "10", "3000", "0"),
        ConfigurationChannel("15", "支付宝-A组", "1", "2.00", "10", "5000", "0"),
    )
    private fun screen(content: @Composable () -> Unit) {
        compose.setContent { FutoonTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/ui-screenshots/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun methodEditorShowsSeparateFeeAndEditableCompatibleChannels() {
        var form by mutableStateOf(NativeForm("编辑支付方式", "ajax_pay.php?act=savePayType", "POST", listOf(
            NativeField("id", "ID", "2", "hidden"), NativeField("name", "类型标识", "wxpay", "text", readOnly = true),
            NativeField("showname", "名称", "微信扫码", "text", required = true),
            NativeField("rate", "支付方式费率", "3.00", "number", required = true),
            NativeField("channels[]", "关联通道", "12,9", "multiselect", listOf(NativeOption("9", "旧通道"))),
        )))
        screen { PaymentEditorScreen(form, false, {}, { name, value -> form = form.copy(fields = form.fields.map { if (it.name == name) it.copy(value = value) else it }) }, { _, _ -> }, {}, remember { SnackbarHostState() }, emptyMap(), emptyMap(), {}, "payment_types", channels) }
        compose.onNodeWithText("向商户收取的费率 (%) *").assertExists()
        compose.onNodeWithText("关联通道 · 已选 2 个").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("通道已失效，取消勾选可移除").assertExists()
        compose.onNodeWithText("支付方式不匹配，不可选").assertExists()
        compose.onAllNodes(isToggleable())[1].performScrollTo().performClick()
        assertTrue("13" in selectedChannelIds(form.methodBindingField()!!.value))
        screenshot("12-method-editor")
        compose.onAllNodes(isToggleable())[2].assertIsNotEnabled()
    }
    @Test fun legacyMethodClearlyShowsUnsupportedServerFields() {
        val form = NativeForm("编辑支付方式", "ajax_pay.php?act=savePayType", "POST", listOf(NativeField("id", "ID", "2", "hidden"), NativeField("showname", "名称", "微信扫码", "text")))
        screen { PaymentEditorScreen(form, false, {}, { _, _ -> }, { _, _ -> }, {}, remember { SnackbarHostState() }, emptyMap(), emptyMap(), {}, "payment_types", channels) }
        compose.onNodeWithText("当前后台表单未提供独立支付方式费率，需后台支持后配置。").assertExists()
        compose.onNodeWithText("当前后台未提供多通道绑定字段；现有关联按通道的支付方式展示。").assertExists()
        screenshot("13-method-legacy-contract")
    }
    @Test fun proxyAssociationsOpenTheExactChannelAndPasswordsStayMasked() {
        var selected: NativeAction? = null
        val actions = listOf(NativeAction("编辑", "ajax_pay.php?act=getProxy&id=7"), NativeAction("删除", "ajax_pay.php?act=delProxy&id=7", destructive = true))
        val document = NativeDocument("中转代理", emptyList(), listOf(NativeTable("代理", listOf("ID", "名称", "地址", "密码"), listOf(listOf("7", "香港代理", "https://relay.example:8443", "SECRET-PASSWORD")), listOf(actions))), emptyList())
        val state = PaymentAdminState(PaymentAdminCatalog.find("proxies"), loading = false, document = document, configurationChannels = channels, configurationLoaded = true)
        screen { ConfigurationListScreen(state, Modifier.fillMaxSize(), "", {}, { selected = it }, {}, {}) }
        compose.onNodeWithText("蓝色微信 #12 →").assertIsDisplayed().performClick()
        assertEquals("futoon://channel-detail?id=12", selected!!.endpoint)
        compose.onNodeWithText("SECRET-PASSWORD").assertDoesNotExist()
        compose.onNodeWithText("••••••••").assertExists()
        assertTrue(configurationActionAssociations(state, actions.last()).contains("蓝色微信 #12"))
        assertFalse(configurationActionAssociations(state, actions.last()).contains("绿色微信"))
        screenshot("14-proxy-associations")
    }
    @Test fun merchantEditorDisablesBalanceAndValidatesRequiredContactBeforeSaving() {
        var state by mutableStateOf(AppUiState(merchantEditorUid = "1011", merchantEditor = merchantProfileForm(profileFixture(), "1011")))
        var saves = 0
        screen { MerchantProfileScreen(state, {}, { name, value -> state = state.copy(merchantEditor = state.merchantEditor!!.copy(fields = state.merchantEditor!!.fields.map { if (it.name == name) it.copy(value = value) else it })) }, { saves++ }, {}) }
        compose.onNodeWithText("邮箱 *").performScrollTo().performTextReplacement("")
        compose.onNodeWithText("保存资料").assertIsNotEnabled()
        compose.onNodeWithText("邮箱 *").performTextReplacement("new@example.com")
        compose.onNodeWithText("保存资料").assertIsEnabled()
        screenshot("15-merchant-profile")
        compose.onNodeWithText("保存资料").performClick()
        assertEquals(0, saves)
        compose.onNodeWithText("确认保存").performClick()
        assertEquals(1, saves)
        assertTrue(state.merchantEditor!!.fields.first { it.name == "money" }.readOnly)
    }
}
