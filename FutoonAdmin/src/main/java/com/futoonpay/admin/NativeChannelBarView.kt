package com.futoonpay.admin

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.compose.ui.graphics.toArgb
import com.futoonpay.admin.ui.theme.FutoonPalette
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface ChannelSheetHost {
    fun heightChanged(pixels: Int)
    fun showSheet(view: ChannelSheetView, kind: String)
    fun closeSheet()
}

/** Small platform View strip. No Compose, scrim, animations or timer in the chat hierarchy. */
class NativeChannelBarView(context: Context, bridge: NativePanelBridge, private val host: ChannelSheetHost) : LinearLayout(context) {
    internal val model = ChannelShortcutModel(context, bridge)
    private val jobs = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var palette = FutoonPalette()
    private var sheet: ChannelSheetView? = null
    private var closed = false
    private var lastHeight = -1
    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
    init {
        orientation = VERTICAL; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        jobs.launch { model.state.collect { render(it) } }
    }
    fun bindChat(label: String, dialog: Long, topic: Long) {
        val previous = model.state.value.scope
        model.bind(label, dialog, topic)
        if (previous != null && previous != model.state.value.scope) { sheet?.forceClose(); host.closeSheet() }
    }
    fun onShown() { model.resume(); sheet?.onShown() }
    fun onHidden() { model.pause(); sheet?.onHidden() }
    fun close() { if (closed) return; closed = true; sheet?.forceClose(); host.closeSheet(); model.close(); jobs.cancel() }
    fun fromOrder(channel: String) { if (!closed) model.fromOrder(channel) }
    fun reloadSettings() { if (!closed) { model.bind(model.state.value.label, model.state.value.scope?.dialog ?: 0, model.state.value.scope?.topic ?: 0); model.reloadSettings() } }
    fun setThemeColors(json: String) { palette = FutoonPalette.parse(json); if (!closed) render(model.state.value); sheet?.setThemeColors(json) }
    fun sheetDismissed() { sheet?.close(); sheet = null }
    private fun open(kind: String) {
        if (closed || sheet != null || !model.state.value.visible) return
        val view = ChannelSheetView(context, model, kind, host)
        sheet = view; host.showSheet(view, kind)
    }
    private fun text(value: String, size: Float, color: Int = palette.text.toArgb()) = TextView(context).apply {
        text = value; textSize = size * palette.fontScale; setTextColor(color); gravity = Gravity.CENTER_VERTICAL
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    private fun button(label: String, weight: Float, action: () -> Unit) = text(label, 13f).apply {
        gravity = Gravity.CENTER; isFocusable = true; isClickable = true; contentDescription = label.replace('\n', ' ') + "，按钮"
        background = GradientDrawable().apply { setColor(palette.background.toArgb()); cornerRadius = dp(10).toFloat() }
        setOnClickListener { action() }
        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply { setMargins(dp(2), 0, dp(2), 0) }
    }
    private fun render(state: ChannelShortcutState) {
        if (closed) return
        removeAllViews(); setBackgroundColor(palette.surface.toArgb())
        val scale = resources.configuration.fontScale * palette.fontScale
        val large = scale >= 1.3f
        val linked = state.link != null
        val height = if (!state.visible) 0 else if (!linked) 48 else if (large) (48 * scale).toInt() + 48 else 76
        visibility = if (height == 0) GONE else VISIBLE
        if (lastHeight != dp(height)) { lastHeight = dp(height); host.heightChanged(lastHeight) }
        if (height == 0) return
        addView(View(context).apply { setBackgroundColor(palette.divider.toArgb()) }, LayoutParams(-1, dp(1)))
        if (!linked) {
            val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(text("选择支付通道", 14f, palette.accentText.toArgb()).apply { setPadding(dp(12),0,0,0); setOnClickListener { open("select") }; isFocusable = true; contentDescription = "选择支付通道，按钮" }, LayoutParams(0,-1,1f))
            row.addView(text("×", 24f, palette.secondaryText.toArgb()).apply { gravity = Gravity.CENTER; contentDescription = "隐藏此话题的通道入口，可在 Futoonpay 设置恢复"; setOnClickListener { model.hide() }; isFocusable = true }, LayoutParams(dp(48),-1))
            addView(row, LayoutParams(-1,dp(47))); return
        }
        val body = LinearLayout(context).apply { orientation = HORIZONTAL }
        val left = LinearLayout(context).apply { orientation = VERTICAL }
        val info = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10),0,dp(4),0) }
        // Identity is a readable label, not a 27dp click target. Full name is repeated in the switch sheet.
        info.addView(text(state.link!!.name, 14f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; contentDescription = "当前通道 ${state.link.name}，ID ${state.link.id}" }, LayoutParams(0,-1,1f))
        info.addView(text(" #${state.link.id} · ${if (state.invalid) "需重新选择" else state.source}", 11f, palette.secondaryText.toArgb()), LayoutParams(-2,-1))
        left.addView(info, LayoutParams(-1,dp(if (large) 47 else 27)))
        val buttons = LinearLayout(context).apply { orientation = HORIZONTAL; setPadding(dp(6),0,dp(4),0) }
        buttons.addView(button("通道订单", 1f) { open("orders") }.apply { isEnabled = !state.invalid; alpha = if (isEnabled) 1f else .38f })
        val rate = if (state.loading) "更新中…" else if (state.error != null) "获取失败" else state.snapshot?.rateText ?: "—"
        val stamp = state.snapshot?.updated?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it)) }.orEmpty()
        val rateLabel = "成功率 $rate\n近${state.window.label}${if (stamp.isBlank()) "" else " · $stamp"}"
        buttons.addView(button(rateLabel, if (large) 1f else 1.35f) { open("stats") }.apply { textSize = 12f * palette.fontScale; isEnabled = !state.invalid; alpha = if (isEnabled) 1f else .38f })
        buttons.addView(button("编辑通道", 1f) { open("edit") }.apply { isEnabled = !state.invalid; alpha = if (isEnabled) 1f else .38f })
        left.addView(buttons, LayoutParams(-1,dp(height - if (large) 48 else 28)))
        body.addView(left, LayoutParams(0,-1,1f))
        body.addView(text(if (state.invalid) "重新\n选择" else "⇄\n切换", 12f, palette.accentText.toArgb()).apply { gravity = Gravity.CENTER; contentDescription = "切换支付通道，按钮"; isFocusable = true; setOnClickListener { open("select") } }, LayoutParams(dp(48),-1))
        addView(body, LayoutParams(-1,dp(height-1)))
    }
}
