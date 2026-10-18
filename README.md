# VineChat Futoon v0.3.2 Lite

Android Telegram business client with a FUTOON panel. Upstream Telegram is pinned to `f32ffeec763f0bdbf4bec25b52d654f77bff733d`; `integration.patch` contains the fork changes. The previous repository contents were replaced as requested; history is retained.

## This version

- Correct the native panel height below Telegram's toolbar and above system navigation / IME. Both large and small windows stay inside their usable area. Block actions have normal height and footer actions stay visible.
- Unrecognized groups show one clear state and a manual TGID/PID lookup action. No-match keeps the identifier and query type. Cancel works with a focused input; confirmed discard switches to the requested tab and Back restores scroll position.
- Login fields survive captcha loading and unrelated updates. Passwords remain transient and are cleared on successful login. Backend changes / logout clear business context. Operation notices stay above footer actions and do not follow a new chat.
- Coalesce UI rendering per frame and retain unchanged DOM. Decode only bounded evidence previews using Telegram's image loader, preserving EXIF orientation and transparency. Guard image selection/edit/restore callbacks when the conversation changes.

- Fix the v0.3 startup crash: color refresh skips the omitted Contacts and Calls tab slots. A regression check executes the real resume/theme-refresh methods and confirms that removing the fix reproduces the original exception. R8 mappings are retained with each APK. Browser checks also cover ordinary groups, manual lookup, login, dirty navigation, notices and portrait / keyboard / landscape sizes; their screenshots are kept in a separate UI-check artifact.

- The panel defaults to nearly full height, with a compact header, independent content scrolling and pinned actions/navigation. It preserves input focus and scroll position during data updates. The floating entry remembers its edge/height.
- The WebView is created only when opening FUTOON. Hidden panels pause rendering. Heavy blur, continuous animated media and debug logging are disabled by default.
- Call native targets (TgCalls/WebRTC RTC transports, call DSP and call WASM backend and conference cryptography) are removed from linking. Microphone Opus encoding and recording worker queues are removed. OpenH264/Opus decoding remain for ordinary received media.
- Android call/phonebook services, activities, receivers and call provider declarations are removed. Phonebook and microphone permissions are removed. Phonebook sync/authenticator implementations are removed. The navigation has Chats, Settings and Profile.
- Saved Messages database/cache/sync/search/pinning/reminder logic is replaced with a small protocol compatibility surface. Existing server messages are not deleted.
- R8 optimization and resource shrinking are enabled; debug and native debugging are off. `ci/audit_apk.py` checks the final manifest and native exported symbols. `REMOVED-JAVA.txt` records optimizer removals.
- Channel monitor parsing and configuration editors are ported from FutoonPayAdmin 1.3.4. Monitor supports nested/string/array/map list formats, read-only GET fallback and six metric windows. Route editing supports channel choices, additional routes, weights and validation. Editors select the correct page form, lock record identity and preserve blank secrets. Payment plugins reload for the selected type; saving waits for loading and validates matching plugins.

## Build and install

Push to `master` starts GitHub Actions. It runs the startup color regression, 177 business/geometry checks, browser UI regressions, Java compilation, native compilation, shrinking and APK audits. Download the `VineChat-Futoon-v0.3.2-lite-*` artifact from Actions. The APK package is `com.futoonpay.vinechat.beta`, ARM64 by default, with the same development signing certificate as v0.2/v0.3 and a higher version code.

Telegram API credentials are optional for development: the source's bundled ID/hash are used when `VINE_API_ID` and `VINE_API_HASH` are absent. To configure your own values, set both repository secrets. The `GOOGLE_SERVICES_JSON_BASE64` secret supplies this package's Firebase configuration. Without it, the artifact is labeled `development-no-fcm`; background push delivery is not verified. `workflow_dispatch` can require push config and select all four architectures.

Live Telegram/FUTOON login, real image delivery, financial mutations and device performance require real-device/backend validation. CI fixtures do not perform live business mutations. All recipients are chosen by the user in Telegram's native picker. Business mutations require native confirmation.

## License

GPL-2.0-or-later, following upstream Telegram. See LICENSE. Upstream: https://github.com/DrKLO/Telegram

