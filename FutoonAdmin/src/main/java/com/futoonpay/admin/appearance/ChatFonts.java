package com.futoonpay.admin.appearance;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.text.TextPaint;

/** Inter stands in for the iOS reference's SF Pro. Missing glyphs (CJK, emoji) fall back to the system fonts. */
public final class ChatFonts {
    public static final String REGULAR = "fonts/inter_regular.ttf";
    private static AssetManager assets;
    public static final String SEMIBOLD = "fonts/inter_semibold.ttf", BOLD = "fonts/inter_bold.ttf";
    private static Typeface regular, semibold, bold;
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

    /** The semibold face used for names and chips, or null when unavailable. */
    public static synchronized Typeface semibold() {
        if (semibold == null && assets != null) {
            try { semibold = Typeface.createFromAsset(assets, SEMIBOLD); } catch (RuntimeException ignored) { semibold = null; }
        }
        return semibold;
    }

    public static synchronized Typeface bold() {
        if (bold == null && assets != null) {
            try { bold = Typeface.createFromAsset(assets, BOLD); } catch (RuntimeException ignored) { bold = null; }
        }
        return bold;
    }

    public static TextPaint paint(int flags) {
        TextPaint paint = new TextPaint(flags);
        Typeface face = regular();
        if (face != null) paint.setTypeface(face);
        return paint;
    }
}
