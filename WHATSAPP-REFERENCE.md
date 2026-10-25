# WhatsApp APK reference — 2.26.42.71

Reference: https://whatsapp-messenger.en.uptodown.com/android/download

The APK downloaded through WhatsApp's official Android download link has exactly the SHA-256 advertised by the supplied Uptodown page:
`528113bcfcfd3c4686a77bffa8a0b5db890c11b8362bfecbc441844c595bb671`.
Manifest: `com.whatsapp`, version `2.26.42.71` (`264207133`), 153,441,879 bytes, minSdk 23 / targetSdk 36.

Apktool 3.0.3 decoded the resource table and XML layouts. JADX 1.5.6 traced `ConversationEntryActionButton` to layout `0x7f0e0502`, confirming which resource defines its 48dp bounds. This is static analysis, not a logged-in runtime capture. The APK contains alternate composer layouts and feature flags; those variants must not be presented as one confirmed live screen.

`tools/inspect_whatsapp_reference.py` reproduces the measurements and refuses a different APK hash. Its output is `reference/whatsapp-2.26.42.71.json`. No WhatsApp binary, decompiled Java, brand artwork, credentials, or account data is included in this repository.

| Component | Reference resource evidence | Previous host | This update |
| --- | --- | --- | --- |
| Bottom navigation | `style/0x7f150668` → inherited minimum 80dp | 72dp | 80dp native and Compose |
| Active indicator | `style/0x7f150669` → 64×32dp | 64×32, placed too high | 64×32, top 12dp |
| Navigation labels | `style/0x7f1504b5` → 14sp / 20sp line height | Host measurement passes reset to 12dp | 14sp, medium; native measurement respects SP |
| Composer | `layout/0x7f0e0509` → input minimum 48dp | 44dp controls, 38dp send circle | 48dp controls and circle, 6dp separation |
| New conversation button | Larger native FAB slot | 48dp | 56dp, existing 16dp corners retained |
| Popup | `drawable/wds_popup_menu` → 12dp corner / vertical inset | 14dp corner, 8dp vertical inset | 12dp corner and vertical inset |
| Row | `layout/0x7f0e06c8` → 76dp minimum, 80dp avatar slot | 76dp, Telegram positions | Height retained; avatar/content positioning still needs device comparison |
| WDS colors | `style/0x7f150546` and its night override | Older palette values | Actual primary/secondary text and accent roles, distinct accent text and selection foreground roles (`res/color/0x7f0602ff` selects attribute `0x7f0409af` for the active icon) |

The Futoon panel retains its five configurable destinations, recognition and merchant actions, draft insertion and manual recipient selection. The host keeps Chats / Settings / Profile; removed calls and capture capabilities are not restored for visual imitation.

This update is not a claim of complete pixel equivalence. Chat attachment grid, profile/settings page structure, wallpaper artwork, animations, and server-controlled WhatsApp variants still require further comparison. Automated screenshots exercise our components with synthetic data; they are not WhatsApp screenshots or Android 17 device captures.
