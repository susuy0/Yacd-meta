package org.telegram.messenger;

/** JVM-only account state; the real native bridge is the subject under test. */
public final class UserConfig {
    private static final UserConfig[] ACCOUNTS = {new UserConfig(), new UserConfig(), new UserConfig()};
    public long owner = 42;
    public boolean active = true;
    public static UserConfig getInstance(int account) { return ACCOUNTS[account]; }
    public long getClientUserId() { return owner; }
    public boolean isClientActivated() { return active; }
}
