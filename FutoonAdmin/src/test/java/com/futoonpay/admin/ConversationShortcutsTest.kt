package com.futoonpay.admin

import android.app.Application
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.AppViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okhttp3.FormBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationShortcutsTest {
    private val context get()=androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
    private fun key(topic:Long=1,dialog:Long=-100,account:String="test:772",backend:String="https://fixture.example/admin/")=ChannelScope(account,backend,dialog,topic)
    private fun store()=ChannelShortcutStore(context,"test:772").also{it.prefs.edit().clear().commit()}
    @Before fun setup(){Dispatchers.setMain(UnconfinedTestDispatcher())}
    @After fun cleanup(){Dispatchers.resetMain()}
    @Test fun multipleChannelsBelongToTheGroupAndAreSharedByItsTopics(){
        val store=store();store.replaceChannels(key(),listOf(ChannelLink("12","A"),ChannelLink("13","B"),ChannelLink("12","duplicate")))
        assertEquals(listOf("12","13"),store.channels(key(topic=2)).map{it.id});assertEquals(2,store.channels(key(topic=0)).size)
        assertTrue(store.channels(key(dialog=-200)).isEmpty());assertTrue(store.channels(key(account="other:773")).isEmpty());assertTrue(store.channels(key(backend="https://fixture.example/other/")).isEmpty())
    }
    @Test fun legacyTopicLinksMergeAndAnExplicitEmptySaveDoesNotResurrectThem(){
        val store=store();store.remember(key(),ChannelLink("12","A"));store.remember(key(topic=2),ChannelLink("13","B"))
        assertEquals(2,store.channels(key(topic=99)).size);assertEquals(1,store.associations().size)
        store.replaceChannels(key(),store.channels(key()));assertTrue(store.links().isEmpty())
        store.replaceChannels(key(topic=2),emptyList());assertTrue(store.channels(key()).isEmpty());assertTrue(store.associations().isEmpty())
    }
    @Test fun invalidAssociationCannotReplaceTheExistingSet(){
        val store=store();store.replaceChannels(key(),listOf(ChannelLink("12","A")))
        for(id in listOf("0","-1","12,13","9223372036854775808")){
            try{store.replaceChannels(key(),listOf(ChannelLink(id,"bad")));fail()}catch(_:IllegalArgumentException){}
            assertEquals("12",store.channels(key()).single().id)
        }
    }
    private fun bridge(handler:(String)->String)=object:FixtureBridge(){
        override fun accountScope()="test:772"
        override fun address()="https://fixture.example/admin/"
        override fun hasSession()=true
        override fun call(operation:String,params:String):String{check(operation=="merchant.lookup");val p=JSONObject(params);assertEquals("telegram_id",p.getString("column"));return handler(p.getString("value"))}
    }
    private fun merchant(id:String,uid:String="2806")=JSONObject().put("uid",uid).put("telegram_id",id).put("username","测试商户").toString()
    private fun rows(vararg rows:String)=JSONObject().put("rows",JSONArray(rows.map(::JSONObject))).toString()
    private suspend fun resolved(model:ChannelShortcutModel)=withTimeout(5000){model.state.first{!it.merchantLoading&&(it.merchant!=null||it.merchantError!=null||it.merchantCandidates.isNotEmpty())}}
    @Test fun privateChatUsesTheExactPeerTelegramId()=runBlocking{
        val seen=mutableListOf<String>();val model=ChannelShortcutModel(context,bridge{id->seen+=id;rows(merchant(id))})
        model.store.prefs.edit().clear().commit();model.bind("私信",5824870757,0,"5824870757","私信");model.resume()
        val s=resolved(model);assertEquals("2806",s.merchant?.uid);assertEquals("5824870757",s.merchant?.telegramId);assertEquals(listOf("5824870757"),seen);model.close()
    }
    @Test fun topicSwitchClearsOldMerchantAndRetainsTheGroupsChannelSet()=runBlocking{
        val model=ChannelShortcutModel(context,bridge{id->rows(merchant(id,if(id=="111")"2806" else "2807"))})
        model.store.prefs.edit().clear().commit();model.store.replaceChannels(key(),listOf(ChannelLink("12","A"),ChannelLink("13","B")))
        model.bind("工单 A",-100,1,"111","话题");model.resume();assertEquals("2806",resolved(model).merchant?.uid)
        model.bind("工单 B",-100,2,"222","话题");assertNull(model.state.value.merchant);val s=resolved(model)
        assertEquals("2807",s.merchant?.uid);assertEquals(listOf("12","13"),s.channels.map{it.id});assertNull(s.link);model.close()
    }
    @Test fun delayedResponseFromAnOldTopicCannotPopulateTheNewTopic()=runBlocking{
        val started=CountDownLatch(1);val release=CountDownLatch(1)
        val model=ChannelShortcutModel(context,bridge{id->if(id=="111"){started.countDown();check(release.await(5,TimeUnit.SECONDS))};rows(merchant(id,if(id=="111")"2806" else "2807"))})
        try{model.store.prefs.edit().clear().commit();model.bind("A",-100,1,"111");model.resume();assertTrue(started.await(3,TimeUnit.SECONDS))
            model.bind("B",-100,2,"222");assertEquals("2807",resolved(model).merchant?.uid);release.countDown();delay(100);assertEquals("2807",model.state.value.merchant?.uid)
        }finally{release.countDown();model.close()}
    }
    @Test fun mismatchedTelegramIdCannotEnableMerchantActions()=runBlocking{
        val model=ChannelShortcutModel(context,bridge{rows(merchant("999"))});model.store.prefs.edit().clear().commit();model.bind("A",-100,1,"111");model.resume()
        val s=resolved(model);assertNull(s.merchant);assertTrue(s.merchantError.orEmpty().contains("TGID"));model.close()
    }
    @Test fun multipleExactMatchesRequireAnExplicitChoice()=runBlocking{
        val model=ChannelShortcutModel(context,bridge{id->rows(merchant(id,"2806"),merchant(id,"2807"))});model.store.prefs.edit().clear().commit();model.bind("A",-100,1,"111");model.resume()
        val s=resolved(model);assertNull(s.merchant);assertEquals(2,s.merchantCandidates.size);model.chooseMerchant("999");assertNull(model.state.value.merchant)
        model.chooseMerchant("2807");assertEquals("2807",model.state.value.merchant?.uid);model.close()
    }
    @Test fun missingTelegramIdDoesNotGuessFromTheConversationName(){
        var calls=0;val model=ChannelShortcutModel(context,bridge{calls++;rows()});model.store.prefs.edit().clear().commit();model.bind("商户2806|111",-100,1);model.resume()
        assertEquals(0,calls);assertNull(model.state.value.merchant);model.close()
    }
    @Test fun logoutClearsMerchantAndLateRepliesAreIgnored()=runBlocking{
        var logged=true;val b=object:FixtureBridge(){override fun address()="https://fixture.example/admin/";override fun hasSession()=logged;override fun call(operation:String,params:String)=rows(merchant("111"))}
        val model=ChannelShortcutModel(context,b);model.store.prefs.edit().clear().commit();model.bind("A",111,0,"111");model.resume();resolved(model)
        logged=false;model.reloadSettings();assertNull(model.state.value.merchant);assertFalse(model.state.value.visible);assertTrue(model.state.value.merchantCandidates.isEmpty());model.close()
    }
    private fun params(body:okhttp3.RequestBody?)=(body as FormBody).let{b->(0 until b.size).associate{b.name(it)to b.value(it)}}
    @Test fun allAssociatedOrdersPaginateAcrossChannelBoundariesWithoutUnfilteredRequests()=runBlocking{
        val seen=mutableListOf<Map<String,String>>()
        val repo=FutoonRepository(fixtureClient({request->val p=params(request.body);seen+=p
            val id=p.getValue("channel");val total=if(id=="12")40 else 31;val offset=p.getValue("offset").toInt();val limit=p.getValue("limit").toInt()
            val rows=(offset until minOf(offset+limit,total)).map{JSONObject().put("trade_no","$id-$it").put("channel",id)}
            200 to JSONObject().put("total",total).put("rows",JSONArray(rows)).toString()
        }))
        val ids=linkedSetOf("12","13");val pages=(1..3).map{repo.associatedOrders(it,emptyMap(),ids)}
        assertTrue(pages.all{it.total==71});assertEquals(listOf(30,30,11),pages.map{it.rows.size});assertEquals(71,pages.flatMap{it.rows}.map{it.tradeNo}.distinct().size)
        assertEquals(10,pages[1].rows.count{it.channelId=="12"});assertEquals(20,pages[1].rows.count{it.channelId=="13"});assertTrue(seen.all{it["channel"] in ids})
    }
    @Test fun unrelatedChannelFiltersAndBackendRowsAreRejected()=runBlocking{
        var calls=0;val repo=FutoonRepository(fixtureClient({calls++;200 to """{"total":1,"rows":[{"trade_no":"a","channel":"99"}]}"""}))
        try{repo.associatedOrders(1,mapOf("channel" to "99"),setOf("12"));fail()}catch(_:IllegalArgumentException){};assertEquals(0,calls)
        try{repo.associatedOrders(1,emptyMap(),setOf("12"));fail()}catch(e:IllegalStateException){assertTrue(e.message.orEmpty().contains("其他通道"))}
    }
    @Test fun merchantOrdersAndSettlementsRemainPinnedAfterResetSearchAndPagination()=runBlocking{
        val seen=mutableListOf<Map<String,String>>();val repo=FutoonRepository(fixtureClient({request->seen+=params(request.body);200 to """{"total":0,"rows":[]}"""}))
        val vm=AppViewModel(TestEnvironment(repo),loadOnStart=false,lockedMerchantId="2806")
        vm.loadOrders(1,OrderSearchFilters(merchantId="999")).join();vm.loadOrders(2,OrderSearchFilters()).join();vm.loadSettlements(1,SettlementSearchFilters(merchantId="999")).join();vm.loadSettlements(2,SettlementSearchFilters()).join()
        assertEquals(4,seen.size);assertTrue(seen.all{it["uid"]=="2806"})
    }
    @Test fun domainAuditFiltersAndAddUseTheRecognizedMerchantEvenAfterChangingSearch()=runBlocking{
        val seen=mutableListOf<Pair<String,Map<String,String>>>();val repo=FutoonRepository(fixtureClient({request->seen+=request.url.queryParameter("act").orEmpty() to params(request.body);200 to """{"code":0,"total":0,"rows":[]}"""}))
        val vm=DomainAuditViewModel(repo,"2806",true);withTimeout(5000){vm.state.first{!it.loading}};vm.load(2,merchantId="999").join();vm.add("999","fixture.invalid").join()
        assertTrue(seen.isNotEmpty());assertTrue(seen.all{it.second["uid"]=="2806"});assertEquals("2806",vm.state.value.merchantId);assertTrue(seen.any{it.first=="addDomain"})
    }
}
