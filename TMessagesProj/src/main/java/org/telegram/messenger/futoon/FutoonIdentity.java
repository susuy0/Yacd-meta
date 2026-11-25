package org.telegram.messenger.futoon;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Identity is a lookup hint, never an authorization or a message recipient. */
public final class FutoonIdentity {
    private static final Pattern TOPIC = Pattern.compile("^工单\\s*([0-9]+)\\s*\\|\\s*([^|]+)\\s*\\|\\s*(.+)\\s*\\|\\s*([0-9]+)\\s*$");
    public final String ticket, role, name, telegramId, source;
    private FutoonIdentity(String ticket, String role, String name, String id, String source) {
        this.ticket = ticket; this.role = role; this.name = name; telegramId = id; this.source = source;
    }
    public static FutoonIdentity topic(String title) {
        if (title == null || title.length() > 1024) return null;
        String text = title.replace('｜', '|').replace("\u200B", "").replace("\uFEFF", "").trim();
        Matcher m = TOPIC.matcher(text);
        if (!m.matches() || !validId(m.group(4))) return null;
        return new FutoonIdentity(m.group(1), m.group(2).trim(), m.group(3).trim(), canonicalId(m.group(4)), "来自话题末尾 TGID");
    }
    public static FutoonIdentity privatePeer(long peer, long self, boolean bot, boolean deleted, String name) {
        if (peer <= 0 || peer == self || bot || deleted) return null;
        return new FutoonIdentity("", "", name == null ? "" : name, Long.toString(peer), "来自私聊对方 TGID");
    }
    public static boolean validId(String value) {
        if (value == null || !value.matches("[0-9]{1,19}")) return false;
        try { return Long.parseLong(value) > 0; } catch (NumberFormatException e) { return false; }
    }
    public static String canonicalId(String value) {
        if (!validId(value)) throw new IllegalArgumentException("TGID 应为有效的正整数");
        return Long.toString(Long.parseLong(value));
    }
}
