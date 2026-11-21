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
- [Capture UI6 reference screenshots and the car's Android version](issues/01-capture-ui6-reference.md): Android 12 / SDK 32, 240 dpi, blur supported, BYD live wallpaper behind every screen. BYD blue is `#3388FF`. Glass is white ≈ 10 % (tiles), ≈ 19 % (selected chip), ≈ 6 % (selected row, dividers). Dialogs are opaque `#2B2E33` over a 60 % black scrim. A selected segment is solid light `#E4E2DB`, not blue.
- [Prototype the Player screen in UI6 glass](issues/03-glass-prototype.md): **variant A** was picked on the car. The system wallpaper shows through a translucent window with window blur, the glass is white 10 %, and the selected speed and active transcript line are solid BYD blue `#3388FF` (UI6's light segment was rejected). The prototype is on branch `prototype/ui6-glass-player`.
- [Glass state styles and driving contrast](issues/04-states-and-contrast.md): text contrast ≥ 4.5:1, or ≥ 3:1 for secondary and large text. An adaptive black scrim sits under everything: 30 %, or 55 % on a light wallpaper. Pressed glass is 20 %, disabled is 35 % white, Russian lines are 70 % white, the now-playing tile has a blue outline, and no outlined buttons remain.
- [Lesson list and Settings screens in glass](issues/05-library-settings-glass.md): the I/II/III tabs and Button mode become a segmented glass track with the selected item filled blue. "▶ Lesson N" is blue, the other header buttons are glass, and every panel gets a 10 dp radius and 12 dp gaps (UI6 measured). The Last button log sits in a black 35 % inset, and errors are white with ⚠ instead of red.
- [Icon style: current glyphs or UI6 line icons](issues/06-icon-style.md): playback icons stay filled (as in UI6 Music), with Play in the blue circle and the others in glass circles. ⚙ and every future non-playback icon are 2 dp line icons. Icons are white, or white 35 % when disabled.
- [Shape of the UI6 glass spec for hand-off](issues/07-spec-shape.md): the spec is `docs/ui6-glass-spec.md`, with the tokens as a `Ui6` set in `Theme.kt` that also fills the Material slots. ADR 0001 explains why the window shows the wallpaper. The hand-off is `.scratch/ui6-restyle/issues/01-apply-ui6-glass.md` (ready-for-agent). 
- [Motion in UI6 glass](issues/08-motion.md): this matches UI6 as recorded on the car. Selection fades over 150 ms and never slides, screens crossfade over 250 ms, the transcript line changes colour over 200 ms, and Play↔Pause takes 150 ms. Easing is `FastOutSlowIn` with no bounce. The rules are spec §7. **Destination reached.**

## Not yet specified

## Out of scope

- Light theme / day mode.
- Layout, feature or font-size changes.
