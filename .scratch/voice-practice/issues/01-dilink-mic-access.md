# Find out whether the app can use the DiLink microphone
Type: research
Status: resolved
Blocked by:

## Question

Can a sideloaded app on the BYD DiLink head unit (Android 12 per the car check, China firmware, Song Plus 2025+) record from the built-in cabin microphone **while it plays a lesson through Media3**? Find out:
- whether `RECORD_AUDIO` with a normal runtime prompt is enough, or BYD restricts the mic to system apps;
- which `MediaRecorder.AudioSource` / `AudioRecord` source works best (MIC, VOICE_RECOGNITION, VOICE_COMMUNICATION) and whether the head unit offers acoustic echo cancellation (`AcousticEchoCanceler`) or a reference signal from its own speakers;
- whether opening the mic changes playback (audio focus loss, ducking, routing to "call" mode) or clashes with the BYD voice assistant ("Hi BYD") holding the mic;
- Android 13 rules for capturing in a foreground service (`foregroundServiceType="microphone"` alongside `mediaPlayback`, while-in-use restrictions when the screen is off or another app is in front).

## Comments

**Research pass (2026-11-21).** Findings are in `.scratch/voice-practice/research/dilink-mic-access.md` on branch `research/dilink-mic-access` (not merged). Summary:
- The car reported Android 12 (SDK 32) in byd-player ticket 09, not 13. `RECORD_AUDIO` with the normal runtime prompt should be enough: BYD says a sideloaded app asking for the mic gets a standard on-screen prompt, and partner karaoke apps already use the roof mic. A system mic toggle (Android 12, plus possibly a BYD one) can make a granted app record silence.
- Source: start with `VOICE_RECOGNITION` (effects off, flat response) or `MIC`. `VOICE_COMMUNICATION` is the only source likely to get echo cancellation, but it is privacy-sensitive and may take the mic from "Hi BYD". Whether BYD's HAL has AEC at all, with the cabin speakers as reference, is unknown.
- Recording does not touch audio focus, so the lesson should keep playing. Do not request focus (as the Android for Cars guide does) and do not switch to `MODE_IN_COMMUNICATION`.
- Foreground service: Media3 1.5.0 always calls `startForeground` with only `mediaPlayback`, so add a separate `RecorderService` with `foregroundServiceType="microphone"`, started from the visible Activity. A recorder started from the background (e.g. a wheel resume) gets no mic access.
- Use `AudioRecordingCallback` / `isClientSilenced()` to drop pauses where the assistant or a call took the mic.
- The file ends with an on-car checklist for ticket 03 (ADB probes, permission flow, per-source bleed test, "Hi BYD", background and call cases).

## Answer

Probably yes, untested on the car. A normal `RECORD_AUDIO` prompt should be enough, and recording does not take audio focus, so the lesson keeps playing. Try `VOICE_RECOGNITION` or `MIC` first; `VOICE_COMMUNICATION` is the only likely route to echo cancellation but may block "Hi BYD". The mic needs its own `RecorderService` (`foregroundServiceType="microphone"`) started while the player screen is visible. Media3's service can't carry it, and a background start records nothing. Drop silenced pauses via `isClientSilenced()`. Echo cancellation, speaker bleed level and BYD-specific mic switches are left to [Check the microphone on the car](03-car-mic-check.md), using the checklist at the end of the research file.
