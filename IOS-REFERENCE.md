# WhatsApp iOS PDF — native implementation reference

The user-supplied `WhatsApp Screens 2025 with Meta AI - UI for iOS (Community).pdf` is the visual baseline for this revision. Its SHA-256 and observed vector fill colors are in `reference/ios-2025.json`. This is the supplied community Figma export, not a claim about the latest official WhatsApp release. The reference itself is not redistributed in this repository.

## Native structure

- Inbox: large 34sp title, real search field and one filter row. Custom server folders use a chooser that calls the existing locked-folder/delegate/transition machinery. Cancellation restores the selected filter. Menu stays available; new-chat uses the original click handler. Detached story/compose FABs are suppressed on this inbox.
- Inbox rows: 56dp avatars, 84dp normal text start, 17sp names, neutral pinned-state chrome and circular unread counts. Native server-folder labels are suppressed; exact-TGID Futoon tags remain. Forum preview keeps its original click/long-click behavior with a transparent background.
- Chat: continuous 44dp native action bar, native back chevron, flat pinned-message strip, bottom-corner bubble tails with original grouping and hit paths. Incoming/outgoing and read-state colors retain their meanings.
- Composer: the original attachment dispatcher is exposed as an inline plus; the redundant exterior attachment bubble is suppressed only for Futoon chat composers. Empty/typed/multiline send behavior, long-press quick replies, channel actions and original send options remain.
- Bot messages: neutral inline buttons explicitly use the neutral keyboard foreground, rather than a white service-message label on a pale background. Styled danger/success/primary buttons keep their semantic styles.
- Profile: centered 120dp normal avatar and larger name, inset grouped native information/action rows, normal full-width media browser and existing Telegram ID. Native avatar expansion and all original row handlers remain.
- Settings: large native title/search entry, grouped inset rows with 10dp corners, 56dp account avatar and separate QR/profile actions. Compact title appears after the large title scrolls away, and during search; pre-create/empty/recycle cases are guarded. Secondary native rows retain real input, switch, radio and navigation handlers.
- Futoon: the existing native/Compose routes use the same light-gray background, white grouped cards and plain icon/label navigation. Existing account/backend/conversation isolation and manual-send flows remain. A smaller 40dp entry is the new default; explicitly saved image/size settings remain valid.

## Palette

Observed vector fills include white `#FFFFFF`, grouped/search gray `#F4F4F4`, primary text `#0A0A0A`, secondary text `#767779`, green `#1DAB61`, outgoing green `#D0FECF`, and chat paper `#F5F2EB`. Type3 text is present in the export; this revision does not claim to have extracted a complete SF font specification. Android uses its available native fonts, Chinese labels and real system insets.

## Scope and validation

The retained app branding is 短信 / Futoon chat. The three existing main destinations, Futoon extensions, Telegram-only folder/topic capabilities and retained attachment types are mapped into the reference style. Removed capture, recording, calls, contacts sync and location capabilities are not restored. The reference's Meta AI, audio/video-call controls and encryption wording are not copied into capabilities this client does not provide.

The reference's Android-hosted rendering is implemented using native Views and existing Compose islands. No HTML/WebView business UI is introduced. Android status/navigation bars, system keyboard and file/photo permission windows stay system-owned. Dark mode is an adaptation because the provided reference primarily shows light surfaces.

Regression evidence covers actual shared native header geometry across widths and font scales, real search/filter callbacks, settings QR/profile routing, actual compact-title source execution with a failing original-header negative control, media/permission boundaries, business workflows and the complete host compile. Synthetic fixture images are not authenticated WhatsApp captures or Android17 device captures. A full runtime per-screen pixel-equivalence result is not claimed without those captures.

The optional QWEA0 Liquid-Glass-Android library was reviewed. Its API24+ View glass and API33+ lens pipeline are technically applicable to selected native surfaces, but it is an iOS26 material layer and is not a dependency of this PDF-layout revision.
