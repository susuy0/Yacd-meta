package com.futoonpay.admin.appearance;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.text.TextPaint;

/** Inter stands in for the iOS reference's SF Pro. Missing glyphs (CJK, emoji) fall back to the system fonts. */
public final class ChatFonts {
    public static final String REGULAR = "fonts/inter_regular.ttf";
    private static AssetManager assets;
    private static Typeface regular;
    private ChatFonts() {}

    public static void init(Context context) {
        if (context != null) assets = context.getAssets();
    }

    /** The regular face, or null when the font is unavailable so callers keep the system default. */
    public static synchronized Typeface regular() {
        if (regular == null && assets != null) {
            try { regular = Typeface.createFromAsset(assets, REGULAR); } catch (RuntimeException ignored) { regular = null; }
        }
        return regular;
    }

    public static TextPaint paint(int flags) {
        TextPaint paint = new TextPaint(flags);
        Typeface face = regular();
        if (face != null) paint.setTypeface(face);
        return paint;
    }
}
