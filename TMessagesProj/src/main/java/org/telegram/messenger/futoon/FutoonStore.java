package org.telegram.messenger.futoon;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.HashMap;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Integration sessions are encrypted and isolated by Telegram user and backend. */
public final class FutoonStore {
    private static final Object KEY_LOCK = new Object();
    private static final HashMap<Long, Draft> drafts = new HashMap<>();
    private final long userId;
    private final SharedPreferences preferences;

    public FutoonStore(int account) {
        userId = UserConfig.getInstance(account).getClientUserId();
        preferences = ApplicationLoader.applicationContext.getSharedPreferences("futoon_" + userId, Context.MODE_PRIVATE);
    }

    public String address() { return preferences.getString("address", ""); }
    public void address(String address) { preferences.edit().putString("address", address).apply(); }
    public String username() { return preferences.getString("username", ""); }
    public void username(String username) { preferences.edit().putString("username", username).apply(); }

    public synchronized String readLocal(String backend) throws Exception {
        return session("local-data:" + backend).load();
    }
    public synchronized void writeLocal(String backend, String value) throws Exception {
        session("local-data:" + backend).save(value);
    }

    public FutoonApi.SessionPersistence session(String address) throws Exception {
        StringBuilder hash = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(address.getBytes(StandardCharsets.UTF_8))) {
            hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        }
        final String preferenceKey = "session_" + hash;
        final String alias = "vinechat_futoon_" + userId;
        return new FutoonApi.SessionPersistence() {
            private SecretKey key() throws Exception {
                synchronized (KEY_LOCK) {
                KeyStore store = KeyStore.getInstance("AndroidKeyStore");
                store.load(null);
                if (!store.containsAlias(alias)) {
                    KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                    generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
                    generator.generateKey();
                }
                return (SecretKey) store.getKey(alias, null);
                }
            }
            @Override public String load() throws Exception {
                String raw = preferences.getString(preferenceKey, "");
                if (raw.isEmpty()) return "";
                try {
                    String[] parts = raw.split(":", 2);
                    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                    cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
                    return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
                } catch (Exception invalidSession) {
                    clear();
                    return "";
                }
            }
            @Override public void save(String value) throws Exception {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, key());
                String raw = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":"
                        + Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
                if (!preferences.edit().putString(preferenceKey, raw).commit()) throw new java.io.IOException("无法保存富通登录会话");
            }
            @Override public void clear() { preferences.edit().remove(preferenceKey).commit(); }
        };
    }

    public static final class Draft {
        public final String address;
        public final FutoonApi.Order order;
        public final String caption;
        public final long sourceDialog;
        public final java.util.ArrayList<String> imagePaths = new java.util.ArrayList<>();
        public long sourceMessage;
        public long sourceTopic;
        Draft(String address, FutoonApi.Order order, String caption, long sourceDialog) {
            this.address = address; this.order = order; this.caption = caption; this.sourceDialog = sourceDialog;
        }
    }
    public static void setDraft(int account, String address, FutoonApi.Order order, String caption, long sourceDialog) {
        drafts.put(UserConfig.getInstance(account).getClientUserId(), new Draft(address, order, caption, sourceDialog));
    }
    public static Draft draft(int account) { return drafts.get(UserConfig.getInstance(account).getClientUserId()); }
    public static void clearDraft(int account) { drafts.remove(UserConfig.getInstance(account).getClientUserId()); }
}
