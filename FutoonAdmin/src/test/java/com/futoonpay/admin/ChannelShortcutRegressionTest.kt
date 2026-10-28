package com.futoonpay.admin

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.futoonpay.admin.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChannelShortcutRegressionTest {
    @org.junit.Before fun freshMerchantCache(){ChannelShortcutModel.clearMerchantCache()}
    private val context get()=androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
    private fun key(account:String="0:915",backend:String="https://pay.example/admin/",dialog:Long=-100,topic:Long=1)=ChannelScope(account,backend,dialog,topic)
    @Test fun topicAccountEnvironmentAndChatAssociationsAreIsolated(){
        val store=ChannelShortcutStore(context,"0:915");store.prefs.edit().clear().commit()
        store.remember(key(),ChannelLink("12","支付线"))
        assertEquals("12",store.link(key())?.id)
        assertNull(store.link(key(topic=2)));assertNull(store.link(key(topic=0)));assertNull(store.link(key(dialog=-200)))
        assertNull(store.link(key(backend="https://other.example/admin/")));assertNull(store.link(key(account="1:916")))
        assertEquals(1,store.links().size)
    }
    @Test fun hidingAndRemovingOnlyAffectsTheChosenTopic(){
        val store=ChannelShortcutStore(context,"0:915");store.prefs.edit().clear().commit()
        store.remember(key(),ChannelLink("12","A"));store.remember(key(topic=2),ChannelLink("13","B"))
        store.hide(key(),true);assertTrue(store.hidden(key()));assertFalse(store.hidden(key(topic=2)))
        store.remove(key());assertNull(store.link(key()));assertEquals("13",store.link(key(topic=2))?.id)
        store.hide(key(),false);assertTrue(store.hiddenScopes().isEmpty())
    }
    @Test fun settingsAndLinksSurviveRecreationWithoutSerializingTemporarySelections(){
        val store=ChannelShortcutStore(context,"0:915");store.prefs.edit().clear().commit();store.enabled=false;store.window=MonitorWindow.HOUR
        store.remember(key(),ChannelLink("7","测试通道","售后 › 订单"))
        val recreated=ChannelShortcutStore(context,"0:915")
        assertFalse(recreated.enabled);assertEquals(MonitorWindow.HOUR,recreated.window);assertEquals("售后 › 订单",recreated.link(key())?.label)
        assertTrue(recreated.prefs.all.keys.none{it.contains("temporary")})
    }
    @Test fun aTemporaryOrderContextCannotOverwriteAPersistedAssociation(){
        val bridge=FixtureBridge();val model=ChannelShortcutModel(context,bridge)
        model.store.prefs.edit().clear().commit();model.bind("会话",-100,1)
        val key=model.state.value.scope!!;model.store.remember(key,ChannelLink("12","已记住"));model.reloadSettings()
        model.fromOrder("99");assertEquals("12",model.state.value.link?.id);assertEquals("已记住",model.state.value.source)
        model.close()
    }
    @Test fun changingTopicImmediatelyClearsTemporaryDataAndItIsNotRestored(){
        val model=ChannelShortcutModel(context,FixtureBridge());model.store.prefs.edit().clear().commit();model.bind("A",-100,1)
        model.fromOrder("12");assertEquals("12",model.state.value.link?.id);assertEquals("来自订单",model.state.value.source)
        model.bind("B",-100,2);assertNull(model.state.value.link);assertNull(model.state.value.snapshot)
        model.bind("A",-100,1);assertNull(model.state.value.link);model.close()
    }
    @Test fun changingBackendRebindsWithoutReusingTheOtherEnvironmentsChannel(){
        var backend="https://a.example/admin/"
        val bridge=object:FixtureBridge(){override fun address()=backend}
        val model=ChannelShortcutModel(context,bridge);model.store.prefs.edit().clear().commit();model.bind("A",-100,1)
        val original=model.state.value.scope!!;model.store.remember(original,ChannelLink("12","A线"));model.reloadSettings();assertEquals("12",model.state.value.link?.id)
        backend="https://b.example/admin/";model.bind("A",-100,1);assertNull(model.state.value.link);assertNull(model.state.value.snapshot)
        backend="https://a.example/admin/";model.bind("A",-100,1);assertEquals("12",model.state.value.link?.id);model.close()
    }
    @Test fun loginAndLogoutRefreshVisibilityWithoutRecreatingTheConversation(){
        var logged=false
        val bridge=object:FixtureBridge(){override fun address()="https://fixture.example/admin/";override fun hasSession()=logged}
        val model=ChannelShortcutModel(context,bridge);model.store.prefs.edit().clear().commit();model.bind("A",-100,1);model.resume();assertFalse(model.state.value.visible)
        logged=true;model.reloadSettings();assertTrue(model.state.value.visible)
        logged=false;model.reloadSettings();assertFalse(model.state.value.visible);assertNull(model.state.value.snapshot);model.close()
    }
    @Test fun malformedOrderChannelIdsAreNeverBound(){
        val model=ChannelShortcutModel(context,FixtureBridge());model.store.prefs.edit().clear().commit();model.bind("A",-100,1)
        listOf("", "0", "-1", "12foo", "1&status=0").forEach{model.fromOrder(it);assertNull(model.state.value.link)};model.close()
    }
    @Test fun zeroSuccessEmptyAndUnknownAreThreeDifferentStates(){
        val zero=parseChannelSnapshot(JSONObject("""{"total_count_5min":12,"success_count_5min":0,"success_rate_5min":"0%"}"""),MonitorWindow.FIVE_MINUTES)
        val empty=parseChannelSnapshot(JSONObject("""{"total_count_5min":0,"success_count_5min":0,"success_rate_5min":"100%"}"""),MonitorWindow.FIVE_MINUTES)
        val unknown=parseChannelSnapshot(JSONObject(),MonitorWindow.FIVE_MINUTES)
        assertEquals("0.0%",zero.rateText);assertEquals("暂无订单",empty.rateText);assertEquals("暂未提供",unknown.rateText)
    }
    @Test fun windowsNeverBorrowCountsOrRatesFromFiveMinutes(){
        val raw=JSONObject("""{"success_rate_5min":"90%","total_count_5min":10,"success_count_5min":9,"success_rate_1hour":"80%"}""")
        val hour=parseChannelSnapshot(raw,MonitorWindow.HOUR);assertEquals(80.0,hour.rate!!,.001);assertNull(hour.total);assertNull(hour.successful)
        val six=parseChannelSnapshot(raw,MonitorWindow.SIX_HOURS);assertNull(six.rate);assertNull(six.total)
    }
    @Test fun sixHoursUsesOnlyItsOwnFieldsWhenBackendProvidesThem(){
        val data=parseChannelSnapshot(JSONObject("""{"success_rate_6hour":"88.5%","total_count_6hour":200,"success_count_6hour":177}"""),MonitorWindow.SIX_HOURS)
        assertEquals(88.5,data.rate!!,.001);assertEquals(200,data.total);assertEquals(177,data.successful)
    }
    @Test fun invalidRatesAndContradictoryCountsAreNotRenderedAsValidStatistics(){
        for(value in listOf("-1%","101%","NaN","Infinity","n/a")){assertNull(parseChannelSnapshot(JSONObject().put("rate_5min",value),MonitorWindow.FIVE_MINUTES).rate)}
        val data=parseChannelSnapshot(JSONObject("""{"total_count_5min":4,"success_count_5min":8}"""),MonitorWindow.FIVE_MINUTES);assertNull(data.successful)
    }
    @Test fun actualComposerInsetsReserveExactlyOneBarAndPreserveBottomControls(){
        for(width in listOf(320,390,844)) for(height in listOf(0,49,76,120)) {
            val composer=FrameLayout(context)
            val input=View(context);composer.addView(input,FrameLayout.LayoutParams(-1,44,android.view.Gravity.BOTTOM))
            val bar=View(context);ChannelComposerHost.attach(composer,bar,0);ChannelComposerHost.resize(composer,bar,height)
            composer.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.AT_MOST))
            composer.layout(0,0,composer.measuredWidth,composer.measuredHeight)
            assertEquals(height+44,composer.measuredHeight);assertEquals(0,bar.top);assertEquals(height,bar.bottom);assertEquals(height,input.top);assertEquals(height+44,input.bottom)
            ChannelComposerHost.resize(composer,bar,0);composer.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.AT_MOST));assertEquals(44,composer.measuredHeight)
        }
    }
    @Test fun replyTranslationDoesNotApplyBarPaddingTwice(){
        for(bar in listOf(0,49,76,120)) for(progress in listOf(0f,.5f,1f)) {
            val total=bar+44+48
            val actualTop=bar+ChannelComposerHost.replyTranslation(total,44f,48,progress,bar)
            assertEquals((total-44-48*progress),actualTop,.001f)
        }
    }
    @Test fun shortcutSheetOwnsItsLifecycleBeforeOwnerlessWindowPremeasurement(){
        val controller=Robolectric.buildActivity(Activity::class.java).setup();val activity=controller.get()
        val model=ChannelShortcutModel(activity,FixtureBridge());model.bind("测试群",-100,1)
        val host=object:ChannelSheetHost{override fun heightChanged(pixels:Int){};override fun showSheet(view:ChannelSheetView,kind:String){};override fun closeSheet(){}}
        val view=ChannelSheetView(activity,model,"stats",host);view.setPanelHeight(560)
        val root=FrameLayout(activity).apply{addView(view)}
        assertNull(root.findViewTreeLifecycleOwner());assertNotNull(view.findViewTreeLifecycleOwner())
        val w=View.MeasureSpec.makeMeasureSpec(780,View.MeasureSpec.AT_MOST);val h=View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.AT_MOST)
        root.measure(w,h);assertTrue((view.getChildAt(0) as ComposeView).hasComposition);assertEquals(560,view.measuredHeight)
        val dialog=Dialog(activity);dialog.setContentView(root);dialog.show();view.onShown();shadowOf(Looper.getMainLooper()).idle()
        assertEquals(Lifecycle.State.RESUMED,view.lifecycle.currentState)
        view.busy=true;assertFalse(view.requestClose());assertFalse(view.discard)
        view.busy=false
        view.dirty=true;assertFalse(view.requestClose());assertTrue(view.discard)
        view.forceClose();assertTrue(view.requestClose());view.close();model.close();dialog.dismiss();shadowOf(Looper.getMainLooper()).idle();controller.pause().stop().destroy();shadowOf(Looper.getMainLooper()).idle()
    }
}
