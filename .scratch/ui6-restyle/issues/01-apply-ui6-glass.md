# Apply the UI6 glass look to the app
Status: ready-for-agent

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
