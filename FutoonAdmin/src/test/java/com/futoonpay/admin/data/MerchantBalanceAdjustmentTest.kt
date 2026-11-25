package com.futoonpay.admin.data

import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class MerchantBalanceAdjustmentTest {
    private fun fields(request: Request): Map<String, String> {
        val form = request.body as FormBody
        return (0 until form.size).associate { form.name(it) to form.value(it) }
    }

    @Test fun increaseUsesCreditCodeAndPositiveAmountAtActualRequestBoundary() = runBlocking {
        var request: Request? = null
        val repository = FutoonRepository(fixtureClient({ request = it; 200 to "{\"code\":0,\"msg\":\"succ\"}" }))
        assertTrue(repository.rechargeMerchant("1011", "add", "12.50").success)
        assertEquals("POST", request!!.method)
        assertEquals("/private-admin/ajax_user.php", request!!.url.encodedPath)
        assertEquals("recharge", request!!.url.queryParameter("act"))
        assertEquals(mapOf("uid" to "1011", "actdo" to "0", "rmb" to "12.50"), fields(request!!))
    }

    @Test fun deductUsesDebitCodeWithoutNegatingAmount() = runBlocking {
        var sent = emptyMap<String, String>()
        val repository = FutoonRepository(fixtureClient({ sent = fields(it); 200 to "{\"code\":0}" }))
        assertTrue(repository.rechargeMerchant("1011", "reduce", "12.50").success)
        assertEquals(mapOf("uid" to "1011", "actdo" to "1", "rmb" to "12.50"), sent)
    }

    @Test fun contractFixtureAppliesIncreaseAndDeductionToSeparateBalances() = runBlocking {
        var balance = "100.00".toBigDecimal()
        val repository = FutoonRepository(fixtureClient({
            val sent = fields(it)
            val amount = sent.getValue("rmb").toBigDecimal()
            balance = when (sent["actdo"]) {
                "0" -> balance + amount
                "1" -> balance - amount
                else -> throw AssertionError("Unexpected operation")
            }
            200 to "{\"code\":0}"
        }))
        repository.rechargeMerchant("1011", "add", "5")
        assertEquals("105.00", balance.toPlainString())
        repository.rechargeMerchant("1011", "reduce", "2.5")
        assertEquals("102.50", balance.toPlainString())
    }

    @Test fun negativeZeroAndMalformedAmountsNeverReachServer() = runBlocking {
        var calls = 0
        val repository = FutoonRepository(fixtureClient({ calls++; 200 to "{\"code\":0}" }))
        listOf("", "0", "0.00", "-1", "+1", "1.001", "1e2", "NaN", "Infinity", "1,000", "1.2.3", ".5", "1.").forEach { input ->
            listOf("add", "reduce").forEach { action -> assertFalse("$action $input", repository.rechargeMerchant("1011", action, input).success) }
        }
        assertEquals(0, calls)
    }

    @Test fun unknownOperationsCannotFallThroughToDeduction() = runBlocking {
        var calls = 0
        val repository = FutoonRepository(fixtureClient({ calls++; 200 to "{\"code\":0}" }))
        listOf("", "0", "1", "ADD", "deduct", "invalid").forEach {
            assertFalse(repository.rechargeMerchant("1011", it, "5").success)
        }
        assertEquals(0, calls)
    }

    @Test fun invalidMerchantIdsCannotMutateAnotherAccount() = runBlocking {
        var calls = 0
        val repository = FutoonRepository(fixtureClient({ calls++; 200 to "{\"code\":0}" }))
        listOf("", "0", "-1", "1011extra", "1011&uid=99").forEach {
            assertFalse(repository.rechargeMerchant(it, "add", "5").success)
        }
        assertEquals(0, calls)
    }

    @Test fun amountIsNormalizedExactlyWithoutBinaryFloatingPoint() {
        assertEquals("0.01", merchantBalanceFields("1011", "add", "0.01")!!["rmb"])
        assertEquals("12.50", merchantBalanceFields("1011", "add", " 0012.5 ")!!["rmb"])
        assertEquals("1000000.99", merchantBalanceFields("1011", "add", "1000000.99")!!["rmb"])
    }

    @Test fun serverRejectionIsNotReportedAsSuccessfulIncrease() = runBlocking {
        val repository = FutoonRepository(fixtureClient({ 200 to "{\"code\":-1,\"msg\":\"权限不足\"}" }))
        val result = repository.rechargeMerchant("1011", "add", "5")
        assertFalse(result.success); assertEquals("权限不足", result.message)
    }

    @Test fun networkFailureDoesNotAutomaticallyRetryTheMoneyMutation() = runBlocking {
        var calls = 0
        val repository = FutoonRepository(fixtureClient({ calls++; throw IOException("connection reset") }))
        try { repository.rechargeMerchant("1011", "add", "5"); fail() } catch (_: IOException) { }
        assertEquals(1, calls)
    }
}
