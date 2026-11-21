# Apply the UI6 glass look to the app
Status: resolved

Restyle the Android app (`android/`) to the spec in `docs/ui6-glass-spec.md`. Look only: no layout, feature or font-size changes. Dark only.

Context:
- The decisions behind the spec are in the map `.scratch/ui6-glass/map.md`.
- [ADR 0001](../../../docs/adr/0001-window-shows-system-wallpaper.md) explains the translucent window.
- The prototype `prototype/ui6-glass-player` shows a rough version of the Player screen: the window setup, glass tokens and the blue selection. It is reference only, so rewrite rather than cherry-pick.

Done when:
- `Theme.kt` holds the `Ui6` token set (spec §2) and every screen uses it: Player, Lesson list, Settings, and the empty and error screens.
- The window shows the wallpaper with blur and the adaptive scrim (§1).
- The states (§4), screens (§5) and icons (§6) match the spec.
- Unit tests pass and the debug APK builds.
- Motion follows §7.
- The car check (§8) has been run with the user, and its results are recorded under `## Comments`, including whether `getWallpaperColors` returns colours on the car.

## Comments

- 2026-11-21: Motion is not in the spec yet. It is being decided in `.scratch/ui6-glass/issues/08-motion.md`. Until it is settled, keep today's motion. A later spec section may add to this ticket.
- 2026-11-21: Motion is settled and is now spec §7. The car check moved to §8.
- 2026-11-21: Implemented and checked on the car (DiLink 5.0).
  - Code:
    - `Theme.kt` holds the `Ui6Colors` tokens through a CompositionLocal (`Ui6.colors`), the radius, gap and motion constants, and the Material slots mapped from the tokens.
    - The new `Glass.kt` adds `enableWallpaperBlur()` (window blur plus a cross-window blur listener), `Ui6Backdrop` (the adaptive scrim from `WallpaperColors`), and `GlassButton`, `GlassLabel` and `Segmented`. They have no ripple and use pressed and selected fades.
    - The theme is a translucent window showing the wallpaper. Screens crossfade over 250 ms.
    - Player, Lesson list, Settings, and the empty and error screens are restyled. ⚙ is now a 2 dp line gear.
    - 18 unit tests pass and the debug APK builds.
  - Car check (§8):
    1. Blur is supported and enabled, and the wallpaper shows behind all screens ✅.
    2. **`getWallpaperColors` returns nothing for `BydWallpaperService`** (log: `Wallpaper colours: none`), so the scrim stays at the 30 % fallback ✅ (as decided).
    3. The brightest wallpaper the user picked: the brightest background behind the transcript panel was `#62656A`. White text there is 5.9:1 (≥ 4.5 ✅) and Russian white-70 % is 3.8:1 (≥ 3, large text ✅). The left column is 7.8:1 and 4.8:1.
    4. At 240 dpi nothing is clipped, and the sizes look as designed ✅.
    5. Pressed and disabled states show correctly on the touch screen. The user confirmed it ✅.
