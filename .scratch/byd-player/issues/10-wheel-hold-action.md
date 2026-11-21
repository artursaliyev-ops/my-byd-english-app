# Wheel hold does the other Button-mode action
Type: task
Status: resolved
Blocked by: 09

## Question

Make a wheel **hold** do the action the current Button mode does *not* do. With −5 s / +5 s selected, holding ◀/▶ goes to the previous/next lesson. With Previous / next lesson selected, holding skips −5 s / +5 s.

Facts from Check steering-wheel button signals on the car: a hold arrives at the media session as one `down` of the BYD keycode `AUTO_MEDIA_PREVIOUS_LP` / `AUTO_MEDIA_NEXT_LP`, with no repeat, no long-press flag and no `up`, in the foreground and in the background. Short presses (`MEDIA_PREVIOUS`/`MEDIA_NEXT`) stay as they are. The numeric keycode values aren't standard Android: read them from **Last button** / `KeyEvent.keyCodeToString` on the car, or match by name, before coding.

Done when: on the car, holding ◀/▶ does the other action in both modes, in the foreground and in the background, and **Last button** shows the hold with its effect. Settings text explains press vs hold.

## Answer

Built and checked on the car on 2026-11-21.

- `ButtonMode.holdEffectOf(forward)` returns the other mode's press action. `wheelHoldDirection(keyName)` matches `KEYCODE_AUTO_MEDIA_PREVIOUS_LP` / `KEYCODE_AUTO_MEDIA_NEXT_LP` by name, because the codes are BYD additions with no Android constant. Both are unit-tested in `ButtonModeTest`.
- `PlaybackService.SessionCallback.onMediaButtonEvent` logs every key as before. On a hold key's first `down` it calls `MediaButtonPlayer.hold()`, which logs `cmd hold next|previous → <effect>` and applies the effect directly. It then returns true. Other keys still go to Media3.
- The Settings Button mode text adds "Holding ◀ ▶ on the wheel does the other choice." GLOSSARY.md gets **Wheel hold**.
- Car check, mode −5 s / +5 s. Last button shows:
  - hold ▶ → `cmd hold next → next lesson`;
  - hold ◀ → `cmd hold previous → previous lesson`;
  - hold ▶ with the app in the background (Home) → next lesson;
  - short ▶ → `cmd next → +5 s`, unchanged.
- Not checked on the car: holds in **Previous / next lesson** mode (they should skip ±5 s). This is covered only by the unit test.
