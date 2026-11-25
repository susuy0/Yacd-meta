package com.futoonpay.admin

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.futoonpay.admin.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChannelBarLayoutTest {
    private val context get()=androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
    private fun texts(view:View):List<TextView> = (if(view is TextView)listOf(view)else emptyList())+(if(view is ViewGroup)(0 until view.childCount).flatMap{texts(view.getChildAt(it))}else emptyList())
    private fun bar():NativeChannelBarView=NativeChannelBarView(context,FixtureBridge(),object:ChannelSheetHost{override fun heightChanged(pixels:Int){};override fun showSheet(view:ChannelSheetView,kind:String){};override fun closeSheet(){}})
    private fun state()=ChannelShortcutState(ChannelScope("0:915","https://fixture.example/admin/",-100,1),"测试群",ChannelLink("1042","微信H5-A线（华南备用·夜间切换）"),visible=true,snapshot=ChannelSnapshot(MonitorWindow.FIVE_MINUTES,86.2,50,43,7))
    private fun render(view:NativeChannelBarView,width:Int,height:Int){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height)}
    @Test fun threeActionsAndSwitchFitNarrowAndRegularScreensWithFullTouchTargets(){
        for(width in listOf(320,390)){
            val view=bar();view.render(state());render(view,width,76)
            val nodes=texts(view)
            for(label in listOf("通道订单","编辑通道")){
                val action=nodes.single{it.text.toString()==label};assertTrue(action.width>=48);assertTrue(action.height>=48);assertTrue(action.isEnabled)
                assertTrue(action.paint.measureText(label)<=action.width)
            }
            val rate=nodes.single{it.text.startsWith("成功率")};assertTrue(rate.width>=48);assertTrue(rate.height>=48)
            val switch=nodes.single{it.contentDescription=="切换支付通道，按钮"};assertEquals(48,switch.width);assertTrue(switch.height>=48)
            val bitmap=Bitmap.createBitmap(width,76,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap));java.io.File("build/ui-screenshots/channel-bar-${width}-light.png").apply{parentFile.mkdirs()}.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle();view.close()
        }
    }
    @Test @Config(qualifiers="w844dp-h390dp-land-mdpi") fun landscapeUsesOne48dpRowAboveTheComposer(){val view=bar();view.render(state());render(view,844,49);val actions=texts(view).filter{it.isClickable};assertEquals(5,actions.size);actions.forEach{assertTrue(it.height>=48);assertTrue(it.width>=48)};assertTrue(actions.any{it.text.toString()=="通道订单"});assertTrue(actions.any{it.text.toString()=="编辑通道"});view.close()}
    @Test fun invalidLinkDisablesOperationsButKeepsReselectionAvailable(){val view=bar();view.render(state().copy(invalid=true));render(view,320,76);val nodes=texts(view);assertFalse(nodes.single{it.text.toString()=="编辑通道"}.isEnabled);assertFalse(nodes.single{it.text.toString()=="通道订单"}.isEnabled);assertTrue(nodes.single{it.text.toString()=="重新\n选择"}.isClickable);view.close()}
    @Test fun unlinkedEntryHas48dpCloseAndChooseTargets(){val view=bar();view.render(state().copy(link=null));render(view,320,49);texts(view).filter{it.isClickable}.forEach{assertTrue(it.height>=48);assertTrue(it.width>=48)};view.close()}
    @Test fun darkThemePreservesActionsAndReadableLabels(){val view=bar();view.setThemeColors("""{"dark":true,"surface":-14671840,"background":-15263977,"text":-1,"secondaryText":-6052957,"divider":-13224394,"accentText":-9784333}""");view.render(state());render(view,390,76);assertTrue(texts(view).any{it.text.toString()=="编辑通道"});texts(view).filter{it.background is android.graphics.drawable.GradientDrawable && it.isClickable}.forEach{val background=(it.background as android.graphics.drawable.GradientDrawable).color!!.defaultColor;assertTrue("Unreadable action label",androidx.core.graphics.ColorUtils.calculateContrast(it.currentTextColor,background)>=4.5)};val bitmap=Bitmap.createBitmap(390,76,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap));java.io.File("build/ui-screenshots/channel-bar-390-dark.png").apply{parentFile.mkdirs()}.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle();view.close()}
}
