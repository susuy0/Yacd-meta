package com.futoonpay.admin

import android.app.Application
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.AppViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.FormBody
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
    @Test fun mismatchedChannelIdentityCannotEnableEditing()=runBlocking{
        val repo=FutoonRepository(fixtureClient({200 to """{"code":0,"data":{"id":"99","name":"Other"}}"""}))
        try{repo.shortcutChannel("12");fail("Must reject another channel")}catch(_:ChannelUnavailableException){}
    }
    @Test fun failedStatisticsAreErrorsRatherThanZeroOrEmptyResults()=runBlocking{
        val repo=FutoonRepository(fixtureClient({200 to """{"code":-1,"msg":"monitor unavailable"}"""}))
        try{repo.shortcutSnapshot("12",MonitorWindow.FIVE_MINUTES);fail("Must surface a monitor error")}catch(e:IllegalStateException){assertTrue(e.message.orEmpty().contains("unavailable"))}
    }
}
