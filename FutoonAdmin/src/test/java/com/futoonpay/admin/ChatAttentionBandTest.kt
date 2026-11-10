package com.futoonpay.admin

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.*
import com.futoonpay.admin.appearance.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatAttentionBandTest {
    private fun layout(view:View,width:Int,height:Int){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height)}
    private val items=listOf(
        ChatAttentionBand.Item("settlement","待审结算",4,"最早 3 小时前"),
        ChatAttentionBand.Item("domain","待审域名",2,"1 小时前"),
        ChatAttentionBand.Item("mention","@我的",10,"3 个工单"),
        ChatAttentionBand.Item("orders","异常订单",7,"12 分钟前"),
        ChatAttentionBand.Item("none","没有的",0,""))
    private fun header(app:Application,dark:Boolean,list:List<ChatAttentionBand.Item>):ChatInboxHeader{
        val header=ChatInboxHeader(app);val editor=EditText(app).apply{setText("商户名、PID、订单号");setSingleLine(true);background=null}
        val search=ImageView(app);val close=ImageView(app);val extras=LinearLayout(app);val tokens=LinearLayout(app)
        val filter=ChatFilterBar(app,arrayOf("全部","商户 86","工单 14","通道群 9"),dark){}
        listOf(editor,search,close,extras,tokens,filter).forEach{header.addView(it,FrameLayout.LayoutParams(if(it==editor||it==filter)-1 else 24,48))}
        header.bindHome("收件箱",filter,editor,search,close,extras,tokens)
        val band=header.ensureAttention(dark);band.setItems(list)
        return header
    }
    @Test fun bandSitsBetweenSearchAndFiltersAndNeverOverlapsAtAnyScale(){
        val app=RuntimeEnvironment.getApplication();val config=Configuration(app.resources.configuration)
        try{for(width in listOf(320,360,393,640))for(scale in listOf(1f,1.3f,1.5f,2f)){
            app.resources.updateConfiguration(Configuration(config).apply{fontScale=scale},app.resources.displayMetrics)
            for(list in listOf(items,emptyList())){
                val header=header(app,false,list);val band=header.attentionBand()!!
                layout(header,width,ChatInboxHeader.homeHeight(scale,band))
                val editor=(0 until header.childCount).map{header.getChildAt(it)}.filterIsInstance<EditText>().single()
                val filter=(0 until header.childCount).map{header.getChildAt(it)}.filterIsInstance<ChatFilterBar>().single()
                assertTrue("search above band",editor.bottom<=band.top);assertTrue("band above filters",band.bottom<=filter.top);assertTrue(filter.bottom<=header.height)
                assertEquals(width,band.width)
            }
        }}finally{app.resources.updateConfiguration(config,app.resources.displayMetrics)}
    }
    @Test fun zeroCountsAreDroppedTotalsAddUpAndTheFourthCardIsHalfShown(){
        val app=RuntimeEnvironment.getApplication()
        val band=ChatAttentionBand(app,false);band.setItems(items);layout(band,360,band.heightDp()*1)
        val scroller=(0 until band.childCount).map{band.getChildAt(it)}.filterIsInstance<HorizontalScrollView>().single();val row=scroller.getChildAt(0)as LinearLayout
        assertEquals(4,row.childCount);assertTrue(band.showsCards())
        val total=(0 until band.childCount).map{band.getChildAt(it)}.filterIsInstance<TextView>().first{it.text.toString().startsWith("共")}
        assertEquals("共 23 项",total.text.toString())
        val third=row.getChildAt(2);val fourth=row.getChildAt(3)
        assertTrue("third card fully visible",third.right<=360);assertTrue("fourth card starts inside",fourth.left<360);assertTrue("fourth card is clipped",fourth.right>360)
    }
    @Test fun cardsAreRealButtonsAndReportTheirKey(){
        val app=RuntimeEnvironment.getApplication();val hits=mutableListOf<String>()
        val band=ChatAttentionBand(app,true);band.setOnCard{hits+=it};band.setItems(items);layout(band,393,band.heightDp())
        val scroller=(0 until band.childCount).map{band.getChildAt(it)}.filterIsInstance<HorizontalScrollView>().single();val row=scroller.getChildAt(0)as LinearLayout
        assertTrue((0 until row.childCount).all{row.getChildAt(it).isClickable&&row.getChildAt(it).contentDescription.toString().contains("项")})
        row.getChildAt(0).performClick();row.getChildAt(2).performClick();assertEquals(listOf("settlement","mention"),hits)
    }
    @Test fun unknownCountsShowADashInsteadOfZeroAndEmptyShowsOneReassuringLine(){
        val app=RuntimeEnvironment.getApplication()
        val band=ChatAttentionBand(app,false);band.setItems(listOf(ChatAttentionBand.Item("settlement","待审结算",-1,"")));layout(band,393,band.heightDp())
        val texts=allText(band);assertTrue(texts.contains("—"));assertFalse(texts.contains("0"))
        band.setItems(items.map{ChatAttentionBand.Item(it.key,it.label,0,"")});assertFalse(band.showsCards());assertTrue(allText(band).any{it.contains("都处理完了")})
        assertTrue(band.heightDp()<ChatAttentionBand.height(1f,false))
    }
    @Test fun countsAbbreviateInWanLikeTheExtremeDataSheet(){
        assertEquals("999",ChatAttentionBand.format(999));assertEquals("9999",ChatAttentionBand.format(9999));assertEquals("1万",ChatAttentionBand.format(10000))
        assertEquals("1.2万",ChatAttentionBand.format(12800));assertEquals("17.6万",ChatAttentionBand.format(176285));assertEquals("120万",ChatAttentionBand.format(1200000))
    }
    @Test fun rendersLightAndDarkScreenshots(){
        val app=RuntimeEnvironment.getApplication()
        for(dark in listOf(false,true)){
            val header=header(app,dark,items);val band=header.attentionBand()!!;layout(header,393,ChatInboxHeader.homeHeight(1f,band))
            header.setBackgroundColor(ChatLook.surface(dark));(0 until header.childCount).map{header.getChildAt(it)}.filterIsInstance<TextView>().filter{it.text.toString()=="收件箱"}.forEach{it.setTextColor(ChatLook.text(dark))}
            val b=Bitmap.createBitmap(393,header.height,Bitmap.Config.ARGB_8888);header.draw(Canvas(b))
            File("build/ui-screenshots/inbox-attention-${if(dark)"dark" else "light"}.png").apply{parentFile.mkdirs()}.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
        }
    }
    private fun allText(root:View):List<String> = if(root is TextView) listOf(root.text.toString()) else if(root is android.view.ViewGroup) (0 until root.childCount).flatMap{allText(root.getChildAt(it))} else emptyList()
}
