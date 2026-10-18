package com.futoonpay.admin

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.theme.FutoonTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Before
import org.junit.After

@RunWith(RobolectricTestRunner::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-xhdpi")
class BackNavigationRegressionTest {
    @Before fun mainDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetDispatcher() { Dispatchers.resetMain() }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val channel = PaymentChannelItem("12", "蓝色微信", "微信", "wx", "2.5%", "10～5000", "启用", PaymentChannelStatus.ENABLED, emptyList(), emptyList())
    private fun model() = PaymentAdminViewModel(FutoonRepository(fixtureClient({ 200 to "{\"code\":0,\"data\":{}}" })),
        PaymentAdminCatalog.find("channels"), initialDocument = NativeDocument("通道", emptyList(), emptyList(), emptyList()), loadOnStart = false)
    private fun show(vm: PaymentAdminViewModel, fallback: AtomicInteger) {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.addCallback(compose.activity, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { fallback.incrementAndGet() } }) }
        compose.setContent { FutoonTheme { PaymentAdminRoot(vm) { fallback.incrementAndGet() } } }
        compose.waitForIdle()
    }
    private fun back() { compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }; compose.waitForIdle() }
    @Test fun listSystemBackReachesActivityExactlyOnceWithoutRecursiveDispatch() {
        val fallback = AtomicInteger(); val vm = model(); show(vm, fallback)
        back(); assertEquals(1, fallback.get())
    }
    @Test fun detailSystemBackClosesDetailBeforeLeavingTheModule() {
        val fallback = AtomicInteger(); val vm = model(); show(vm, fallback)
        compose.runOnUiThread { vm.openChannelDetail(channel) }; compose.waitUntil(5000) { !vm.state.value.channelDetailLoading }; compose.waitForIdle()
        back(); assertNull(vm.state.value.selectedChannel); assertEquals(0, fallback.get())
        back(); assertEquals(1, fallback.get())
    }
    @Test fun paymentTestSystemBackClosesOnlyTestAndPreservesDetail() {
        val fallback = AtomicInteger(); val vm = model(); show(vm, fallback)
        compose.runOnUiThread { vm.openChannelDetail(channel) }; compose.waitUntil(5000) { !vm.state.value.channelDetailLoading }; compose.waitForIdle()
        compose.runOnUiThread { vm.openPaymentTest(channel) }; compose.waitForIdle()
        back(); assertNull(vm.state.value.testChannel); assertNotNull(vm.state.value.selectedChannel); assertEquals(0, fallback.get())
    }
    @Test fun editorSystemBackClosesEditorAndDoesNotExitActivity() {
        val form = NativeForm("新建支付方式", "save", "POST", listOf(NativeField("showname", "名称", "", "text"), NativeField("device", "设备", "0", "text")))
        val vm = PaymentAdminViewModel(FutoonRepository(fixtureClient({ 200 to "{}" })), PaymentAdminCatalog.find("payment_types"),
            initialDocument = NativeDocument("支付方式", emptyList(), emptyList(), listOf(form)), loadOnStart = false)
        val fallback = AtomicInteger(); show(vm, fallback)
        compose.runOnUiThread { vm.openAdd() }; compose.waitForIdle(); assertNotNull(vm.state.value.editor)
        back(); assertNull(vm.state.value.editor); assertEquals(0, fallback.get())
    }
}
