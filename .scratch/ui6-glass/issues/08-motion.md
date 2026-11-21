# Motion in UI6 glass
Type: grilling
Status: resolved
Blocked by: 07

## Question

Beyond the pressed state (already decided in Glass state styles and driving contrast), does the app need a UI6 treatment for motion? Settle:
- the transitions between Lesson list, Player and Settings (today they swap instantly);
- how the transcript auto-scroll and the active-line change animate (today `animateScrollToItem`, colour swaps instantly);
- how tab and segment selection, and the Play↔Pause swap, animate;
- the durations and easing, and whether anything should be reduced or switched off while driving.

If useful, watch how UI6's own screens move on the car first (Music, Settings) and match them. The answer adds a Motion section to `docs/ui6-glass-spec.md`.

## Answer

Settled with the user on 2026-11-21 and written to `docs/ui6-glass-spec.md` §7.

Fact: a 45 s screen recording of UI6 on the car (Settings menu changes, opening Settings), measured from frame timestamps.
- A selected menu item jumps with no slide.
- Page content cross-fades in about 300 ms.
- Small page swaps take about 100 ms.
- App launch takes about 500 ms and is the system animation.
- The Music tabs and the Display Mode segment didn't make it into the recording.

| Decision | |
|---|---|
| What moves | Only state changes: selection, the active transcript line, Play↔Pause, pressed, and screen changes. |
| Selection | The blue fill fades out and in over 150 ms instead of sliding. This replaces the "sliding" in the first round. |
| Screen change | 250 ms crossfade, no slide. App launch is left to the system. |
| Active transcript line | 200 ms colour change. Auto-scroll stays `animateScrollToItem`. |
| Play↔Pause | 150 ms crossfade. Pressed is 100 ms in and 150 ms out. |
| Easing | `FastOutSlowIn` everywhere. No bounce, spring, scale or overshoot, so nothing pulls the driver's eye. |
| System animations off | Everything is instant when the system scale is 0. |
