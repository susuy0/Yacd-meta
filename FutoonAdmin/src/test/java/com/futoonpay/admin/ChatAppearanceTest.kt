package com.futoonpay.admin

import android.app.Application
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.futoonpay.admin.appearance.ChatLook
import com.futoonpay.admin.appearance.ChatFilterBar
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class, qualifiers="w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatAppearanceTest {
    private val context get()=androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
    @Test fun lightAndDarkMessageTextRemainReadableOnBothBubbleColors() {
        for(dark in listOf(false,true)) for(bg in listOf(ChatLook.incoming(dark),ChatLook.outgoing(dark)))
            assertTrue("text contrast $dark",ColorUtils.calculateContrast(ChatLook.text(dark),bg)>=4.5)
    }
    @Test fun headersAndSearchRemainReadableInBothModes() {
        for(dark in listOf(false,true)) for(bg in listOf(ChatLook.surface(dark),ChatLook.field(dark))) {
            assertTrue(ColorUtils.calculateContrast(ChatLook.text(dark),bg)>=4.5)
            assertTrue(ColorUtils.calculateContrast(ChatLook.secondary(dark),bg)>=3.0)
        }
    }
    @Test fun semanticErrorAndFinancialStatusColorsAreNeverReplacedByAppearance() {
        for(key in listOf("key_text_RedRegular","key_dialogTextRed","key_text_RedBold","key_chat_mediaTimeText","key_statisticChartLine_orange"))
            for(dark in listOf(false,true))assertNull(key,ChatLook.color(key,dark))
    }
    @Test fun gradientsAreDisabledWhileSelectedAndUnreadStatesRemainDistinct() {
        for(dark in listOf(false,true)){
            assertEquals(0,ChatLook.color("key_chat_outBubbleGradient1",dark));assertEquals(0,ChatLook.color("key_chat_outBubbleGradientAnimated",dark))
            assertNotEquals(ChatLook.color("key_chat_outBubble",dark),ChatLook.color("key_chat_outBubbleSelected",dark))
            assertNotEquals(ChatLook.color("key_chat_outSentCheck",dark),ChatLook.color("key_chat_outSentCheckRead",dark))
        }
    }
    @Test fun bubbleTailsStayAtTheTopAndJoinedBubblesRemoveTheirTail() {
        for(out in listOf(false,true)){
            val body=RectF(12f,4f,200f,70f);val path=Path();val bounds=RectF()
            ChatLook.bubblePath(path,body,10f,out,false,false);path.computeBounds(bounds,true)
            if(out)assertTrue(bounds.right>body.right)else assertTrue(bounds.left<body.left)
            assertEquals(body.top,bounds.top,.01f);assertEquals(body.bottom,bounds.bottom,.01f)
            ChatLook.bubblePath(path,body,10f,out,true,true);path.computeBounds(bounds,true);assertEquals(body,bounds)
            ChatLook.bubblePath(path,RectF(),10f,out,false,false);assertTrue(path.isEmpty)
        }
    }
    private fun render(drawable:Drawable,alpha:Int=255):Bitmap {
        val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);drawable.setBounds(8,8,56,56);drawable.alpha=alpha;drawable.draw(Canvas(bitmap));return bitmap
    }
    @Test fun vectorIconsRespectBoundsAndAlphaForEveryAction() {
        for(name in listOf("chat","chat_selected","compose","profile","profile_selected","settings","settings_selected","search","menu","send","clip","emoji","back")){
            val d=ChatLook.icon(name,Color.BLACK,2f);val bitmap=render(d)
            assertEquals(48,d.intrinsicWidth);assertTrue((8..55).any{x->(8..55).any{y->Color.alpha(bitmap.getPixel(x,y))>0}})
            assertEquals(0,bitmap.getPixel(0,0));val hidden=render(d,0);assertTrue((0..63).all{x->(0..63).all{y->Color.alpha(hidden.getPixel(x,y))==0}})
            bitmap.recycle();hidden.recycle()
        }
    }
    @Test fun wallpaperIsBoundedAndTransparentWhenHidden() {
        for(dark in listOf(false,true)){
            val paper=ChatLook.wallpaperDrawable(dark,1f);val bitmap=render(paper);assertEquals(ChatLook.wallpaper(dark),bitmap.getPixel(9,9));assertEquals(0,bitmap.getPixel(0,0))
            val hidden=render(paper,0);assertEquals(0,hidden.getPixel(32,32));bitmap.recycle();hidden.recycle()
        }
    }
    private fun buttons(view:View):List<TextView> =(if(view is TextView)listOf(view)else emptyList())+(if(view is ViewGroup)(0 until view.childCount).flatMap{buttons(view.getChildAt(it))}else emptyList())
    @Test fun realNativeFilterButtonsKeepRoutingSelectionAndTouchTargets() {
        val calls=mutableListOf<Int>();val bar=ChatFilterBar(context,arrayOf("全部","未读","群组"),false){calls+=it}
        bar.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(48,View.MeasureSpec.EXACTLY));bar.layout(0,0,320,48)
        val nodes=buttons(bar);assertEquals(3,nodes.size);nodes.forEach{assertTrue(it.width>=48);assertTrue(it.height>=48);assertEquals(16,it.paddingLeft);assertEquals(16,it.paddingRight);assertTrue(it.width>=it.paint.measureText(it.text.toString())+32)}
        nodes[2].performClick();assertEquals(listOf(2),calls);assertEquals(2,bar.selectedFilter());assertTrue(nodes[2].isSelected);assertFalse(nodes[0].isSelected)
        bar.setDark(true);nodes.forEach{assertEquals(16,it.paddingLeft);assertEquals(16,it.paddingRight)};assertEquals(2,bar.selectedFilter());assertEquals(listOf(2),calls);assertEquals(ChatLook.accent(true),nodes[2].currentTextColor)
        bar.select(0);assertEquals(listOf(2),calls)
    }
    @Test fun narrowWideAndRtlAppearanceGeometryRendersWithoutClipping() {
        for(width in listOf(320,390,640))for(dark in listOf(false,true)){
            val bar=ChatFilterBar(context,arrayOf("All","Unread","Groups"),dark){};if(width==640)bar.layoutDirection=View.LAYOUT_DIRECTION_RTL
            bar.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(48,View.MeasureSpec.EXACTLY));bar.layout(0,0,width,48)
            val bitmap=Bitmap.createBitmap(width,300,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);canvas.drawColor(ChatLook.surface(dark));bar.draw(canvas)
            val paper=ChatLook.wallpaperDrawable(dark,1f);paper.setBounds(0,52,width,300);paper.draw(canvas)
            val paint=Paint(3);val path=Path();ChatLook.bubblePath(path,RectF(16f,78f,width-96f,130f),10f,false,false,false);paint.color=ChatLook.incoming(dark);canvas.drawPath(path,paint)
            ChatLook.bubblePath(path,RectF(82f,150f,width-16f,204f),10f,true,false,false);paint.color=ChatLook.outgoing(dark);canvas.drawPath(path,paint)
            val send=ChatLook.icon("send",Color.WHITE,1f);paint.color=ChatLook.accent(dark);canvas.drawCircle(width-30f,265f,24f,paint);send.setBounds(width-42,253,width-18,277);send.draw(canvas)
            java.io.File("build/ui-screenshots/appearance-$width-${if(dark)"dark"else"light"}.png").apply{parentFile.mkdirs()}.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
}
