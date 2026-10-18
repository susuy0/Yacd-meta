#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1]
ui = (root / "FutoonAdmin/src/main/java/com/futoonpay/admin/NativePanelView.kt").read_text()
host = (root / "integration.patch").read_text()

required_modules = {
    "channels", "payment_types", "rolls", "channel_monitor", "proxies", "domains",
    "funds", "risk", "blacklist", "merchant_stats", "inactive", "profit_receivers",
    "profit_orders", "transfer", "announcements", "payment_settings", "site_settings",
    "integrations", "login_logs", "merchant",
}
missing = sorted(module for module in required_modules if f'"{module}"' not in ui)
assert not missing, f"native More modules missing: {missing}"
assert "class NativePanelView" in ui and ": ComposeView" in ui
assert "NavigationBarItem" in ui and "NativeTab.ORDERS" in ui and "NativeTab.SETTLEMENTS" in ui
assert "NativeFormScreen" in ui and "SelectField" in ui and "MultiSelectField" in ui
assert "PendingAction" in ui and "确认提交" in ui
assert "sendEvidence" in ui and "Telegram 收件人选择" in ui
for forbidden in ("android.webkit", "WebView(", "JavascriptInterface", "loadUrl(", "evaluateJavascript"):
    assert forbidden not in ui, f"native UI contains forbidden web API: {forbidden}"
for removed in (
    "src/main/assets/futoon/index.html", "src/main/assets/futoon/panel.js",
    "src/main/assets/futoon/panel.css", "org/telegram/ui/FutoonActivity.java",
):
    assert removed not in host, f"obsolete panel component still patched: {removed}"
assert "new NativePanelView" in host
assert "FutoonPanel.canUsePhoto" in host
print(f"native panel contract OK: {len(required_modules)} More entries, no business WebView assets")
