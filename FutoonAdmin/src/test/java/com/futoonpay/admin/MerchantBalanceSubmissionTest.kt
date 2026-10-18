package com.futoonpay.admin

import android.app.Application
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MerchantBalanceSubmissionTest {
    @Before fun setDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetDispatcher() { Dispatchers.resetMain() }
    private fun model(handler: (okhttp3.Request) -> Pair<Int, String>) =
        AppViewModel(TestEnvironment(), FutoonRepository(fixtureClient(handler)), loadOnStart = false)

    @Test fun concurrentBalanceActionsIssueOnlyOneMutation() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val complete = CountDownLatch(1)
        val calls = AtomicInteger(); var second: ApiResult? = null
        val vm = model {
            calls.incrementAndGet(); entered.countDown(); release.await(5, TimeUnit.SECONDS)
            200 to "{\"code\":-1,\"msg\":\"暂时失败\"}"
        }
        try {
            vm.rechargeMerchant("1011", "add", "5") { complete.countDown() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertTrue(vm.state.value.merchantBalanceUpdating)
            vm.rechargeMerchant("1011", "reduce", "5") { second = it }
            assertEquals(1, calls.get()); assertFalse(second!!.success)
        } finally { release.countDown() }
        assertTrue(complete.await(5, TimeUnit.SECONDS)); assertFalse(vm.state.value.merchantBalanceUpdating)
    }

    @Test fun rejectedRequestReleasesBusyStateAndAllowsAnExplicitNewAttempt() {
        val calls = AtomicInteger(); val first = CountDownLatch(1); val second = CountDownLatch(1)
        val vm = model { calls.incrementAndGet(); 200 to "{\"code\":-1,\"msg\":\"权限不足\"}" }
        vm.rechargeMerchant("1011", "add", "5") { assertFalse(it.success); first.countDown() }
        assertTrue(first.await(5, TimeUnit.SECONDS)); assertFalse(vm.state.value.merchantBalanceUpdating)
        vm.rechargeMerchant("1011", "add", "5") { second.countDown() }
        assertTrue(second.await(5, TimeUnit.SECONDS)); assertEquals(2, calls.get())
    }

    @Test fun networkFailureIsReportedAndDoesNotLeaveTheScreenSubmitting() {
        val calls = AtomicInteger(); val complete = CountDownLatch(1); var result: ApiResult? = null
        val vm = model { calls.incrementAndGet(); throw IOException("connection reset") }
        vm.rechargeMerchant("1011", "add", "5") { result = it; complete.countDown() }
        assertTrue(complete.await(5, TimeUnit.SECONDS)); assertFalse(result!!.success)
        assertEquals("connection reset", result!!.message)
        assertFalse(vm.state.value.merchantBalanceUpdating); assertFalse(vm.state.value.refreshing); assertEquals(1, calls.get())
    }
}
