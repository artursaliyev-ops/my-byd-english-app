# UI6 glass visual spec

The look the app takes to match BYD DiLink UI6 on the car (DiLink 5.0, Android 12 / SDK 32, 1920×1080 at 240 dpi). **Look only**: layout, features and font sizes stay as they are. **Dark only**.

Every value here was measured on the car or decided on the car. The decisions behind it are the tickets in `.scratch/ui6-glass/` (map: `.scratch/ui6-glass/map.md`). The throwaway prototype of the Player screen is on branch `prototype/ui6-glass-player` (never merge). Car reference screenshots are in `.scratch/ui6-glass/reference/`, which is gitignored and exists only on the machine that took them.

## 1. Window and background

The app's background is the **car's own wallpaper**, as in UI6's own screens (see [ADR 0001](adr/0001-window-shows-system-wallpaper.md)).

- Theme: `android:windowIsTranslucent=true`, a transparent `android:windowBackground`, `android:windowShowWallpaper=true`, and a transparent status bar. The navigation bar is `#20232A`, the colour of the car's bottom bar.
- Blur: on API 31+, `window.setBackgroundBlurRadius(60)` (40 dp). Listen with `WindowManager.addCrossWindowBlurEnabledListener`. When the system turns blur off (battery saver, thermal), run without blur and change nothing else. On BYD's soft gradient wallpapers the two look almost the same.
- **Scrim**: a full-window black layer under all content.
  - **30 %** by default.
  - **55 %** when `WallpaperManager.getWallpaperColors(FLAG_SYSTEM)` reports `HINT_SUPPORTS_DARK_TEXT` (a light wallpaper).
  - **30 %** when the wallpaper reports no colours (BYD's live wallpaper may not).
  - Re-check on `OnColorsChangedListener`.
- Never read the wallpaper bitmap: it needs storage permission and goes stale with the live wallpaper.

## 2. Tokens

Live in `Theme.kt` as a `Ui6` token set provided through a CompositionLocal. The main Material slots are filled from it too (`primary`, `onPrimary`, `surface`, `onSurface`, `background`, `onBackground`, `secondaryContainer`), so stock components default sensibly.

| Token | Value | Used for |
|---|---|---|
| `blue` | `#3388FF` | Play circle, selected speed, active transcript line, selected tab and segment, "▶ Lesson N", an active A-B, the now-playing outline |
| `blueOn` | `#FFFFFF` | text and icons on blue |
| `bluePressed` | `#3388FF` 15 % darker | pressed blue controls |
| `glass` | white 10 % | buttons, tiles, the transcript panel, segmented tracks |
| `glassPressed` | white 20 % | pressed glass |
| `divider` | white 6 % | 1 px dividers |
| `missingOutline` | white 15 % | the outline of a missing lesson tile |
| `inset` | black 35 % | the Last button log panel |
| `scrim` | black 30 % / 55 % | see §1 |
| `done` | `#6FD08C` | ✓ on done lessons |
| `text` | white 100 % | primary text, English transcript lines |
| `textSecondary` | white 70 % | counts lines, explanations, Russian transcript lines |
| `textDisabled` | white 35 % | disabled icons and text, missing lesson numbers |
| `radius` | 10 dp | every glass panel, tile and rectangular button |
| `gap` | 12 dp | between tiles and between grouped buttons |

The UI6 values these come from (car, 2026-11-21) are: tiles white 9–12 %, selected chip 18–20 %, selected row 5–7 %, dialog `#2B2E33` opaque over a 60 % scrim, and a selected segment of solid `#E4E2DB`. The light segment was **rejected** in favour of blue.

## 3. Contrast

- Primary text: **≥ 4.5:1** against the real pixels behind it, on any wallpaper.
- Secondary text and large text (≥ 24 sp): **≥ 3:1**.
- White on `#3388FF` is 3.4:1, so text on blue is **≥ 24 sp or SemiBold large text only**.

## 4. States

| State | Look |
|---|---|
| Selected (speed, tab, Button mode, active transcript line) | `blue` fill, white text. The active transcript line is SemiBold at 26 sp. |
| Pressed | No Material ripple. Glass goes to `glassPressed`, blue goes to `bluePressed`. |
| Disabled (for example ⏮ on the first lesson) | `glass` stays. The icon or text is `textDisabled`. |
| Done lesson | Glass tile, green ✓ top-right, "done" caption. |
| Missing lesson | No fill, `missingOutline`, the number in `textDisabled`, "missing" caption. |
| Now playing (lesson tile) | Glass tile, **3 dp `blue` outline**, white "▶ playing". A blue fill is only for selection. |
| Error message | White text with a leading **⚠**, never red. |

## 5. Screens

**Player**
- Play/Pause: a `blue` circle of 140 dp with an 80 dp white filled glyph.
- ⏮ ⏭ −5 s +5 s: `glass` circles of 104 dp.
- Speed row: glass buttons, with the selected one `blue`.
- Progress slider: active track `blue`, inactive track `glass`.
- Volume − / +: glass. Its bar: `blue` on `glass`.
- ‹ Lessons and an unset A-B: borderless glass. An active A-B is `blue`.
- Transcript panel: `glass`, `radius`. English lines are `text` Medium, Russian lines `textSecondary` Normal, and the active line follows §4.

**Lesson list**
- Header: title `text`, counts line `textSecondary`. "▶ Lesson N" is `blue`. Change folder and ⚙ are borderless glass.
- I/II/III tabs: a **segmented control** on a `glass` track with the selected tab `blue`. No underline indicator.
- Tiles: `radius` 10 dp, `gap` 12 dp. Size and columns stay as they are. States follow §4.

**Settings**
- Section titles are `text`, explanations `textSecondary`.
- Button mode: the same segmented control as the tabs.
- Last button log: an `inset` panel in monospace, with the newest line `text` and older lines white 65 %.

**Empty and error screens**
- The primary button is `blue`, the secondary one glass.
- The error text follows §4.

## 6. Icons

- Playback controls ▶ ⏸ ⏮ ⏭ stay **filled** (as in UI6 Music).
- Everything else, starting with ⚙, uses **line** icons with a **2 dp** stroke.
- All icons are white, or `textDisabled` when disabled.

## 7. Motion

UI6 itself (screen recording on the car, 2026-11-21):
- A selected menu item **jumps** with no slide.
- Page content cross-fades in about 300 ms.
- Small page swaps take about 100 ms.
- Opening an app is the system window animation, about 500 ms.

The app matches this, and nothing should pull the driver's eye to the screen.

| What | Motion |
|---|---|
| Selection (tabs, segments, speed, Button mode) | The blue fill **does not slide**. It fades out at the old spot and in at the new one over **150 ms**. |
| Screen change (Lesson list ↔ Player ↔ Settings) | **250 ms crossfade**, no slide. App launch is left to the system. |
| Active transcript line | The fill and text colour change over **200 ms**. Auto-scroll stays `animateScrollToItem`. |
| Play ↔ Pause | Icon crossfade over **150 ms**. |
| Pressed | In over **100 ms**, out over **150 ms**. |
| Easing | One curve everywhere: Material standard `FastOutSlowIn`. **No bounce, spring, scale or overshoot.** |
| System animations off | When `animator_duration_scale` is 0, everything is instant (Compose does this). |

## 8. Car check after the restyle

1. Blur is on (`adb shell wm disable-blur` says enabled) and the wallpaper shows behind every screen.
2. Find out whether `getWallpaperColors` returns colours for `BydWallpaperService`. Log it once and record the answer in the restyle ticket.
3. Switch to the brightest wallpaper the car offers and check that primary text stays readable (§3). If it doesn't, revisit the scrim fallback.
4. Check sizes at 240 dpi. The emulator AVD runs at 160 dpi, so things look smaller there.
5. Check that the pressed and disabled states show on the touch screen, and that Settings → Last button stays readable.
