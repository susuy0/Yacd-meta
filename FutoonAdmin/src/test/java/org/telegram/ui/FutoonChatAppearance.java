package org.telegram.ui;
/** Test adapter for icon lookup; no native layout or font method is replaced. */
public final class FutoonChatAppearance {
    public static android.graphics.drawable.Drawable icon(String glyph) {
        return com.futoonpay.admin.appearance.ChatLook.icon(glyph, 0xff0a0a0a, 1f);
    }
}
