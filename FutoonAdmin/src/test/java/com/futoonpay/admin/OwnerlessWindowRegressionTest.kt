package com.futoonpay.admin

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import java.time.Duration
import androidx.compose.material3.Text
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Deliberately no ComponentActivity or Compose test rule: both can hide missing window owners. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OwnerlessWindowRegressionTest {
    private lateinit var controller: ActivityController<Activity>
    private val dialogs = mutableListOf<Dialog>()
    private val cleanup = mutableListOf<() -> Unit>()
    private val activity get() = controller.get()

    @Before fun startPlainActivity() {
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        assertNull(activity.window.decorView.findViewTreeLifecycleOwner())
    }

    @After fun finishWindows() {
        cleanup.asReversed().forEach { it() }
        dialogs.forEach { it.dismiss() }
        shadowOf(Looper.getMainLooper()).idle()
        controller.pause().stop().destroy()
    }

    private fun content(view: View) = FrameLayout(activity).apply {
        addView(view, FrameLayout.LayoutParams(-1, -1))
        // Models BottomSheet$1 above the owned panel, immediately under android.R.id.content.
        assertNull(findViewTreeLifecycleOwner())
    }

    private fun dialog(root: FrameLayout) = Dialog(activity).also {
        dialogs += it
        it.setContentView(root)
        assertNull(it.window!!.decorView.findViewTreeLifecycleOwner())
    }

    private fun render(root: View) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        root.measure(View.MeasureSpec.makeMeasureSpec(780, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val bitmap = Bitmap.createBitmap(780, 1200, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        bitmap.recycle()
    }

    private fun compose(view: FrameLayout) = view.getChildAt(0) as ComposeView

    private fun premeasure(root: View) {
        root.measure(View.MeasureSpec.makeMeasureSpec(780, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.AT_MOST))
    }

    @Test fun bindingOnlyOnAttachReproducesTelegramSheetsPremeasurementFailure() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle get() = registry
        }
        owner.registry.currentState = Lifecycle.State.CREATED
        val view = ComposeView(activity).apply { setContent { Text("old premeasurement path") } }
        val legacy = object : FrameLayout(activity) {
            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                PanelComposition(view, owner.lifecycle).prepare()
            }
        }.apply { setViewTreeLifecycleOwner(owner); addView(view) }
        cleanup += { view.disposeComposition(); owner.registry.currentState = Lifecycle.State.DESTROYED }
        assertFalse(legacy.isAttachedToWindow)
        val failure = assertThrows(IllegalStateException::class.java) { premeasure(content(legacy)) }
        assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains("not attached to a window"))
    }

    @Test fun quickRepliesSurvivePremeasurementBeforeShowingTheDialog() {
        val panel = ChatToolsView(activity, ChatToolsFixture(), true).apply { setPanelHeight(560) }
        cleanup += panel::close
        val root = content(panel)
        premeasure(root)
        assertFalse(panel.isAttachedToWindow)
        assertEquals(560, panel.measuredHeight)
        assertTrue(compose(panel).hasComposition)
        // BottomSheet.show() also measures synchronously after Dialog.show(), before traversal.
        dialog(root).show(); premeasure(root)
        panel.onShown(); render(root)
        assertTrue(compose(panel).hasComposition)
        assertEquals(Lifecycle.State.RESUMED, panel.lifecycle.currentState)
    }

    @Test fun settingsSurviveMeasuringBeforeTheFragmentViewIsAttached() {
        val panel = ChatToolsView(activity, ChatToolsFixture(), false)
        cleanup += panel::close
        val root = content(panel)
        premeasure(root)
        assertTrue(compose(panel).hasComposition)
        activity.setContentView(root)
        panel.onShown(); render(root)
        assertTrue(panel.isAttachedToWindow)
        assertTrue(compose(panel).hasComposition)
    }

    @Test fun businessPanelSurvivesMeasuringBeforeTheHostIsAttached() {
        val bridge = FixtureBridge()
        val panel = NativePanelView(activity, bridge)
        cleanup += panel::close
        val root = content(panel)
        premeasure(root)
        assertTrue(compose(panel).hasComposition)
        activity.setContentView(root)
        panel.onShown(); render(root)
        assertTrue(panel.isAttachedToWindow)
        assertEquals(0, bridge.sent.get())
    }

    @Test fun closingAPremeasuredPickerBeforeWindowAttachmentDisposesItsComposition() {
        val panel = ChatToolsView(activity, ChatToolsFixture(), true)
        cleanup += panel::close
        val root = content(panel)
        premeasure(root)
        val child = compose(panel)
        assertTrue(child.hasComposition)
        panel.close(); panel.close()
        assertFalse(child.hasComposition)
        dialog(root).show(); render(root)
        assertEquals(0, panel.childCount)
        assertEquals(Lifecycle.State.DESTROYED, panel.lifecycle.currentState)
    }

    @Test fun originalSubtreeOwnerAloneStillReproducesReportedCrash() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle get() = registry
        }
        owner.registry.currentState = Lifecycle.State.RESUMED
        val legacy = FrameLayout(activity).apply { setViewTreeLifecycleOwner(owner) }
        val view = ComposeView(activity).apply { setContent { Text("legacy panel") } }
        legacy.addView(view)
        cleanup += { view.disposeComposition(); owner.registry.currentState = Lifecycle.State.DESTROYED }
        val sheet = dialog(content(legacy))
        val failure = assertThrows(IllegalStateException::class.java) {
            sheet.show()
            render(legacy)
            view.createComposition()
        }
        assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains("ViewTreeLifecycleOwner not found"))
    }

    @Test fun quickRepliesAttachAndDrawInsideOwnerlessDialogAndDisposeIdempotently() {
        val bridge = ChatToolsFixture()
        val panel = ChatToolsView(activity, bridge, true).apply { setPanelHeight(560) }
        cleanup += panel::close
        val root = content(panel)
        dialog(root).show()
        panel.onShown()
        render(root)
        assertTrue(panel.isAttachedToWindow)
        assertTrue(compose(panel).hasComposition)
        assertTrue(panel.measuredHeight in 1..560)
        assertEquals(Lifecycle.State.RESUMED, panel.lifecycle.currentState)
        panel.handleBack()
        assertEquals(1, bridge.closed)
        val child = compose(panel)
        panel.close(); panel.close(); panel.onShown()
        render(root)
        assertEquals(Lifecycle.State.DESTROYED, panel.lifecycle.currentState)
        assertFalse(child.hasComposition)
        assertEquals(0, panel.childCount)
        assertEquals("", bridge.inserted)
        assertEquals(0, bridge.writes)
    }

    @Test fun settingsAttachAndDrawInPlainTelegramStyleActivity() {
        val panel = ChatToolsView(activity, ChatToolsFixture(), false)
        cleanup += panel::close
        val root = content(panel)
        activity.setContentView(root)
        panel.onShown()
        render(root)
        assertTrue(compose(panel).hasComposition)
        assertNull(root.findViewTreeLifecycleOwner())
        panel.onHidden()
        assertEquals(Lifecycle.State.CREATED, panel.lifecycle.currentState)
        panel.onShown()
        render(root)
        assertEquals(Lifecycle.State.RESUMED, panel.lifecycle.currentState)
    }

    @Test fun businessPanelAttachesAndDrawsInOwnerlessActivityWithoutSending() {
        val bridge = FixtureBridge()
        val panel = NativePanelView(activity, bridge)
        cleanup += panel::close
        val root = content(panel)
        activity.setContentView(root)
        panel.onShown()
        render(root)
        assertTrue(compose(panel).hasComposition)
        assertNull(root.findViewTreeLifecycleOwner())
        panel.onHidden(); panel.onShown()
        render(root)
        val child = compose(panel)
        panel.close()
        render(root)
        assertFalse(child.hasComposition)
        assertEquals(0, bridge.sent.get())
    }

    @Test fun detachedPanelGetsWorkingCompositionWhenReattached() {
        val panel = ChatToolsView(activity, ChatToolsFixture(), true)
        cleanup += panel::close
        val root = content(panel)
        dialog(root).show()
        panel.onShown()
        render(root)
        panel.onHidden()
        root.removeView(panel)
        shadowOf(Looper.getMainLooper()).idle()
        root.addView(panel)
        panel.onShown()
        render(root)
        assertTrue(panel.isAttachedToWindow)
        assertTrue(compose(panel).hasComposition)
        assertEquals(Lifecycle.State.RESUMED, panel.lifecycle.currentState)
    }

    @Test fun closingOnePanelDoesNotDestroyAnotherPanelsComposition() {
        val first = ChatToolsView(activity, ChatToolsFixture(), true)
        val second = ChatToolsView(activity, ChatToolsFixture(), false)
        cleanup += first::close; cleanup += second::close
        val root = content(first).apply { addView(second, FrameLayout.LayoutParams(-1, -1)) }
        dialog(root).show()
        first.onShown(); second.onShown()
        render(root)
        val child = compose(first)
        first.close()
        render(root)
        assertFalse(child.hasComposition)
        assertTrue(compose(second).hasComposition)
        assertEquals(Lifecycle.State.RESUMED, second.lifecycle.currentState)
        assertNull(root.findViewTreeLifecycleOwner())
    }

    @Test fun dismissedBeforeFirstTraversalNeverAttachesDestroyedComposeContent() {
        val panel = ChatToolsView(activity, ChatToolsFixture(), true)
        cleanup += panel::close
        panel.close()
        val root = content(panel)
        dialog(root).show()
        render(root)
        assertEquals(0, panel.childCount)
        assertEquals(Lifecycle.State.DESTROYED, panel.lifecycle.currentState)
    }
}
