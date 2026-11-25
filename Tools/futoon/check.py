#!/usr/bin/env python3
"""Portable check entrypoint; APK builds always keep official native compilation enabled."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument("action", choices=["tests", "host", "apk"])
parser.add_argument("--gradle", help="Optional installed Gradle executable; default is the pinned wrapper")
parser.add_argument("--sdk", type=Path)
args = parser.parse_args()
env = os.environ.copy()
sdk = args.sdk or (Path(env["ANDROID_HOME"]) if env.get("ANDROID_HOME") else None)
if sdk:
    env["ANDROID_HOME"] = str(sdk.resolve())
if not env.get("ANDROID_HOME"):
    sys.exit("Set ANDROID_HOME or supply --sdk with an Android SDK 36 installation.")
free = shutil.disk_usage(ROOT).free // (1024 ** 3)
minimum = 30 if args.action == "apk" else 3
if free < minimum:
    sys.exit(f"Available disk: {free} GiB; reserve at least {minimum} GiB for {args.action}.")
gradle = args.gradle or str(ROOT / "gradlew")
common = ["--no-daemon", "--max-workers=2", "-Pkotlin.compiler.execution.strategy=in-process", "--console=plain"]
if args.action == "tests":
    command = [gradle, "-p", str(ROOT / "Tools/FutoonTests"), ":FutoonAdmin:testDebugUnitTest", ":FutoonAdmin:lintDebug", ":HostCompatibility:test", "-PfutoonUiTesting=true"]
elif args.action == "host":
    command = [gradle, ":TMessagesProj:compileDebugJavaWithJavac", "-PfutoonJavaChecks=true", "-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g"]
else:
    if not (ROOT / "TMessagesProj/jni/CMakeLists.txt").is_file():
        sys.exit("Expand the native checkout and initialize the pinned submodules before an APK build; see FUTOON.md.")
    command = [gradle, ":TMessagesProj_App:assembleAfatDebug"]
result = subprocess.call(command + common, cwd=ROOT, env=env)
if result == 0 and args.action == "tests":
    result = subprocess.call([sys.executable, str(ROOT / "Tools/futoon/test_panel_scope.py")], cwd=ROOT, env=env)
raise SystemExit(result)
