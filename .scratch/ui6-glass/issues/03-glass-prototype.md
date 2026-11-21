# Prototype the Player screen in UI6 glass
Type: prototype
Status: resolved
Blocked by: 01, 02

## Question

Which glass treatment of the Player screen (controls + transcript panel) does the user pick? Build 2–3 variants on the emulator (`byd_dilink`, 1920×1080), each with translucent glass buttons/panels and solid BYD blue for primary actions, differing in background: the wallpaper (if 02 allows), a warm brown gradient, a neutral grey smoky gradient. Use the hex/alpha values from 01. Throwaway code on a branch, screenshots linked here.

## Comments

- 2026-11-21: Unblocked. The measured tokens are in the Answer of Capture UI6 reference screenshots and the car's Android version. Two things to settle in the prototype: whether the selected speed and tab use BYD blue (the Accent decision) or UI6's light solid selected segment, and whether the panels can stay crisp at 240 dpi. The emulator AVD runs at 160 dpi, so check sizes on the car too.

## Answer

The user picked **variant A**: the system wallpaper shows through a translucent window with window blur, and selected items stay **solid BYD blue**. Decided on the car (DiLink 5.0) on 2026-11-21.

- Prototype: branch `prototype/ui6-glass-player` (commit 0875397, throwaway, never merge). It adds `Ui6GlassPrototype.kt` and patches the theme. Car screenshots are in `.scratch/ui6-glass/prototype/` on that branch: A wallpaper+blur, B wallpaper no blur, C warm gradient, D grey gradient with the light selected style.
- How A works: the theme sets `windowIsTranslucent`, a transparent `windowBackground` and `windowShowWallpaper`. On API 31+ it calls `window.setBackgroundBlurRadius(60)`. This works on the car with the BYD live wallpaper, so the app's background follows the wallpaper the way UI6's own screens do.
- Tokens used, which held up on the car:
  - glass buttons and the transcript panel are white 10 %;
  - Play, the selected speed and the active transcript line are `#3388FF` with white text;
  - text is white, secondary text white ~65–80 %;
  - the outline on ‹ Lessons and A-B is white 25 %.
- Seen while comparing:
  - With BYD's soft gradient wallpaper, A and B look almost the same; blur only shows on a photo wallpaper.
  - The UI6 light selected segment looked more native, but it was rejected. Blue stays as the Accent decision says.
- Not checked in the prototype: the Lesson list and Settings screens (they only inherited the colours), pressed/disabled states, and a photo wallpaper.
