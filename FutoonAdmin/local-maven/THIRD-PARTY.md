# Third-party: LiquidGlass for Android

- Project: https://github.com/QWEA0/Liquid-Glass-Android (MIT, copyright pandadog) - license text in `LiquidGlass-License.txt`.
- Version: v2.0.11 (tag published 2026-09-21), taken from JitPack as `com.github.QWEA0:liquidglass:v2.0.11`.
- Vendored here so the build never fetches it from JitPack. Only the group `com.github.QWEA0` resolves from this folder.
- Pinned hashes (sha256):
  - `liquidglass-v2.0.11.aar` 8218f8075c3c447241f2a1e96d502fc48718c6d007e9e05347bb9669901f13a9
  - `liquidglass-v2.0.11.pom` a01cbd1b50bfceeb0d3119304c2c75ae76f13d854c5daff1206f18c587eb6a95
  - `liquidglass-v2.0.11.module` 4db588bfdebaa1fbc888f9711dee80180adb1e6bc59b83168a7586f12151d40c
- Contents: 3 view classes' Kotlin code, 2 small native libraries (arm64-v8a, armeabi-v7a), no permissions. minSdk 24.
- The host app's minSdk is 23, so the host manifest declares `tools:overrideLibrary="com.example.liquidglass"`; the library is only used on API 24+.
