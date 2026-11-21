# Glass state styles and driving contrast
Type: grilling
Status: resolved
Blocked by: 03

## Question

On the chosen glass variant, how do selected / pressed / disabled / done (✓) / missing states look, and how is the active transcript line marked, so that everything stays readable at arm's length over a translucent, wallpaper-dependent background? Settle minimum contrast, text color rules for English vs Russian lines, and whether panels need a darker scrim behind text.

## Comments

## Answer

Settled with the user on 2026-11-21, on variant A (wallpaper + blur, glass white 10 %, BYD blue `#3388FF`).

Facts behind it:
- On today's BYD wallpapers, white text measured about 7:1 and 65 % white text about 4.1:1.
- On a bright patch (L ≈ 0.3–0.5), white on glass falls under 2.5:1.
- White on `#3388FF` is 3.4:1.
- `dumpsys` does not show whether the BYD live wallpaper reports `WallpaperColors`.

| Topic | Decision |
|---|---|
| Minimum contrast | Primary text ≥ 4.5:1 against the real pixels behind it. Secondary text and large text (≥ 24 sp) ≥ 3:1. This must hold on any wallpaper. |
| Scrim | Adaptive. A black **30 %** layer over the whole window, rising to **55 %** when the wallpaper reports itself as light (`WallpaperColors` dark-text hint). If the wallpaper reports no colours, stay at 30 %. Check on the car that colours arrive. |
| Active transcript line | BYD blue fill, white **SemiBold** text, 26 sp (passes as large text). |
| English vs Russian lines | English is white 100 %, Medium. Russian is white **70 %**, Normal. |
| Pressed | No Material ripple. Glass goes from white 10 % to **20 %**. Blue goes **15 % darker**. |
| Disabled | Glass stays 10 %. Icon and text are white **35 %**. |
| Done lesson (✓) | Glass tile, green ✓ `#6FD08C`, "done" label. |
| Missing lesson | No fill, white 15 % outline, number white 35 %, "missing" label. |
| Now-playing tile | Glass tile, **BYD blue 3 dp outline**, white "▶ playing". A blue fill is only for selection. |
| Outlined buttons | None. ‹ Lessons, Change folder and an unset A-B become borderless glass (white 10 %). An active A-B is blue. |
