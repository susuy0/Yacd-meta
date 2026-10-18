package com.futoonpay.admin

import android.app.Application
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.AppViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MerchantProfileSubmissionTest {
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun teardown() { Dispatchers.resetMain() }
    private fun model(handler: (okhttp3.Request) -> Pair<Int, String>) = AppViewModel(TestEnvironment(), FutoonRepository(fixtureClient(handler)), false)
    @Test fun failedSavePreservesTheDirtyInputAndAllowsRetry() = runBlocking {
        val calls = AtomicInteger()
        val vm = model { request -> if (request.method == "POST") { calls.incrementAndGet(); 200 to "{\"code\":-1,\"msg\":\"权限不足\"}" } else 200 to profileFixture() }
        vm.openMerchantEditor("1011").join()
        vm.editMerchantField("email", "new@example.com")
        vm.saveMerchantEditor().join()
        assertEquals("new@example.com", vm.state.value.merchantEditor!!.fields.first { it.name == "email" }.value)
        assertEquals("权限不足", vm.state.value.merchantEditorError)
        assertFalse(vm.state.value.merchantEditorSaving)
        vm.saveMerchantEditor().join(); assertEquals(2, calls.get())
    }
    @Test fun balanceAndHiddenFieldsCannotBeChangedViaProfileCallbacks() = runBlocking {
        val vm = model { 200 to profileFixture() }
        vm.openMerchantEditor("1011").join()
        vm.editMerchantField("money", "-100")
        assertEquals("20.00", vm.state.value.merchantEditor!!.fields.first { it.name == "money" }.value)
        vm.closeMerchantEditor(); assertNull(vm.state.value.merchantEditor)
    }
    @Test fun repeatedSaveWhilePostingDoesNotDuplicateTheMutation() = runBlocking {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val calls = AtomicInteger()
        val vm = model { request -> if (request.method == "POST") { calls.incrementAndGet(); entered.countDown(); release.await(5, TimeUnit.SECONDS); 200 to "{\"code\":-1,\"msg\":\"重试\"}" } else 200 to profileFixture() }
        vm.openMerchantEditor("1011").join(); vm.editMerchantField("email", "new@example.com")
        val job = vm.saveMerchantEditor()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            vm.saveMerchantEditor().join(); vm.closeMerchantEditor()
            assertEquals(1, calls.get()); assertTrue(vm.state.value.merchantEditorSaving)
            assertNotNull(vm.state.value.merchantEditor)
        } finally { release.countDown() }
        job.join(); assertFalse(vm.state.value.merchantEditorSaving)
    }
}
