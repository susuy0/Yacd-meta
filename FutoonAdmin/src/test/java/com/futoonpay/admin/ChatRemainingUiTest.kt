package com.futoonpay.admin

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.futoonpay.admin.appearance.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class, qualifiers="w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatRemainingUiTest {
    private val base get()=ApplicationProvider.getApplicationContext<Application>()
    private fun layout(v:View,w:Int,h:Int,exact:Boolean=false){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,if(exact)View.MeasureSpec.EXACTLY else View.MeasureSpec.AT_MOST));v.layout(0,0,v.measuredWidth,v.measuredHeight);v.viewTreeObserver.dispatchOnPreDraw()}
    private fun screenshot(v:View,name:String){val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));java.io.File("build/ui-screenshots/$name.png").apply{parentFile?.mkdirs()}.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    @Test fun settingsHeaderPreservesDistinctQrAndProfileActionsAtEveryWidthAndFontScale(){
        val activity=org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup()
        try {
        for(scale in listOf(1f,1.5f,2f))for(width in listOf(280,320,390))for(rtl in listOf(false,true)){
            val context=base.createConfigurationContext(Configuration(base.resources.configuration).apply{fontScale=scale})
            val avatar=TextView(context).apply{text="F";textSize=24f;gravity=android.view.Gravity.CENTER;setTextColor(Color.WHITE);background=android.graphics.drawable.GradientDrawable().apply{shape=android.graphics.drawable.GradientDrawable.OVAL;setColor(ChatLook.accent(false))}};val title=TextView(context).apply{text="Futoon merchant";setTextColor(ChatLook.text(false));setSingleLine();ellipsize=android.text.TextUtils.TruncateAt.END};val subtitle=TextView(context).apply{text="@merchant · Telegram";setTextColor(ChatLook.secondary(false));setSingleLine();ellipsize=android.text.TextUtils.TruncateAt.END};val qr=android.widget.ImageView(context).apply{setImageDrawable(ChatLook.icon("app",ChatLook.text(false),1f));scaleType=android.widget.ImageView.ScaleType.CENTER};var profile=0;var scans=0
            val header=ChatSettingsHeader(context).apply{setBackgroundColor(ChatLook.surface(false));setParts(avatar,title,subtitle,qr);setRtl(rtl);setOnClickListener{profile++}}
            qr.setOnClickListener{scans++};activity.get().setContentView(header);org.robolectric.shadows.ShadowLooper.idleMainLooper();layout(header,width,300)
            assertTrue("Title must have a visible line: ${title.textSize}px ${title.width}x${title.height}",title.width>0 && title.height>0 && title.textSize>0)
            assertTrue("Status must have a visible line: ${subtitle.textSize}px ${subtitle.width}x${subtitle.height}",subtitle.width>0 && subtitle.height>0 && subtitle.textSize>0)
            assertEquals(64,avatar.width);assertEquals(64,avatar.height);assertEquals(56,qr.width);assertTrue(header.height>=104)
            for(child in listOf(avatar,title,subtitle,qr)){assertTrue(child.left>=0);assertTrue(child.right<=width);assertTrue(child.top>=0);assertTrue(child.bottom<=header.height)}
            val geometry="w=$width scale=$scale rtl=$rtl actual=${header.layoutDirection}: avatar=${avatar.left}..${avatar.right}, title=${title.left}..${title.right}, qr=${qr.left}..${qr.right}"
            assertTrue(geometry+" vertical",title.bottom<=subtitle.top);if(rtl){assertTrue(geometry+" avatar",title.right<=avatar.left);assertTrue(geometry+" qr",qr.right<=title.left)}else{assertTrue(geometry+" avatar",avatar.right<=title.left);assertTrue(geometry+" qr",title.right<=qr.left)}
            if(width==390 && scale==1f){val image=Bitmap.createBitmap(header.width,header.height,Bitmap.Config.ARGB_8888);header.draw(Canvas(image));assertTrue("Header name must actually render for rtl=$rtl",(title.left until title.right).any{x->(title.top until title.bottom).any{y->image.getPixel(x,y)!=Color.WHITE}});image.recycle()}
            qr.performClick();assertEquals(1,scans);assertEquals(0,profile);header.performClick();assertEquals(1,profile)
            if(width==390&&scale==1f)screenshot(header,"settings-native-${if(rtl)"rtl"else"ltr"}")
        }
        } finally { activity.pause().stop().destroy() }
    }
    private fun grid(width:Int,scale:Float=1f,rtl:Boolean=false,call:(Int)->Unit={}):ChatAttachmentGrid{
        val context=base.createConfigurationContext(Configuration(base.resources.configuration).apply{fontScale=scale})
        return ChatAttachmentGrid(context,false,(0..8).map{i->ChatAttachmentGrid.Entry("id-$i",if(i==4)"Audio document with a long label" else "Choice $i",ChatLook.icon("file",Color.BLACK,1f),{call(i)})}).apply{setRtl(rtl);layout(this,width,260)}
    }
    @Test fun attachmentChoicesScrollInsteadOfClippingAtNarrowWidthsAndLargeFonts(){
        for(width in listOf(240,304,374,624))for(scale in listOf(1f,1.5f,2f)){
            val g=grid(width,scale);val tiles=g.getChildAt(0) as ViewGroup
            assertTrue(g.columns() in 2..4);assertTrue(g.height<=260);assertTrue(tiles.height>g.height)
            for(i in 0 until tiles.childCount){val item=tiles.getChildAt(i) as ViewGroup;assertTrue(item.left>=0);assertTrue(item.right<=width);assertTrue(item.bottom<=tiles.height);val label=item.getChildAt(1);assertTrue(label.bottom<=item.height)}
            if(scale==1f)screenshot(g,"attachments-native-$width")
        }
    }
    @Test fun attachmentRtlMirrorsCellsWithoutChangingActionIdentity(){
        val calls=mutableListOf<Int>();val g=grid(374,rtl=true){calls+=it};val tiles=g.getChildAt(0)as ViewGroup
        assertTrue(tiles.getChildAt(0).left>tiles.getChildAt(1).left);assertTrue(calls.isEmpty());tiles.getChildAt(4).performClick();tiles.getChildAt(4).performClick();tiles.getChildAt(1).performClick();assertEquals(listOf(4),calls)
    }
    @Test fun attachmentBotLongPressNeverAlsoOpensThePicker(){
        var clicks=0;var holds=0;val g=ChatAttachmentGrid(base,true,listOf(ChatAttachmentGrid.Entry("bot","Bot",ChatLook.icon("app",Color.WHITE,1f),{clicks++},{holds++})));layout(g,304,260);val cell=(g.getChildAt(0)as ViewGroup).getChildAt(0)
        assertTrue(cell.performLongClick());cell.performClick();assertEquals(1,holds);assertEquals(0,clicks)
    }
    @Test fun cancellingAnUnchosenAttachmentMenuDoesNotInvokeAnyCallback(){var calls=0;val g=grid(304){calls++};g.visibility=View.GONE;assertEquals(0,calls)}
    @Test fun referenceWallpaperRendersBothModesAndHonorsBoundsAndTransparency(){
        for(dark in listOf(false,true)){
            val d=ChatLook.wallpaperDrawable(base.resources,dark);val bitmap=Bitmap.createBitmap(390,844,Bitmap.Config.ARGB_8888);d.setBounds(8,8,382,836);d.draw(Canvas(bitmap));assertEquals(0,bitmap.getPixel(0,0));assertTrue(Color.alpha(bitmap.getPixel(100,100))>0)
            val background=ChatLook.wallpaper(dark)
            val sampled=(8..381 step 3).flatMap{x->(8..835 step 7).map{y->bitmap.getPixel(x,y)}}.toSet()
            assertTrue("Mask must leave blank background regions",background in sampled)
            assertTrue("Doodles must remain visible, not a solid tint",sampled.size>2)
            assertTrue("Reference ink must remain subtle",sampled.all{pixel-> kotlin.math.abs(Color.red(pixel)-Color.red(background))<=26 && kotlin.math.abs(Color.blue(pixel)-Color.blue(background))<=26})
            java.io.File("build/ui-screenshots/wallpaper-native-${if(dark)"dark"else"light"}.png").apply{parentFile?.mkdirs()}.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.eraseColor(Color.TRANSPARENT);d.alpha=0;d.draw(Canvas(bitmap));assertEquals(0,bitmap.getPixel(100,100));bitmap.recycle()
        }
    }
}
