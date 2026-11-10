# Futoon visual language: graphite + amber

Replaces the WhatsApp-iOS look. Every colour comes from `ChatLook` (host chat, via `FutoonChatAppearance`)
and `FutoonPalette` (Compose panel); no screen hard-codes brand colours.

| Role | Light | Dark | Use |
|---|---|---|---|
| Graphite (`accent`) | `#111827` | `#F3F4F6` | primary buttons, outgoing bubbles, selected chips/tabs, FAB |
| Amber (`badge`) | `#F59E0B` | same | unread counters, selection highlight: "needs your hand" |
| Amber text (`accentText`) | `#B45309` | `#FBBF24` | entries that need a hand (choose merchant, choose channel, log in), drafts |
| Success | `#15803D` | `#4ADE80` | completed / healthy only |
| Danger | `#DC2626` | Telegram red | failed / destructive (never remapped) |
| Canvas (`background`) | `#F3F4F6` | `#0C0E12` | flat chat background, no wallpaper art |
| Surface | `#FFFFFF` | `#14171C` | bars, sheets, incoming bubbles (with a 1px edge) |

Rules
- Green always means done, red failed, amber needs attention. Brand colour never doubles as status colour:
  use `Success` (Compose) / `ChatLook.success` for state, `GreenDark` only for links and text actions.
- Outgoing bubbles are dark, so their ink uses `onOutgoing*` and `outgoingAccent`, never `text()`.
- Avatars are squircles (radius about 30 percent of the side) in lists, headers and contact rows.
- Status is always text plus colour, never colour alone.

Panel and bar rules (Futoon Chat UI, 30-page draft)
- Links and text buttons in the panel are graphite and underlined; amber is only for things that need a hand.
- Irreversible or money-moving merchant actions (balance, ban, reset key) are red-outlined; recoverable ones (close pay, close settlement, user group) are graphite-outlined.
- The panel is a header (面板, fullscreen, collapse), a scope bar, five segmented tabs, then the page. Top-level pages draw no title bar of their own.
- The conversation bar is one 48dp row: name, success rate (red 偏低 below 80%), channel, PID; the rate is never the part that gets cut. 结算, 域名, 通道订单 and 编辑通道 live in the 更多 menu.
- High-impact dialogs show 影响范围, ask for the PID or channel name, and keep the confirm button asleep until it matches (banning and pausing a channel also wait 3 seconds).
