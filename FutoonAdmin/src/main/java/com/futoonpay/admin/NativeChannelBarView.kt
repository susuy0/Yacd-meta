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
    @JvmOverloads fun bindChat(label: String, dialog: Long, topic: Long, telegramId: String = "", identitySource: String = "") {
        val previous = model.state.value
        model.bind(label, dialog, topic, telegramId, identitySource)
        if (previous.scope != null && (previous.scope != model.state.value.scope || previous.telegramId != model.state.value.telegramId)) { sheet?.forceClose(); host.closeSheet() }
    }
    fun onShown() { model.resume(); sheet?.onShown() }
    fun onHidden() { model.pause(); sheet?.onHidden() }
    fun close() { if (closed) return; closed = true; sheet?.forceClose(); host.closeSheet(); model.close(); jobs.cancel() }
    fun fromOrder(channel: String) { if (!closed) model.fromOrder(channel) }
    fun reloadSettings() { if (!closed) { bindChat(model.state.value.label, model.state.value.scope?.dialog ?: 0, model.state.value.scope?.topic ?: 0, model.state.value.telegramId, model.state.value.identitySource); model.reloadSettings(); if (!model.state.value.visible) { sheet?.forceClose(); host.closeSheet() } } }
    fun setThemeColors(json: String) { palette = FutoonPalette.parse(json); if (!closed) render(model.state.value); sheet?.setThemeColors(json) }
    fun sheetDismissed() { sheet?.close(); sheet = null }
    internal fun open(kind: String) {
        reloadSettings()
        if (closed || sheet != null || !model.state.value.visible) return
        val state = model.state.value
        val resolvedKind = when { kind.startsWith("merchant") -> kind; kind == "edit" && state.link == null && state.channels.isNotEmpty() -> "edit-select"; state.targets.isEmpty() && kind != "select" -> "select"; else -> kind }
        val view = ChannelSheetView(context, model, resolvedKind, host)
        sheet = view; host.showSheet(view, resolvedKind)
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
    private fun addMerchantRow(state: ChannelShortcutState, height: Int, large: Boolean) {
        val merchant = state.merchant
        if (merchant == null) {
            val label = when { state.merchantLoading -> "正在识别商户 · TG ${state.telegramId}"; state.merchantCandidates.isNotEmpty() -> "选择商户（${state.merchantCandidates.size}） · TG ${state.telegramId}"; else -> "${state.merchantError ?: "识别商户"} · 重试" }
            addView(text(label, 13f, palette.accentText.toArgb()).apply { setPadding(dp(12),0,dp(12),0); isFocusable=true; contentDescription=label+"，按钮"; setOnClickListener { if(state.merchantCandidates.isNotEmpty()) open("merchant-select") else model.refreshMerchant() } }, LayoutParams(-1,dp(height)))
            return
        }
        val column=LinearLayout(context).apply { orientation=VERTICAL }
        column.addView(text("${merchant.username.ifBlank { "当前商户" }} · ID ${merchant.uid} · TG ${state.telegramId}",13f).apply { setPadding(dp(10),0,dp(10),0);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END;contentDescription="当前商户 ${merchant.username}，商户ID ${merchant.uid}，Telegram ID ${state.telegramId}" },LayoutParams(-1,dp(if(large)47 else 27)))
        val actions=LinearLayout(context).apply { orientation=HORIZONTAL;setPadding(dp(4),0,dp(4),0) }
        listOf("编辑商户" to "merchant-edit", "商户订单" to "merchant-orders", "结算" to "merchant-settlements", "域名审核" to "merchant-domains").forEach { (label,kind) -> actions.addView(button(label,1f){open(kind)}) }
        column.addView(actions,LayoutParams(-1,dp(height-if(large)47 else 27)))
        addView(column,LayoutParams(-1,dp(height)))
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) { super.onConfigurationChanged(newConfig); if (!closed) render(model.state.value) }
    internal fun render(state: ChannelShortcutState) {
        if (closed) return
        removeAllViews(); setBackgroundColor(palette.surface.toArgb())
        val scale = resources.configuration.fontScale * palette.fontScale
        val large = scale >= 1.3f
        val linked = state.targets.isNotEmpty()
        val merchantRow = state.telegramId.isNotBlank()
        val showChannels = linked || !merchantRow || (state.scope?.dialog ?: 0) < 0
        val compact = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && resources.configuration.screenWidthDp >= 560
        val channelHeight = if (!showChannels) 0 else if (!linked || compact) 48 else if (large) (48 * scale).toInt() + 47 else 75
        val merchantHeight = if (!merchantRow) 0 else if (state.merchant == null) 48 else if (large) (48 * scale).toInt() + 47 else 75
        val height = if (!state.visible) 0 else 1 + channelHeight + merchantHeight
        visibility = if (height == 0) GONE else VISIBLE
        if (lastHeight != dp(height)) { lastHeight = dp(height); host.heightChanged(lastHeight) }
        if (height == 0) return
        addView(View(context).apply { setBackgroundColor(palette.divider.toArgb()) }, LayoutParams(-1, dp(1)))
        if (merchantRow) addMerchantRow(state, merchantHeight, large)
        if (!showChannels) return
        val current = state.link ?: ChannelLink("", "全部关联通道（${state.channels.size}）")
        if (!linked) {
            val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(text("选择支付通道", 14f, palette.accentText.toArgb()).apply { setPadding(dp(12),0,0,0); setOnClickListener { open("select") }; isFocusable = true; contentDescription = "选择支付通道，按钮" }, LayoutParams(0,-1,1f))
            row.addView(text("×", 24f, palette.secondaryText.toArgb()).apply { gravity = Gravity.CENTER; contentDescription = "隐藏此话题的通道入口，可在 Futoonpay 设置恢复"; setOnClickListener { model.hide() }; isFocusable = true }, LayoutParams(dp(48),-1))
            addView(row, LayoutParams(-1,dp(48))); return
        }
        if (compact) {
            val row=LinearLayout(context).apply{orientation=HORIZONTAL;setPadding(dp(4),0,dp(4),0)}
            row.addView(text("${current.name}${if(current.id.isBlank()) "" else " #${current.id}"}",12f).apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.MIDDLE;contentDescription="当前通道 ${current.name}，ID ${current.id}，点击切换";setOnClickListener{open("select")};isFocusable=true},LayoutParams(0,dp(48),1f))
            row.addView(button("通道订单",.9f){open("orders")}.apply{isEnabled=!state.invalid})
            row.addView(button("成功率 ${if(state.loading) "更新中" else if(state.error!=null) "获取失败" else state.snapshot?.rateText?:"—"}",1.2f){open("stats")}.apply{isEnabled=!state.invalid})
            row.addView(button("编辑通道",.9f){open("edit")}.apply{isEnabled=!state.invalid})
            row.addView(text("切换",12f,palette.accentText.toArgb()).apply{gravity=Gravity.CENTER;setOnClickListener{open("select")};isFocusable=true;contentDescription="切换支付通道，按钮"},LayoutParams(dp(48),dp(48)))
            addView(row,LayoutParams(-1,dp(48)));return
        }
        val body = LinearLayout(context).apply { orientation = HORIZONTAL }
        val left = LinearLayout(context).apply { orientation = VERTICAL }
        val info = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10),0,dp(4),0) }
        // Identity is a readable label, not a 27dp click target. Full name is repeated in the switch sheet.
        info.addView(text(current.name, 14f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; contentDescription = "当前通道 ${current.name}，ID ${current.id}" }, LayoutParams(0,-1,1f))
        info.addView(text(if(current.id.isBlank()) "" else " #${current.id} ", 11f, palette.secondaryText.toArgb()), LayoutParams(-2,-1))
        info.addView(text(if(state.invalid) "需重新选择" else state.source, 11f, if(state.source == "已记住") palette.secondaryText.toArgb() else palette.accentText.toArgb()).apply {
            setPadding(dp(4),0,dp(4),0)
            background=GradientDrawable().apply { cornerRadius=dp(8).toFloat(); setStroke(dp(1), if(state.source == "已记住") palette.divider.toArgb() else palette.accentText.toArgb()) }
        }, LayoutParams(-2,dp(20)))
        left.addView(info, LayoutParams(-1,dp(if (large) 47 else 27)))
        val buttons = LinearLayout(context).apply { orientation = HORIZONTAL; setPadding(dp(6),0,dp(4),0) }
        buttons.addView(button("通道订单", 1f) { open("orders") }.apply { isEnabled = !state.invalid; alpha = if (isEnabled) 1f else .38f })
        val rate = if (state.link == null) "${state.channels.size}个通道" else if (state.loading) "更新中…" else if (state.error != null) "获取失败" else state.snapshot?.rateText ?: "—"
        val stamp = state.snapshot?.updated?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it)) }.orEmpty()
        val stale = state.snapshot?.updated?.let { System.currentTimeMillis() - it > 600_000 } == true
        val rateLabel = "成功率 $rate\n近${state.window.label}${if (stamp.isBlank()) "" else " · $stamp"}${if (stale) " 已过期" else ""}"
        buttons.addView(button(rateLabel, if (large) 1f else 1.35f) { open("stats") }.apply { textSize = 12f * palette.fontScale; isEnabled = !state.invalid; alpha = if (isEnabled) 1f else .38f })
        buttons.addView(button("编辑通道", 1f) { open("edit") }.apply { isEnabled = !state.invalid; alpha = if (isEnabled) 1f else .38f })
        left.addView(buttons, LayoutParams(-1,dp(channelHeight - if (large) 47 else 27)))
        body.addView(left, LayoutParams(0,-1,1f))
        body.addView(text(if (state.invalid) "重新\n选择" else "⇄\n切换", 12f, palette.accentText.toArgb()).apply { gravity = Gravity.CENTER; contentDescription = "切换支付通道，按钮"; isFocusable = true; setOnClickListener { open("select") } }, LayoutParams(dp(48),-1))
        addView(body, LayoutParams(-1,dp(channelHeight)))
    }
}
