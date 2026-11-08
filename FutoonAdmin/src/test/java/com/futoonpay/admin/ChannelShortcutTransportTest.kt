package com.futoonpay.admin

import android.app.Application
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.AppViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.FormBody
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ChannelShortcutTransportTest {
    @Before fun setup(){Dispatchers.setMain(UnconfinedTestDispatcher())}
    @After fun cleanup(){Dispatchers.resetMain()}
    @Test fun orderSearchResetAndPaginationKeepThePinnedChannel()=runBlocking{
        val filters=mutableListOf<Map<String,String>>()
        val repo=FutoonRepository(fixtureClient({request->
            if(request.url.queryParameter("act")=="orderList"){
                val body=request.body as FormBody;filters+= (0 until body.size).associate{body.name(it) to body.value(it)}
            }
            200 to """{"total":0,"rows":[]}"""
        }))
        val vm=AppViewModel(TestEnvironment(repo),loadOnStart=false,lockedChannelId="12")
        vm.loadOrders(1,OrderSearchFilters()).join();vm.loadOrders(2,OrderSearchFilters(channelId="99",status=1)).join();vm.loadOrders(1,OrderSearchFilters(column="out_trade_no",value="A & #1")).join()
        assertEquals(3,filters.size);assertTrue(filters.all{it["channel"]=="12"});assertEquals("1",filters[1]["dstatus"]);assertEquals("A & #1",filters[2]["value"])
    }
    @Test fun targetedStatisticsNeverUseAnotherChannelsRowOrInventMissingCounts()=runBlocking{
        val repo=FutoonRepository(fixtureClient({request->
            val body=request.body as FormBody;val params=(0 until body.size).associate{body.name(it) to body.value(it)}
            assertEquals("channelMonitor",request.url.queryParameter("act"));assertEquals("12",params["channel_id"])
            200 to """{"code":0,"data":[{"id":"99","success_rate_5min":"100%"},{"id":"12","success_rate_1hour":"80%","total_count_5min":10}]}"""
        }))
        val hour=repo.shortcutSnapshot("12",MonitorWindow.HOUR);assertEquals(80.0,hour.rate!!,.001);assertNull(hour.total)
        val five=repo.shortcutSnapshot("12",MonitorWindow.FIVE_MINUTES);assertNull(five.rate);assertEquals(10,five.total)
    }
    @Test fun oneMonitorRequestGivesEveryWindowAndNeverInventsMissingOnes()=runBlocking{
        var requests=0
        val repo=FutoonRepository(fixtureClient({requests++;200 to """{"code":0,"data":[{"id":"99","success_rate_5min":"100%"},{"id":"12","success_rate_5min":"50%","total_count_5min":4,"success_count_5min":2,"processing_count_5min":1,"success_rate_1hour":"90%","total_count_1hour":40,"success_count_1hour":36,"success_rate_24hour":"97.5%","total_count_24hour":0}]}"""}))
        val all=repo.shortcutWindows("12");assertEquals(1,requests)
        assertEquals(CHANNEL_WINDOWS_OLDEST_FIRST,all.keys.toList())
        assertEquals(50.0,all.getValue(MonitorWindow.FIVE_MINUTES).rate!!,.001);assertEquals(1,all.getValue(MonitorWindow.FIVE_MINUTES).processing);assertEquals(4,all.getValue(MonitorWindow.FIVE_MINUTES).total)
        assertEquals(36,all.getValue(MonitorWindow.HOUR).successful)
        assertNull("no orders means no rate, not 97.5%",all.getValue(MonitorWindow.DAY).rate);assertTrue(all.getValue(MonitorWindow.DAY).empty)
        for(window in listOf(MonitorWindow.HALF_HOUR,MonitorWindow.SIX_HOURS,MonitorWindow.TWELVE_HOURS)){val s=all.getValue(window);assertNull(s.rate);assertNull(s.total)}
        val other=FutoonRepository(fixtureClient({200 to """{"code":0,"data":[{"id":"99","success_rate_5min":"100%"}]}"""})).shortcutWindows("12")
        assertTrue("another channel's row must not be used",other.values.all{it.rate==null&&it.total==null})
    }
    @Test fun curveColumnsFollowTheWindowsAndMissingOnesBreakTheLine(){
        val windows=parseChannelWindows(JSONObject("""{"success_rate_24hour":"98%","total_count_24hour":100,"success_rate_12hour":"96%","total_count_12hour":50,"success_rate_1hour":"80%","total_count_1hour":10,"success_rate_5min":"0%","total_count_5min":3,"success_count_5min":0}"""))
        val points=curvePoints(windows)
        assertEquals(listOf("24小时","12小时","6小时","1小时","30分","5分"),points.map{it.label})
        assertEquals(listOf(98.0,96.0,null,80.0,null,0.0),points.map{it.rate})
        val geometry=curveGeometry(points)
        assertEquals(3,geometry.runs.size);assertEquals(listOf(2,1,1),geometry.runs.map{it.size})
        assertEquals(.98f,geometry.points[0]!!.second,.001f);assertEquals(0f,geometry.points[5]!!.second,.001f);assertNull(geometry.points[2])
        assertEquals(.5f/6,geometry.points[0]!!.first,.001f);assertEquals(5.5f/6,geometry.points[5]!!.first,.001f)
        assertEquals(listOf(3,3,0,2,0,1),points.map{rateColorLevel(it.rate)})
        assertTrue(curvePoints(null).all{it.rate==null&&it.total==null});assertTrue(curveGeometry(curvePoints(null)).runs.isEmpty())
    }
    @Test fun emptyCountColumnsAreHiddenOnlyWhenNoWindowHasARealValue(){
        val ratesOnly=parseChannelWindows(JSONObject("""{"success_rate_5min":"100%","success_rate_1hour":"70.1%"}"""))
        assertEquals(StatColumns(counts=false,processing=false),statColumns(ratesOnly))
        val withTotals=parseChannelWindows(JSONObject("""{"success_rate_5min":"90%","total_count_5min":10,"success_count_5min":9}"""))
        assertEquals(StatColumns(counts=true,processing=false),statColumns(withTotals))
        val withProcessing=parseChannelWindows(JSONObject("""{"success_rate_5min":"90%","processing_count_5min":2}"""))
        assertEquals(StatColumns(counts=false,processing=true),statColumns(withProcessing))
        assertEquals(StatColumns(counts=false,processing=false),statColumns(emptyMap()))
    }
    @Test fun mismatchedChannelIdentityCannotEnableEditing()=runBlocking{
        val repo=FutoonRepository(fixtureClient({200 to """{"code":0,"data":{"id":"99","name":"Other"}}"""}))
        try{repo.shortcutChannel("12");fail("Must reject another channel")}catch(_:ChannelUnavailableException){}
    }
    @Test fun failedStatisticsAreErrorsRatherThanZeroOrEmptyResults()=runBlocking{
        val repo=FutoonRepository(fixtureClient({200 to """{"code":-1,"msg":"monitor unavailable"}"""}))
        try{repo.shortcutSnapshot("12",MonitorWindow.FIVE_MINUTES);fail("Must surface a monitor error")}catch(e:IllegalStateException){assertTrue(e.message.orEmpty().contains("unavailable"))}
    }
}
