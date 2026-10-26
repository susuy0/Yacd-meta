package com.futoonpay.admin

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.view.View
import android.widget.*
import androidx.core.graphics.ColorUtils
import com.futoonpay.admin.appearance.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosChromeUiTest {
    private fun layout(view:View,width:Int,height:Int){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height)}
    @Test fun actualInboxHeaderKeepsNativeInputAndSingleFilterRowClearAtEveryFontScale(){
        val app=RuntimeEnvironment.getApplication();val config=Configuration(app.resources.configuration)
        try{for(width in listOf(280,320,393,640))for(scale in listOf(1f,1.5f,2f)){
            app.resources.updateConfiguration(Configuration(config).apply{fontScale=scale},app.resources.displayMetrics)
            val header=ChatInboxHeader(app);var selected=-1
            val editor=EditText(app).apply{setText("订单 S202610250018");setTextSize(17f);setSingleLine(true);background=null}
            val search=ImageView(app);val close=ImageView(app);val extras=LinearLayout(app);val tokens=LinearLayout(app)
            val filter=ChatFilterBar(app,arrayOf("全部","未读","群组","列表 ⌄"),false){selected=it}
            listOf(editor,search,close,extras,tokens,filter).forEach{header.addView(it,FrameLayout.LayoutParams(if(it==editor||it==filter)-1 else 24,48))}
            header.bindHome("Futoon chat",filter,editor,search,close,extras,tokens)
            layout(header,width,ChatInboxHeader.homeHeight(scale))
            val title=(0 until header.childCount).map{header.getChildAt(it)}.filterIsInstance<TextView>().single{it.text.toString()=="Futoon chat"}
            assertTrue(title.bottom<=editor.top);assertTrue(editor.bottom<=filter.top);assertTrue(filter.bottom<=header.height)
            assertEquals(editor.top,header.searchTop());assertEquals(editor.bottom,header.searchBottom())
            assertEquals("订单 S202610250018",editor.text.toString());assertTrue(editor.width>100)
            val row=filter.getChildAt(0)as LinearLayout;assertEquals(4,row.childCount);row.getChildAt(2).performClick();assertEquals(2,selected)
            assertTrue((0 until row.childCount).all{row.getChildAt(it).height>=48 && row.getChildAt(it).height<=filter.height})
        }}finally{app.resources.updateConfiguration(config,app.resources.displayMetrics)}
    }
    @Test fun realSettingsIntroOpensOnlyItsOriginalSearchActionAndSupportsDark(){
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get();var calls=0
        val view=ChatSettingsIntro(activity,"设置","搜索设置"){calls++};activity.setContentView(view)
        view.measure(View.MeasureSpec.makeMeasureSpec(393,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));view.layout(0,0,393,view.measuredHeight)
        val title=view.getChildAt(0)as TextView;val search=view.getChildAt(1)as TextView
        assertTrue(title.bottom<search.top);assertTrue(search.height>=36);search.performClick();assertEquals(1,calls)
        view.setDark(true);assertEquals(ChatLook.text(true),title.currentTextColor);view.setDark(false)
        val b=Bitmap.createBitmap(393,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(b));File("build/ui-screenshots/ios-settings-large-title.png").apply{parentFile.mkdirs()}.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle();activity.finish()
    }
    @Test fun referenceBottomTailsNeverIntrudeIntoTheTopOfAnIncomingOrOutgoingGroup(){
        for(out in listOf(false,true)){
            val body=RectF(20f,10f,200f,80f);val p=Path();val clip=Region(0,0,240,100)
            ChatLook.bubblePath(p,body,12f,out,false,false);val region=Region().apply{setPath(p,clip)};val x=if(out)203 else 17
            assertFalse(region.contains(x,11));assertTrue(region.contains(x,79))
            ChatLook.bubblePath(p,body,12f,out,false,true);val joined=Region().apply{setPath(p,clip)};assertFalse(joined.contains(x,79))
        }
    }
    @Test fun iosSelectionUsesReadableNeutralIconsAndGreenCounters(){
        for(dark in listOf(false,true)){
            assertEquals(ChatLook.text(dark),ChatLook.color("key_glass_tabSelected",dark))
            assertTrue(ColorUtils.calculateContrast(ChatLook.color("key_chat_botKeyboardButtonText",dark)!!,ChatLook.color("key_chat_botKeyboardButtonBackground",dark)!!)>=4.5)
            assertNotEquals(ChatLook.color("key_chat_outSentCheck",dark),ChatLook.color("key_chat_outSentCheckRead",dark))
        }
        assertEquals(56,ChatLook.NAV_HEIGHT);assertEquals(12,ChatLook.BUBBLE_RADIUS)
    }
}
