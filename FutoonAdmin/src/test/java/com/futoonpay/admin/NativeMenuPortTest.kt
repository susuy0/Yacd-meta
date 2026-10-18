package com.futoonpay.admin

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.futoonpay.admin.ui.ModernManage
import com.futoonpay.admin.ui.theme.FutoonTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class, qualifiers="w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeMenuPortTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun everyOriginalMoreEntryOpensItsOwnModule() {
        var target=""
        compose.setContent { FutoonTheme { ModernManage({target=it},{target="domains"},{target="settings"}) } }
        val entries=listOf("支付通道" to "channels","支付方式" to "payment_types","轮询组" to "rolls",
            "通道监测" to "channel_monitor","中转代理" to "proxies","域名审核" to "domains","设置" to "settings",
            "资金明细" to "funds","风控记录" to "risk","支付黑名单" to "blacklist","商户统计" to "merchant_stats",
            "5 日无流水商户" to "inactive","分账接收方" to "profit_receivers","分账记录" to "profit_orders",
            "企业付款" to "transfer","公告管理" to "announcements","支付与结算设置" to "payment_settings",
            "站点与账号设置" to "site_settings","公众号与证书" to "integrations","登录日志" to "login_logs")
        entries.forEach { (label,id) ->
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.runOnIdle { assertEquals(label,id,target) }
        }
    }
}
