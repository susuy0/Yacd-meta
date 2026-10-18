package com.futoonpay.admin

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class, qualifiers="w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EmbeddedMainActivitySmokeTest {
    private val host = object : ExternalResource() {
        override fun before() {
            FutoonIntegration.register("404", object : FutoonIntegration.Store {
                override fun isActive() = true
                override fun address() = ""
                override fun setAddress(address: String) {}
                override fun loadCookies(address: String) = ""
                override fun saveCookies(address: String, cookies: String) {}
                override fun clearCookies(address: String) {}
                override fun prepareEvidence(tradeNo: String, merchantTradeNo: String, merchantId: String, amount: String, currency: String) {}
            })
        }
    }
    private val activityRule = ActivityScenarioRule<MainActivity>(
        Intent(ApplicationProvider.getApplicationContext<Context>(),MainActivity::class.java)
            .putExtra(FutoonIntegration.EXTRA_SCOPE,"404").putExtra(MainActivity.EXTRA_TAB,"ORDERS"),
    )
    private val compose = AndroidComposeTestRule(activityRule) { rule ->
        var activity: MainActivity? = null
        rule.scenario.onActivity { activity = it }
        checkNotNull(activity)
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(host).around(compose)

    @Test fun actualNativeActivityRendersUnderTheHostApplicationInABoundedWindow() {
        compose.onNodeWithText("FUTOON PAY").assertExists()
        compose.onNodeWithText("登录后台").assertExists()
        compose.runOnIdle {
            val activity = compose.activity
            assertFalse(activity.isFinishing)
            val attributes = activity.window.attributes
            assertTrue("Width after content attachment: ${attributes.width}",attributes.width > 0)
            assertTrue(attributes.width < activity.resources.displayMetrics.widthPixels)
            assertTrue(attributes.height > 0)
            assertTrue(attributes.height < activity.resources.displayMetrics.heightPixels)
        }
    }
}
