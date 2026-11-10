package com.futoonpay.admin.appearance;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a notification may show on the lock screen. Order numbers keep their prefix and the first and last four digits, amounts are
 * replaced by a sentence-sized placeholder, and long digit runs (phone numbers, card numbers) are shortened the same way.
 * The unlocked notification shade keeps the full text; Android shows this redacted copy only while the device is locked and the
 * user's lock-screen setting hides sensitive content.
 */
public final class ChatNotificationPrivacy {
    private ChatNotificationPrivacy() {}
    public static final String HIDDEN_AMOUNT = "金额已隐藏";
    // 金额 12,000.00 / 金额：12000 / 金额为 ¥12000 — the label already says "amount", so the whole phrase collapses.
    private static final Pattern LABELLED = Pattern.compile("(金额|余额|结算金额|订单金额)\\s*[:：为是]?\\s*[¥￥$]?\\s*\\d[\\d,]*(?:\\.\\d+)?(?:\\s*(?:元|CNY|USDT|USD|RMB))?", Pattern.CASE_INSENSITIVE);
    // ¥128,400.00 / ￥12000 / $5.5 / 128,400.00 元 / 12.50 USDT
    private static final Pattern CURRENCY = Pattern.compile("[¥￥$]\\s*\\d[\\d,]*(?:\\.\\d+)?|\\d[\\d,]*(?:\\.\\d+)?\\s*(?:元|CNY|USDT|USD|RMB)(?![A-Za-z])", Pattern.CASE_INSENSITIVE);
    // P2611100093417 -> P2611…3417; needs at least 11 digits so ordinary numbers and times survive.
    private static final Pattern LONG_ID = Pattern.compile("(?<![A-Za-z0-9])([A-Za-z]{0,4})(\\d{4})\\d{3,}(\\d{4})(?![A-Za-z0-9])");

    /** Always on for now: the host applies it to the lock-screen copy of every chat notification. */
    public static boolean enabled() { return true; }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) return text == null ? "" : text;
        String out = LABELLED.matcher(text).replaceAll(Matcher.quoteReplacement(HIDDEN_AMOUNT));
        out = CURRENCY.matcher(out).replaceAll(Matcher.quoteReplacement(HIDDEN_AMOUNT));
        Matcher m = LONG_ID.matcher(out);
        StringBuffer sb = new StringBuffer();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + m.group(2) + "…" + m.group(3)));
        m.appendTail(sb);
        return sb.toString();
    }
}
