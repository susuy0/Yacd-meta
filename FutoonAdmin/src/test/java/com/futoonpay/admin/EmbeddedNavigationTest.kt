package com.futoonpay.admin

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

class ScopeProbeActivity : EmbeddedAdminActivity()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EmbeddedNavigationTest {
    private class Store(var active: Boolean = true) : FutoonIntegration.Store {
        override fun isActive() = active
        override fun address() = ""
        override fun setAddress(address: String) {}
        override fun loadCookies(address: String) = ""
        override fun saveCookies(address: String, cookies: String) {}
        override fun clearCookies(address: String) {}
        override fun prepareEvidence(tradeNo: String, merchantTradeNo: String, merchantId: String, amount: String, currency: String) {}
    }
    @Test fun nestedNativeActivitiesKeepTheOriginatingTelegramIdentity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FutoonIntegration.register("101", Store())
        val controller = Robolectric.buildActivity(ScopeProbeActivity::class.java,
            Intent(context, ScopeProbeActivity::class.java).putExtra(FutoonIntegration.EXTRA_SCOPE,"101")).setup()
        val activity = controller.get()
        activity.startActivity(Intent(activity, NativeModuleActivity::class.java).putExtra(NativeModuleActivity.EXTRA_MODULE,"rolls"))
        val child = shadowOf(activity).nextStartedActivity
        assertEquals("101",child.getStringExtra(FutoonIntegration.EXTRA_SCOPE))
        assertEquals("rolls",child.getStringExtra(NativeModuleActivity.EXTRA_MODULE))
        activity.getSharedPreferences("draft",Context.MODE_PRIVATE).edit().putString("key","only-101").commit()
        assertNull(context.getSharedPreferences("draft",Context.MODE_PRIVATE).getString("key",null))
        assertEquals("only-101",context.getSharedPreferences("futoon_native_101_draft",Context.MODE_PRIVATE).getString("key",null))
        controller.pause().stop().destroy()
    }
    @Test fun processRecreationRestoresOnlyTheRequestedAccountAndLoggedOutAccountsClose() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FutoonIntegration.setRestorer { if (it == "202") Store() else null }
        val restored = Robolectric.buildActivity(ScopeProbeActivity::class.java,
            Intent(context,ScopeProbeActivity::class.java).putExtra(FutoonIntegration.EXTRA_SCOPE,"202")).setup()
        assertFalse(restored.get().isFinishing)
        val missing = Robolectric.buildActivity(ScopeProbeActivity::class.java,
            Intent(context,ScopeProbeActivity::class.java).putExtra(FutoonIntegration.EXTRA_SCOPE,"999")).create()
        assertTrue(missing.get().isFinishing)
        restored.pause().stop().destroy(); missing.destroy()
    }
}
