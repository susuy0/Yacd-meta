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
        if (kind == "merchant-more") {
            val merchant = state.merchant ?: return
            val scope = state.scope ?: return
            runCatching { model.bridge.call("ui.channel.open", org.json.JSONObject().put("kind", kind).put("id", merchant.uid).put("dialog", scope.dialog).put("topic", scope.topic).toString()) }
                .onFailure { Toast.makeText(context, it.message ?: "会话已变化，请重新打开", Toast.LENGTH_LONG).show() }
            return
        }
        val resolvedKind = when { kind.startsWith("merchant") -> kind; kind == "edit" && state.link == null && state.channels.isNotEmpty() -> "edit-select"; state.targets.isEmpty() && kind != "select" -> "select"; else -> kind }
        val view = ChannelSheetView(context, model, resolvedKind, host)
        sheet = view; host.showSheet(view, resolvedKind)
    }
    private fun text(value: String, size: Float, color: Int = palette.text.toArgb()) = TextView(context).apply {
        text = value; textSize = size * palette.fontScale; setTextColor(color); gravity = Gravity.CENTER_VERTICAL
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    /** Soft accent-tinted pill: reads as a control without the hard white boxes. The view stays 48dp tall for touch; only the drawn pill is smaller. */
    private fun pill(verticalInset: Int): android.graphics.drawable.Drawable {
        val tint = GradientDrawable().apply { setColor((palette.accentText.toArgb() and 0x00FFFFFF) or 0x1F000000); cornerRadius = dp(18).toFloat() }
        val mask = GradientDrawable().apply { setColor(android.graphics.Color.BLACK); cornerRadius = dp(18).toFloat() }
        val ripple = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf((palette.accentText.toArgb() and 0x00FFFFFF) or 0x33000000), null, mask)
        return android.graphics.drawable.InsetDrawable(android.graphics.drawable.LayerDrawable(arrayOf(tint, ripple)), 0, verticalInset, 0, verticalInset)
    }
    /**
     * Rows draw no outline of their own. The chat already paints a white, rounded-top panel behind the strip; an extra bordered capsule
     * on top of it read as a double outline, so the controls now sit directly on that panel.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun capsule(verticalInset: Int = 6, horizontalInset: Int = 8): android.graphics.drawable.Drawable = android.graphics.drawable.ColorDrawable(0)
    /** Plain accent text action inside a capsule; the ripple is a 36dp pill inside the 48dp touch target. */
    private fun capsuleAction(label: String, kind: String) = text(label, 13f, palette.accentText.toArgb()).apply {
        gravity = Gravity.CENTER; isFocusable = true; isClickable = true; minWidth = dp(48); setPadding(dp(8), 0, dp(8), 0)
        contentDescription = "$label，按钮"; typeface = android.graphics.Typeface.create(typeface, android.graphics.Typeface.BOLD)
        val mask = GradientDrawable().apply { setColor(android.graphics.Color.BLACK); cornerRadius = dp(18).toFloat() }
        background = android.graphics.drawable.InsetDrawable(android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf((palette.accentText.toArgb() and 0x00FFFFFF) or 0x33000000), null, mask), 0, dp(6), 0, dp(6))
        setOnClickListener { open(kind) }
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
    }
    private fun button(label: String, weight: Float, action: () -> Unit) = text(label, 13f, palette.accentText.toArgb()).apply {
        gravity = Gravity.CENTER; isFocusable = true; isClickable = true; contentDescription = label.replace('\n', ' ') + "，按钮"
        typeface = android.graphics.Typeface.create(typeface, android.graphics.Typeface.BOLD)
        background = pill(dp(6))
        setOnClickListener { action() }
        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply { setMargins(dp(2), 0, dp(2), 0) }
    }
    private fun addMerchantRow(state: ChannelShortcutState, height: Int, large: Boolean, foldSelect: Boolean = false) {
        val merchant = state.merchant
        if (merchant == null) {
            val label = when { state.merchantLoading -> "正在识别商户 · TG ${state.telegramId}"; state.merchantCandidates.isNotEmpty() -> "选择商户（${state.merchantCandidates.size}） · TG ${state.telegramId}"; else -> "${state.merchantError ?: "识别商户"} · 重试" }
            addView(text(label, 13f, palette.accentText.toArgb()).apply { setPadding(dp(24),0,dp(24),0); background = capsule(if (height >= 48) 6 else 2); isFocusable=true; contentDescription=label+"，按钮"; setOnClickListener { if(state.merchantCandidates.isNotEmpty()) open("merchant-select") else model.refreshMerchant() } }, LayoutParams(-1,dp(height)))
            return
        }
        val name = merchant.username.ifBlank { "当前商户" }
        val row = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = capsule(); setPadding(dp(14), 0, dp(14), 0) }
        row.addView(IdentityLabel(context, name, "ID ${merchant.uid} · TG ${state.telegramId}", palette.secondaryText.toArgb()).apply {
            textSize = 13f * palette.fontScale; setTextColor(palette.text.toArgb()); gravity = Gravity.CENTER_VERTICAL
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES; isFocusable = true; isClickable = true
            contentDescription = "当前商户 ${merchant.username}，商户ID ${merchant.uid}，Telegram ID ${state.telegramId}，点击查看详情"
            setOnClickListener { open("merchant-more") }
        }, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        listOf("订单" to "merchant-orders", "结算" to "merchant-settlements", "域名" to "merchant-domains").forEach { (label, kind) -> row.addView(capsuleAction(label, kind)) }
        if (foldSelect) {
            row.addView(capsuleAction("通道 ▾", "select").apply {
                contentDescription = "选择支付通道，按钮；长按隐藏此话题的通道入口"
                setPadding(dp(12), 0, dp(12), 0); background = pill(dp(6))
                (layoutParams as LayoutParams).leftMargin = dp(4)
                setOnLongClickListener { model.hide(); true }
            })
            // Separate file: the channel store's own prefs file triggers a full strip reload on every write.
            val prefs = context.applicationContext.getSharedPreferences("futoon_channel_hints", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("hideHintShown", false)) {
                prefs.edit().putBoolean("hideHintShown", true).apply()
                post { Toast.makeText(context, "长按「通道」可隐藏此入口，可在 Futoonpay 设置里恢复", Toast.LENGTH_LONG).show() }
            }
        }
        addView(row, LayoutParams(-1, dp(height)))
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) { super.onConfigurationChanged(newConfig); if (!closed) render(model.state.value) }
    internal fun render(state: ChannelShortcutState) {
        if (closed) return
        removeAllViews(); setBackgroundColor(0)
        val scale = resources.configuration.fontScale * palette.fontScale
        val large = scale >= 1.3f
        val linked = state.targets.isNotEmpty()
        val merchantRow = state.telegramId.isNotBlank()
        // With a known merchant and no linked channel, "choose channel" folds into the merchant capsule instead of a second row.
        val foldSelect = state.merchant != null && !linked && (state.scope?.dialog ?: 0) < 0
        val showChannels = (linked || !merchantRow || (state.scope?.dialog ?: 0) < 0) && !foldSelect
        val compact = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && resources.configuration.screenWidthDp >= 560
        val channelHeight = if (!showChannels) 0 else if (!linked || compact) 48 else if (large) (48 * scale).toInt() + 47 else 75
        val merchantHeight = if (!merchantRow) 0 else if (state.merchant == null) (if (state.merchantError != null && !state.merchantLoading) 36 else 48) else if (large) (48 * scale).toInt() else 48
        val height = when {
            state.loginRequired -> 49
            state.visible -> 1 + channelHeight + merchantHeight
            else -> 0
        }
        visibility = if (height == 0) GONE else VISIBLE
        if (lastHeight != dp(height)) { lastHeight = dp(height); host.heightChanged(lastHeight) }
        if (height == 0) return
        addView(View(context), LayoutParams(-1, dp(1)))
        if (state.loginRequired) {
            val scope = state.scope ?: return
            val login = text("会话快捷栏 · 登录富通后台", 13f, palette.accentText.toArgb()).apply {
                setPadding(dp(24), 0, dp(24), 0); background = capsule()
                isFocusable = true
                setOnClickListener {
                    runCatching { model.bridge.call("ui.channel.login", org.json.JSONObject().put("dialog", scope.dialog).put("topic", scope.topic).toString()) }
                        .onFailure { Toast.makeText(context, "会话已变化，请重新打开", Toast.LENGTH_SHORT).show() }
                }
            }
            addView(login, LayoutParams(-1, dp(48)))
            return
        }
        if (merchantRow) addMerchantRow(state, merchantHeight, large, foldSelect)
        if (!showChannels) return
        val current = state.link ?: ChannelLink("", "全部关联通道（${state.channels.size}）")
        if (!linked) {
            val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            row.background = capsule(); row.setPadding(dp(14), 0, dp(10), 0)
            row.addView(text("选择支付通道", 14f, palette.accentText.toArgb()).apply { typeface = android.graphics.Typeface.create(typeface, android.graphics.Typeface.BOLD); setOnClickListener { open("select") }; isFocusable = true; isClickable = true; contentDescription = "选择支付通道，按钮" }, LayoutParams(0,-1,1f))
            row.addView(text("×", 24f, palette.secondaryText.toArgb()).apply { gravity = Gravity.CENTER; contentDescription = "隐藏此话题的通道入口，可在 Futoonpay 设置恢复"; setOnClickListener { model.hide() }; isFocusable = true }, LayoutParams(dp(48),-1))
            addView(row, LayoutParams(-1,dp(48))); return
        }
        if (compact) {
            val row=LinearLayout(context).apply{orientation=HORIZONTAL;background=capsule();setPadding(dp(12),0,dp(12),0)}
            row.addView(text("${current.name}${if(current.id.isBlank()) "" else " #${current.id}"}",12f).apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.MIDDLE;contentDescription="当前通道 ${current.name}，ID ${current.id}，点击切换";setOnClickListener{open("select")};isFocusable=true},LayoutParams(0,dp(48),1f))
            row.addView(button("通道订单",.9f){open("orders")}.apply{isEnabled=!state.invalid})
            row.addView(button("成功率 ${if(state.loading) "更新中" else if(state.error!=null) "获取失败" else state.snapshot?.rateText?:"—"}",1.2f){open("stats")}.apply{isEnabled=!state.invalid})
            row.addView(button("编辑通道",.9f){open("edit")}.apply{isEnabled=!state.invalid})
            row.addView(text("切换",12f,palette.accentText.toArgb()).apply{gravity=Gravity.CENTER;setOnClickListener{open("select")};isFocusable=true;contentDescription="切换支付通道，按钮"},LayoutParams(dp(48),dp(48)))
            addView(row,LayoutParams(-1,dp(48)));return
        }
        val body = LinearLayout(context).apply { orientation = HORIZONTAL; background = capsule(2, 8) }
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

/** Two lines in one label: bold merchant name and a quiet "ID · TG" line. Each line is ellipsized on its own, so a long name never pushes the ID out of sight. */
private class IdentityLabel(context: Context, private val name: String, private val sub: String, private val subColor: Int) : TextView(context) {
    private fun compose(first: CharSequence, second: CharSequence) = android.text.SpannableStringBuilder().apply {
        append(first); setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, length, 0)
        val start = length; append("\n").append(second)
        setSpan(android.text.style.ForegroundColorSpan(subColor), start, length, 0)
        setSpan(android.text.style.RelativeSizeSpan(.8f), start, length, 0)
    }
    init { text = compose(name, sub); maxLines = 2 }
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val room = (w - compoundPaddingLeft - compoundPaddingRight).toFloat()
        if (room <= 0f) return
        val bold = android.text.TextPaint(paint).apply { typeface = android.graphics.Typeface.create(typeface, android.graphics.Typeface.BOLD) }
        val small = android.text.TextPaint(paint).apply { textSize *= .8f }
        text = compose(android.text.TextUtils.ellipsize(name, bold, room, android.text.TextUtils.TruncateAt.END), android.text.TextUtils.ellipsize(sub, small, room, android.text.TextUtils.TruncateAt.END))
    }
}
