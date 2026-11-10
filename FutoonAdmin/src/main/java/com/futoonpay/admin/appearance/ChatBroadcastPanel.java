package com.futoonpay.admin.appearance;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/**
 * The three screens of a group broadcast, drawn as one view: preview with a typed confirmation, progress with a stop button,
 * and a result that names every group that did not get the message. The host owns the sending; this view only shows state and reports taps.
 */
public final class ChatBroadcastPanel extends LinearLayout {
    public static final int QUEUED = 0, SENDING = 1, SENT = 2, FAILED = 3, NOT_SENT = 4;
    public interface Listener { void onConfirm(); void onBack(); void onStop(); void onRetry(); void onCopyFailed(String names); void onDone(); }
    public static final class Row {
        public final String name; public final int state; public final String reason;
        public Row(String name, int state, String reason) { this.name = name; this.state = state; this.reason = reason == null ? "" : reason; }
    }
    private final boolean dark;
    private final Listener listener;
    public ChatBroadcastPanel(Context context, boolean dark, Listener listener) {
        super(context);
        this.dark = dark; this.listener = listener;
        setOrientation(VERTICAL); setPadding(dp(20), dp(18), dp(20), dp(16)); setBackgroundColor(ChatLook.surface(dark));
    }

    /** What the groups will see, who gets it, and the typed number that unlocks sending. */
    public void showPreview(String text, List<String> names, int skipped, int gapSeconds) {
        removeAllViews();
        final int count = names.size();
        addView(label("群里看到的样子"));
        TextView bubble = body(text.length() > 300 ? text.substring(0, 300) + "…" : text, ChatLook.onOutgoing(dark), 14);
        bubble.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(ChatLook.outgoing(dark)); bg.setCornerRadius(dp(12)); bubble.setBackground(bg);
        addView(bubble, params(-2, -2, 6, 0));
        LinearLayout table = card();
        table.addView(keyValue("发送", count + " 个群，每 " + gapSeconds + " 秒一个，约 " + Math.max(0, (count - 1) * gapSeconds) + " 秒发完"));
        table.addView(keyValue("包括", summarize(names)));
        if (skipped > 0) table.addView(keyValue("排除", skipped + " 个私聊／频道／没有发言权限的聊天"));
        addView(table, params(-1, -2, 10, 0));
        addView(impact("这是对外发送的消息，" + count + " 个群里的所有成员都会收到。发出后只能逐个群手动删除，不能一键撤回；遇到任何发送失败会立即停止。"), params(-1, -2, 10, 0));
        final String target = String.valueOf(count);
        final TextView hint = body("输入完全一致后才能确认", ChatLook.secondary(dark), 12);
        addView(body("输入群数量「" + target + "」确认", ChatLook.secondary(dark), 12), params(-1, -2, 12, 0));
        final EditText input = new EditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER); input.setSingleLine(true); input.setTextSize(15); input.setTextColor(ChatLook.text(dark)); input.setContentDescription("输入群数量「" + target + "」确认");
        input.setPadding(dp(12), 0, dp(12), 0); input.setBackground(fieldBackground(ChatLook.incomingEdge(dark)));
        addView(input, params(-1, dp(48), 4, 0));
        addView(hint, params(-2, -2, 4, 0));
        final TextView confirm = button("确认发送 " + count + " 个群", true);
        confirm.setEnabled(false); confirm.setAlpha(.4f);
        confirm.setOnClickListener(v -> { if (confirm.isEnabled()) listener.onConfirm(); });
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) {
                String v = e.toString().trim(); boolean ok = v.equals(target), wrong = !v.isEmpty() && !ok;
                hint.setText(ok ? "已匹配" : wrong ? "和群数量「" + target + "」不一致，确认按钮不可点" : "输入完全一致后才能确认");
                hint.setTextColor(ok ? ChatLook.success(dark) : wrong ? danger() : ChatLook.secondary(dark));
                input.setBackground(fieldBackground(wrong ? danger() : ChatLook.incomingEdge(dark)));
                confirm.setEnabled(ok); confirm.setAlpha(ok ? 1f : .4f);
            }
        });
        addView(confirm, params(-1, dp(48), 14, 0));
        TextView back = link("返回修改"); back.setOnClickListener(v -> listener.onBack());
        addView(back, params(-1, dp(44), 4, 0));
    }

    public void showProgress(List<Row> rows) {
        removeAllViews();
        int done = 0, remaining = 0;
        for (Row r : rows) { if (r.state == SENT) done++; if (r.state == QUEUED || r.state == SENDING) remaining++; }
        TextView count = body(done + " / " + rows.size(), ChatLook.text(dark), 28); count.setTypeface(bold());
        addView(count, params(-2, -2, 0, 0));
        ProgressBar bar = new ProgressBar(getContext(), null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(Math.max(1, rows.size())); bar.setProgress(done);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(ChatLook.accent(dark)));
        addView(bar, params(-1, dp(6), 8, 0));
        addView(body("每个群之间留有间隔，被限速或失败会立即停下。可以先离开这里，发送会继续，结束后会再通知你。", ChatLook.secondary(dark), 12), params(-1, -2, 8, 0));
        addView(rowsCard(rows), params(-1, -2, 10, 0));
        TextView stop = button("停止，不发剩下的 " + Math.max(0, remaining - (hasSending(rows) ? 1 : 0)) + " 个", false);
        stop.setOnClickListener(v -> listener.onStop());
        addView(stop, params(-1, dp(48), 14, 0));
    }

    public void showResult(List<Row> rows, String stopReason, String startedAt, String endedAt) {
        removeAllViews();
        int sent = 0, failed = 0, notSent = 0;
        for (Row r : rows) { if (r.state == SENT) sent++; else if (r.state == FAILED) failed++; else notSent++; }
        final boolean allSent = failed == 0 && notSent == 0;
        LinearLayout chip = new LinearLayout(getContext()); chip.setOrientation(HORIZONTAL); chip.setGravity(Gravity.CENTER_VERTICAL); chip.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable cbg = new GradientDrawable(); cbg.setColor(ChatLook.field(dark)); cbg.setCornerRadius(dp(8)); chip.setBackground(cbg);
        TextView chipText = body((allSent ? "✓ 全部发出" : "⚠ " + (failed + notSent) + " 个群没有发出"), allSent ? ChatLook.success(dark) : danger(), 12); chipText.setTypeface(bold());
        chip.addView(chipText);
        addView(chip, params(-2, -2, 0, 0));
        TextView title = body(allSent ? rows.size() + " 个群都收到了" : sent + " 个已发出，" + failed + " 个失败" + (notSent > 0 ? "，" + notSent + " 个未发" : ""), ChatLook.text(dark), 20);
        title.setTypeface(bold()); addView(title, params(-2, -2, 10, 0));
        if (!allSent && !stopReason.isEmpty()) addView(body("停止原因：" + stopReason, ChatLook.secondary(dark), 13), params(-1, -2, 6, 0));
        if (allSent) {
            LinearLayout table = card();
            table.addView(keyValue("发送时间", startedAt + " 到 " + endedAt));
            addView(table, params(-1, -2, 10, 0));
        } else {
            LinearLayout list = card();
            for (Row r : rows) if (r.state == FAILED || r.state == NOT_SENT) list.addView(failureRow(r));
            addView(list, params(-1, -2, 10, 0));
            addView(body("已发出的 " + sent + " 个不会重复发送。", ChatLook.secondary(dark), 12), params(-1, -2, 8, 0));
        }
        final int retryable = failed + notSent;
        if (retryable > 0) {
            TextView retry = button("重试可重试的 " + retryable + " 个", true); retry.setOnClickListener(v -> listener.onRetry());
            addView(retry, params(-1, dp(48), 14, 0));
            TextView copy = link("复制失败名单");
            copy.setOnClickListener(v -> { StringBuilder s = new StringBuilder(); for (Row r : rows) if (r.state == FAILED || r.state == NOT_SENT) s.append(r.name).append('\n'); listener.onCopyFailed(s.toString().trim()); });
            addView(copy, params(-1, dp(44), 4, 0));
        } else {
            TextView done = button("完成", true); done.setOnClickListener(v -> listener.onDone());
            addView(done, params(-1, dp(48), 14, 0));
        }
    }

    private static boolean hasSending(List<Row> rows) { for (Row r : rows) if (r.state == SENDING) return true; return false; }
    private static String summarize(List<String> names) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < names.size() && i < 3; i++) { if (i > 0) s.append("、"); s.append(names.get(i)); }
        if (names.size() > 3) s.append(" 等 ").append(names.size()).append(" 个");
        return s.toString();
    }

    private View rowsCard(List<Row> rows) {
        LinearLayout list = new LinearLayout(getContext()); list.setOrientation(VERTICAL);
        for (Row r : rows) list.addView(progressRow(r));
        ScrollView scroll = new ScrollView(getContext()); scroll.addView(list);
        LinearLayout card = card(); card.addView(scroll, new LayoutParams(-1, Math.min(dp(rows.size() * 48), dp(240))));
        return card;
    }
    private View progressRow(Row r) {
        LinearLayout row = new LinearLayout(getContext()); row.setOrientation(HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, dp(10), 0, dp(10));
        TextView name = body(r.name, ChatLook.text(dark), 14); name.setSingleLine(true); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(name, new LayoutParams(0, -2, 1f));
        String state = r.state == SENT ? "已发出" : r.state == SENDING ? "发送中" : r.state == FAILED ? "失败" : r.state == NOT_SENT ? "未发送" : "排队中";
        int color = r.state == SENT ? ChatLook.success(dark) : r.state == FAILED ? danger() : ChatLook.secondary(dark);
        TextView s = body(state, color, 12); s.setTypeface(bold()); row.addView(s);
        row.setContentDescription(r.name + "，" + state);
        return row;
    }
    private View failureRow(Row r) {
        LinearLayout row = new LinearLayout(getContext()); row.setOrientation(VERTICAL); row.setPadding(0, dp(8), 0, dp(8));
        TextView name = body(r.name, ChatLook.text(dark), 14); name.setTypeface(bold()); row.addView(name);
        String why = r.state == NOT_SENT ? "没有发送" : r.reason.isEmpty() ? "发送失败" : r.reason;
        row.addView(body(why, r.state == FAILED ? danger() : ChatLook.secondary(dark), 12));
        return row;
    }

    private int danger() { return dark ? 0xfff87171 : 0xffdc2626; }
    private Typeface bold() { Typeface f = ChatFonts.semibold(); return f != null ? f : Typeface.create("sans-serif", Typeface.BOLD); }
    private int dp(float v) { return ChatChrome.dp(getContext(), v); }
    private LayoutParams params(int w, int h, int topDp, int bottomDp) { LayoutParams p = new LayoutParams(w, h); p.topMargin = dp(topDp); p.bottomMargin = dp(bottomDp); return p; }
    private TextView body(CharSequence text, int color, int sp) { TextView v = new TextView(getContext()); v.setText(text); v.setTextColor(color); v.setTextSize(sp); Typeface f = ChatFonts.regular(); if (f != null) v.setTypeface(f); return v; }
    private TextView label(String text) { return body(text, ChatLook.secondary(dark), 12); }
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(getContext()); c.setOrientation(VERTICAL); c.setPadding(dp(12), dp(4), dp(12), dp(4));
        c.setBackground(fieldBackground(ChatLook.incomingEdge(dark))); return c;
    }
    private GradientDrawable fieldBackground(int stroke) { GradientDrawable d = new GradientDrawable(); d.setColor(ChatLook.surface(dark)); d.setCornerRadius(dp(10)); d.setStroke(Math.max(1, dp(1)), stroke); return d; }
    private View keyValue(String key, String value) {
        LinearLayout row = new LinearLayout(getContext()); row.setOrientation(HORIZONTAL); row.setPadding(0, dp(8), 0, dp(8));
        TextView k = body(key, ChatLook.secondary(dark), 13); row.addView(k, new LayoutParams(dp(48), -2));
        TextView v = body(value, ChatLook.text(dark), 13); v.setGravity(Gravity.END); row.addView(v, new LayoutParams(0, -2, 1f));
        return row;
    }
    private View impact(String text) {
        LinearLayout box = new LinearLayout(getContext()); box.setOrientation(VERTICAL); box.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable d = new GradientDrawable(); d.setColor(ChatLook.field(dark)); d.setCornerRadius(dp(10)); box.setBackground(d);
        TextView head = body("⚠ 影响范围", ChatLook.text(dark), 13); head.setTypeface(bold()); box.addView(head);
        box.addView(body(text, ChatLook.text(dark), 13));
        return box;
    }
    private TextView button(String text, boolean primary) {
        TextView b = body(text, primary ? ChatLook.onAccent(dark) : danger(), 15); b.setTypeface(bold()); b.setGravity(Gravity.CENTER); b.setClickable(true); b.setFocusable(true);
        b.setContentDescription(text + "，按钮");
        GradientDrawable d = new GradientDrawable(); d.setCornerRadius(dp(10));
        if (primary) d.setColor(ChatLook.accent(dark)); else { d.setColor(ChatLook.surface(dark)); d.setStroke(Math.max(1, dp(1)), danger()); }
        b.setBackground(d); return b;
    }
    private TextView link(String text) {
        TextView t = body(text, ChatLook.text(dark), 14); t.setGravity(Gravity.CENTER); t.setPaintFlags(t.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        t.setClickable(true); t.setFocusable(true); t.setContentDescription(text + "，按钮"); return t;
    }
}
