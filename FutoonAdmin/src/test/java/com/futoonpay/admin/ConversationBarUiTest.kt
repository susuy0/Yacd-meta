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
            for(label in listOf("更多","商户订单","结算","域名审核")){
                val node=texts(view).single{it.text.toString()==label};assertTrue(node.isEnabled);assertTrue(node.width>=48);assertTrue(node.height>=48);assertTrue(node.paint.measureText(label)<=node.width)
            }
            assertTrue(texts(view).any{it.contentDescription.toString().contains("商户ID 2806")});assertFalse(texts(view).any{it.text.toString()=="选择支付通道"})
            screenshot(view,"merchant-bar-$width",width,76);view.close()
        }
    }
    @Test fun merchantAndMultipleChannelsRemainSeparateAndAllActionsStayVisible(){
        val view=NativeChannelBarView(context,FixtureBridge(),host());view.render(state(-100).copy(channels=listOf(ChannelLink("12","支付宝"),ChannelLink("13","微信"))));render(view,390,151)
        val labels=texts(view).map{it.text.toString()};for(label in listOf("更多","商户订单","结算","域名审核","通道订单","编辑通道"))assertTrue(label,labels.contains(label))
        assertTrue(labels.any{it.contains("全部关联通道（2）")});screenshot(view,"merchant-multi-channel-bar",390,151);view.close()
    }
    @Test fun aMerchantTopicWithNoAssociationsStillOffersGroupChannelManagement(){
        val view=NativeChannelBarView(context,FixtureBridge(),host());view.render(state(-100));render(view,320,124)
        val node=texts(view).single{it.text.toString()=="选择支付通道"};assertTrue(node.isClickable);assertTrue(node.height>=48);view.close()
    }
    @Test fun merchantMoreUsesExistingPanelAndOtherButtonsKeepDistinctNativeSheets(){
        val opened=mutableListOf<String>()
        val calls=mutableListOf<Pair<String,JSONObject>>()
        val bridge=object:FixtureBridge(){override fun accountScope()="test:772";override fun address()="https://fixture.example/admin/";override fun hasSession()=true;override fun call(operation:String,params:String):String{if(operation=="ui.channel.open"){calls+=operation to JSONObject(params);return "{}"};assertEquals("merchant.lookup",operation);return """{"rows":[{"uid":"2806","telegram_id":"5824870757","username":"诺诺"}]}"""}}
        val view=NativeChannelBarView(context,bridge,object:ChannelSheetHost{override fun heightChanged(pixels:Int){};override fun showSheet(view:ChannelSheetView,kind:String){opened+=kind};override fun closeSheet(){}})
        try{view.model.store.prefs.edit().clear().commit();view.bindChat("私信",5824870757,0,"5824870757","私信");view.onShown();val deadline=System.nanoTime()+5_000_000_000L
            while(view.model.state.value.merchant==null&&System.nanoTime()<deadline){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(5)}
            assertNotNull(view.model.state.value.merchant)
            texts(view).single{it.text.toString()=="更多"}.performClick()
            assertTrue(opened.isEmpty());assertEquals(1,calls.size)
            val target=calls.single().second;assertEquals("merchant-more",target.getString("kind"));assertEquals("2806",target.getString("id"));assertEquals(5824870757L,target.getLong("dialog"));assertEquals(0L,target.getLong("topic"))
            val expected=linkedMapOf("商户订单" to "merchant-orders","结算" to "merchant-settlements","域名审核" to "merchant-domains")
            for((label,kind)in expected){texts(view).single{it.text.toString()==label}.performClick();assertEquals(kind,opened.last());view.sheetDismissed()}
            assertEquals(3,opened.distinct().size)
        }finally{view.close();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()}
    }
}
