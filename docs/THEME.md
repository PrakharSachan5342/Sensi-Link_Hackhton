# THEME — SENSE-LINK Visual System

Direction: **industrial / tactical edge-AI HUD** on near-black, with a dotted-grid atmosphere.
Keep this exactly when porting to Compose.

## Colors
| Token        | Value      | Use |
|--------------|------------|-----|
| bg           | `#0E0E10`  | app background |
| bg2          | `#161619`  | cards |
| bg3          | `#1E1E23`  | insets / controls |
| inset        | `#101013`  | deep panels |
| line         | `rgba(255,255,255,.08)` | hairline borders |
| **orange**   | `#F26B2A`  | primary accent / CTAs / brand |
| orange2      | `#FF8A4C`  | gradient highlight |
| **cyan**     | `#22D3EE`  | landmarks, live status, "OK", listening |
| text         | `#F4F4F5`  | primary text |
| muted        | `#8A8A92`  | secondary text |
| good         | `#34D399`  | OK states |

## Typography (Google Fonts)
- **Display / headlines:** `Anton` (condensed heavy) — wordmark, big transcription output.
- **Telemetry / labels:** `Space Mono` (uppercase, letter-spacing ~.12–.18em) — badges, labels.
- **Body / UI:** `Archivo` (400–900).
- Avoid generic fonts (Inter/Roboto/Arial).

## Signature motifs
- **Dotted grid** background (radial-gradient dots, 22px).
- **Monster Halo:** breathing cyan inner glow border, animates while mic is listening.
- **Cyan landmark overlay:** 21 points + hand connections drawn with glow.
- **Latency badge:** live measured ms (e.g. "GPU Delegate: 14ms").
- Orange primary CTA buttons (mono uppercase, letter-spaced), cyan status dots with glow.

## Brand
- Wordmark: **SENSE-LINK** (orange hyphen accent).
- Tagline: "EDGE AI COMMUNICATIONS NETWORK".
- Bottom nav: Translate · Library · Sync · Profile.
