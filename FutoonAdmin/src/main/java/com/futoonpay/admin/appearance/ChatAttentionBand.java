package com.futoonpay.admin.appearance;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.function.Consumer;

/**
 * "待我处理" band under the inbox search field: one card per thing that needs a hand.
 * Counts of -1 mean "not loaded yet" and render as a dash; cards with 0 are dropped, and when nothing is left the
 * band collapses to a single reassurance line instead of a row of zeros.
 */
public final class ChatAttentionBand extends FrameLayout {
    public static final class Item {
        public final String key, label, detail; public final int count;
        public Item(String key, String label, int count, String detail) { this.key = key; this.label = label; this.count = count; this.detail = detail == null ? "" : detail; }
    }
    private static final int HEADING = 34, CARD_HEIGHT = 100, CARD_WIDTH = 92, GAP = 10, SIDE = 16, BOTTOM = 12;
    private final TextView heading, total, empty;
    private final HorizontalScrollView scroller;
    private final LinearLayout row;
    private final Paint fade = new Paint();
    private boolean dark, hasCards;
    private Consumer<String> callback = key -> {};
    private List<Item> items = java.util.Collections.emptyList();
    private String signature;

    public ChatAttentionBand(Context context, boolean dark) {
        super(context);
        this.dark = dark;
        setWillNotDraw(false);
        heading = text(15, true); heading.setText("待我处理"); heading.setGravity(Gravity.CENTER_VERTICAL);
        total = text(13, false); total.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        empty = text(14, false); empty.setGravity(Gravity.CENTER_VERTICAL); empty.setText("都处理完了，新的待办会出现在这里");
        scroller = new HorizontalScrollView(context); scroller.setHorizontalScrollBarEnabled(false); scroller.setOverScrollMode(OVER_SCROLL_NEVER); scroller.setOnScrollChangeListener((v, x, y, ox, oy) -> invalidate());
        row = new LinearLayout(context); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(dp(SIDE), 0, dp(SIDE - GAP), 0);
        scroller.addView(row, new LayoutParams(-2, -1));
        addView(heading, new LayoutParams(-2, dp(HEADING), Gravity.TOP | Gravity.START)); ((LayoutParams) heading.getLayoutParams()).leftMargin = dp(SIDE);
        addView(total, new LayoutParams(-2, dp(HEADING), Gravity.TOP | Gravity.END)); ((LayoutParams) total.getLayoutParams()).rightMargin = dp(SIDE);
        addView(scroller, new LayoutParams(-1, dp(CARD_HEIGHT), Gravity.TOP)); ((LayoutParams) scroller.getLayoutParams()).topMargin = dp(HEADING);
        addView(empty, new LayoutParams(-1, dp(CARD_HEIGHT / 2), Gravity.TOP)); ((LayoutParams) empty.getLayoutParams()).topMargin = dp(HEADING); ((LayoutParams) empty.getLayoutParams()).leftMargin = dp(SIDE);
        recolor();
    }

    /** Total height in dp, which the inbox header adds to its fixed bands. */
    public static int height(float fontScale, boolean emptyState) {
        float scale = Math.max(1f, fontScale);
        int heading = Math.round(HEADING * Math.min(scale, 1.3f));
        return emptyState ? heading + CARD_HEIGHT / 2 + 4 : heading + Math.round(CARD_HEIGHT * Math.min(scale, 1.3f)) + BOTTOM;
    }
    public boolean showsCards() { return hasCards; }
    public int heightDp() { return height(getResources().getConfiguration().fontScale, !hasCards); }

    public void setOnCard(Consumer<String> callback) { this.callback = callback == null ? key -> {} : callback; }
    public void setDark(boolean value) { if (dark == value) return; dark = value; recolor(); signature = null; setItems(items); }

    public void setItems(List<Item> list) {
        items = list == null ? java.util.Collections.emptyList() : list;
        // The host re-sends the same items on every dialog update; rebuilding would reset the row's scroll position.
        StringBuilder sig = new StringBuilder();
        for (Item item : items) sig.append(item.key).append('|').append(item.label).append('|').append(item.count).append('|').append(item.detail).append(';');
        if (sig.toString().equals(signature)) return;
        signature = sig.toString();
        row.removeAllViews();
        int sum = 0; boolean unknown = false;
        for (Item item : items) {
            if (item.count == 0) continue;
            if (item.count < 0) unknown = true; else sum += item.count;
            row.addView(card(item), cardParams());
        }
        hasCards = row.getChildCount() > 0;
        scroller.setVisibility(hasCards ? VISIBLE : GONE);
        empty.setVisibility(hasCards ? GONE : VISIBLE);
        total.setVisibility(hasCards ? VISIBLE : GONE);
        total.setText(unknown && sum == 0 ? "—" : "共 " + format(sum) + " 项");
        scroller.scrollTo(0, 0);
        requestLayout(); invalidate();
    }

    private LinearLayout.LayoutParams cardParams() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(CARD_WIDTH), -1); p.setMarginEnd(dp(GAP)); return p; }

    private View card(Item item) {
        final String key = item.key;
        LinearLayout card = new LinearLayout(getContext()) {
            @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) { super.onInitializeAccessibilityNodeInfo(info); info.setClassName("android.widget.Button"); }
        };
        card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(12), dp(10), dp(8), dp(8));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(ChatLook.surface(dark)); bg.setCornerRadius(dp(12)); bg.setStroke(Math.max(1, dp(1)), ChatLook.incomingEdge(dark));
        card.setBackground(bg); card.setClickable(true); card.setFocusable(true);
        card.setContentDescription(item.label + "，" + (item.count < 0 ? "加载中" : item.count + " 项") + (item.detail.isEmpty() ? "" : "，" + item.detail));
        card.setOnClickListener(v -> callback.accept(key));
        View dot = new View(getContext()); GradientDrawable d = new GradientDrawable(); d.setColor(ChatLook.badge(dark)); d.setCornerRadius(dp(3)); dot.setBackground(d);
        card.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        TextView number = text(24, true); number.setText(item.count < 0 ? "—" : format(item.count)); number.setIncludeFontPadding(false); number.setTextColor(ChatLook.text(dark)); number.setSingleLine(true);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-2, -2); np.topMargin = dp(4); card.addView(number, np);
        TextView label = text(13, true); label.setText(item.label); label.setTextColor(ChatLook.text(dark)); label.setSingleLine(true); label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(label, new LinearLayout.LayoutParams(-1, -2));
        TextView detail = text(11, false); detail.setText(item.detail); detail.setSingleLine(true); detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(detail, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    /** 10,000 and up switch to 万 with at most one decimal, matching the extreme-data sheet. */
    public static String format(int value) {
        if (value < 10000) return String.valueOf(value);
        double wan = value / 10000d;
        String text = wan >= 100 ? String.valueOf(Math.round(wan)) : String.format(java.util.Locale.US, "%.1f", Math.floor(wan * 10) / 10);
        if (text.endsWith(".0")) text = text.substring(0, text.length() - 2);
        return text + "万";
    }

    private TextView text(int sp, boolean bold) {
        TextView v = new TextView(getContext()); v.setTextSize(sp);
        Typeface face = bold ? ChatFonts.semibold() : ChatFonts.regular();
        v.setTypeface(face != null ? face : Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return v;
    }
    private void recolor() {
        setBackgroundColor(ChatLook.background(dark));
        heading.setTextColor(ChatLook.text(dark)); total.setTextColor(ChatLook.secondary(dark)); empty.setTextColor(ChatLook.secondary(dark));
    }
    private int dp(float value) { return ChatChrome.dp(getContext(), value); }

    @Override protected void onMeasure(int ws, int hs) {
        int h = dp(heightDp());
        int scrollerHeight = hasCards ? h - dp(HEADING) : 0;
        ((LayoutParams) scroller.getLayoutParams()).height = Math.max(0, scrollerHeight - dp(BOTTOM));
        super.onMeasure(ws, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
    }

    /** A soft fade on the right edge says the row scrolls; it only shows while more cards are hidden. */
    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (!hasCards || row.getChildCount() == 0) return;
        int contentWidth = row.getWidth(), viewport = scroller.getWidth();
        if (contentWidth - scroller.getScrollX() <= viewport + dp(2)) return;
        int width = dp(28), right = getWidth(), top = scroller.getTop(), bottom = scroller.getBottom();
        int base = ChatLook.background(dark);
        fade.setShader(new LinearGradient(right - width, 0, right, 0, base & 0x00ffffff, base, Shader.TileMode.CLAMP));
        canvas.drawRect(right - width, top, right, bottom, fade);
    }
    @Override public boolean onInterceptTouchEvent(android.view.MotionEvent ev) { return false; }
}
