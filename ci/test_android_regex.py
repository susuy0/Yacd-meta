#!/usr/bin/env python3
"""Run business regexes against ICU4C, the engine used by Android's Pattern.

JVM/Robolectric regexes can accept syntax that Android rejects. This gate uses
libicui18n directly, including Android's ERROR_ON_UNKNOWN_ESCAPES flag, and
checks the actual source expressions plus variable matching and a failing
control for the closing brace regression. No Android or network access needed.

References:
https://android.googlesource.com/platform/prebuilts/fullsdk/sources/+/refs/heads/androidx-constraintlayout-release/android-35/java/util/regex/Pattern.java
https://unicode-org.github.io/icu-docs/apidoc/released/icu4c/uregex_8h.html
"""
from pathlib import Path
import ctypes as C
import ctypes.util
import json
import re
import sys


class IcuRegex:
    def __init__(self):
        library = ctypes.util.find_library("icui18n")
        if not library and sys.platform == "darwin":
            library = next((str(path.resolve()) for prefix in ("/opt/homebrew", "/usr/local")
                            if (path := Path(prefix) / "opt/icu4c/lib/libicui18n.dylib").exists()), None)
        assert library, "ICU4C must be installed; the Android regex gate cannot be skipped"
        self.lib = C.CDLL(library)
        version = re.search(r"\.so\.(\d+)|\.(\d+)(?:\.\d+)*\.dylib$", library)
        self.version = next((group for group in version.groups() if group), "unversioned") if version else "unversioned"
        suffix = "_" + self.version if version else ""
        self.open = self.function("uregex_open" + suffix, C.c_void_p,
                                  [C.POINTER(C.c_uint16), C.c_int32, C.c_uint32, C.c_void_p, C.POINTER(C.c_int32)])
        self.close = self.function("uregex_close" + suffix, None, [C.c_void_p])
        self.set_text = self.function("uregex_setText" + suffix, None,
                                      [C.c_void_p, C.POINTER(C.c_uint16), C.c_int32, C.POINTER(C.c_int32)])
        self.find_next = self.function("uregex_findNext" + suffix, C.c_int8, [C.c_void_p, C.POINTER(C.c_int32)])
        self.start = self.function("uregex_start" + suffix, C.c_int32, [C.c_void_p, C.c_int32, C.POINTER(C.c_int32)])
        self.end = self.function("uregex_end" + suffix, C.c_int32, [C.c_void_p, C.c_int32, C.POINTER(C.c_int32)])

    def function(self, name, result, arguments):
        fn = getattr(self.lib, name)
        fn.restype, fn.argtypes = result, arguments
        return fn

    @staticmethod
    def utf16(value):
        raw = value.encode("utf-16-le")
        return (C.c_uint16 * (len(raw) // 2)).from_buffer_copy(raw)

    def compile(self, pattern):
        buffer, status = self.utf16(pattern), C.c_int32(0)
        handle = self.open(buffer, len(buffer), 512, None, C.byref(status))
        return handle, status.value

    def captures(self, pattern, text):
        handle, code = self.compile(pattern)
        assert handle and code <= 0, (pattern, code)
        buffer, status = self.utf16(text), C.c_int32(0)
        raw = text.encode("utf-16-le")
        found = []
        try:
            self.set_text(handle, buffer, len(buffer), C.byref(status))
            while self.find_next(handle, C.byref(status)):
                start = self.start(handle, 1, C.byref(status))
                end = self.end(handle, 1, C.byref(status))
                found.append(raw[start * 2:end * 2].decode("utf-16-le"))
            assert status.value <= 0, status.value
            return found
        finally:
            self.close(handle)


STRING = re.compile(r'"(?:[^"\\]|\\.)*"')
CONSTRUCTOR = re.compile(r'\b(?:Regex|Pattern\.compile)\s*\(')
JAVA_METHOD = re.compile(r'\.(?:matches|replaceAll|replaceFirst|split)\s*\(')


def argument(source, offset):
    """Read the first argument, preserving concatenations and nested calls."""
    begin, depth = offset, 0
    while offset < len(source):
        if source[offset] == '"':
            token = STRING.match(source, offset)
            assert token, "unsupported regex string literal"
            offset = token.end()
            continue
        ch = source[offset]
        if ch in "([":
            depth += 1
        elif ch in ")]":
            if depth == 0:
                return source[begin:offset].strip()
            depth -= 1
        elif ch == "," and depth == 0:
            return source[begin:offset].strip()
        offset += 1
    raise AssertionError("unterminated regex expression")


def expression(value, constants):
    # Source uses ordinary Java/Kotlin literals. Fail rather than omit new syntax.
    parts, end = [], 0
    for token in STRING.finditer(value):
        gap = value[end:token.start()].strip().strip("+").strip()
        if gap:
            assert gap in constants, f"unresolved regex expression: {value}"
            parts.append(constants[gap])
        parts.append(json.loads(token.group()))
        end = token.end()
    tail = value[end:].strip().strip("+").strip()
    if tail:
        assert tail in constants, f"unresolved regex expression: {value}"
        parts.append(constants[tail])
    pattern = "".join(parts)
    # Only these source interpolations are allowed; values are escaped names or
    # validated numeric IDs. Compile a representative expansion of each.
    pattern = re.sub(r'\$\{Regex\.escape\((?:key|name)\)\}', r'\\Qfixture.key\\E', pattern)
    pattern = re.sub(r'\$(?:uid|formId)\b', "9001", pattern)
    assert not re.search(r'\$\{|\$[A-Za-z_]', pattern), f"unresolved regex interpolation: {value}"
    return pattern


def audit(root):
    icu = IcuRegex()
    kotlin = root / "FutoonAdmin/src/main/java/com/futoonpay/admin"
    host = root / "TMessagesProj/src/main/java/org/telegram/messenger/futoon"
    assert kotlin.is_dir() and host.is_dir(), "pass the prepared, patched source root"
    files = sorted(kotlin.rglob("*.kt")) + sorted(host.glob("*.java"))
    count, opening_pattern = 0, None
    for path in files:
        source = path.read_text()
        constants = {m[1]: json.loads(m[2]) for m in re.finditer(
            r'\b(?:String|val)\s+(\w+)\s*=\s*("(?:[^"\\]|\\.)*")\s*;', source)}
        calls = list(CONSTRUCTOR.finditer(source))
        if path.suffix == ".java":
            calls += [m for m in JAVA_METHOD.finditer(source) if argument(source, m.end())]
        for call in calls:
            value = argument(source, call.end())
            pattern = expression(value, constants)
            handle, code = icu.compile(pattern)
            if handle:
                icu.close(handle)
            location = f"{path.relative_to(root)}:{source.count(chr(10), 0, call.start()) + 1}"
            assert handle and code <= 0, f"Android ICU regex rejected {location}: {pattern!r} (status={code})"
            count += 1
            if path.name == "OpeningMessage.kt":
                assert opening_pattern is None, "validation and replacement must share one expression"
                opening_pattern = pattern
    assert count >= 75, f"unexpectedly incomplete regex audit: {count} expressions"
    assert opening_pattern, "opening variable expression was not tested"
    cases = [
        ("{商户ID}{密钥}{网站域名}{结算账号姓名}{结算周期}{绑定链接}", ["商户ID", "密钥", "网站域名", "结算账号姓名", "结算周期", "绑定链接"]),
        ("🏡【开户成功】\n用户 ID: {商户ID}\n结算周期: {结算周期}", ["商户ID", "结算周期"]),
        ("{} } { {跨\n行} 普通文案", []),
        ("{商户ID}{拼错了}", ["商户ID", "拼错了"]),
        ("{网站域名} {商户ID}", ["网站域名", "商户ID"]),
    ]
    for template, expected in cases:
        assert icu.captures(opening_pattern, template) == expected, (template, expected)
    # Deliberately restore the exact shipped defect. This must fail under ICU;
    # a switch back to the permissive JVM regex engine must not pass this gate.
    broken = opening_pattern.replace(r"\}", "}")
    assert broken != opening_pattern
    handle, code = icu.compile(broken)
    if handle:
        icu.close(handle)
    assert not handle and code > 0, "negative control unexpectedly accepted the original Android defect"
    print(f"ICU4C {icu.version}: {count} business regexes and {len(cases)} opening variable cases passed; original defect rejected (status={code}).")


if __name__ == "__main__":
    audit(Path(sys.argv[1]))
