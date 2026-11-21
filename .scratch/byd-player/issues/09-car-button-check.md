# Check steering-wheel button signals on the car
Type: task
Status: open
Blocked by: 08

HITL. Install the APK from Button mode setting for the steering-wheel buttons on the car from a USB stick. With the car parked, open Settings and watch **Last button** while you:
1. press wheel ◀ once, then ▶ once
2. hold ◀ for 2 s, then hold ▶ for 2 s
3. tap ⏮ ⏭ in the DiLink now-playing widget, if there is one

Record what each shows (a photo is fine). Also check that with Button mode set to −5 s / +5 s, a wheel press actually skips 5 s in a playing lesson. The answer settles whether hold can carry the second action.

Reading the list: one press normally shows about 4 lines, "key … via screen", "key … via session", "cmd …", and the "up" key. During a hold, look for rising **repeat** numbers, **long-press**, or different key names (FAST_FORWARD/REWIND, or a non-MEDIA code). A widget tap should show only a "cmd" line from another package. The APK is `android/app/build/outputs/apk/debug/app-debug.apk`.
