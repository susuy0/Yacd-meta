#!/usr/bin/env python3
"""Execute the real MainTabs lifecycle/color methods with lightweight view doubles.

This covers the logged-in tab refresh path without Telegram credentials or a device.
It is deliberately not an Android launch/instrumentation test.
"""
from pathlib import Path
import argparse
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument("root", type=Path)
args = parser.parse_args()
source = (args.root / "TMessagesProj/src/main/java/org/telegram/ui/MainTabsActivity.java").read_text()

def method(signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]

methods = "\n".join(method(s) for s in (
    "public void onResume()", "private void blur3_updateFadeColors()", "private void blur3_updateColors()"
))
test = r'''
public class StartupColorsTest {
    static class View {
        int invalidations, updates, background;
        void setBackgroundColor(int color) { background = color; }
        void invalidate() { invalidations++; }
        void updateColors() { updates++; }
    }
    static class GlassTabView extends View {
        void updateColorsLottie() { updates++; }
    }
    static class FutoonChatAppearance { static boolean dark() { return true; } }
    static class ColorSource { void setColor(int color) {} }
    static class Parent { public void onResume() {} }
    static class Tabs extends Parent {
        ColorSource iBlur3SourceColor = new ColorSource();
        View fadeView, tabsView, tabsViewBackground, tabsViewWrapper, tabsTopLine;
        GlassTabView[] tabs;
        int badgeChecks, unreadChecks, hints, blurChecks;
        int getEstBackgroundColor() { return 0xFF121212; }
        void blur3_invalidateBlur() { blurChecks++; }
        void checkContactsTabBadge() { badgeChecks++; }
        void checkUnreadCount(boolean animated) { unreadChecks++; }
        void showAccountChangeHint() { hints++; }
        METHODS
    }
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Tabs t = new Tabs();
        t.onResume(); // before view creation
        t.tabs = new GlassTabView[5];
        t.onResume(); // all omitted / views not ready
        GlassTabView chats = new GlassTabView(), settings = new GlassTabView(), profile = new GlassTabView();
        t.tabs = new GlassTabView[] {chats, null, settings, null, profile};
        t.fadeView = new View(); t.tabsView = new View(); t.tabsViewBackground = new View(); t.tabsViewWrapper = new View(); t.tabsTopLine = new View();
        for (int i = 0; i < 20; i++) {
            t.onResume(); // cold start, foreground return, account/page recreation
            t.blur3_updateColors(); // theme delegate refresh
        }
        check(chats.updates == 40 && settings.updates == 40 && profile.updates == 40,
            "Every surviving tab must refresh on resume and theme change");
        check(t.tabsView.invalidations == 40 && t.tabsViewBackground.updates == 40,
            "Tab background refresh must remain active");
        check(t.tabsView.background == 0xFF111B21 && t.tabsViewWrapper.background == 0xFF111B21,
            "Both tab surfaces must refresh without missing lifecycle views");
        check(t.tabsTopLine.background == 0xFF20272B, "The tab bar hairline must follow the theme");
        check(t.badgeChecks == 22 && t.unreadChecks == 22 && t.hints == 22,
            "Resume must complete the remaining lifecycle work");
        check(t.blurChecks == 42, "Blur invalidation must remain active");
        System.out.println("PASS: real MainTabs onResume/theme refresh, sparse Lite tabs, pre-create views, repeated resume");
    }
}
'''.replace("METHODS", methods)
with tempfile.TemporaryDirectory(prefix="vinechat-startup-") as temporary:
    root = Path(temporary)
    appearance = root / "com/futoonpay/admin/appearance/ChatLook.java"
    appearance.parent.mkdir(parents=True)
    appearance.write_text("package com.futoonpay.admin.appearance; public class ChatLook { public static int surface(boolean dark) { return dark ? 0xFF111B21 : 0xFFFFFFFF; } public static int tabBar(boolean dark) { return surface(dark); } public static int tabBorder(boolean dark) { return dark ? 0xFF20272B : 0xFFD6D6D6; } }")
    (root / "StartupColorsTest.java").write_text(test)
    subprocess.run(["javac", "StartupColorsTest.java"], cwd=root, check=True)
    subprocess.run(["java", "StartupColorsTest"], cwd=root, check=True)
    # Prove this test catches the shipped v0.3 omission as well as passing the fix.
    guard = "if (tabView == null) continue;"
    if methods.count(guard) != 1:
        raise SystemExit("Expected exactly one sparse-tab guard in the production color method")
    (root / "StartupColorsTest.java").write_text(test.replace(guard, ""))
    subprocess.run(["javac", "StartupColorsTest.java"], cwd=root, check=True)
    regression = subprocess.run(["java", "StartupColorsTest"], cwd=root, capture_output=True, text=True)
    if regression.returncode == 0 or "NullPointerException" not in regression.stderr or "blur3_updateColors" not in regression.stderr:
        raise SystemExit("Test failed to reproduce the original startup NullPointerException")
    print("PASS: removing the fix reproduces the original startup NullPointerException")
