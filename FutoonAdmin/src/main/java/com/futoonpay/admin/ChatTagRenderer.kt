package com.futoonpay.admin

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.Layout
import android.text.style.ReplacementSpan
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** A bounded inline tag that reserves its width before ellipsizing the conversation title. */
object ChatTagRenderer {
    @JvmStatic fun decorate(name: CharSequence?, tag: String, paint: TextPaint, width: Int, density: Float, dark: Boolean): CharSequence? {
        if (name == null || tag.isEmpty() || width < 36 * density) return name
        val small = TextPaint(paint).apply { textSize = paint.textSize * .7f }
        val text = fit(tag.replace('\n', ' ').replace('\r', ' '), small, max(1f, min(120 * density, width * .48f) - 14 * density)).toString()
        if (text.isEmpty()) return name
        val badge = Badge(text, density, dark)
        val badgeWidth = badge.getSize(paint, "", 0, 0, null)
        val title = fit(name, paint, max(0, width - badgeWidth).toFloat())
        return SpannableStringBuilder(title).apply { val start = length; append('\uFFFC'); setSpan(badge, start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }
    private fun fit(text: CharSequence, paint: TextPaint, width: Float): CharSequence {
        if (width <= 0) return ""
        if (Layout.getDesiredWidth(text, paint) <= width) return text
        if (Layout.getDesiredWidth("…", paint) > width) return ""
        var low = 0
        var high = Character.codePointCount(text, 0, text.length)
        while (low < high) {
            val middle = (low + high + 1) / 2
            val end = Character.offsetByCodePoints(text, 0, middle)
            val candidate = SpannableStringBuilder(text, 0, end).append("…")
            if (Layout.getDesiredWidth(candidate, paint) <= width) low = middle else high = middle - 1
        }
        return SpannableStringBuilder(text, 0, Character.offsetByCodePoints(text, 0, low)).append("…")
    }
    private class Badge(val text: String, val density: Float, val dark: Boolean) : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int = ceil(Paint(paint).apply { textSize = paint.textSize * .7f }.measureText(this.text) + 14 * density).toInt()
        override fun draw(canvas: Canvas, source: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            val small = Paint(paint).apply { isAntiAlias = true; textSize = paint.textSize * .7f }
            val baseline = y + (paint.ascent() + paint.descent() - small.ascent() - small.descent()) / 2
            val left = x + 4 * density
            small.color = if (dark) 0x2634c993 else 0x1a087d53
            canvas.drawRoundRect(RectF(left, baseline + small.ascent() - 2 * density, x + getSize(paint, source, start, end, null), baseline + small.descent() + 2 * density), 4 * density, 4 * density, small)
            small.color = if (dark) 0xff80e7bd.toInt() else 0xff087d53.toInt()
            canvas.drawText(text, left + 5 * density, baseline, small)
        }
    }
}
