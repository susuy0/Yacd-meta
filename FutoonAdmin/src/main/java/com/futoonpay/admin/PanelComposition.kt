package com.futoonpay.admin

import android.view.ViewGroup
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.platform.findViewTreeCompositionContext
import androidx.lifecycle.Lifecycle

/** Telegram's fragment containers and BottomSheet windows have no AndroidX window owners. */
@OptIn(ExperimentalComposeUiApi::class)
internal class PanelComposition(private val view: ComposeView, private val lifecycle: Lifecycle) {
    private var recomposer: Recomposer? = null

    // BottomSheet.show() measures its container synchronously before the first window
    // traversal. Prepare before BOTH measurement and attachment, without replacing a live
    // recomposer between those calls. Subtree owners cannot supply a window recomposer.
    fun prepare() {
        if (view.findViewTreeCompositionContext() != null) return
        if (recomposer?.currentState?.value?.let { it > Recomposer.State.ShuttingDown } == true) return
        recomposer?.cancel()
        recomposer = view.createLifecycleAwareWindowRecomposer(lifecycle = lifecycle)
        view.setParentCompositionContext(recomposer)
    }

    fun close() {
        // A dismissed sheet may still get a final traversal. Its destroyed Compose child
        // must not create a new composition while the window is being removed.
        (view.parent as? ViewGroup)?.removeView(view)
        view.disposeComposition()
        view.setParentCompositionContext(null)
        recomposer?.cancel()
        recomposer = null
    }
}
