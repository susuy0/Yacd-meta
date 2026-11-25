package org.telegram.messenger.futoon;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Selected text is interpreted locally; only an explicit query reaches Futoon. */
public final class FutoonQuery {
    public enum Kind { AUTO, PID, SYSTEM_ORDER, MERCHANT_ORDER, CHANNEL_ORDER }
    private static final String TOKEN = "([A-Za-z0-9][A-Za-z0-9_.:@/+\\-]{0,127})";
    private static final Pattern PID = Pattern.compile("(?i)^(?:pid|uid|商户(?:id|编号|号))\\s*[:：=#]?\\s*" + TOKEN + "$");
    private static final Pattern MERCHANT = Pattern.compile("(?i)^(?:out_trade_no|商户订单(?:号)?)\\s*[:：=#]?\\s*" + TOKEN + "$");
    private static final Pattern SYSTEM = Pattern.compile("(?i)^(?:trade_no|系统订单(?:号)?|平台订单(?:号)?)\\s*[:：=#]?\\s*" + TOKEN + "$");
    private static final Pattern CHANNEL = Pattern.compile("(?i)^(?:api_trade_no|通道订单(?:号)?|渠道订单(?:号)?)\\s*[:：=#]?\\s*" + TOKEN + "$");
    private static final Pattern GENERIC = Pattern.compile("^订单号\\s*[:：=#]?\\s*" + TOKEN + "$");
    public final Kind kind;
    public final String value;

    private FutoonQuery(Kind kind, String value) {
        this.kind = kind;
        this.value = value;
    }

    public static FutoonQuery parse(String selected, Kind requested) {
        String text = Normalizer.normalize(selected == null ? "" : selected, Normalizer.Form.NFKC)
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "").trim();
        if (text.length() == 0 || text.length() > 256) {
            throw new IllegalArgumentException("请选择一个 PID 或订单号，最长 128 个字符");
        }
        Kind detected = Kind.AUTO;
        Pattern[] labels = {PID, MERCHANT, SYSTEM, CHANNEL, GENERIC};
        Kind[] kinds = {Kind.PID, Kind.MERCHANT_ORDER, Kind.SYSTEM_ORDER, Kind.CHANNEL_ORDER, Kind.AUTO};
        for (int i = 0; i < labels.length; i++) {
            Matcher matcher = labels[i].matcher(text);
            if (matcher.matches()) {
                text = matcher.group(1);
                detected = kinds[i];
                break;
            }
        }
        if (!text.matches(TOKEN) || text.contains("://")) {
            throw new IllegalArgumentException("请只选中一个编号；也可带上 PID、系统订单号或商户订单号前缀");
        }
        Kind kind = requested == Kind.AUTO ? detected : requested;
        if (kind == Kind.PID && (!text.matches("[0-9]{1,10}") || Long.parseLong(text) == 0)) {
            throw new IllegalArgumentException("商户 PID 应为 1 至 10 位数字");
        }
        return new FutoonQuery(kind, text);
    }

    public List<Kind> searchKinds() {
        ArrayList<Kind> result = new ArrayList<>();
        if (kind != Kind.AUTO) {
            result.add(kind);
        } else {
            result.add(Kind.SYSTEM_ORDER);
            result.add(Kind.MERCHANT_ORDER);
            result.add(Kind.CHANNEL_ORDER);
            if (value.matches("[0-9]{1,10}") && Long.parseLong(value) > 0) result.add(Kind.PID);
        }
        return result;
    }

    public static String label(Kind kind) {
        switch (kind) {
            case PID: return "商户 PID";
            case SYSTEM_ORDER: return "系统订单号";
            case MERCHANT_ORDER: return "商户订单号";
            case CHANNEL_ORDER: return "通道订单号";
            default: return "自动识别";
        }
    }

    /** Extracts candidates for review; amounts, TGIDs and ticket numbers are not PID guesses. */
    public static List<FutoonQuery> extract(String input) {
        String text = Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFKC)
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "");
        if (text.length() > 16384) throw new IllegalArgumentException("每次最多识别 16384 个字符");
        java.util.LinkedHashMap<String, FutoonQuery> out = new java.util.LinkedHashMap<>();
        try { FutoonQuery q = parse(text, Kind.AUTO); out.put(q.kind + ":" + q.value, q); return new ArrayList<>(out.values()); }
        catch (IllegalArgumentException ignored) { }
        Pattern labeled = Pattern.compile("(?i)(?<![A-Za-z0-9_])(?:api_trade_no|out_trade_no|trade_no|商户订单号?|系统订单号?|平台订单号?|通道订单号?|渠道订单号?|订单号|pid|uid|商户id|商户编号|商户号)\\s*[:：=#]?\\s*" + TOKEN + "(?![A-Za-z0-9_.:@/+\\-*…●•])");
        Matcher m = labeled.matcher(text);
        boolean[] used = new boolean[text.length()];
        while (m.find() && out.size() < 40) {
            try {
                FutoonQuery q = parse(m.group(), Kind.AUTO);
                out.put(q.kind + ":" + q.value, q);
                for (int i = m.start(); i < m.end(); i++) used[i] = true;
            } catch (IllegalArgumentException ignored) { }
        }
        Matcher bare = Pattern.compile("(?<![A-Za-z0-9_.:@/+\\-*…●•])[A-Za-z0-9][A-Za-z0-9_.:@/+\\-]{5,127}(?![A-Za-z0-9_.:@/+\\-*…●•])").matcher(text);
        while (bare.find() && out.size() < 40) {
            if (used[bare.start()]) continue;
            String token = bare.group();
            if (token.contains("://") || token.matches("[0-9]+\\.[0-9]{1,2}") || !token.matches(".*[0-9].*")) continue;
            String before = text.substring(Math.max(0, bare.start() - 12), bare.start());
            if (before.matches("(?is).*(?:tgid|telegram|工单|金额|实付|币种|时间|日期)\\s*[:：=#]?\\s*$")) continue;
            if (token.matches("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}")) continue;
            try { FutoonQuery q = parse(token, Kind.AUTO); out.put(q.kind + ":" + q.value, q); } catch (IllegalArgumentException ignored) { }
        }
        return new ArrayList<>(out.values());
    }
}
