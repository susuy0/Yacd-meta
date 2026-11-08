package com.futoonpay.admin

import android.app.Application
import com.futoonpay.admin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.json.JSONObject
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
@OptIn(ExperimentalCoroutinesApi::class)
class MerchantOpeningFlowTest {
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun teardown() { Dispatchers.resetMain() }
    private val chat = ChatScope("测试会话", "5824870757", key = "0:5824870757:0:5824870757", dialog = 5824870757)
    private fun model(handler: (okhttp3.Request) -> Pair<Int, String>, writer: suspend (String, ChatScope) -> Unit = { _, _ -> }) =
        MerchantOpeningViewModel(FutoonRepository(fixtureClient(handler)), chat, { ChatToolsConfig() }, writer)
    private suspend fun ready(vm: MerchantOpeningViewModel) { vm.load().join(); vm.edit("username", "@fixture(业务)"); vm.edit("url", "fixture.example"); vm.review() }

    @Test fun creationAutomaticallyInsertsAnEditableDraftWithExactD0OrD1() = runBlocking {
        for ((permission, cycle) in listOf("1" to "D0", "0" to "D1")) {
            var posted = emptyMap<String, String>(); var inserted = ""; var destination: ChatScope? = null
            val vm = model({ request -> when (request.url.queryParameter("act")) {
                "addUser" -> { posted = request.openingFields(); 200 to openingSuccess }
                "bind" -> 200 to JSONObject().put("code", 0).put("bind", fixtureBinding).toString()
                else -> 200 to openingFixture()
            } }, { text, target -> inserted = text; destination = target })
            vm.load().join(); vm.edit("username", "@fixture"); vm.edit("settle", permission); vm.review(); vm.create().join()
            assertEquals(permission, posted["settle"]); assertTrue(inserted.contains("结算周期: $cycle")); assertTrue(inserted.contains(fixtureBinding))
            assertEquals(chat, destination); assertTrue(vm.state.value.draftInserted); assertNull(vm.state.value.form)
        }
    }
    private fun openingFormFor(remark: String) = NativeForm("开户", "ajax_user.php?act=addUser", "POST", listOf(NativeField("email", "邮箱", "", "text"), NativeField("username", "*备注", remark, "text", required = true)))
    private fun username(vm: MerchantOpeningViewModel) = vm.state.value.form!!.fields.first { it.name == "username" }.value
    @Test fun theConversationTelegramIdFillsTheRemarkOnceAndIsNotAnEdit() = runBlocking {
        val vm = model({ 200 to openingFixture() })
        vm.load().join()
        assertEquals("5824870757", username(vm)); assertEquals("已按当前会话填入 TG ID，可修改", vm.state.value.prefillNote); assertFalse(vm.state.value.dirty)
        vm.edit("username", "手动备注"); assertEquals("手动备注", username(vm)); assertNull(vm.state.value.prefillNote); assertTrue(vm.state.value.dirty)
        vm.edit("username", ""); vm.load().join(); assertEquals("", username(vm))
    }
    @Test fun aConversationWithoutAnIdLeavesTheRemarkEmptyAndSaysSo() = runBlocking {
        for (id in listOf("", "abc", "123", "0123456", "12345678901234")) {
            val vm = MerchantOpeningViewModel(FutoonRepository(fixtureClient({ 200 to openingFixture() })), ChatScope("某群", id, key = "g", dialog = -100), { ChatToolsConfig() }, { _, _ -> })
            vm.load().join(); assertEquals(id, "", username(vm)); assertTrue(id, vm.state.value.prefillNote!!.contains("没有可识别")); assertFalse(vm.state.value.dirty)
        }
    }
    @Test fun anExistingRemarkIsNeverOverwrittenAndNoNoteIsShown() {
        val form = openingFormFor("已有备注")
        assertEquals(form to null, openingPrefill(form, "5824870757"))
        val filled = openingPrefill(openingFormFor(""), "5824870757")
        assertEquals("5824870757", filled.first.fields.first { it.name == "username" }.value)
        assertTrue(openingPrefill(NativeForm("", "", "POST", emptyList()), "5824870757").second == null)
    }
    @Test fun theFilledRemarkIsSubmittedOnlyAfterReviewAndAppearsInTheDraftVariables() = runBlocking {
        var posted = emptyMap<String, String>(); var inserted = ""
        val vm = model({ request -> when (request.url.queryParameter("act")) {
            "addUser" -> { posted = request.openingFields(); 200 to openingSuccess }
            "bind" -> 200 to JSONObject().put("code", 0).put("bind", fixtureBinding).toString()
            else -> 200 to openingFixture()
        } }, { text, _ -> inserted = text })
        vm.load().join(); vm.create().join(); assertTrue("not reviewed yet", posted.isEmpty())
        vm.review(); vm.create().join(); assertEquals("5824870757", posted["username"]); assertTrue(vm.state.value.draftInserted)
    }
    @Test fun failedBindingOnlyRetriesBindingAndNeverRecreatesMerchant() = runBlocking {
        var creates = 0; var bindings = 0; var drafts = 0
        val vm = model({ request -> when (request.url.queryParameter("act")) {
            "addUser" -> { creates++; 200 to openingSuccess }
            "bind" -> { bindings++; 200 to if (bindings == 1) """{"code":-1,"msg":"暂不可用"}""" else JSONObject().put("code", 0).put("bind", fixtureBinding).toString() }
            else -> 200 to openingFixture()
        } }, { _, _ -> drafts++ })
        ready(vm); vm.create().join()
        assertEquals(OpeningStage.RESULT, vm.state.value.stage); assertFalse(vm.state.value.draftInserted); assertEquals(0, drafts)
        vm.create().join(); assertEquals(1, creates)
        vm.retryBinding().join(); assertEquals(1, creates); assertEquals(2, bindings); assertEquals(1, drafts)
        vm.insert().join(); vm.retryBinding().join(); assertEquals(1, drafts); assertEquals(2, bindings)
    }
    @Test fun rejectedCreationKeepsInputForCorrectionAndDoesNotRequestBinding() = runBlocking {
        var bindings = 0
        val vm = model(handler = { request -> when (request.url.queryParameter("act")) {
            "addUser" -> 200 to """{"code":-1,"msg":"权限不足"}"""
            "bind" -> { bindings++; 200 to "{}" }
            else -> 200 to openingFixture()
        } })
        ready(vm); vm.create().join()
        assertEquals(OpeningStage.FORM, vm.state.value.stage); assertEquals("权限不足", vm.state.value.error)
        assertEquals("@fixture(业务)", vm.state.value.form!!.fields.first { it.name == "username" }.value); assertEquals(0, bindings)
    }
    @Test fun lostCreationResponseBlocksRepeatAndReturnsToMerchantLookup() = runBlocking {
        var creates = 0
        val vm = model(handler = { request -> if (request.url.queryParameter("act") == "addUser") { creates++; throw IOException("lost") }; 200 to openingFixture() })
        ready(vm); vm.create().join(); vm.create().join(); vm.load().join(); vm.review(); vm.create().join()
        assertEquals(OpeningStage.UNCERTAIN, vm.state.value.stage); assertEquals(1, creates); assertFalse(vm.state.value.draftInserted)
    }
    @Test fun secondTapWhilePostingCannotIssueAnotherCreate() = runBlocking {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val creates = AtomicInteger()
        val vm = model(handler = { request -> when (request.url.queryParameter("act")) {
            "addUser" -> { creates.incrementAndGet(); entered.countDown(); release.await(5, TimeUnit.SECONDS); 200 to """{"code":-1,"msg":"拒绝"}""" }
            else -> 200 to openingFixture()
        } })
        ready(vm); val first = vm.create()
        try { assertTrue(entered.await(5, TimeUnit.SECONDS)); vm.create().join(); vm.backToForm(); assertEquals(OpeningStage.SUBMITTING, vm.state.value.stage); assertEquals(1, creates.get()) }
        finally { release.countDown() }
        first.join()
    }
    @Test fun failedDraftInsertionRetainsCreatedAccountAndRetryOnlyWritesDraft() = runBlocking {
        var creates = 0; var attempts = 0; var writes = 0
        val vm = model({ request -> when (request.url.queryParameter("act")) {
            "addUser" -> { creates++; 200 to openingSuccess }
            "bind" -> 200 to JSONObject().put("code", 0).put("bind", fixtureBinding).toString()
            else -> 200 to openingFixture()
        } }, { _, target -> assertEquals(chat.key, target.key); if (++attempts == 1) throw IOException("会话已变化"); writes++ })
        ready(vm); vm.create().join()
        assertEquals(OpeningStage.RESULT, vm.state.value.stage); assertFalse(vm.state.value.draftInserted); assertEquals(0, writes)
        vm.insert().join(); assertTrue(vm.state.value.draftInserted); assertEquals(1, writes); assertEquals(1, creates)
    }
}
