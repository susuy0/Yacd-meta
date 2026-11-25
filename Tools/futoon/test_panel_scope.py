#!/usr/bin/env python3
"""Run extracted production panel methods against local, non-Android host fixtures.

Requires a JDK and org.json:json:20240303 (normally populated by the module tests).
No network, Android device, backend credentials, or Telegram account is used.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def extract_method(source: str, signature: str) -> str:
    """Balance a method body while ignoring Java strings, characters and comments."""
    if source.count(signature) != 1:
        raise ValueError(f"Expected exactly one production method: {signature}")
    start = source.index(signature)
    body = source.index("{", start)
    depth = 0
    state = "code"
    escaped = False
    i = body
    while i < len(source):
        char = source[i]
        following = source[i + 1:i + 2]
        if state in ('"', "'"):
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == state:
                state = "code"
        elif state == "line":
            if char == "\n":
                state = "code"
        elif state == "block":
            if char == "*" and following == "/":
                state = "code"
                i += 1
        elif char == "/" and following in ("/", "*"):
            state = "line" if following == "/" else "block"
            i += 1
        elif char in ('"', "'"):
            state = char
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[start:i + 1]
        i += 1
    raise ValueError(f"Unterminated production method: {signature}")


def json_jar(explicit: str | None) -> Path:
    candidate = explicit or os.environ.get("JSON_JAR")
    if candidate:
        path = Path(candidate).expanduser().resolve()
        if not path.is_file():
            raise SystemExit(f"JSON JAR not found: {path}")
        return path
    gradle_home = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
    candidates = sorted((gradle_home / "caches/modules-2/files-2.1/org.json/json/20240303").glob("*/*.jar"))
    if not candidates:
        raise SystemExit("Run the FutoonAdmin module tests first, or pass --json-jar /path/to/json-20240303.jar")
    return candidates[0].resolve()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json-jar", help="Explicit org.json JSON JAR, overriding the Gradle cache")
    args = parser.parse_args()
    for program in ("javac", "java"):
        if shutil.which(program) is None:
            raise SystemExit(f"Required JDK command not found: {program}")
    here = Path(__file__).resolve().parent
    root = here.parents[1]
    source = (root / "TMessagesProj/src/main/java/org/telegram/ui/FutoonPanel.java").read_text()
    signatures = (
        "private void discardBackendPanel(",
        "private void changeBackendScope(",
        "private void prepareEvidence(",
        "private void sendEvidence(",
        "private void ensureClient(",
    )
    methods = "\n\n".join(extract_method(source, signature) for signature in signatures)
    # Exercise the actual final dispatch block, including its monitor-entry scope check.
    native_call = extract_method(source, "private String nativeCall(")
    dispatch = extract_method(native_call, "synchronized(this)")
    fixture = (here / "PanelScopeFixture.java.in").read_text()
    marker = "// __PRODUCTION_METHODS__"
    if fixture.count(marker) != 1:
        raise SystemExit("Expected exactly one method insertion point in PanelScopeFixture.java.in")
    dispatch_marker = "// __PRODUCTION_DISPATCH__"
    if fixture.count(dispatch_marker) != 1:
        raise SystemExit("Expected exactly one dispatch insertion point in PanelScopeFixture.java.in")
    jar = json_jar(args.json_jar)
    with tempfile.TemporaryDirectory(prefix="futoon-panel-scope-") as directory:
        path = Path(directory) / "ScopeCheck.java"
        path.write_text(fixture.replace(marker, methods).replace(dispatch_marker, dispatch))
        subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), str(path)], check=True)
        subprocess.run(["java", "-cp", os.pathsep.join((str(jar), directory)), "ScopeCheck"], check=True)
    print(f"Verified {len(signatures)} methods and the synchronized business dispatch extracted from the current FutoonPanel.java; Android runtime behavior is outside this fixture.")


if __name__ == "__main__":
    main()
