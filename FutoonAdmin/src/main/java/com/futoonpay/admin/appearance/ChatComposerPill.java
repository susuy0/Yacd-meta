package com.futoonpay.admin.appearance;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * The composer's white pill. Measured on device: the host field frame is 68dp tall for one line (48dp control
 * row plus 10dp above and below). The pill is 32dp, sits on the same centre line as the plus and send buttons
 * (8dp above the frame bottom) and grows upward by whatever the frame gains for extra text lines.
 */
public final class ChatComposerPill extends Drawable {
    public static final float ONE_LINE_FRAME_DP = 68f, PILL_DP = 32f, BOTTOM_GAP_DP = 8f;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final RectF rect = new RectF();

    public ChatComposerPill(int fillColor, int strokeColor, float density) {
        this.density = density;
        fill.setColor(fillColor);
        stroke.setColor(strokeColor);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1f, density * 0.5f));
    }

    /** Pill bounds inside a frame, in pixels; pure so it can be unit tested. */
    public static void pillRect(float frameLeft, float frameTop, float frameRight, float frameBottom, float density, RectF out) {
        float extra = Math.max(0f, (frameBottom - frameTop) - ONE_LINE_FRAME_DP * density);
        float bottom = frameBottom - BOTTOM_GAP_DP * density;
        out.set(frameLeft, bottom - PILL_DP * density - extra, frameRight, bottom);
    }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        pillRect(b.left, b.top, b.right, b.bottom, density, rect);
        float r = Math.min(rect.height() / 2f, PILL_DP * density / 2f * 1.0f);
        r = Math.max(r, PILL_DP * density / 2f);
        canvas.drawRoundRect(rect, r, r, fill);
        rect.inset(stroke.getStrokeWidth() / 2f, stroke.getStrokeWidth() / 2f);
        canvas.drawRoundRect(rect, r, r, stroke);
    }
    @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); stroke.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { fill.setColorFilter(filter); stroke.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
