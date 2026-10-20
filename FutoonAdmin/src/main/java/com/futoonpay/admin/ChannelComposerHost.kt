package com.futoonpay.admin

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout

/** Reserves space above Telegram's existing composer without reparenting any of its controls. */
object ChannelComposerHost {
    @JvmStatic fun attach(parent: FrameLayout, bar: View, height: Int) {
        parent.addView(bar, FrameLayout.LayoutParams(-1, height, Gravity.TOP).apply { topMargin = -height })
        parent.clipToPadding = false
    }
    @JvmStatic fun resize(parent: FrameLayout, bar: View?, height: Int) {
        val h = height.coerceAtLeast(0)
        parent.setPadding(parent.paddingLeft, h, parent.paddingRight, parent.paddingBottom)
        bar?.let { view -> (view.layoutParams as FrameLayout.LayoutParams).apply { this.height = h; topMargin = -h; view.layoutParams = this } }
        parent.requestLayout()
    }
    @JvmStatic fun replyTranslation(totalHeight: Int, fieldHeight: Float, replyHeight: Int, visible: Float, barHeight: Int): Float = totalHeight - fieldHeight - replyHeight * visible - barHeight
}
