package com.futoonpay.admin

import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.platform.findViewTreeCompositionContext
import androidx.lifecycle.Lifecycle

/** Telegram's fragment containers and BottomSheet windows have no AndroidX window owners. */
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
        view.disposeComposition()
        view.setParentCompositionContext(null)
        recomposer?.cancel()
        recomposer = null
    }
}
