package com.futoonpay.admin

import android.app.Application
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PanelWorkflowsTest {
    @Before fun before() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun after() { Dispatchers.resetMain() }
    private suspend fun ready(vm: PanelWorkflows) { withTimeout(5000) { vm.state.first { !it.localLoading } } }
    private suspend fun extracted(vm: PanelWorkflows) { withTimeout(5000) { vm.state.first { it.candidates.isNotEmpty() && !it.extracting } } }

    @Test fun messageKeepsEveryCandidateAndOnlyQueriesCheckedRows() = runBlocking {
        val bridge = WorkflowFixtureBridge()
        val vm = PanelWorkflows(bridge); ready(vm)
        vm.receive(RecognizedQuery("系统订单号 S1000001\n商户订单号 M2000002", "AUTO", source = MessageSource(-100, 12627, 19)))
        extracted(vm); assertEquals(2, vm.state.value.candidates.size)
        vm.editCandidate(vm.state.value.candidates.last().id, selected = false)
        vm.runQueries().join()
        assertEquals(listOf("S1000001"), bridge.calls.filter { it.first == "orders.search" }.map { it.second.optString("text") })
        val order = JSONObject(vm.state.value.candidates.first().orders.single())
        assertEquals(19, order.getJSONObject("_source").getInt("message"))
        assertTrue(bridge.calls.none { it.first in listOf("order.action", "fillorder", "merchant.balance") })
    }
    @Test fun cancelStopsQueuedQueriesAfterTheCurrentRead() = runBlocking {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val bridge = WorkflowFixtureBridge { op, _ -> if (op == "orders.search") { entered.countDown(); release.await(5, TimeUnit.SECONDS) }; null }
        val vm = PanelWorkflows(bridge); ready(vm); vm.receive(RecognizedQuery("two", "AUTO")); extracted(vm)
        val job = vm.runQueries()
        try { assertTrue(entered.await(5, TimeUnit.SECONDS)); vm.stopQueries() } finally { release.countDown() }
        job.join()
        assertEquals(1, bridge.calls.count { it.first == "orders.search" })
        assertEquals("queued", vm.state.value.candidates.last().phase)
        assertFalse(vm.state.value.running)
    }
    @Test fun retriesOnlyExplicitQueryFailures() = runBlocking {
        var second = 0
        val bridge = WorkflowFixtureBridge { op, p -> if (op == "orders.search" && p.optString("text") == "M2000002" && ++second == 1) "{\"rows\":[]}" else null }
        val vm = PanelWorkflows(bridge); ready(vm); vm.receive(RecognizedQuery("two", "AUTO")); extracted(vm)
        vm.runQueries().join(); assertEquals("failed", vm.state.value.candidates.last().phase)
        vm.runQueries(onlyFailed = true).join()
        assertTrue(vm.state.value.candidates.all { it.phase == "ok" })
        assertEquals(1, bridge.calls.count { it.first == "orders.search" && it.second.optString("text") == "S1000001" })
        assertEquals(2, second)
    }
    @Test fun oldQueryResponseCannotReplaceANewerMessage() = runBlocking {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val bridge = WorkflowFixtureBridge { op, _ -> if (op == "orders.search") { entered.countDown(); release.await(5, TimeUnit.SECONDS) }; null }
        val vm = PanelWorkflows(bridge); ready(vm); vm.receive(RecognizedQuery("old", "AUTO")); extracted(vm)
        val job = vm.runQueries()
        try { assertTrue(entered.await(5, TimeUnit.SECONDS)); vm.receive(RecognizedQuery("new", "PID")); extracted(vm) } finally { release.countDown() }
        job.join(); assertEquals("new", vm.state.value.text)
        assertTrue(vm.state.value.candidates.all { it.kind == "PID" && it.orders.isEmpty() })
    }
    @Test fun automaticAndManualMerchantLookupsKeepTheirIdentitySeparate() = runBlocking {
        val bridge = WorkflowFixtureBridge(); val vm = PanelWorkflows(bridge); ready(vm)
        vm.updateChat(ChatScope("工单", "5824870757", "来自话题末尾 TGID", "-100:12627", -100, 12627, "12627", "游客"))
        withTimeout(5000) { vm.state.first { it.merchant != null } }
        vm.lookup("uid", "2022").join(); assertEquals("2022", JSONObject(vm.state.value.merchant!!).getString("uid"))
        assertTrue(vm.state.value.manualMerchant)
        vm.lookupAutomatic()
        withTimeout(5000) { vm.state.first { it.merchant != null && !it.manualMerchant } }
        assertEquals("5824870757", bridge.calls.last { it.first == "merchant.lookup" }.second.optString("value"))
        assertEquals("1011", JSONObject(vm.state.value.merchant!!).getString("uid"))
    }
    @Test fun duplicateTgidBindingNeverChoosesAMerchant() = runBlocking {
        val bridge = WorkflowFixtureBridge { op, _ -> if (op == "merchant.lookup") "{\"rows\":[{\"uid\":\"1\"},{\"uid\":\"2\"}]}" else null }
        val vm = PanelWorkflows(bridge); ready(vm); vm.lookup("telegram_id", "5824870757").join()
        assertNull(vm.state.value.merchant); assertTrue(vm.state.value.merchantError!!.contains("冲突"))
    }
    @Test fun legacyGroupBindingHistoryFavoritesAndDraftsRemainReadable() = runBlocking {
        val bridge = WorkflowFixtureBridge().apply { saved = """{"groups":{"0:-100:0:":"2022"},"history":[{"text":"S0","kind":"AUTO"}],"favorites":[{"trade_no":"S0"}],"drafts":{"S0":{"order":{"trade_no":"S0"},"caption":"凭证","updated":1}},"futureKey":"keep"}""" }
        val vm = PanelWorkflows(bridge); ready(vm); vm.updateChat(ChatScope("群", "", "", "0:-100:0:", -100))
        withTimeout(5000) { vm.state.first { it.merchant != null } }
        assertEquals("2022", JSONObject(vm.state.value.merchant!!).optString("uid"))
        assertEquals(1, vm.array("history").size); assertTrue(vm.isFavorite("S0")); assertEquals("凭证", vm.drafts().single().optString("caption"))
        vm.saveNote("S0", "已核对")
        withTimeout(5000) { while (!JSONObject(bridge.saved).optJSONObject("cases").let { it?.has("S0") == true }) yield() }
        assertEquals("keep", JSONObject(bridge.saved).optString("futureKey"))
    }
    @Test fun receiptDraftAndSupplementTemplateDoNotMutateOrderStatus() = runBlocking {
        val bridge = WorkflowFixtureBridge(); val vm = PanelWorkflows(bridge); ready(vm)
        val order = JSONObject(WorkflowFixtureBridge.order).put("api_trade_no", "C3000003").put("_source", MessageSource(-100, 12627, 19).json())
        val caption = evidenceCaption(order, "补单")
        assertTrue(caption.contains("请协助补单")); assertTrue(caption.contains("C3000003")); assertTrue(caption.contains("1011"))
        vm.saveDraft(order, caption, "待处理")
        withTimeout(5000) { while (bridge.calls.none { it.first == "evidence.draft" }) yield() }
        vm.restoreDraft(vm.drafts().single()).join()
        val request = bridge.calls.last { it.first == "evidence.restore" }.second
        assertEquals(19, request.getJSONObject("order").getJSONObject("_source").getInt("message"))
        assertTrue(bridge.calls.none { it.first == "order.action" || it.first == "fillorder" })
        assertEquals(0, bridge.sent.get())
    }
    @Test fun ocrCancellationKeepsTheExistingTextAndCandidates() = runBlocking {
        val vm = PanelWorkflows(WorkflowFixtureBridge()); ready(vm); vm.receive(RecognizedQuery("two", "AUTO")); extracted(vm)
        vm.receive(RecognizedQuery("", "AUTO", error = "取消识别"))
        assertEquals("two", vm.state.value.text); assertEquals(2, vm.state.value.candidates.size)
    }
    @Test fun csvNeutralizesFormulaPrefixesAndEscapesQuotes() {
        assertEquals("\"'=1+2\"", PanelWorkflows.csvCell("=1+2"))
        assertEquals("\"'  +SUM(A1)\"", PanelWorkflows.csvCell("  +SUM(A1)"))
        assertEquals("\"A\"\"B\"", PanelWorkflows.csvCell("A\"B"))
    }
    @Test fun deliveryStatusComesFromTelegramAcknowledgementsWithoutSendingAgain() = runBlocking {
        val bridge = WorkflowFixtureBridge { op, _ -> if (op == "delivery.read") """{"S1000001":{"sent":2,"failed":1,"pending":0,"dialog":"-100","message":88}}""" else null }
        val vm = PanelWorkflows(bridge); ready(vm); vm.refreshDeliveries().join()
        assertTrue(vm.case("S1000001").optString("sendState").contains("Telegram 已确认 2 张凭证"))
        assertTrue(vm.case("S1000001").optString("sendState").contains("1 张发送失败"))
        assertEquals(0, bridge.sent.get())
    }
    @Test fun batchCandidatesRememberTheirOwnMessages() = runBlocking {
        val vm = PanelWorkflows(WorkflowFixtureBridge()); ready(vm)
        vm.receive(RecognizedQuery("two", "AUTO", pointers = mapOf("SYSTEM_ORDER:S1000001" to MessageSource(-100, 1, 11), "MERCHANT_ORDER:M2000002" to MessageSource(-100, 1, 22))))
        extracted(vm)
        assertEquals(listOf(11L, 22L), vm.state.value.candidates.map { it.source.message })
    }
}

internal class WorkflowFixtureBridge(private val handler: (String, JSONObject) -> String? = { _, _ -> null }) : FixtureBridge() {
    val calls = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    @Volatile var saved = "{}"
    var selectedOrder = ""
    var selectedCaption = ""
    var pickedChat = 0
    var pickedGallery = 0
    var files = emptyArray<String>()
    override fun call(operation: String, params: String): String {
        val p = JSONObject(params); calls += operation to p
        handler(operation, p)?.let { return it }
        return when (operation) {
            "local.read" -> saved
            "local.write" -> { saved = params; "{}" }
            "recognize" -> """[{"value":"S1000001","kind":"SYSTEM_ORDER"},{"value":"M2000002","kind":"MERCHANT_ORDER"}]"""
            "orders.search" -> """{"total":1,"rows":[$order]}"""
            "merchant.lookup" -> { val uid = if (p.optString("column") == "uid") p.getString("value") else "1011"; """{"rows":[{"uid":"$uid","username":"测试商户","telegram_id":"5824870757","money":"20.00","status":1,"pay":1,"settle":1}]}""" }
            else -> "{}"
        }
    }
    override fun selectEvidence(order: String, caption: String) { selectedOrder = order; selectedCaption = caption }
    override fun pickEvidenceFromChat() { pickedChat++ }
    override fun openGallery() { pickedGallery++ }
    override fun evidencePaths() = files
    companion object { const val order = """{"trade_no":"S1000001","out_trade_no":"M2000002","uid":"1011","money":"10.00","realmoney":"10.00","status":0,"channel":"12","addtime":"2026-10-18 15:50:00"}""" }
}
