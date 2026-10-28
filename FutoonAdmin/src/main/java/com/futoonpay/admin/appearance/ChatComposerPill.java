package com.futoonpay.admin.appearance;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * The composer's white pill. Its vertical extent is the text field's own box: the field is 32dp for one line and
 * sits 8dp above the bottom edge, so the pill's centre is 24dp above the bottom, the same centre line as the 48dp
 * plus and send buttons. Extra lines grow the field, and with it the pill, upward.
 */
public final class ChatComposerPill extends Drawable {
    public static final float PILL_DP = 32f, BOTTOM_GAP_DP = 8f;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private View source;
    private final RectF rect = new RectF();

    public ChatComposerPill(int fillColor, int strokeColor, float density, View source) {
        this.density = density;
        this.source = source;
        fill.setColor(fillColor);
        stroke.setColor(strokeColor);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1f, density * 0.5f));
    }

    public void setSource(View view) { source = view; invalidateSelf(); }

    /** Pill bounds in pixels from the field's top and bottom; pure so it can be unit tested. */
    public static void pillRect(float left, float right, float fieldTop, float fieldBottom, float density, RectF out) {
        float height = Math.max(PILL_DP * density, fieldBottom - fieldTop);
        out.set(left, fieldBottom - height, right, fieldBottom);
    }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (source != null && source.getHeight() > 0 && source.getBottom() > 0) {
            pillRect(b.left, b.right, source.getTop(), source.getBottom(), density, rect);
        } else {
            rect.set(b.left, b.bottom - (BOTTOM_GAP_DP + PILL_DP) * density, b.right, b.bottom - BOTTOM_GAP_DP * density);
        }
        float r = Math.min(rect.height(), PILL_DP * density) / 2f;
        r = Math.max(r, PILL_DP * density / 2f);
        canvas.drawRoundRect(rect, r, r, fill);
        rect.inset(stroke.getStrokeWidth() / 2f, stroke.getStrokeWidth() / 2f);
        canvas.drawRoundRect(rect, r, r, stroke);
    }
    @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); stroke.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { fill.setColorFilter(filter); stroke.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
