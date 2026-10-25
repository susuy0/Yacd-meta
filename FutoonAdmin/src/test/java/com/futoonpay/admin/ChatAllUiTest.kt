package com.futoonpay.admin

import android.app.Application
import android.content.res.Configuration
import android.graphics.*
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import com.futoonpay.admin.appearance.*
import com.futoonpay.admin.ui.*
import com.futoonpay.admin.ui.theme.FutoonTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatAllUiTest {
    @get:Rule val compose=createComposeRule()
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private fun layout(v:View,width:Int){v.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.AT_MOST));v.layout(0,0,width,v.measuredHeight);v.viewTreeObserver.dispatchOnPreDraw()}
    @Test fun realPreferenceRowsFitIconsAndMultilineTextForEveryDirectionAndFont(){
        val activity=org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup()
        try{for(width in listOf(240,280,390,640))for(scale in listOf(1f,1.5f,2f))for(rtl in listOf(false,true)){
            val c=app.createConfigurationContext(Configuration(app.resources.configuration).apply{fontScale=scale})
            val title=TextView(c).apply{text="Telegram ID";setTextColor(ChatLook.text(false));setSingleLine()};val detail=TextView(c).apply{text="5824870757\nMerchant identity · preserved native detail";setTextColor(ChatLook.secondary(false));maxLines=4};val icon=View(c)
            val row=ChatPreferenceRow(c).apply{setBackgroundColor(ChatLook.surface(false))};listOf(icon,title,detail).forEach(row::addView);row.bindChatRow(title,detail,icon,rtl);activity.get().setContentView(row);layout(row,width)
            assertTrue(row.height>=72);assertEquals(24,icon.width);assertEquals(24,icon.height)
            for(v in listOf(title,detail,icon)){assertTrue(v.left>=0);assertTrue(v.right<=width);assertTrue(v.top>=0);assertTrue(v.bottom<=row.height)}
            assertTrue(title.bottom<=detail.top);assertTrue(detail.height>=detail.lineHeight*2)
            if(rtl)assertTrue(detail.right<=icon.left)else assertTrue(icon.right<=detail.left)
            if(width==390 && ((scale==1f && !rtl)||(scale==1.5f && rtl))){val image=Bitmap.createBitmap(width,row.height,Bitmap.Config.ARGB_8888);row.draw(Canvas(image));java.io.File("build/ui-screenshots/all-ui-preference-${if(rtl)"rtl-font150"else"ltr"}.png").apply{parentFile?.mkdirs()}.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()}
        }}finally{activity.pause().stop().destroy()}
    }
    @Test fun recycledPreferenceRowsRemoveOldIconGuttersAndRetainTheirSpansAndClick(){
        val title=TextView(app);val detail=TextView(app);val icon=View(app);val row=ChatPreferenceRow(app);listOf(icon,title,detail).forEach(row::addView)
        val span=StyleSpan(Typeface.BOLD);val value=SpannableString("Telegram ID 5824870757").apply{setSpan(span,0,11,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)}
        title.text="Identity";detail.text=value;row.bindChatRow(title,detail,icon,false);var calls=0;row.setOnClickListener{calls++};layout(row,320);assertEquals(72,title.left)
        icon.visibility=View.GONE;layout(row,320);assertEquals(24,title.left);assertTrue((detail.text as Spanned).getSpans(0,11,StyleSpan::class.java).contains(span));row.performClick();assertEquals(1,calls)
        detail.text="";layout(row,320);assertEquals(56,row.height);assertEquals(0,detail.height)
    }
    @Test fun secondaryNativeLabelsUseTheirActualLineHeightsAtLargeFonts(){
        for(scale in listOf(1f,1.5f,2f)){
            val c=app.createConfigurationContext(Configuration(app.resources.configuration).apply{fontScale=scale})
            val title=TextView(c).apply{text="Privacy";setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,16f)}
            val detail=TextView(c).apply{text="A long secondary setting description\nSecond line\nThird line";setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,14f)}
            val parent=android.widget.FrameLayout(c);parent.addView(title,android.widget.FrameLayout.LayoutParams(272,-2).apply{leftMargin=24;topMargin=10});parent.addView(detail,android.widget.FrameLayout.LayoutParams(272,-2).apply{leftMargin=24;topMargin=35})
            parent.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
            val height=ChatChrome.preferenceHeight(c,title,detail);parent.layout(0,0,320,height)
            if(scale==2f)assertTrue("Old fixed subtitle offset must reproduce overlap",title.bottom>detail.top)
            ChatChrome.stackLabels(parent,title,detail);assertTrue(title.bottom<detail.top);assertTrue(detail.bottom<=height);assertTrue(title.top>=0)
        }
    }
    @Test fun galleryColumnsAndGuttersFitEveryWindowIncludingLandscape(){
        for(width in listOf(240,280,320,390,600,640,900,1024)){
            val cols=ChatChrome.galleryColumns(width);assertTrue(cols in 2..6);val size=ChatChrome.galleryTile(width,cols,2,2)
            assertTrue(size>0);assertTrue(size*cols+4+2*(cols-1)<=width);assertTrue(width-(size*cols+4+2*(cols-1))<cols)
        }
        assertEquals(2,ChatChrome.galleryColumns(280));assertEquals(3,ChatChrome.galleryColumns(390));assertEquals(4,ChatChrome.galleryColumns(640))
    }
    @Test fun selectionToolbarsBotButtonsAndDialogsRetainReadableRoles(){
        for(dark in listOf(false,true)){
            for(pair in listOf("key_actionBarActionModeDefaultIcon" to "key_actionBarActionModeDefault","key_chat_botKeyboardButtonText" to "key_chat_botKeyboardButtonBackground","key_dialogSearchText" to "key_dialogSearchBackground")){
                assertTrue(pair.toString(),ColorUtils.calculateContrast(ChatLook.color(pair.first,dark)!!,ChatLook.color(pair.second,dark)!!)>=4.5)
            }
            for(key in listOf("key_dialogTextGray","key_profile_tabText"))assertTrue(ColorUtils.calculateContrast(ChatLook.color(key,dark)!!,ChatLook.surface(dark))>=3)
            for(key in listOf("key_dialogTextRed","key_text_RedRegular","key_botKeyboard_button_danger"))assertNull(ChatLook.color(key,dark))
        }
    }
    @Test fun nativeSearchBackgroundChangesThemeWithoutRecreatingTheView(){
        var dark=false;val d=ChatChrome.field(app){dark};d.setBounds(8,8,112,56);val b=Bitmap.createBitmap(120,64,Bitmap.Config.ARGB_8888);val canvas=Canvas(b)
        d.draw(canvas);assertEquals(ChatLook.field(false),b.getPixel(60,30));assertEquals(0,b.getPixel(0,0));dark=true;b.eraseColor(0);d.draw(canvas);assertEquals(ChatLook.field(true),b.getPixel(60,30));d.alpha=0;b.eraseColor(0);d.draw(canvas);assertEquals(0,b.getPixel(60,30));b.recycle()
    }
    @Test fun menuMotionHonorsTheSystemAnimationPreference(){
        Settings.Global.putFloat(app.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,0f);assertFalse(ChatChrome.animationsEnabled(app))
        Settings.Global.putFloat(app.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f);assertTrue(ChatChrome.animationsEnabled(app))
        val attrs=app.obtainStyledAttributes(R.style.ChatMenuAnimation,intArrayOf(android.R.attr.windowEnterAnimation,android.R.attr.windowExitAnimation))
        try { assertEquals(150L,android.view.animation.AnimationUtils.loadAnimation(app,attrs.getResourceId(0,0)).duration);assertEquals(100L,android.view.animation.AnimationUtils.loadAnimation(app,attrs.getResourceId(1,0)).duration) } finally { attrs.recycle() }
    }
    @Test fun sharedBusinessButtonsKeepRoutesDisabledStatesAndTouchTargets(){
        val calls=mutableListOf<String>();compose.setContent{FutoonTheme{Column{ChatButton(onClick={calls+="submit"}){Text("提交")};ChatOutlinedButton(onClick={calls+="choose"}){Text("选择图片")};ChatTextButton(enabled=false,onClick={calls+="disabled"}){Text("未启用")}}}}
        for(label in listOf("提交","选择图片","未启用")){val bounds=compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot;assertTrue(bounds.height>=48f)}
        compose.onNodeWithText("提交").performClick();compose.onNodeWithText("选择图片").performClick();compose.onNodeWithText("未启用").assertIsNotEnabled();assertEquals(listOf("submit","choose"),calls)
    }
    @Test fun newContextActionGlyphsRemainBoundedAndHideAtAlphaZero(){
        for(name in listOf("copy","forward","delete","download")){
            val d=ChatLook.icon(name,Color.BLACK,1f);val b=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888);d.setBounds(4,4,44,44);d.draw(Canvas(b));assertEquals(0,b.getPixel(0,0));assertTrue((4..43).any{x->(4..43).any{y->Color.alpha(b.getPixel(x,y))>0}});d.alpha=0;b.eraseColor(0);d.draw(Canvas(b));assertEquals(0,b.getPixel(24,24));b.recycle()
        }
    }
}
