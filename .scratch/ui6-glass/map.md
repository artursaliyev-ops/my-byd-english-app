# UI6 glass restyle — map
Label: wayfinder:map

## Destination

An agreed **"UI6 glass" visual spec** (palette, transparency levels, background, button/panel/state styles) for all three screens (Lesson list, Player with transcript, Settings), proven by an emulator prototype, ready to hand off as a restyle of the app.

## Notes

- Domain: the Android app in `android/` (Kotlin + Compose, theme in `android/app/src/main/java/app/bydplayer/Theme.kt`). Today: flat `darkColorScheme` (bg `#101214`, surface `#1A1D21`, primary light blue `#7CC4FF`) and stock Material3 buttons.
- Reference (user photos, Nov 2026, car firmware 34.1.31): UI6 takes its background **from the wallpaper** (one photo warm brown, another grey smoky). Cards are light grey translucent (≈ white at 20–25 % alpha), ≈16 dp corners, no borders. Dialogs are dark translucent frosted panels with thin dividers. The primary action is a solid **BYD blue** button.
- Look only: layout, features and font sizes stay as they are. Dark mode only.
- Driving use: contrast over translucent backgrounds must stay readable at arm's length.
- Never record the IMEI/ICCID/phone number visible in the reference photos.
- Skills: `grilling` + `domain-modeling` for grilling tickets, `prototype` for prototype tickets, `research` for research tickets.

## Decisions so far

- **Destination**: a spec plus a prototype. The restyle itself is a separate hand-off.
- **Scope**: all three screens, look only, dark only.
- **Accent**: solid BYD blue for primary actions (Play, selected speed, active transcript line, selected tab). Everything else is translucent glass. The exact hex comes from [Capture UI6 reference screenshots and the car's Android version](issues/01-capture-ui6-reference.md).

- [Can the app show the DiLink wallpaper behind it, blurred?](issues/02-wallpaper-and-blur.md): probably yes. The best option is a translucent window over the system wallpaper with system blur (API 31+, if the car supports it), falling back to tinted panels with no blur. Plan B is a bundled background + Haze. Avoid reading the wallpaper bitmap ourselves. The car checks are in ticket 01.

## Not yet specified

- Lesson list: tile grid styling, the ✓ done mark and missing tiles over glass, the tab row.
- Settings screen and the Last button log panel styling.
- Icon style (current `Icons.kt` glyphs vs UI6 line icons), and motion/press feedback.
- The final shape of the spec: how glass tokens sit in `Theme.kt` (a custom color set vs. Material slots), and whether the spec is a markdown doc, the prototype code, or both.

## Out of scope

- Light theme / day mode.
- Layout, feature or font-size changes.
