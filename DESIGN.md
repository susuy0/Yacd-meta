# Futoon visual language: graphite + amber

Replaces the WhatsApp-iOS look. Every colour comes from `ChatLook` (host chat, via `FutoonChatAppearance`)
and `FutoonPalette` (Compose panel); no screen hard-codes brand colours.

| Role | Light | Dark | Use |
|---|---|---|---|
| Graphite (`accent`) | `#111827` | `#F3F4F6` | primary buttons, outgoing bubbles, selected chips/tabs, FAB |
| Amber (`badge`) | `#F59E0B` | same | unread counters, selection highlight: "needs your hand" |
| Amber text (`accentText`) | `#B45309` | `#FBBF24` | links, text buttons, drafts |
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
