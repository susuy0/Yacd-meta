package com.futoonpay.admin

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import com.futoonpay.admin.ui.*
import com.futoonpay.admin.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class, qualifiers="w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatNavigationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val destinations = listOf(
        ChatNavItem("recognition", "识别", Icons.Outlined.DocumentScanner), ChatNavItem("orders", "订单", Icons.Outlined.ReceiptLong),
        ChatNavItem("merchants", "商户", Icons.Outlined.Storefront), ChatNavItem("settlements", "结算", Icons.Outlined.AccountBalanceWallet), ChatNavItem("more", "更多", Icons.Outlined.MoreHoriz))
    private fun screenshot(name: String) {
        val rect=compose.onNodeWithTag("appearance-panel").fetchSemanticsNode().boundsInRoot
        compose.runOnUiThread {
            val view=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
            val bmp=Bitmap.createBitmap(rect.width.toInt(),rect.height.toInt(),Bitmap.Config.ARGB_8888)
            Canvas(bmp).apply{translate(-rect.left,-rect.top);view.draw(this)}
            File("build/ui-screenshots/$name.png").apply{parentFile.mkdirs()}.outputStream().use{bmp.compress(Bitmap.CompressFormat.PNG,100,it)};bmp.recycle()
        }
    }
    @Test fun reorderedFiveTabsKeepTheirExactDestinationAndSelectedSemantics() {
        val calls=mutableListOf<String>();var selected by mutableStateOf("orders")
        compose.setContent { FutoonTheme { Column(Modifier.width(320.dp).height(220.dp).testTag("appearance-panel")) {
            Text("当前模块 $selected",Modifier.weight(1f));ChatNavigationBar(destinations.reversed(),selected){calls+=it;selected=it}
        } } }
        compose.onNodeWithTag("chat-nav-orders").assertIsSelected()
        destinations.forEach { item -> compose.onNodeWithTag("chat-nav-${item.id}").assertIsDisplayed().performClick();compose.onNodeWithTag("chat-nav-${item.id}").assertIsSelected();compose.onNodeWithText("当前模块 ${item.id}").assertIsDisplayed() }
        assertEquals(destinations.map{it.id},calls)
        screenshot("54-chat-navigation-320-reordered")
    }
    @Test fun darkRtlLargeFontKeepsAllFiveTabsVisibleAndWithinThePanel() {
        val dark=FutoonPalette(dark=true,surface=Color(0xff111b21),background=Color(0xff0b141a),text=Color(0xffe9edef),secondaryText=Color(0xff8696a0),accent=Color(0xff21c063),accentText=Color(0xff21c063),divider=Color(0xff202c33))
        compose.setContent { val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f),LocalLayoutDirection provides LayoutDirection.Rtl) {
                FutoonTheme(dark) { Column(Modifier.width(320.dp).height(250.dp).testTag("appearance-panel")) { Box(Modifier.weight(1f));ChatNavigationBar(destinations,"merchants"){} } }
            }
        }
        val panel=compose.onNodeWithTag("appearance-panel").fetchSemanticsNode().boundsInRoot
        val bounds=destinations.map { compose.onNodeWithTag("chat-nav-${it.id}").assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        bounds.forEach { assertTrue(it.left>=panel.left-.5f && it.right<=panel.right+.5f && it.bottom<=panel.bottom+.5f);assertTrue(it.height>=96f) }
        assertTrue("RTL must reverse visual order",bounds.first().left>bounds.last().left)
        screenshot("55-chat-navigation-dark-rtl-font150")
    }
    @Test fun longFilterLabelsScrollAndPreserveTheSelectedStableKey() {
        val options=listOf("all" to "全部订单","unpaid" to "等待付款的订单","paid" to "已支付","refund" to "退款审核与售后核实")
        var selected by mutableStateOf("all");val calls=mutableListOf<String>()
        compose.setContent { FutoonTheme { Column(Modifier.width(320.dp).height(200.dp).testTag("appearance-panel")) {
            PayTabs(options,selected){selected=it;calls+=it};Box(Modifier.weight(1f))
        } } }
        compose.onNodeWithTag("chat-filter-list").performScrollToNode(hasTestTag("chat-filter-refund"))
        compose.onNodeWithTag("chat-filter-refund").performClick().assertIsSelected()
        assertEquals(listOf("refund"),calls)
        compose.onNodeWithTag("chat-filter-list").performScrollToNode(hasTestTag("chat-filter-all"))
        compose.onNodeWithTag("chat-filter-all").performClick().assertIsSelected()
        assertEquals(listOf("refund","all"),calls)
        screenshot("56-chat-filter-long-labels")
    }
    @Test fun threeTabMainNavigationUsesTheSameAccessibleRouting() {
        val tabs=listOf(ChatNavItem("chat","聊天",Icons.Outlined.ChatBubbleOutline),ChatNavItem("settings","设置",Icons.Outlined.Settings),ChatNavItem("profile","个人资料",Icons.Outlined.PersonOutline))
        var selected by mutableStateOf("chat");var clicks=0
        compose.setContent { FutoonTheme { Column(Modifier.width(390.dp).height(220.dp).testTag("appearance-panel")) {
            PayTopBar("Futoon chat");Box(Modifier.weight(1f));ChatNavigationBar(tabs,selected){selected=it;clicks++}
        } } }
        compose.onNodeWithTag("chat-nav-profile").performClick().assertIsSelected();assertEquals(1,clicks)
        screenshot("57-chat-navigation-three-tabs")
    }
}
