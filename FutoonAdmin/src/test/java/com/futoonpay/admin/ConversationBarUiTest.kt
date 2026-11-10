package com.futoonpay.admin

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.futoonpay.admin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationBarUiTest {
    private val context get()=androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
    private val merchant get()=JSONObject("""{"uid":"2806","username":"诺诺永久在𝕏","telegram_id":"5824870757"}""").toMerchantSummary()
    private fun state(dialog:Long=5824870757)=ChannelShortcutState(scope=ChannelScope("test:772","https://fixture.example/admin/",dialog,if(dialog<0)12627 else 0),label="当前会话",visible=true,telegramId="5824870757",merchant=merchant)
    private fun texts(view:View):List<TextView> =(if(view is TextView)listOf(view)else emptyList())+(if(view is ViewGroup)(0 until view.childCount).flatMap{texts(view.getChildAt(it))}else emptyList())
    private fun host()=object:ChannelSheetHost{override fun heightChanged(pixels:Int){};override fun showSheet(view:ChannelSheetView,kind:String){};override fun closeSheet(){}}
    private fun render(view:NativeChannelBarView,width:Int,height:Int){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height)}
    private fun screenshot(view:View,name:String,width:Int,height:Int){val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap));java.io.File("build/ui-screenshots/$name.png").apply{parentFile.mkdirs()}.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    @Test fun merchantActionsHaveFullTouchTargetsOnNarrowAndRegularScreens(){
        for(width in listOf(320,390)){
            val view=NativeChannelBarView(context,FixtureBridge(),host());view.render(state());render(view,width,76)
            for(label in listOf("订单","面板")){
                val node=texts(view).single{it.text.toString()==label};assertTrue(node.isEnabled);assertTrue(node.width>=48);assertTrue(node.height>=48);assertTrue(node.paint.measureText(label)<=node.width)
            }
            assertTrue(texts(view).any{it.contentDescription.toString().contains("商户ID 2806")&&it.isClickable&&it.height>=48});assertFalse(texts(view).any{it.text.toString()=="选择支付通道"})
            assertEquals("one 48dp row, nothing stacked",49,view.height.let{(0 until view.childCount).sumOf{i->view.getChildAt(i).height}})
            screenshot(view,"merchant-bar-$width",width,76);view.close()
        }
    }
    @Test fun merchantAndMultipleChannelsShareOneRowAndTheMenuKeepsEveryOtherAction(){
        val view=NativeChannelBarView(context,FixtureBridge(),host());view.render(state(-100).copy(channels=listOf(ChannelLink("12","支付宝"),ChannelLink("13","微信")),link=null));render(view,390,49)
        val nodes=texts(view);assertTrue(nodes.any{it.text.toString()=="订单"});assertTrue(nodes.any{it.text.toString()=="面板"})
        assertTrue(nodes.any{it.text.toString().contains("共 2 个通道")});assertEquals(49,(0 until view.childCount).sumOf{view.getChildAt(it).height})
        nodes.single{it.contentDescription=="更多，按钮"}.performClick()
        val menu=texts(view.moreMenu!!).map{it.text.toString()};for(label in listOf("切换支付通道","结算","域名"))assertTrue(label,menu.contains(label))
        screenshot(view,"merchant-multi-channel-bar",390,49);view.close()
    }
    @Test fun linkedChannelShowsRateWithLowFlagAndKeepsItWhenTheLineIsTight(){
        for(width in listOf(320,360,390)){
            val view=NativeChannelBarView(context,FixtureBridge(),host());view.render(state().copy(link=ChannelLink("1042","代收-A2"),snapshot=ChannelSnapshot(MonitorWindow.FIVE_MINUTES,71.4,50,36,7),channels=listOf(ChannelLink("1042","代收-A2"))));render(view,width,49)
            val label=texts(view).single{it.contentDescription.toString().startsWith("当前商户")};assertTrue(label.text.toString().contains("71.4%"));assertTrue(label.text.toString().contains("偏低"));assertTrue(label.height>=48)
            if(width==320)assertFalse("PID goes before the rate",label.text.toString().contains("PID 2806")&&!label.text.toString().contains("代收"))
            val menu=run{texts(view).single{it.contentDescription=="更多，按钮"}.performClick();texts(view.moreMenu!!).map{it.text.toString()}};for(l in listOf("切换支付通道","结算","域名","通道订单","编辑通道"))assertTrue(l,menu.contains(l))
            screenshot(view,"merchant-bar-low-$width",width,49);view.close()
        }
        val ok=NativeChannelBarView(context,FixtureBridge(),host());ok.render(state().copy(link=ChannelLink("1042","代收-A2"),snapshot=ChannelSnapshot(MonitorWindow.FIVE_MINUTES,97.2,50,49,1),channels=listOf(ChannelLink("1042","代收-A2"))));render(ok,390,49)
        assertFalse(texts(ok).single{it.contentDescription.toString().startsWith("当前商户")}.text.toString().contains("偏低"));ok.close()
    }
    @Test fun aMerchantTopicWithNoAssociationsStillOffersGroupChannelManagement(){
        val view=NativeChannelBarView(context,FixtureBridge(),host());view.render(state(-100));render(view,320,124)
        val node=texts(view).single{it.contentDescription.toString().startsWith("选择支付通道，按钮")};assertTrue(node.isClickable);assertTrue(node.height>=48);assertTrue(node.width>=48);view.close()
    }
    @Test fun merchantMoreUsesExistingPanelAndOtherButtonsKeepDistinctNativeSheets(){
        val opened=mutableListOf<String>()
        val calls=mutableListOf<Pair<String,JSONObject>>()
        val bridge=object:FixtureBridge(){override fun accountScope()="test:772";override fun address()="https://fixture.example/admin/";override fun hasSession()=true;override fun call(operation:String,params:String):String{if(operation=="ui.channel.open"){calls+=operation to JSONObject(params);return "{}"};assertEquals("merchant.lookup",operation);return """{"rows":[{"uid":"2806","telegram_id":"5824870757","username":"诺诺"}]}"""}}
        val view=NativeChannelBarView(context,bridge,object:ChannelSheetHost{override fun heightChanged(pixels:Int){};override fun showSheet(view:ChannelSheetView,kind:String){opened+=kind};override fun closeSheet(){}})
        try{view.model.store.prefs.edit().clear().commit();view.bindChat("私信",5824870757,0,"5824870757","私信");view.onShown();val deadline=System.nanoTime()+5_000_000_000L
            while(view.model.state.value.merchant==null&&System.nanoTime()<deadline){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(5)}
            assertNotNull(view.model.state.value.merchant)
            texts(view).single{it.contentDescription.toString().startsWith("当前商户")}.performClick()
            assertTrue(opened.isEmpty());assertEquals(1,calls.size)
            val target=calls.single().second;assertEquals("merchant-more",target.getString("kind"));assertEquals("2806",target.getString("id"));assertEquals(5824870757L,target.getLong("dialog"));assertEquals(0L,target.getLong("topic"))
            texts(view).single{it.text.toString()=="订单"}.performClick();assertEquals("merchant-orders",opened.last());view.sheetDismissed()
            texts(view).single{it.text.toString()=="面板"}.performClick();assertEquals(2,calls.size);assertEquals("merchant-more",calls.last().second.getString("kind"))
            for((label,kind)in linkedMapOf("结算" to "merchant-settlements","域名" to "merchant-domains")){texts(view).single{it.contentDescription=="更多，按钮"}.performClick();texts(view.moreMenu!!).single{it.text.toString()==label}.performClick();assertEquals(kind,opened.last());view.sheetDismissed()}
            assertEquals(3,opened.distinct().size)
        }finally{view.close();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()}
    }
}
