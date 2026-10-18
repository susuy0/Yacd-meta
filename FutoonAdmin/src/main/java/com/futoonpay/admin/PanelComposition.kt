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

    // Called by the panel before its Compose child is attached. Owners on the panel alone
    // cannot satisfy Compose's default lookup on the *window content root* above the panel.
    fun attach() {
        if (view.findViewTreeCompositionContext() != null) return
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
