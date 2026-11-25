package org.telegram.messenger.futoon;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;

import com.futoonpay.admin.ChatToolsConfig;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import java.util.HashMap;

/** Chat-only local preferences, scoped by logged-in Telegram user rather than account slot. */
public final class FutoonChatPreferences {
    private static final HashMap<Long, FutoonChatPreferences> instances = new HashMap<>();
    private final long owner;
    private final SharedPreferences preferences;
    private volatile ChatToolsConfig config;
    private volatile long revision;
    private FutoonChatPreferences(long owner) {
        this.owner = owner;
        preferences = ApplicationLoader.applicationContext.getSharedPreferences("futoon_chat_" + owner, Context.MODE_PRIVATE);
        try { config = ChatToolsConfig.parse(raw()); } catch (Exception e) { FileLog.e(e); config = ChatToolsConfig.parse(""); }
    }
    public static synchronized FutoonChatPreferences get(int account) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        FutoonChatPreferences result = instances.get(owner);
        if (result == null) { result = new FutoonChatPreferences(owner); instances.put(owner, result); }
        if (owner > 0) FutoonAutomation.ensureStarted(account);
        return result;
    }
    public String raw() { return preferences.getString("config", ""); }
    public long revision() { return revision; }
    public com.futoonpay.admin.ExtensionOptions options() { return config.getExtensions(); }
    public synchronized void save(int account, String json) throws Exception {
        if (owner <= 0 || UserConfig.getInstance(account).getClientUserId() != owner) throw new IllegalStateException("Telegram 账号已变化，请重新打开设置");
        ChatToolsConfig next = ChatToolsConfig.parse(json);
        if (!preferences.edit().putString("config", next.json()).commit()) throw new java.io.IOException("保存失败，请重试");
        if (config.getExtensions().getPanel().getRemember() && !next.getExtensions().getPanel().getRemember()) {
            android.content.SharedPreferences ui = ApplicationLoader.applicationContext.getSharedPreferences("futoon_panel_ui_" + owner, Context.MODE_PRIVATE);
            android.content.SharedPreferences.Editor edit = ui.edit(); for (String key : ui.getAll().keySet()) if (key.startsWith("navigation:")) edit.remove(key); edit.apply();
        }
        config = next; revision++;
        AndroidUtilities.runOnUIThread(() -> FutoonAutomation.get(account).reload());
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME));
    }
    public String tag(String telegramId) { return config.tagFor(telegramId == null ? "" : telegramId); }
    public static String peerId(long userId, String topicTitle) {
        if (topicTitle != null) { FutoonIdentity identity = FutoonIdentity.topic(topicTitle); return identity == null ? "" : identity.telegramId; }
        return userId > 0 ? Long.toString(userId) : "";
    }
    public static CharSequence decorate(int account, String telegramId, CharSequence name, TextPaint paint, int width) {
        return com.futoonpay.admin.ChatTagRenderer.decorate(name, get(account).tag(telegramId), paint, width, AndroidUtilities.density, Theme.isCurrentThemeDark());
    }
}
