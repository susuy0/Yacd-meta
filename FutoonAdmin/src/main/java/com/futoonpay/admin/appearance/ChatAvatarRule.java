package com.futoonpay.admin.appearance;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Letters and tint of a text avatar for groups and merchants: "星河电商 对接群" becomes 星河, a channel group becomes its code (A2),
 * a ticket becomes #. The tint is one of five quiet colours picked from a stable number (the chat id), so a merchant keeps its colour.
 */
public final class ChatAvatarRule {
    private ChatAvatarRule() {}
    private static final String[] SUFFIXES = {"对接群", "结算群", "通道群", "工单群", "工单", "对接", "结算"};
    private static final Pattern CHANNEL_CODE = Pattern.compile("[-－\\s]([A-Za-z]\\d{1,3})(?:\\s|$)");
    private static final Pattern TICKET = Pattern.compile("^\\s*(?:#|工单)\\s*\\d*");
    private static final int[] LIGHT = {0xffdde3ec, 0xffece6da, 0xffdde9e0, 0xffe8dde6, 0xffdde2f0};
    private static final int[] DARK = {0xff2a323f, 0xff38342b, 0xff2a3830, 0xff382c37, 0xff2b3042};
    private static final int NEUTRAL_LIGHT = 0xffe8e9ec, NEUTRAL_DARK = 0xff262a31;

    public static String label(String title) {
        if (title == null) return "#";
        String t = stripEmoji(title).trim();
        if (t.isEmpty()) return "#";
        if (TICKET.matcher(t).find()) return "#";
        Matcher code = CHANNEL_CODE.matcher(t);
        if (code.find()) return code.group(1).toUpperCase();
        for (String suffix : SUFFIXES) {
            int at = t.lastIndexOf(suffix);
            if (at > 0 && at + suffix.length() == t.length()) { t = t.substring(0, at).trim(); break; }
        }
        // Two Han characters when there are any, otherwise two Latin letters or digits, upper-cased.
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < t.length() && out.length() < 2; ) {
            int cp = t.codePointAt(i); i += Character.charCount(cp);
            if (Character.isWhitespace(cp)) continue;
            if (isHan(cp) || Character.isLetterOrDigit(cp)) out.appendCodePoint(Character.toUpperCase(cp));
        }
        String s = out.toString();
        // Everything collected so far is BMP text for our purposes (Han, Latin, digits); take the first two characters.
        String result = s.substring(0, Math.min(2, s.length()));
        return result.isEmpty() ? "#" : result;
    }

    /** One of five low-saturation tints by id, or a neutral grey when there is no id to hash. */
    public static int tint(long id, boolean dark) {
        if (id == 0) return dark ? NEUTRAL_DARK : NEUTRAL_LIGHT;
        int index = (int) (Math.abs(id % 5));
        return (dark ? DARK : LIGHT)[index];
    }

    private static boolean isHan(int cp) { return Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN; }

    private static String stripEmoji(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i); i += Character.charCount(cp);
            int type = Character.getType(cp);
            boolean emoji = cp >= 0x1F000 || (cp >= 0x2600 && cp <= 0x27BF) || cp == 0x200D || (cp >= 0xFE00 && cp <= 0xFE0F) || type == Character.OTHER_SYMBOL && !isHan(cp);
            if (!emoji) out.appendCodePoint(cp);
        }
        return out.toString();
    }
}
