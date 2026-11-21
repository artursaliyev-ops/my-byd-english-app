# Icon style: current glyphs or UI6 line icons
Type: grilling
Status: resolved
Blocked by: 04

## Question

Do the player icons in `Icons.kt` (filled ⏮ ▶ ⏸ ⏭) stay, or move to UI6-style thin line icons like the ones in the car's Settings and Music app? Settle the stroke weight and size at arm's length, and whether Play/Pause stays filled inside the blue circle.

## Answer

Settled with the user on 2026-11-21.

Facts:
- The app has 5 icons, all filled Material paths in `Icons.kt`: Play, Pause, Next, Previous, Settings.
- UI6 uses **filled** white glyphs for the Music app's ⏮ ⏸ ⏭, with no circle behind them.
- UI6 uses **thin line** icons (≈ 1.3 dp stroke) everywhere else: search, the Settings menu, ♡, tile icons.

| Topic | Decision |
|---|---|
| Player icons ▶ ⏸ ⏮ ⏭ | Stay **filled**, like UI6 Music. They read faster than line icons while driving. |
| Play/Pause | A white filled glyph in the blue circle, as today (circle 140 dp, glyph 80 dp). |
| ⏮ ⏭ and −5 s / +5 s | They keep their **glass circles** (white 10 %), because a bare glyph is too small a tap target in the car. |
| ⚙ Settings | Becomes a **line** gear, with a stroke of about **2 dp**: a bit heavier than UI6's, for arm's length. |
| Rule for new icons | Playback controls are filled. Everything else is a 2 dp line icon. All icons are white, or white 35 % when disabled. |
