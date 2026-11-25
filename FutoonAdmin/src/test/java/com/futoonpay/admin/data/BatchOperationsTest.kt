package com.futoonpay.admin.data

import kotlinx.coroutines.*
import okhttp3.FormBody
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class BatchOperationsTest {
    private val key = "0123456789abcdef0123456789abcdef"
    private fun command(id: String, kind: BatchKind = BatchKind.MERCHANT_KEY, fields: Map<String, String> = emptyMap()) = BatchCommand(id, "UID $id", kind, fields)
    private fun fields(request: Request): Map<String, String> = (request.body as FormBody).let { form -> (0 until form.size).associate { form.name(it) to form.value(it) } }

    @Test fun merchantResetUsesActualListGetContractAndConfiguredAdminPath() = runBlocking {
        var recorded: Request? = null
        val repo = FutoonRepository(fixtureClient({ recorded = it; 200 to "{\"code\":0,\"key\":\"$key\"}" }))
        val result = repo.runBatch(listOf(command("1011"))).single()
        assertEquals(BatchOutcomeStatus.SUCCESS, result.status); assertEquals(key, result.newKey)
        assertEquals("GET", recorded!!.method); assertEquals("/private-admin/ajax_user.php", recorded!!.url.encodedPath)
        assertEquals("resetUser", recorded!!.url.queryParameter("act")); assertEquals("1011", recorded!!.url.queryParameter("uid"))
        assertEquals("https://fixture.example/private-admin/ulist.php", recorded!!.header("Referer"))
        assertFalse(result.toString().contains(key))
    }
    @Test fun rejectedResetDoesNotExposeBackendSecretOrProduceANewKey() = runBlocking {
        val repo = FutoonRepository(fixtureClient({ 200 to "{\"code\":-1,\"msg\":\"$key\",\"key\":\"$key\"}" }))
        val result = repo.runBatch(listOf(command("1011"))).single()
        assertEquals(BatchOutcomeStatus.FAILED, result.status); assertEquals("", result.newKey); assertFalse(result.message.contains(key))
    }
    @Test fun missingKeyAfterSuccessIsUncertainAndStopsFurtherResets() = runBlocking {
        var calls = 0
        val repo = FutoonRepository(fixtureClient({ calls++; 200 to "{\"code\":0}" }))
        val result = repo.runBatch(listOf(command("1011"), command("1012")))
        assertEquals(listOf(BatchOutcomeStatus.UNCERTAIN, BatchOutcomeStatus.NOT_RUN), result.map { it.status }); assertEquals(1, calls)
        assertEquals(listOf("1012"), BatchProgress(BatchRequest("merchants", "重置", result.map { it.command }), results = result).retryable.map { it.id })
    }
    @Test fun invalidUidsNeverReachServer() = runBlocking {
        var calls = 0
        val repo = FutoonRepository(fixtureClient({ calls++; 200 to "{\"code\":0}" }))
        val result = repo.runBatch(listOf(command(""), command("0"), command("-2"), command("1&uid=3")))
        assertTrue(result.all { it.status == BatchOutcomeStatus.FAILED }); assertEquals(0, calls)
    }
    @Test fun duplicateTargetIsOnlySubmittedOnce() = runBlocking {
        var calls = 0
        val repo = FutoonRepository(fixtureClient({ calls++; 200 to "{\"code\":0,\"key\":\"$key\"}" }))
        assertEquals(2, repo.runBatch(listOf(command("1011"), command("1011"), command("1012"))).size); assertEquals(2, calls)
    }
    @Test fun partialFailurePreservesSuccessfulKeysAndProgressForEachRecord() = runBlocking {
        val progress = mutableListOf<List<BatchOutcome>>()
        val repo = FutoonRepository(fixtureClient({ if (it.url.queryParameter("uid") == "1012") 200 to "{\"code\":-1}" else 200 to "{\"code\":0,\"key\":\"$key\"}" }))
        val results = repo.runBatch(listOf(command("1011"), command("1012"), command("1013"))) { progress += it }
        assertEquals(listOf(1, 2, 3), progress.map { it.size })
        assertEquals(listOf(BatchOutcomeStatus.SUCCESS, BatchOutcomeStatus.FAILED, BatchOutcomeStatus.SUCCESS), results.map { it.status })
        assertEquals(listOf("1012"), BatchProgress(BatchRequest("merchants", "重置", results.map { it.command }), results = results).retryable.map { it.id })
        assertEquals(key, results.first().newKey)
    }
    @Test fun lostResponseIsNotRetriedOrReportedAsFailureAndRemainingTargetsStop() = runBlocking {
        var calls = 0
        val repo = FutoonRepository(fixtureClient({ calls++; throw IOException("timeout") }))
        val results = repo.runBatch(listOf(command("1011"), command("1012")))
        assertEquals(listOf(BatchOutcomeStatus.UNCERTAIN, BatchOutcomeStatus.NOT_RUN), results.map { it.status }); assertEquals(1, calls)
    }
    @Test fun expiredSessionStopsBatchAndClearsCookies() = runBlocking {
        var calls = 0; val cookies = TestCookies()
        val repo = FutoonRepository(fixtureClient({ calls++; 401 to "{}" }, cookies = cookies))
        val results = repo.runBatch(listOf(command("1011"), command("1012")))
        assertTrue(cookies.cleared); assertTrue(results.all { it.status == BatchOutcomeStatus.NOT_RUN }); assertEquals(1, calls)
    }
    @Test fun httpPermissionDenialStopsBatchWithoutLoggingOut() = runBlocking {
        var calls = 0; val cookies = TestCookies()
        val repo = FutoonRepository(fixtureClient({ calls++; 403 to "{}" }, cookies = cookies))
        val results = repo.runBatch(listOf(command("1011"), command("1012")))
        assertFalse(cookies.cleared); assertEquals(listOf(BatchOutcomeStatus.FAILED, BatchOutcomeStatus.NOT_RUN), results.map { it.status }); assertEquals(1, calls)
    }
    @Test fun merchantBatchKeepsUserPaymentAndSettlementTypesSeparate() = runBlocking {
        val sent = mutableListOf<Map<String, String>>()
        val repo = FutoonRepository(fixtureClient({ sent += fields(it); assertEquals("setUser", it.url.queryParameter("act")); 200 to "{\"code\":0}" }))
        listOf("status", "pay", "settle").forEach { type -> repo.runBatch(listOf(command("1011", BatchKind.MERCHANT_STATUS, mapOf("type" to type, "status" to "1")))) }
        assertEquals(listOf("status", "pay", "settle"), sent.map { it["type"] }); assertTrue(sent.all { it["uid"] == "1011" && it["status"] == "1" })
    }
    @Test fun malformedMerchantActionsCannotFallThroughToUserStatusMutation() = runBlocking {
        var calls = 0
        val repo = FutoonRepository(fixtureClient({ calls++; 200 to "{\"code\":0}" }))
        val results = repo.runBatch(listOf(command("1011", BatchKind.MERCHANT_STATUS, mapOf("type" to "bad", "status" to "1"))))
        assertEquals(BatchOutcomeStatus.FAILED, results.single().status); assertEquals(0, calls)
    }
    @Test fun orderQueryAndNotifyPreserveTheirDifferentFieldNames() = runBlocking {
        val sent = mutableListOf<Map<String, String>>()
        val repo = FutoonRepository(fixtureClient({ sent += fields(it); 200 to "{\"code\":0}" }))
        repo.runBatch(listOf(command("A", BatchKind.ORDER_ACTION, mapOf("action" to "queryChannelOrder")), command("B", BatchKind.ORDER_ACTION, mapOf("action" to "notify"))))
        assertEquals(listOf(mapOf("tradeNo" to "A"), mapOf("trade_no" to "B")), sent)
    }
    @Test fun settlementStatusKeepsPerRecordUidRateAndUsdt() = runBlocking {
        var request: Request? = null
        val repo = FutoonRepository(fixtureClient({ request = it; 200 to "{\"code\":0}" }))
        repo.runBatch(listOf(command("42", BatchKind.SETTLEMENT_STATUS, mapOf("uid" to "18", "status" to "1", "usdt_rate" to "7.21", "usdt" to "13.86"))))
        assertEquals("setSettleStatus", request!!.url.queryParameter("act")); assertEquals("18", request!!.url.queryParameter("uid")); assertEquals("7.21", request!!.url.queryParameter("usdt_rate")); assertEquals("13.86", request!!.url.queryParameter("usdt"))
    }
    @Test fun checkedIsWrittenAsFailureReasonWithoutChangingSettlementStatus() = runBlocking {
        var sent = emptyMap<String, String>()
        val repo = FutoonRepository(fixtureClient({ assertEquals("settle_setresult", it.url.queryParameter("act")); sent = fields(it); 200 to "{\"code\":0}" }))
        repo.runBatch(listOf(command("42", BatchKind.SETTLEMENT_ACTION, mapOf("action" to "settle_setresult", "result" to "checked"))))
        assertEquals(mapOf("id" to "42", "result" to "checked"), sent)
    }
    @Test fun skippedRecordMakesNoRequestAndIsNeverOfferedForRetry() = runBlocking {
        var calls = 0
        val repo = FutoonRepository(fixtureClient({ calls++; 200 to "{\"code\":0}" }))
        val command = command("1").copy(skipReason = "该状态不支持")
        val result = repo.runBatch(listOf(command))
        assertEquals(BatchOutcomeStatus.SKIPPED, result.single().status); assertEquals(0, calls)
        assertTrue(BatchProgress(BatchRequest("merchants", "重置", listOf(command)), results = result).retryable.isEmpty())
    }
    @Test fun secondBatchCannotRunWhileFirstIsSuspended() = runBlocking {
        val runner = BatchRunner(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); var calls = 0
        val execute: suspend (BatchCommand) -> BatchOutcome = { calls++; entered.complete(Unit); release.await(); BatchOutcome(it, BatchOutcomeStatus.SUCCESS, "完成") }
        val first = async { runner.run(listOf(command("1")), execute) }; entered.await()
        val second = runner.run(listOf(command("2")), execute)
        assertEquals(BatchOutcomeStatus.NOT_RUN, second.single().status); assertEquals(1, calls)
        release.complete(Unit); assertEquals(BatchOutcomeStatus.SUCCESS, first.await().single().status)
    }
    @Test fun cancellationPropagatesAndReleasesBatchLock() = runBlocking {
        val runner = BatchRunner()
        try { runner.run(listOf(command("1")), { throw CancellationException("cancel") }); fail() } catch (_: CancellationException) { }
        assertEquals(BatchOutcomeStatus.SUCCESS, runner.run(listOf(command("2")), { BatchOutcome(it, BatchOutcomeStatus.SUCCESS, "完成") }).single().status)
    }
    @Test fun configurationBulkOnlyUsesAvailableExactNativeActions() {
        val disable = NativeAction("停用", "ajax_pay.php?act=customSet&id=7", "POST", mapOf("enabled" to "0"))
        val requests = paymentBatchOptions("proxies", listOf(PaymentBatchRow("7", "代理 #7", listOf(disable)), PaymentBatchRow("8", "代理 #8", emptyList())))
        assertEquals(listOf("停用"), requests.map { it.title }); assertEquals(disable, requests.single().commands.first().nativeAction)
        assertTrue(requests.single().commands.last().skipReason.isNotBlank())
        assertTrue(paymentBatchOptions("channels", listOf(PaymentBatchRow("1", "通道 #1", listOf(NativeAction("编辑", "get.php"), NativeAction("启用", "futoon://detail"))))).isEmpty())
    }
    @Test fun configurationExecutionPreservesNativeMethodAndFields() = runBlocking {
        var sent: Request? = null
        val repo = FutoonRepository(fixtureClient({ if (it.url.queryParameter("act") == "customSet") { sent = it; 200 to "{\"code\":0}" } else 200 to "<html>配置</html>" }))
        val action = NativeAction("停用", "ajax_pay.php?act=customSet&id=7", "POST", mapOf("enabled" to "0"))
        val command = paymentBatchOptions("proxies", listOf(PaymentBatchRow("7", "代理 #7", listOf(action)))).single().commands
        assertEquals(BatchOutcomeStatus.SUCCESS, repo.runBatch(command).single().status)
        assertEquals("POST", sent!!.method); assertEquals("7", sent!!.url.queryParameter("id")); assertEquals(mapOf("enabled" to "0"), fields(sent!!))
    }
}
