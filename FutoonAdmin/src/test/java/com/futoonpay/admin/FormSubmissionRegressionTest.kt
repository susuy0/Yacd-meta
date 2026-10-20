package com.futoonpay.admin

import android.app.Application
import com.futoonpay.admin.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Before
import org.junit.After

@RunWith(RobolectricTestRunner::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Config(sdk = [35], application = Application::class)
class FormSubmissionRegressionTest {
    @Before fun mainDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetDispatcher() { Dispatchers.resetMain() }
    // These cases exercise ViewModels, not a composition. Mixing Espresso's main-loop
    // idler with an UnconfinedTestDispatcher can wait forever for an unrelated UI frame.
    private fun runModel(action: () -> Unit) = action()
    private fun awaitModel(timeoutMillis: Long, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (!condition()) {
            if (System.nanoTime() >= deadline) fail("ViewModel condition did not complete within $timeoutMillis ms")
            Thread.sleep(10)
        }
    }
    private val form = NativeForm("支付方式", "ajax_pay.php?act=savePayType", "POST", listOf(
        NativeField("action", "action", "add", "hidden"), NativeField("showname", "名称", "", "text"), NativeField("device", "设备", "0", "text")))
    private fun model(handler: (okhttp3.Request) -> Pair<Int, String>) = PaymentAdminViewModel(FutoonRepository(fixtureClient(handler)),
        PaymentAdminCatalog.find("payment_types"), initialDocument = NativeDocument("支付方式", emptyList(), emptyList(), listOf(form)), loadOnStart = false)
    @Test fun failedSaveRetainsEditedFieldsAndRetryUsesTheSameInput() {
        val calls = AtomicInteger(); var savedName = ""
        val vm = model { request ->
            if (request.url.queryParameter("act") == "savePayType") {
                val body = request.body as okhttp3.FormBody
                savedName = (0 until body.size).firstOrNull { body.name(it) == "showname" }?.let { body.value(it) }.orEmpty()
                if (calls.incrementAndGet() == 1) 200 to "{\"code\":-1,\"msg\":\"暂时失败\"}" else 200 to "{\"code\":0}"
            } else 200 to "<html></html>"
        }
        runModel { vm.openAdd(); vm.updateEditorField("showname", "自定义支付"); val editor = vm.state.value.editor!!; vm.submit(editor, editor.fields.associate { it.name to it.value }) }
        awaitModel(5000) { calls.get() == 1 && !vm.state.value.loading }
        assertEquals("自定义支付", vm.state.value.editor!!.fields.first { it.name == "showname" }.value)
        runModel { val editor = vm.state.value.editor!!; vm.submit(editor, editor.fields.associate { it.name to it.value }) }
        awaitModel(5000) { calls.get() == 2 && vm.state.value.editor == null }
        assertEquals("自定义支付", savedName)
    }
    @Test fun rapidDoubleSubmissionProducesOnlyOneMutation() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val calls = AtomicInteger()
        val vm = model { request ->
            if (request.url.queryParameter("act") == "savePayType") { calls.incrementAndGet(); entered.countDown(); release.await(5, TimeUnit.SECONDS) }
            200 to "{\"code\":-1,\"msg\":\"fixture\"}"
        }
        try {
            runModel { vm.openAdd(); vm.updateEditorField("showname", "微信"); val editor = vm.state.value.editor!!; vm.submit(editor, editor.fields.associate { it.name to it.value }); vm.submit(editor, editor.fields.associate { it.name to it.value }) }
            assertTrue(entered.await(5, TimeUnit.SECONDS)); assertEquals(1, calls.get())
        } finally { release.countDown() }
        awaitModel(5000) { !vm.state.value.loading }
    }
    @Test fun invalidTestPaymentDoesNotCreateAnOrder() {
        val calls = AtomicInteger()
        val vm = model { calls.incrementAndGet(); 200 to "{}" }
        val channel = PaymentChannelItem("12", "微信", "微信", "wx", "2.5%", "", "1", PaymentChannelStatus.ENABLED, emptyList(), emptyList())
        runModel { vm.openPaymentTest(channel); vm.submitPaymentTest("测试", "1.001") }
        assertEquals(0, calls.get()); assertFalse(vm.state.value.testResult!!.success)
    }
    private fun channelModel(withId: Boolean, calls: AtomicInteger, accountCalls: AtomicInteger) = PaymentAdminViewModel(FutoonRepository(fixtureClient(handler = { request ->
        when (request.url.queryParameter("act")) {
            "getPlugins" -> 200 to "{\"code\":0,\"data\":[{\"name\":\"wx\",\"showname\":\"微信\"}]}"
            "getPlugin" -> 200 to "{\"code\":0,\"inputs\":{\"appid\":\"应用 ID\",\"api_key\":\"API 密钥\"}}"
            "saveChannel" -> { calls.incrementAndGet(); 200 to if (withId) "{\"code\":0,\"id\":42}" else "{\"code\":0}" }
            "saveChannelInfo" -> { accountCalls.incrementAndGet(); 200 to "{\"code\":-1,\"msg\":\"账户暂时失败\"}" }
            else -> 200 to "<html></html>"
        }
    })), PaymentAdminCatalog.find("channels"), initialDocument = NativeDocument("通道", emptyList(), emptyList(), listOf(NativeForm("通道", "saveChannel", "POST", listOf(
        NativeField("action", "action", "add", "hidden"), NativeField("id", "ID", "", "hidden"), NativeField("name", "名称", "", "text"),
        NativeField("type", "支付方式", "1", "select", listOf(NativeOption("1", "微信"))), NativeField("plugin", "插件", "", "select"), NativeField("rate", "费率", "", "number"))))), loadOnStart = false)
    private fun prepareChannel(vm: PaymentAdminViewModel) {
        runModel { vm.openAdd() }
        awaitModel(5000) { !vm.state.value.pluginLoading }
        runModel { vm.updateEditorField("name", "测试微信"); vm.updateEditorField("rate", "2.5"); vm.updateEditorField("plugin", "wx") }
        awaitModel(5000) { !vm.state.value.accountLoading }
        assertNotNull(vm.state.value.accountForm)
        runModel { vm.updateEditorField("account::appid", "app42") }
    }
    @Test fun accountSaveRetryDoesNotCreateTheChannelTwice() {
        val calls = AtomicInteger(); val accounts = AtomicInteger(); val vm = channelModel(true, calls, accounts)
        prepareChannel(vm)
        runModel { val form = vm.state.value.editor!!; vm.submit(form, form.fields.associate { it.name to it.value }) }
        awaitModel(5000) { !vm.state.value.loading && accounts.get() == 1 }
        assertTrue(vm.state.value.primarySaved); assertEquals("42", vm.state.value.editor!!.fields.first { it.name == "id" }.value)
        runModel { val form = vm.state.value.editor!!; vm.submit(form, form.fields.associate { it.name to it.value }) }
        awaitModel(5000) { !vm.state.value.loading && accounts.get() == 2 }
        assertEquals(1, calls.get()); assertEquals("app42", vm.state.value.editor!!.fields.first { it.name == "account::appid" }.value)
    }
    @Test fun missingCreatedIdPreventsRetryFromCreatingADuplicate() {
        val calls = AtomicInteger(); val accounts = AtomicInteger(); val vm = channelModel(false, calls, accounts)
        prepareChannel(vm)
        runModel { val form = vm.state.value.editor!!; vm.submit(form, form.fields.associate { it.name to it.value }) }
        awaitModel(5000) { !vm.state.value.loading && vm.state.value.primarySaved }
        runModel { vm.updateEditorField("name", "修正名称"); val form = vm.state.value.editor!!; vm.submit(form, form.fields.associate { it.name to it.value }) }
        awaitModel(5000) { !vm.state.value.loading }
        assertEquals(1, calls.get()); assertEquals(0, accounts.get()); assertNotNull(vm.state.value.editor)
    }
    @Test fun lostSaveResponseRequiresFreshReadBeforeAnotherMutation() {
        val calls = AtomicInteger()
        val vm = model { request ->
            if (request.url.queryParameter("act") == "savePayType") { calls.incrementAndGet(); throw java.io.IOException("response lost") }
            200 to "<html></html>"
        }
        runModel { vm.openAdd(); vm.updateEditorField("showname", "微信"); val editor = vm.state.value.editor!!; vm.submit(editor, editor.fields.associate { it.name to it.value }) }
        awaitModel(5000) { !vm.state.value.loading && vm.state.value.writeUncertain }
        runModel { val editor = vm.state.value.editor!!; vm.submit(editor, editor.fields.associate { it.name to it.value }) }
        assertEquals(1, calls.get()); assertNotNull(vm.state.value.editor)
    }

}
