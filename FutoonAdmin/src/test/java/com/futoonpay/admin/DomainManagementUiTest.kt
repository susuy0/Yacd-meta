package com.futoonpay.admin

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.futoonpay.admin.data.FutoonRepository
import com.futoonpay.admin.data.fixtureClient
import com.futoonpay.admin.ui.theme.FutoonTheme
import okhttp3.FormBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DomainManagementUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val posts = CopyOnWriteArrayList<Map<String, String>>()
    private fun root(rows: String, merchant: String = "") {
        val repo = FutoonRepository(fixtureClient({ r ->
            if (r.method == "POST" && r.url.queryParameter("act") == "setDomainStatus") { val b = r.body as FormBody; posts += (0 until b.size).associate { b.name(it) to b.value(it) }; 200 to """{"code":0,"msg":"ok"}""" }
            else 200 to """{"total":${if (rows.isBlank()) 0 else 1},"rows":[$rows]}"""
        }))
        val vm = DomainAuditViewModel(repo, merchant)
        compose.setContent { FutoonTheme { PanelOverlayHost { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { DomainAuditRoot(vm) {} } } } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("域名记录", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun revokingAnApprovedDomainAsksForAReasonAndExplainsTheImpact() {
        root("""{"id":"7","uid":"10482","domain":"www.xinghe-shop.example","status":1,"addtime":"11-02","endtime":"11-02"}""")
        assertTrue(compose.onAllNodesWithText("已通过").fetchSemanticsNodes().size >= 2)
        compose.onNodeWithContentDescription("域名操作").performClick()
        compose.onNodeWithText("撤销授权（改为拒绝）").performClick()
        compose.onNodeWithText("影响范围").assertIsDisplayed()
        compose.onNodeWithText("被举报").performClick()
        compose.onNodeWithText("确认改为拒绝").assertIsEnabled().performClick()
        compose.waitUntil(5000) { posts.isNotEmpty() }
        assertEquals("2", posts.single()["status"]); assertEquals("被举报", posts.single()["remark"]); assertEquals("7", posts.single()["id"])
    }
    @Test fun anotherReasonNeedsTextBeforeItCanBeSent() {
        root("""{"id":"7","uid":"10482","domain":"www.xinghe-shop.example","status":1}""")
        compose.onNodeWithContentDescription("域名操作").performClick()
        compose.onNodeWithText("撤销授权（改为拒绝）").performClick()
        compose.onNodeWithText("其他").performClick()
        compose.onNodeWithText("确认改为拒绝").assertIsNotEnabled()
        compose.onNodeWithText("拒绝原因（商户会看到）").performTextInput("域名过期")
        compose.onNodeWithText("确认改为拒绝").assertIsEnabled()
        compose.onNodeWithText("取消").performClick()
        assertTrue(posts.isEmpty())
    }
    @Test fun aMerchantWithoutDomainsGetsTheExplanationAndAnAddButton() {
        root("", merchant = "10482")
        compose.waitUntil(5000) { compose.onAllNodesWithText("新增域名").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("商户 10482 还没有授权域名").assertExists()
        compose.onNodeWithText("没有授权域名时，商户只能用接口下单，不能拉起收银台。").assertExists()
    }
}
