package com.futoonpay.admin.appearance;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Measures the label before placing it; the native host owns the click and badge. */
public final class ChatTabGeometry {
    public static final float LABEL_SP = 10f;
    private ChatTabGeometry() {}

    public static void prepare(View icon, TextView label, int width, int height) {
        int edge = ChatChrome.dp(label.getContext(), 4);
        int gap = ChatChrome.dp(label.getContext(), 3);
        int labelWidth = Math.max(0, width - edge * 2);
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, LABEL_SP);
        label.setIncludeFontPadding(false);
        label.measure(View.MeasureSpec.makeMeasureSpec(labelWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int labelHeight = Math.max(label.getMeasuredHeight(), label.getLineHeight());
        if (labelHeight > ChatChrome.dp(label.getContext(), 20)) {
            edge = gap = ChatChrome.dp(label.getContext(), 2);
        }
        int iconSize = Math.max(0, Math.min(ChatChrome.dp(label.getContext(), 28), height - edge * 2 - gap - labelHeight));
        int blockHeight = iconSize + gap + labelHeight;
        int top = Math.max(0, (height - blockHeight) / 2);
        FrameLayout.LayoutParams image = (FrameLayout.LayoutParams) icon.getLayoutParams();
        image.width = image.height = iconSize;
        image.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        image.topMargin = top;
        FrameLayout.LayoutParams text = (FrameLayout.LayoutParams) label.getLayoutParams();
        text.width = -1;
        text.height = labelHeight;
        text.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        text.topMargin = top + iconSize + gap;
        text.leftMargin = text.rightMargin = edge;
    }
}
