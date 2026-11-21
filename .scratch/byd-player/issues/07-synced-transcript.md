# Synced transcript panel
Type: task
Status: open
Blocked by: 02, 06

Load the bundled transcript JSON for the current lesson. Highlight the active segment and auto-scroll to it; tap a segment to seek. Show the panel next to the player on the landscape screen.

## Comments

**From Main player screen and resume (2026-11-21):** Fill `TranscriptArea` in `PlayerScreen.kt` (right ~54% of the screen, `surface` background). The screen already polls the controller every 250 ms into `PlayerUi.positionMs`, so use that for the active segment. Use `controller.seekTo` for tap-to-seek. The current lesson number is `mediaId` of the current item. The bundled JSON can be keyed by it: I_01 = 1, II_01 = 31.
