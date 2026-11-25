package com.futoonpay.admin

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.widget.*
import com.futoonpay.admin.appearance.*
import com.futoonpay.admin.nativehost.NativeTabFixture
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
class NativeChromeRegressionTest {
    private fun layout(v:View,w:Int,h:Int){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h)}
    private fun capture(v:View,name:String){
        val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b))
        if(v is LinearLayout)for(i in 0 until v.childCount){val tab=v.getChildAt(i)as NativeTabFixture;val label=tab.textView;var ink=0
            for(y in label.top until label.bottom)for(x in tab.left+label.left until tab.left+label.right)if(b.getPixel(x,y)!=ChatLook.surface(false))ink++
            if(ink<=4)println("LABEL ${label.text}: ink=$ink bounds=${label.left},${label.top},${label.right},${label.bottom} parent=${tab.left},${tab.top},${tab.right},${tab.bottom} alpha=${label.alpha} color=${label.currentTextColor} baseline=${label.baseline} layout=${label.layout?.width}x${label.layout?.height} scroll=${label.scrollX},${label.scrollY}")
            assertTrue("Rendered label ${label.text} is missing",ink>4)
        }
        File("build/ui-screenshots/$name.png").apply{parentFile.mkdirs()}.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
    }
    @Test fun actualNativeTabMeasurementCannotOverrideTheLabelOrClipItsChineseText(){
        val app=RuntimeEnvironment.getApplication();val original=Configuration(app.resources.configuration)
        try{for(scale in listOf(1f,1.3f,1.5f,2f))for(width in listOf(280,320,393,640)){
            app.resources.updateConfiguration(Configuration(original).apply{fontScale=scale},app.resources.displayMetrics)
            val root=LinearLayout(app).apply{orientation=LinearLayout.HORIZONTAL;setPadding(8,0,8,0);setBackgroundColor(ChatLook.surface(false))}
            var clicks=0
            for(label in listOf("聊天","设置","个人资料")){
                val tab=NativeTabFixture(app,label)
                // MainTabsLayout invokes both methods during every measure pass.
                tab.measureTextWidth(14f);tab.setTextSizeDp(14f)
                tab.setOnClickListener{clicks++};root.addView(tab,LinearLayout.LayoutParams(0,56,1f))
            }
            layout(root,width,56)
            root.viewTreeObserver.dispatchOnPreDraw()
            for(i in 0 until root.childCount){val tab=root.getChildAt(i)as NativeTabFixture
                val label=tab.textView;val icon=tab.imageView
                assertEquals(10f*app.resources.displayMetrics.scaledDensity,label.textSize,.1f)
                assertTrue("$width/$scale label clipped",label.bottom<=tab.height)
                assertTrue("$width/$scale icon overlap",icon.bottom<label.top)
                assertTrue("$width/$scale icon height ${icon.height}",icon.height>=20)
                assertTrue("$width/$scale label ${label.height} line ${label.lineHeight}",label.height>=label.lineHeight)
                assertTrue(label.text.isNotEmpty())
                assertTrue("$width/$scale baseline ${label.baseline} layout ${label.layout?.height} measured ${label.height}",label.baseline in 1..label.height)
                val visible=Rect();assertTrue(label.getLocalVisibleRect(visible));assertEquals(label.height,visible.height())
                tab.performClick()
            };assertEquals(3,clicks)
            if(width==393 && (scale==1f||scale==2f))capture(root,"native-tabs-font-$scale")
        }}finally{app.resources.updateConfiguration(original,app.resources.displayMetrics)}
    }
    @Test fun fullInboxContainerKeepsTheFilterViewportAndRoundedPillsInsideItsBounds(){
        val app=RuntimeEnvironment.getApplication();val original=Configuration(app.resources.configuration)
        try{for(scale in listOf(1f,1.3f,1.5f,2f)){
            app.resources.updateConfiguration(Configuration(original).apply{fontScale=scale},app.resources.displayMetrics)
            val root=FrameLayout(app).apply{setBackgroundColor(ChatLook.surface(false))}
            val header=ChatInboxHeader(app).apply{setPadding(4,4,4,4)}
            val filter=ChatFilterBar(app,arrayOf("全部","未读","群组","列表 ⌄"),false){}
            val editor=EditText(app).apply{setSingleLine(true);setTextSize(17f);hint="搜索聊天";setHintTextColor(ChatLook.secondary(false));setTextColor(ChatLook.text(false));setPadding(38,0,38,0);background=null}
            val search=ImageView(app).apply{setImageDrawable(ChatLook.icon("search",ChatLook.secondary(false),1f))};val close=ImageView(app);val extras=LinearLayout(app);val tokens=LinearLayout(app)
            listOf(editor,search,close,extras,tokens,filter).forEach{header.addView(it,FrameLayout.LayoutParams(if(it==editor||it==filter)-1 else 24,48))}
            header.bindHome("Futoon chat",filter,editor,search,close,extras,tokens)
            header.setHeaderDark(false)
            val height=ChatInboxHeader.homeHeight(scale)
            // Exercise the inherited compact-search height, not an idealized parent.
            root.addView(header,FrameLayout.LayoutParams(-1,48).apply{topMargin=70;leftMargin=7;rightMargin=7})
            layout(root,393,852)
            assertEquals(height,header.height)
            val title=(0 until header.childCount).map{header.getChildAt(it)}.filterIsInstance<TextView>().single{it.text.toString()=="Futoon chat"}
            assertEquals("Native inbox keeps its 16dp horizontal inset",16,header.left+title.left)
            val row=filter.getChildAt(0)as LinearLayout
            assertTrue(header.top+filter.bottom<=header.bottom)
            for(i in 0 until row.childCount){val button=row.getChildAt(i);if(button.left>=filter.width)continue
                val visible=Rect();assertTrue(button.getLocalVisibleRect(visible));assertEquals(button.height,visible.height())
                assertTrue(header.top+filter.top+row.top+button.bottom<=header.bottom)
            }
            if(scale==1f||scale==2f)capture(root,"native-inbox-container-font-$scale")
        }}finally{app.resources.updateConfiguration(original,app.resources.displayMetrics)}
    }
}
