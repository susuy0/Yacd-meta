package com.futoonpay.admin

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.MerchantBalanceSheet
import com.futoonpay.admin.ui.theme.FutoonTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w390dp-h844dp-xhdpi")
class ModernMerchantBalanceUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun reasonAndLatestSnapshotRequiredBeforeExplicitConfirmationAndCancelDoesNotPost() {
        val posts=AtomicInteger();val repo=FutoonRepository(balanceClient { if(it.method=="POST")posts.incrementAndGet();balanceFixture() })
        var closed=false
        compose.setContent { FutoonTheme { PanelOverlayHost { MerchantBalanceSheet("18","add",{repo.merchantBalanceInfo("18")},{s,c,r->repo.adjustMerchantBalance(s,c,r)},{closed=true},{}) } } }
        compose.waitUntil(10000){compose.onAllNodesWithText("70.00").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("核对充值").assertIsNotEnabled()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("金额（元）"))
        compose.onNodeWithText("金额（元）").performTextInput("5.50")
        compose.onNodeWithText("核对充值").assertIsNotEnabled()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("操作原因（3–200字）"))
        compose.onNodeWithText("操作原因（3–200字）").performTextInput("人工测试原因")
        compose.onNodeWithText("核对充值").performClick()
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        compose.runOnUiThread {
            val view=compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap));val out=java.io.File("build/ui-screenshots/61-balance-confirm-fixture.png");out.parentFile?.mkdirs();out.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        }
        compose.onNodeWithText("金额 5.50 CNY").assertIsDisplayed()
        compose.onNodeWithText("返回修改").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("金额（元）"))
        compose.onNodeWithText("5.50").assertExists()
        compose.onNodeWithText("取消").performClick()
        assertTrue(closed);assertEquals(0,posts.get())
    }
    @Test fun unfreezeCannotExceedServerReleasableBalanceAndOperationChangeClearsAmount() {
        val repo=FutoonRepository(balanceClient {balanceFixture()})
        compose.setContent { FutoonTheme { PanelOverlayHost { MerchantBalanceSheet("18","unfreeze",{repo.merchantBalanceInfo("18")},{_,_,_->error("must not submit")},{},{}) } } }
        compose.waitUntil(10000){compose.onAllNodesWithText("70.00").fetchSemanticsNodes().isNotEmpty()}
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("金额（元）"))
        compose.onNodeWithText("金额（元）").performTextInput("10.000001")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("操作原因（3–200字）"))
        compose.onNodeWithText("操作原因（3–200字）").performTextInput("核对冻结款")
        compose.onNodeWithText("核对解冻").assertIsNotEnabled()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("金额（元）"))
        compose.onNodeWithText("金额（元）").performTextReplacement("0.123456")
        compose.onNodeWithText("核对解冻").assertIsEnabled()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("操作"))
        compose.onNodeWithText("操作",useUnmergedTree=true).performClick()
        compose.onNodeWithText("扣除",useUnmergedTree=true).performClick()
        compose.onNodeWithText("核对扣除").assertIsNotEnabled()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("操作原因（3–200字）"))
        compose.onNodeWithText("核对冻结款").assertExists()
        compose.onNodeWithText("0.123456").assertDoesNotExist()
    }
    @Test fun unknownRequestReopensLockedAndRetriesOriginalCommandOnly() {
        val store=MemoryBalancePendingStore()
        val raw=org.json.JSONObject().put("uid","18").put("actdo","0").put("rmb","5.00").put("note","原操作原因").put("request_id","e".repeat(32))
        store.write("18",raw.toString())
        val repo=FutoonRepository(balanceClient {balanceFixture()},store)
        var retried=false
        compose.setContent { FutoonTheme { PanelOverlayHost { MerchantBalanceSheet("18","reduce",{repo.merchantBalanceInfo("18")},{_,c,r->assertTrue(r);assertEquals("5.00",c.amount);assertEquals("原操作原因",c.note);retried=true;ApiResult(true,"confirmed")},{},{}) } } }
        compose.waitUntil(10000){compose.onAllNodesWithText("查询结果 / 安全重试").fetchSemanticsNodes().isNotEmpty()}
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("金额（元）"))
        compose.onNodeWithText("金额（元）").assertIsNotEnabled()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("操作原因（3–200字）"))
        compose.onNodeWithText("操作原因（3–200字）").assertIsNotEnabled()
        compose.onNodeWithText("查询结果 / 安全重试").performClick()
        compose.onNodeWithText("金额 5.00 CNY").assertIsDisplayed()
        compose.onNodeWithText("查询原操作").performClick()
        compose.waitUntil(10000){retried}
    }
    @Test fun pendingWithdrawalConfirmationShowsOriginalReleaseInsteadOfChangedSnapshot() {
        val store=MemoryBalancePendingStore()
        store.write("18",org.json.JSONObject().put("uid","18").put("actdo","4").put("rmb","0").put("note","原操作原因").put("request_id","e".repeat(32)).put("application_id","42").put("application_version","c".repeat(64)).toString())
        val fixture=org.json.JSONObject(balanceFixture());fixture.getJSONObject("balance").getJSONArray("applications").getJSONObject(0).put("release_amount","8.00")
        val repo=FutoonRepository(balanceClient {fixture.toString()},store)
        compose.setContent { FutoonTheme { PanelOverlayHost { MerchantBalanceSheet("18","add",{repo.merchantBalanceInfo("18")},{_,_,_->error("must not post")},{},{}) } } }
        compose.waitUntil(10000){compose.onAllNodesWithText("查询结果 / 安全重试").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("查询结果 / 安全重试").performClick()
        compose.mainClock.advanceTimeBy(500);compose.waitForIdle()
        compose.onNodeWithText("提现申请 #42 · 释放 0 元，解除后商户不能再确认此申请。").assertIsDisplayed()
    }

}
