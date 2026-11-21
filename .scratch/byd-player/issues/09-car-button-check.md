# Check steering-wheel button signals on the car
Type: task
Status: claimed
Blocked by: 08

HITL. Install the APK from Button mode setting for the steering-wheel buttons on the car from a USB stick. With the car parked, open Settings and watch **Last button** while you:
1. press wheel ◀ once, then ▶ once
2. hold ◀ for 2 s, then hold ▶ for 2 s
3. tap ⏮ ⏭ in the DiLink now-playing widget, if there is one

Record what each shows (a photo is fine). Also check that with Button mode set to −5 s / +5 s, a wheel press actually skips 5 s in a playing lesson. The answer settles whether hold can carry the second action.

Reading the list: one press normally shows about 4 lines, "key … via screen", "key … via session", "cmd …", and the "up" key. During a hold, look for rising **repeat** numbers, **long-press**, or different key names (FAST_FORWARD/REWIND, or a non-MEDIA code). A widget tap should show only a "cmd" line from another package. The APK is `android/app/build/outputs/apk/debug/app-debug.apk`.

## Comments

**2026-11-21, user (from memory, car not at hand):** in other apps, wheel presses showed **code 301** and **code 302**. These aren't media keycodes. On Android 13, 301/302 are `KEYCODE_DEMO_APP_1` / `KEYCODE_DEMO_APP_2`, so **Last button** will probably show `DEMO_APP_1` / `DEMO_APP_2` (via screen only).

What this means for the current build: those keys reach `MainActivity.dispatchKeyEvent` only while the app is on screen, where they're logged but trigger nothing. They never reach the media session, so Button mode doesn't apply to them. The car check now also needs to settle:

4. Which code is ◀ and which is ▶ (301 or 302)?
5. Does a press also send a MEDIA_NEXT/PREVIOUS key or a "cmd" line, or only 301/302?
6. Put the app in the background (Home, or open another DiLink screen) and press the wheel. Does anything reach the app? Check Last button when you come back. While the app is off screen, only "via session" and "cmd" lines can appear.
7. While holding: does 301/302 repeat (rising repeat number) or show long-press?
