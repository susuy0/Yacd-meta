package com.futoonpay.admin

import android.app.Application
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withTimeout
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
class BatchSubmissionTest {
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun teardown() { Dispatchers.resetMain() }
    private fun vm(handler: (okhttp3.Request) -> Pair<Int, String>) = AppViewModel(TestEnvironment(), FutoonRepository(fixtureClient(handler)), loadOnStart = false)
    private fun request() = BatchRequest("merchants", "重置密钥", listOf("1011", "1012").map { BatchCommand(it, "UID $it", BatchKind.MERCHANT_KEY) })
    private fun finished(vm: AppViewModel) = runBlocking { withTimeout(5000) { while (vm.state.value.batch?.running == true) delay(10) } }

    @Test fun offeringOrCancellingAResetDoesNotCallTheBackend() {
        val calls = AtomicInteger(); val vm = vm { calls.incrementAndGet(); 200 to "{\"code\":0}" }
        vm.offerBatch(request()); assertFalse(vm.state.value.batch!!.started)
        vm.closeBatch(); assertNull(vm.state.value.batch); assertEquals(0, calls.get())
    }
    @Test fun repeatedConfirmAndOtherMerchantWriteCannotOverlapBatch() {
        val calls = AtomicInteger(); val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val vm = vm { calls.incrementAndGet(); entered.countDown(); release.await(5, TimeUnit.SECONDS); 200 to "{\"code\":-1}" }
        try {
            vm.offerBatch(request()); vm.startBatch(); assertTrue(entered.await(5, TimeUnit.SECONDS))
            vm.startBatch(); vm.offerBatch(request().copy(title = "其他操作")); vm.closeBatch()
            var balance: ApiResult? = null
            vm.rechargeMerchant("1011", "add", "5") { balance = it }
            assertFalse(balance!!.success); assertEquals(1, calls.get()); assertTrue(vm.state.value.batch!!.running)
        } finally { release.countDown() }
        finished(vm); assertEquals(2, calls.get()); assertFalse(vm.state.value.refreshing)
    }
    @Test fun explicitRetryOnlyContainsConfirmedFailuresAndRequiresAnotherConfirmation() {
        val calls = AtomicInteger()
        val vm = vm { calls.incrementAndGet(); if (it.url.queryParameter("uid") == "1011") 200 to "{\"code\":0,\"key\":\"0123456789abcdef0123456789abcdef\"}" else 200 to "{\"code\":-1}" }
        vm.offerBatch(request()); vm.startBatch(); finished(vm); assertEquals(2, calls.get())
        vm.retryBatch(); assertEquals(listOf("1012"), vm.state.value.batch!!.request.commands.map { it.id }); assertFalse(vm.state.value.batch!!.started); assertEquals(2, calls.get())
        vm.startBatch(); finished(vm); assertEquals(3, calls.get())
    }
    @Test fun unknownResultCannotBeIncludedInRetry() {
        val calls = AtomicInteger(); val vm = vm { calls.incrementAndGet(); throw IOException("timeout") }
        vm.offerBatch(request()); vm.startBatch(); finished(vm)
        assertEquals(1, calls.get()); vm.retryBatch()
        assertEquals(listOf("1012"), vm.state.value.batch!!.request.commands.map { it.id }); assertFalse(vm.state.value.batch!!.started)
    }
    @Test fun paymentViewModelUsesBatchGuardAndReleasesBusyState() {
        val calls = AtomicInteger(); val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val repository = FutoonRepository(fixtureClient({
            if (it.url.queryParameter("act") == "delProxy") { calls.incrementAndGet(); entered.countDown(); release.await(5, TimeUnit.SECONDS); 200 to "{\"code\":-1}" }
            else 200 to "<html></html>"
        }))
        val vm = PaymentAdminViewModel(repository, PaymentAdminCatalog.find("proxies"), loadOnStart = false)
        val request = paymentBatchOptions("proxies", listOf(PaymentBatchRow("7", "代理 #7", listOf(NativeAction("删除", "ajax_pay.php?act=delProxy&id=7", destructive = true))))).single()
        try { vm.offerBatch(request); vm.startBatch(); assertTrue(entered.await(5, TimeUnit.SECONDS)); vm.startBatch(); vm.execute(request.commands.first().nativeAction!!); assertEquals(1, calls.get()) } finally { release.countDown() }
        runBlocking { withTimeout(5000) { while (vm.state.value.batch!!.running) delay(10) } }
        assertFalse(vm.state.value.loading); assertEquals(BatchOutcomeStatus.FAILED, vm.state.value.batch!!.results.single().status)
    }
}
