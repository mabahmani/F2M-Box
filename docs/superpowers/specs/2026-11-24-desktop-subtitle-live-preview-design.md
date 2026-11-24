# Desktop subtitle live preview — design

Date: 2026-11-24
Status: implemented

## Intent

On desktop the subtitle customize panel edits a draft, but nothing is visible
until **Save**, which persists the style and rebuilds the libVLC engine at the
current position. Users cannot judge size, weight, color, background, edge or
offset while adjusting. Add an immediate preview inside the panel.

## What stays the same

libVLC keeps rendering the real playing subtitles, exactly as today. Save still
persists the draft and rebuilds the engine once at the current position; Cancel
still discards it. No change to playback, cue handling, or dependencies.

## Approaches considered

1. **Sample preview inside the panel (chosen)** — draw a fixed sample line in
   Compose with the draft style. Instant, no playback risk, reuses the existing
   `DesktopSubtitleView` composable.
2. **Rebuild-on-change** — applies the real subtitle style, but reloads the
   engine on every tweak; the video flickers and slider scrubbing becomes
   unusable. Rejected.
3. **Compose renders the real cues** — parse the container (MP4 `tx3g`, MKV
   `S_TEXT/UTF8`/ASS) and drive actual subtitles from the position. A much
   larger project with streaming/format risk; out of scope for this change.

## Design

- Add a `SubtitlePreview` at the top of `SubtitleSettingsPanel`: a rounded,
  dark, video-like band (about the panel's inner width × 160 dp) showing a
  sample subtitle in the draft style.
- Render the sample with the existing `DesktopSubtitleView(cue, style)`, so the
  preview uses the same size, weight, color, background, edge and bottom-offset
  logic that a Compose subtitle layer would use.
- Sample text is one short Persian line, held as a private constant in the
  player screen (not added to the ported `Strings` table), so RTL shaping and
  the app font are exercised.
- The preview recomposes on every panel change; it is non-focusable, so the
  panel's keyboard and mouse behaviour is unchanged.
- The panel grows by the band height + gap. Keep the whole panel within a 720p
  window; shrink the band if needed.

## Non-goals

- No change to libVLC subtitle rendering, no cue extraction, no new libraries.
- No Android/TV change.
- Not a pixel-exact copy of libVLC's freetype output; the preview shows the
  same style concepts, not the exact glyph rasterization.

## Testing

- Compose UI test: the panel shows the preview sample (make the panel/preview
  `internal` so the test can compose it), and the existing panel keyboard
  behaviour still works.
- Manual: every control visibly updates the preview; Save applies the style to
  playback; Cancel reverts.

## Follow-up: size as a slider

The Size row was four chips (12/20/28/40 sp). It is now a slider like the
other numeric rows: Left/Right scrubs 12–40 sp in 1 sp steps, the bar shows
the position, and the label shows the exact value (e.g. `24sp`). Persistence
now accepts any in-range size rather than only the old presets, so a scrubbed
value survives a restart.

Bottom offset 0 now means the subtitle is flush with the bottom edge of the
preview; the old 8 dp floor is gone. The 0–30 % setting maps to 0–75 dp, which
matches libVLC's `--sub-margin=0` at 0 %.

## Left to the builder

- Exact band height, corner radius, and sample string.
- Whether to also correct the stale README line claiming the desktop subtitle
  styling panel was cut from v1.
