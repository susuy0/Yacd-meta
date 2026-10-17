#!/usr/bin/env python3
"""Validate CI credentials, then install only the app's Firebase client configuration."""
import argparse
import base64
import json
import os
import re
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--root", type=Path)
parser.add_argument("--validate-only", action="store_true")
args = parser.parse_args()
required = os.environ.get("REQUIRE_PUSH", "true").lower() == "true"
api_id = os.environ.get("VINE_API_ID", "")
api_hash = os.environ.get("VINE_API_HASH", "")
encoded = os.environ.get("GOOGLE_SERVICES_JSON_BASE64", "")
package = "com.futoonpay.vinechat.beta"
missing = []
if not re.fullmatch(r"[1-9][0-9]{0,8}", api_id) or api_id == "4":
    missing.append("VINE_API_ID")
if not re.fullmatch(r"[a-fA-F0-9]{32}", api_hash):
    missing.append("VINE_API_HASH")
data = None
if encoded:
    try:
        data = json.loads(base64.b64decode(encoded, validate=True))
    except Exception:
        raise SystemExit("Invalid GOOGLE_SERVICES_JSON_BASE64; supply base64 of the downloaded JSON file.")
    clients = data.get("client", [])
    matches = [c for c in clients if c.get("client_info", {}).get("android_client_info", {}).get("package_name") == package]
    if len(matches) != 1:
        raise SystemExit("Firebase JSON must contain exactly one client for " + package)
    if data.get("project_info", {}).get("project_id") == "tmessages2":
        raise SystemExit("The upstream dummy Firebase configuration cannot be used for this app.")
    if not matches[0].get("api_key") or not matches[0].get("client_info", {}).get("mobilesdk_app_id"):
        raise SystemExit("Firebase client configuration is incomplete.")
else:
    missing.append("GOOGLE_SERVICES_JSON_BASE64")
if required and missing:
    raise SystemExit("Push-enabled build requires repository Actions secrets: " + ", ".join(missing))
if args.validate_only:
    print("Push configuration validated." if required else "Explicit development build selected; device push is not verified.")
    raise SystemExit(0)
if args.root is None:
    raise SystemExit("--root is required")
if data is not None:
    target = args.root / "TMessagesProj_App/google-services.json"
    target.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    target.chmod(0o600)
output = os.environ.get("GITHUB_OUTPUT")
if output:
    with open(output, "a", encoding="utf-8") as f:
        f.write("skip_google=" + ("false" if data is not None else "true") + "\n")
        f.write("push_mode=" + ("fcm-configured" if data is not None else "development-no-fcm") + "\n")
print("Configuration installed. Real background delivery still requires an on-device test.")

