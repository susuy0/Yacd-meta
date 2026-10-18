package com.futoonpay.admin

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.*
import com.futoonpay.admin.ui.theme.FutoonTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File

/** Real Compose screens rendered with isolated fixture data; no live financial actions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeUiRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun model() = AppViewModel(FutoonApplication())
    private fun order(id: String, status: Int) = OrderSummary(id, "M-77310-0042", "41882", "example.com", "年度订阅", "500.00", "500.00", "487.50", "微信扫码", "12", "wxpay_v3", "2026-10-08 14:15:02", "2026-10-08 14:15:41", status, 0)
    private val orders = listOf(order("S2610081430158812", 1), order("S2610081428074401", 0))
    private val stat = OrderChannelStat("12", "蓝色微信", 92.0, total = 50, successful = 46, processing = 3, source = ChannelRateSource.OFFICIAL, countsOfficial = true)
    private fun screen(content: @Composable () -> Unit) {
        compose.setContent { FutoonTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        compose.runOnUiThread {
            val dialog = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }
            val view = dialog?.window?.decorView ?: compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val output = File("build/ui-screenshots/$name.png").apply { parentFile?.mkdirs() }
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun loginHasSeparateOriginAndDirectory() {
        screen { ModernLogin(AppUiState(authStage = AuthStage.LOGIN), {}, { _, _, _ -> }, {}) }
        compose.onNodeWithText("官网域名或基础地址").assertIsDisplayed()
        compose.onNodeWithText("后台路径").assertIsDisplayed()
        compose.onNodeWithText("登录").assertIsNotEnabled()
        screenshot("01-login")
    }
    @Test fun orderSummaryAndChannelRateAreVisibleAndRateOpens() {
        val state = AppUiState(orders = orders, orderTotal = 1284, orderChannelStats = mapOf("12" to stat))
        val vm = model()
        screen { Column { PayTopBar("订单"); OrdersScreen(state, vm) } }
        compose.onNodeWithText("当前页 · 第 1 页 (2 条)").assertIsDisplayed()
        compose.onNodeWithText("订单金额").assertIsDisplayed()
        compose.onNodeWithText("¥1000.00").assertIsDisplayed()
        compose.onAllNodesWithText("92.0%")[0].assertIsDisplayed()
        screenshot("02-orders")
        compose.onAllNodesWithText("92.0%")[0].performClick()
        compose.onNodeWithText("近5分钟 · 全通道统计").assertExists()
        compose.onNodeWithText("全通道订单数量").assertExists()
        screenshot("03-order-channel-rate")
    }
    @Test fun fullOrderSearchOpensAndScrollsToApplyButton() {
        val vm = model()
        screen { OrdersScreen(AppUiState(orders = orders), vm) }
        compose.onNodeWithText("完整搜索").performClick()
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("开始日期 YYYY-MM-DD"))
        compose.onNodeWithText("开始日期 YYYY-MM-DD").assertIsDisplayed()
        compose.onNodeWithText("应用筛选").assertIsDisplayed()
        screenshot("04-order-search")
    }
    @Test fun monitoringSwitchesWindowsWithoutInventingMissingData() {
        val document = NativeDocument("监测", emptyList(), listOf(NativeTable("监测", listOf("ID", "名称", "状态", "5 分钟成功率", "total_count_5min", "success_count_5min", "processing_count_5min", "监测结果", "最近检测"),
            listOf(listOf("12", "蓝色微信", "1", "92%", "50", "46", "3", "通过", "14:20:12")))), emptyList())
        val state = PaymentAdminState(PaymentAdminCatalog.find("channel_monitor"), loading = false, document = document)
        screen { Column { PayTopBar("通道监测"); ChannelMonitorScreen(state, Modifier, "", {}, { _, _ -> }, {}) } }
        compose.onNodeWithText("92%").assertIsDisplayed()
        compose.onNodeWithText("成功 46").assertIsDisplayed()
        compose.onNodeWithText("健康").assertDoesNotExist()
        screenshot("05-monitor")
        compose.onNodeWithText("6小时").performClick()
        compose.onNodeWithText("暂无统计").assertIsDisplayed()
        compose.onNodeWithText("成功 —").assertIsDisplayed()
    }
    @Test fun backendChangeHasFixedSaveActionAndEditablePath() {
        screen { SettingsScreen(AppUiState(adminUrl = "https://pay.example.com/private-admin/", accountName = "admin"), {}, {}, {}) }
        compose.onNodeWithText("更换后台地址 ›").performClick()
        compose.onNodeWithText("保存并重新登录").assertIsDisplayed()
        compose.onNodeWithText("/private-admin").assertIsDisplayed()
        screenshot("06-settings-address")
    }
    @Test fun pluginIsAChoiceAndSelectionUpdatesField() {
        screen {
            var value by remember { mutableStateOf("") }
            Column(Modifier.padding(16.dp)) {
                NativeFieldEditor(NativeField("plugin", "支付插件", value, "select", listOf(NativeOption("wx_v3", "微信 V3"), NativeOption("alipay", "支付宝"))), value) { value = it }
                Text("已选择：$value")
            }
        }
        compose.onNodeWithText("请选择").performClick()
        compose.onNodeWithText("微信 V3").performClick()
        compose.onNodeWithText("选择 微信 V3").performClick()
        compose.onNodeWithText("已选择：wx_v3").assertIsDisplayed()
    }
    @Test fun overviewAndManageUseNativeCards() {
        screen { ModernDashboard(AppUiState(dashboard = DashboardStats(orderCount = "1284", merchantCount = "82", todayTotal = "386420.50", yesterdaySettled = "52000.00", todayByType = listOf("微信" to "120000.00", "支付宝" to "266420.50"))), {}, {}, {}, {}) }
        compose.onNodeWithText("今日交易金额").assertIsDisplayed()
        screenshot("07-overview")
    }
    @Test fun failedSettlementCheckedPresetRemainsEditable() {
        val item = SettlementSummary("2197", "41882", "测试商户", "支付宝", "", "merchant@example.com", "500.00", "487.50", "", "", "2026-10-08 14:00:00", "", 3, false, false, "账户信息不匹配")
        val vm = model()
        screen { Column { PayTopBar("结算"); SettlementsScreen(AppUiState(settlements = listOf(item), settlementTotal = 1), vm) } }
        compose.onNodeWithContentDescription("结算操作").performClick()
        compose.onNodeWithText("检查完成").performClick()
        compose.onNodeWithText("checked").assertIsDisplayed().performTextReplacement("checked · 已核对账户")
        compose.onNodeWithText("checked · 已核对账户").assertIsDisplayed()
        compose.onNodeWithText("确认").assertIsDisplayed()
        screenshot("08-settlement-checked")
    }
    @Test fun routeEditorShowsRateLimitsAndCostAndAddsMembers() {
        val options = listOf(NativeOption("12", "蓝色微信"), NativeOption("15", "备用微信"))
        val form = NativeForm("配置轮询路由", "ajax_pay.php?act=saveRollInfo&id=7", "POST", listOf(
            NativeField("list[0][channel]", "成员通道", "12", "select", options),
            NativeField("list[0][weight]", "权重", "70", "number"),
            NativeField("list[1][channel]", "成员通道", "", "select", options),
            NativeField("list[1][weight]", "权重", "30", "number")))
        val channel = PaymentChannelItem("12", "蓝色微信", "微信", "wx_v3", "2.50%", "10.00～5000.00", "启用", PaymentChannelStatus.ENABLED, emptyList(), emptyList())
        screen { PaymentEditorScreen(form, false, {}, { _, _ -> }, { _, _ -> }, {}, remember { SnackbarHostState() }, mapOf("12" to channel), mapOf("12" to stat), {}) }
        compose.onNodeWithText("近5分钟成功率").assertIsDisplayed()
        compose.onNodeWithText("92%").assertIsDisplayed()
        compose.onNodeWithText("10.00～5000.00").assertIsDisplayed()
        compose.onNodeWithText("2.50%").assertIsDisplayed()
        compose.onNodeWithText("保存").assertIsDisplayed()
        screenshot("09-route-editor")
        compose.onNodeWithText("添加通道").performScrollTo().performClick()
        compose.onAllNodesWithText("成员通道").assertCountEquals(2)
        compose.onNodeWithText("轮询路由可视化").performScrollTo().assertIsDisplayed()
        screenshot("10-route-visualization")
    }
    @Test fun pluginSelectionCanBeCancelledWithoutChangingConfiguration() {
        screen {
            var value by remember { mutableStateOf("wx_v3") }
            Column { NativeFieldEditor(NativeField("plugin", "支付插件", value, "select", listOf(NativeOption("wx_v3", "微信 V3"), NativeOption("alipay", "支付宝"))), value) { value = it }; Text("当前插件 $value") }
        }
        compose.onNodeWithText("微信 V3").performClick()
        compose.onNodeWithText("支付宝").performClick()
        compose.onNodeWithContentDescription("关闭").performClick()
        compose.onNodeWithText("当前插件 wx_v3").assertIsDisplayed()
    }
    @Test fun duplicateOrderIdentifiersRenderWithoutListKeyCrash() {
        screen { OrdersScreen(AppUiState(orders = listOf(orders[0], orders[0])), model()) }
        compose.onAllNodesWithText(orders[0].tradeNo).assertCountEquals(2)
    }
    @Test fun secretFieldHasAnExplicitVisibilityControl() {
        screen { Column(Modifier.padding(16.dp)) { NativeFieldEditor(NativeField("api_key", "API 密钥", "private", "password", sensitive = true), "private") {} } }
        compose.onNodeWithText("显示").performClick()
        compose.onNodeWithText("隐藏").assertIsDisplayed().performClick()
        compose.onNodeWithText("显示").assertIsDisplayed()
    }
    @Test fun merchantDetailHasNativeRangesAndSeparateCurrencyBalances() {
        val merchant = MerchantSummary(uid = "18", username = "测试商户", groupName = "VIP", balance = "500.00", frozenBalance = "0", telegramId = "", website = "", createdAt = "", status = 1, paymentStatus = 1, settlementStatus = 1, balances = mapOf("CNY" to "500.00", "USDT" to "10.00"))
        val flow = MerchantFlowSummary("18", listOf(MerchantFlowPoint("2026-10-08", 10.0)), currencyPoints = mapOf("CNY" to listOf(MerchantFlowPoint("2026-10-08", 10.0)), "USDT" to listOf(MerchantFlowPoint("2026-10-08", 5.0))))
        var days = 7
        screen { MerchantDetailDialog(merchant, flow, false, {}, 7, null, { days = it }) }
        compose.onNodeWithText("CNY 余额").assertIsDisplayed()
        compose.onNodeWithText("USDT 余额").assertIsDisplayed()
        compose.onNodeWithText("30天").performClick(); org.junit.Assert.assertEquals(30, days)
        screenshot("11-merchant-detail")
    }
}
