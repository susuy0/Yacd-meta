package com.futoonpay.admin

import android.app.Application
import com.futoonpay.admin.data.*
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
class ModuleSubmissionTest {
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun teardown() { Dispatchers.resetMain() }
    @Test fun repeatedBlacklistConfirmationCannotSubmitTheSameWriteTwice() = runBlocking {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val writes = AtomicInteger()
        val repository = FutoonRepository(fixtureClient({ request ->
            if (request.url.queryParameter("act") == "addBlack") {
                writes.incrementAndGet(); entered.countDown(); release.await(5, TimeUnit.SECONDS)
                200 to """{"code":-1,"msg":"fixture rejection"}"""
            } else 200 to """{"total":0,"rows":[]}"""
        }))
        val model = NativeModuleViewModel(repository, ModuleCatalog.find("blacklist"))
        withTimeout(5000) { while (model.state.value.loading) delay(10) }
        val first = model.addBlacklist("ip", "127.0.0.1", "fixture", "1", "")
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            model.addBlacklist("ip", "127.0.0.1", "fixture", "1", "").join()
            assertEquals(1, writes.get())
        } finally { release.countDown() }
        first.join(); assertFalse(model.state.value.loading)
    }
}
