#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1]
src = root / "FutoonAdmin/src/main/java/com/futoonpay/admin"
host = (root / "integration.patch").read_text()
ui = "\n".join(p.read_text() for p in src.rglob("*.kt"))
for removed in ("src/main/assets/futoon/index.html", "src/main/assets/futoon/panel.js", "src/main/assets/futoon/panel.css", "org/telegram/ui/FutoonActivity.java"):
    assert removed not in host, f"obsolete panel component still patched: {removed}"
for forbidden in ("android.webkit", "JavascriptInterface", "evaluateJavascript", "MainActivity::class", "NativeModuleActivity::class", "DomainAuditActivity::class"):
    assert forbidden not in ui, f"unexpected web or standalone activity API: {forbidden}"
for required in ("OrdersScreen(state, model", "SettlementsScreen(state, model", "ModernManage(", "PaymentAdminRoot(model", "NativeModuleRoot(model", "DomainAuditRoot(model"):
    assert required in ui, f"original Android component not embedded: {required}"
assert "setViewTreeLifecycleOwner(this)" in ui
assert "viewModelStore.clear()" in ui
assert "PanelOverlayHost" in ui and "PanelDialog" in ui
assert "FutoonPanel.this.sendEvidence(order,caption)" in host, "Evidence bridge must invoke its host instead of recursively calling itself"
print("Native composition audit passed; interactive verification is performed by Robolectric Compose tests.")
