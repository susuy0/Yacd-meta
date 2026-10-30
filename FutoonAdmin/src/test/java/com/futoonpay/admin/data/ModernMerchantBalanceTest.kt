package com.futoonpay.admin.data

import com.futoonpay.admin.PanelBusinessEngine
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

internal fun balanceFixture(request: String = "a".repeat(32), token: String = "fixture-token") = JSONObject("""{"code":0,"balance":{"money":"100.00","available":"70.00","freeze_money":"30.00","reserved":"20.00","reservation_shortfall":"0","freezable":"70.00","releasable":"10.00","reservation_consistent":true,"applications_more":false,"applications":[{"id":"42","amount":"3","amount_unit":"USDT","release_amount":"0","status_label":"待确认","chain_label":"TRON","created_at":"2026-10-30 12:00:00","version":"${"c".repeat(64)}","can_cancel":true},{"id":"43","amount":"20","amount_unit":"CNY","release_amount":"20","status_label":"处理中","chain_label":"TRON","created_at":"","version":"${"d".repeat(64)}","can_cancel":false,"blocked_reason":"正在处理"}]}}""").put("token", token).put("request_id", request).toString()
internal fun balanceClient(handler: (Request) -> String): FutoonApiClient {
    val transport=OkHttpClient.Builder().addInterceptor { c -> Response.Builder().request(c.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture").body(handler(c.request()).toResponseBody("application/json".toMediaType())).build() }.build()
    return FutoonApiClient(TestCookies(), "https://fixture.example/svc_aabbcc/".toHttpUrl(), transport=transport)
}
internal fun balanceFields(r: Request): Map<String,String> {
    val buffer=okio.Buffer();r.body!!.writeTo(buffer)
    return buffer.readUtf8().split("&").associate { part->val kv=part.split("=",limit=2);java.net.URLDecoder.decode(kv[0],"UTF-8") to java.net.URLDecoder.decode(kv.getOrElse(1){""},"UTF-8") }
}

class ModernMerchantBalanceTest {
    @Test fun openingReadsOfficialSnapshotAndOldBareMutationIsBlocked()=runBlocking {
        val calls=mutableListOf<Request>();val repo=FutoonRepository(balanceClient { calls+=it;balanceFixture() })
        val s=repo.merchantBalanceInfo("18")
        assertEquals("70.00",s.info.available);assertEquals("30.00",s.info.frozen)
        assertEquals("balance_info",calls.single().url.queryParameter("act"));assertEquals("18",calls.single().url.queryParameter("uid"))
        assertEquals("/svc_aabbcc/ajax_user",calls.single().url.encodedPath)
        assertFalse(repo.rechargeMerchant("18","add","5").success);assertEquals(1,calls.size)
    }
    @Test fun allFourAdjustmentsSendOfficialReasonTokenAndIdWithExactDecimals()=runBlocking {
        val writes=mutableListOf<Request>()
        val repo=FutoonRepository(balanceClient { if(it.method=="GET")balanceFixture() else {writes+=it;"{\"code\":0}"} })
        for(op in ModernBalanceOperation.entries.filter {it!=ModernBalanceOperation.CANCEL_WITHDRAWAL}) {
            val s=repo.merchantBalanceInfo("18")
            val amount=if(op.scale==6)"0.123456" else "1.25"
            assertTrue(repo.adjustMerchantBalance(s,BalanceCommand(op,amount,"人工核对原因")).success)
            assertEquals(mapOf("uid" to "18","actdo" to op.code,"rmb" to amount,"note" to "人工核对原因","request_id" to "a".repeat(32),"token" to "fixture-token"),balanceFields(writes.last()))
            assertTrue(writes.last().body!!.isOneShot())
        }
    }
    @Test fun limitsPrecisionReasonAndBlockedApplicationNeverMutate()=runBlocking {
        var writes=0;val repo=FutoonRepository(balanceClient { if(it.method=="POST")writes++;balanceFixture() });val s=repo.merchantBalanceInfo("18")
        for(c in listOf(BalanceCommand(ModernBalanceOperation.DEDUCT,"70.01","测试原因"),BalanceCommand(ModernBalanceOperation.UNFREEZE,"10.000001","测试原因"),BalanceCommand(ModernBalanceOperation.FREEZE,"1.0000001","测试原因"),BalanceCommand(ModernBalanceOperation.ADD,"01","测试原因"),BalanceCommand(ModernBalanceOperation.ADD,"100000000","测试原因"),BalanceCommand(ModernBalanceOperation.ADD,"1","短"),BalanceCommand(ModernBalanceOperation.ADD,"1","字".repeat(201)),BalanceCommand(ModernBalanceOperation.CANCEL_WITHDRAWAL,"0","测试原因","43")))assertFalse(repo.adjustMerchantBalance(s,c).success)
        assertEquals(0,writes)
        assertNull(balanceCommandError(s.info,BalanceCommand(ModernBalanceOperation.ADD,"1","😀😀😀")))
    }
    @Test fun cancelUsesSelectedApplicationVersionAndServerReleaseEvenWhenZero()=runBlocking {
        var r:Request?=null;val repo=FutoonRepository(balanceClient { if(it.method=="GET")balanceFixture() else {r=it;"{\"code\":0}"} })
        val s=repo.merchantBalanceInfo("18")
        assertTrue(repo.adjustMerchantBalance(s,BalanceCommand(ModernBalanceOperation.CANCEL_WITHDRAWAL,"999","核对并取消","42")).success)
        assertEquals("withdrawal_cancel",r!!.url.queryParameter("act"));val f=balanceFields(r!!)
        assertEquals("0",f["rmb"]);assertEquals("42",f["application_id"]);assertEquals("c".repeat(64),f["application_version"])
    }
    @Test fun lostResponseSurvivesRepositoryRecreationAndOnlyExplicitOriginalRetryIsAllowed()=runBlocking {
        val store=MemoryBalancePendingStore();val writes=mutableListOf<Map<String,String>>()
        var generation=0
        val client=balanceClient { if(it.method=="GET")balanceFixture(if(++generation==1)"a".repeat(32) else "b".repeat(32),"token-$generation") else {writes+=balanceFields(it);if(writes.size==1)throw IOException("lost reply");"{\"code\":0}"} }
        val first=FutoonRepository(client,store);val s=first.merchantBalanceInfo("18")
        assertTrue(first.adjustMerchantBalance(s,BalanceCommand(ModernBalanceOperation.ADD,"5.00","原始原因")).uncertain)
        assertEquals(1,writes.size);assertFalse(store.read("18").contains("token"))
        val next=FutoonRepository(client,store);val reopened=next.merchantBalanceInfo("18")
        assertEquals("5.00",reopened.pending!!.command().amount)
        assertTrue(next.adjustMerchantBalance(reopened,BalanceCommand(ModernBalanceOperation.DEDUCT,"2","不应发送")).uncertain)
        assertEquals(1,writes.size)
        assertTrue(next.adjustMerchantBalance(reopened,reopened.pending!!.command(),retry=true).success)
        assertEquals(writes[0]-"token",writes[1]-"token");assertNotEquals(writes[0]["token"],writes[1]["token"])
        assertEquals("",store.read("18"))
    }
    @Test fun unknownResponseKeepsPendingWhileExplicitRejectionClearsIt()=runBlocking {
        val store=MemoryBalancePendingStore();var code=1
        val repo=FutoonRepository(balanceClient {if(it.method=="GET")balanceFixture() else "{\"code\":$code}"},store)
        val s=repo.merchantBalanceInfo("18");assertTrue(repo.adjustMerchantBalance(s,BalanceCommand(ModernBalanceOperation.ADD,"5","测试原因")).uncertain)
        assertTrue(store.read("18").isNotEmpty());code=-1
        val opened=repo.merchantBalanceInfo("18");assertFalse(repo.adjustMerchantBalance(opened,opened.pending!!.command(),true).success)
        assertEquals("",store.read("18"))
    }
    @Test fun storageFailureAbortsBeforePostAndCorruptPendingCannotBeSilentlyDiscarded()=runBlocking {
        var posts=0
        val client=balanceClient {if(it.method=="POST")posts++;balanceFixture()}
        val store=object:BalancePendingStore{override fun read(uid:String)="";override fun write(uid:String,value:String){throw IOException("disk unavailable")}}
        val repo=FutoonRepository(client,store);val s=repo.merchantBalanceInfo("18")
        try{repo.adjustMerchantBalance(s,BalanceCommand(ModernBalanceOperation.ADD,"5","测试原因"));fail()}catch(_:IOException){}
        assertEquals(0,posts)
        val corrupt=object:BalancePendingStore{override fun read(uid:String)="invalid";override fun write(uid:String,value:String){fail("must not clear")}}
        try{FutoonRepository(client,corrupt).merchantBalanceInfo("18");fail()}catch(_:Exception){}
    }
    @Test fun telegramBridgeRequiresSnapshotTicketAndPreservesPendingIdentity() {
        val posts=mutableListOf<Request>();val engine=PanelBusinessEngine(balanceClient {if(it.method=="GET")balanceFixture() else {posts+=it;"{\"code\":0}"}})
        val p=JSONObject().put("uid","18").put("currency","CNY").put("direction","add").put("amount","5").put("note","人工核对")
        try{engine.execute("merchant.balance",p);fail()}catch(_:Exception){}
        assertTrue(posts.isEmpty())
        val info=engine.execute("merchant.balance.info",JSONObject().put("uid","18"))
        assertFalse(info.has("token"));assertFalse(info.has("request_id"))
        p.put("ticket",info.getString("ticket"));assertEquals(0,engine.execute("merchant.balance",p).getInt("code"))
        try{engine.execute("merchant.balance",p);fail()}catch(_:Exception){}
        assertEquals(1,posts.size)
    }
}
