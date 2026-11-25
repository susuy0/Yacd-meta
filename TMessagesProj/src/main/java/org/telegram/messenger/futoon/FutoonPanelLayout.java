package org.telegram.messenger.futoon;

/** Pixel geometry shared by the chat container and floating sheet. No Android dependencies. */
public final class FutoonPanelLayout {
    private FutoonPanelLayout() {}

    public static int contentHeight(int parentHeight, int paddingTop, int actionBarHeight, int bottomInset) {
        return Math.max(0, parentHeight - Math.max(0, paddingTop)
                - Math.max(0, actionBarHeight) - Math.max(0, bottomInset));
    }

    public static int sheetHeight(int available, int margin, int preferredMinimum, boolean large, boolean keyboard) {
        int maximum = Math.max(0, available - Math.max(0, margin) * 2);
        return large || keyboard ? maximum : Math.min(maximum, Math.max(preferredMinimum, available * 85 / 100));
    }
    public static int sheetHeight(int available, int margin, int preferredMinimum, boolean large, boolean keyboard, int percent) {
        int maximum = Math.max(0, available - Math.max(0, margin) * 2);
        return large || keyboard ? maximum : Math.min(maximum, Math.max(Math.min(preferredMinimum, maximum), maximum * Math.max(50, Math.min(100, percent)) / 100));
    }
}
