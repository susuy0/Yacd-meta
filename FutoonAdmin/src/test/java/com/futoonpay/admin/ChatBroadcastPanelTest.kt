package com.futoonpay.admin

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.futoonpay.admin.appearance.ChatBroadcastPanel
import com.futoonpay.admin.appearance.ChatBroadcastPanel.Row
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatBroadcastPanelTest {
    private val events=mutableListOf<String>()
    private fun panel(dark:Boolean=false)=ChatBroadcastPanel(RuntimeEnvironment.getApplication(),dark,object:ChatBroadcastPanel.Listener{
        override fun onConfirm(){events+="confirm"};override fun onBack(){events+="back"};override fun onStop(){events+="stop"};override fun onRetry(){events+="retry"}
        override fun onCopyFailed(names:String){events+="copy:$names"};override fun onDone(){events+="done"}})
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList())
    private fun texts(v:View)=views(v).filterIsInstance<TextView>()
    private fun layout(v:View){v.measure(View.MeasureSpec.makeMeasureSpec(390,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));v.layout(0,0,390,v.measuredHeight)}
    private fun shot(v:View,name:String){layout(v);val b=Bitmap.createBitmap(390,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));File("build/ui-screenshots/$name.png").apply{parentFile.mkdirs()}.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    private val names=listOf("星河电商 对接群","鲸落游戏 结算群","云杉商城 对接群","送杭出行 对接群")

    @Test fun confirmStaysAsleepUntilTheGroupCountIsTyped(){
        val p=panel();p.showPreview("代收-A2 上游维护中，已切到代收-B1。",names,2,5);layout(p)
        val confirm=texts(p).single{it.text.toString()=="确认发送 4 个群"};assertFalse(confirm.isEnabled)
        confirm.performClick();assertTrue(events.isEmpty())
        val input=views(p).filterIsInstance<EditText>().single()
        input.setText("3");assertFalse(confirm.isEnabled);assertTrue(texts(p).any{it.text.toString().contains("不一致")})
        input.setText("4");assertTrue(confirm.isEnabled);assertTrue(texts(p).any{it.text.toString()=="已匹配"})
        confirm.performClick();assertEquals(listOf("confirm"),events)
        texts(p).single{it.text.toString()=="返回修改"}.performClick();assertEquals("back",events.last())
        assertTrue(texts(p).any{it.text.toString().contains("影响范围")});assertTrue(texts(p).any{it.text.toString().contains("不能一键撤回")})
        assertTrue(texts(p).any{it.text.toString().startsWith("2 个私聊")})
        shot(p,"broadcast-preview-light");val d=panel(true);d.showPreview("hi",names,0,5);shot(d,"broadcast-preview-dark")
    }
    @Test fun progressShowsCountAndStopsTheRest(){
        val rows=listOf(Row("A",ChatBroadcastPanel.SENT,null),Row("B",ChatBroadcastPanel.SENT,null),Row("C",ChatBroadcastPanel.SENDING,null),Row("D",ChatBroadcastPanel.QUEUED,null),Row("E",ChatBroadcastPanel.QUEUED,null))
        val p=panel();p.showProgress(rows);layout(p)
        assertTrue(texts(p).any{it.text.toString()=="2 / 5"});assertTrue(texts(p).any{it.text.toString()=="发送中"});assertEquals(2,texts(p).count{it.text.toString()=="排队中"})
        texts(p).single{it.text.toString()=="停止，不发剩下的 2 个"}.performClick();assertEquals(listOf("stop"),events)
        shot(p,"broadcast-progress")
    }
    @Test fun aStoppedRunNamesEveryGroupThatDidNotGetTheMessageAndCanRetryOrCopy(){
        val rows=listOf(Row("A",ChatBroadcastPanel.SENT,null),Row("B",ChatBroadcastPanel.FAILED,"机器人已被移出群，重试也发不出"),Row("C",ChatBroadcastPanel.NOT_SENT,null))
        val p=panel();p.showResult(rows,"Telegram 报告发送失败","14:52:10","14:52:24");layout(p)
        assertTrue(texts(p).any{it.text.toString()=="1 个已发出，1 个失败，1 个未发"});assertTrue(texts(p).any{it.text.toString().contains("机器人已被移出群")})
        assertTrue(texts(p).any{it.text.toString().contains("已发出的 1 个不会重复发送")})
        texts(p).single{it.text.toString()=="重试可重试的 2 个"}.performClick();assertEquals("retry",events.last())
        texts(p).single{it.text.toString()=="复制失败名单"}.performClick();assertEquals("copy:B\nC",events.last())
        shot(p,"broadcast-result-failed")
    }
    @Test fun aCleanRunEndsWithDoneAndNoRetry(){
        val p=panel();p.showResult(names.map{Row(it,ChatBroadcastPanel.SENT,null)},"","14:52:10","14:52:24");layout(p)
        assertTrue(texts(p).any{it.text.toString()=="4 个群都收到了"});assertTrue(texts(p).none{it.text.toString().startsWith("重试")})
        texts(p).single{it.text.toString()=="完成"}.performClick();assertEquals(listOf("done"),events)
        shot(p,"broadcast-result-ok")
    }
}
