# Check the microphone on the car
Type: task
Status: resolved
Blocked by: 01

## Question

HITL. Use the on-car checklist at the end of `.scratch/voice-practice/research/dilink-mic-access.md` (branch `research/dilink-mic-access`). On the car, with a lesson playing at normal volume, does a test build capture the driver's voice clearly, and how loud is the speaker's voice from the car speakers in the same recording? The result feeds the speaker-bleed decision.

## Progress

2026-11-21, AFK part done:

- **Test build**: the Mic probe (Settings → Mic probe) on throwaway branch `task/car-mic-probe` (not pushed, never merged). It records 30 s clips in a `microphone` foreground service from MIC, VOICE_RECOGNITION, VOICE_COMMUNICATION or UNPROCESSED, optionally adds AcousticEchoCanceler + NoiseSuppressor, and shows a live level, `isClientSilenced`, client effects and the routed device. The first 5 s of each clip are measured separately (stay quiet: that's lesson bleed plus noise). Each clip can be rated (voice / speaker in clip / lesson while recording), and "Save all to SD card…" writes the clips plus a report. "Start in 10 s, from background" covers checklist step 15. Checked on the emulator: recording works, and a background start is refused with `ForegroundServiceStartNotAllowedException`, which the probe logs.
- **ADB works to the car** over Wi-Fi (`172.20.10.2:5555`), so section A of the checklist was done from the laptop (read-only):
  - DiLink 5.0, Android 12 (SDK 32), build `SKQ1.230128.001`; **not AAOS** (no `android.hardware.type.automotive`); has `android.hardware.microphone`.
  - Inputs: `AUDIO_DEVICE_IN_BUILTIN_MIC` @bottom and `AUDIO_DEVICE_IN_BACK_MIC` @back, plus BT SCO, telephony RX, FM tuner, USB, wired headset.
  - `/vendor/etc/audio_effects.xml`: Qualcomm `libqcomvoiceprocessing.so`; `<preprocess>` applies **aec + ns to `voice_communication` only**. Nothing for `voice_recognition` or `mic`.
  - A system client (uid 1000, likely the "Hi BYD" voice assistant) **holds the built-in mic all the time** with `VOICE_RECOGNITION` at 16 kHz, 4 channels. Whether our capture runs alongside it, or silences it or us, is what checklist steps 11–12 test.
  - Audio focus: the car routes focus through `IviVehicleAudioBroker`; the app holds `USAGE_MEDIA/CONTENT_TYPE_SPEECH` with GAIN today.

Remaining (HITL, in the car): install the probe, then checklist sections B–D.

## Answer

Resolved 2026-11-21 in the car (HITL), parked, Lesson 1 playing at media volume 9/39. I drove the Mic probe over ADB; the user spoke and listened. Clips are in the probe's app storage on the car (`files/probe/`); levels are RMS per 100 ms chunk, dBFS.

| Source | Lesson alone (first 5 s, p90) | User's voice (p90 / peak) | Effects on input | Lesson playback | Verdict |
|---|---|---|---|---|---|
| MIC | −43 dB | −40 / −35 dB | none | unchanged | bleed about as loud as the voice |
| VOICE_RECOGNITION | −38 dB | −40 / −36 dB | none | unchanged | bleed about as loud as the voice |
| VOICE_COMMUNICATION | **−65 / −59 dB** (two runs) | −36 / −5 to −15 dB | Acoustic Echo Canceler, Noise Suppression (platform, automatic) | sounded unchanged to the user, though the HAL moves media output from `speaker` to `voice-speaker-stereo` while it records | **voice clear, speaker "almost none"** (user) |
| UNPROCESSED | −40 dB | −40 / −33 dB | none | unchanged | bleed about as loud as the voice |

- **Source: `VOICE_COMMUNICATION`.** The car's Qualcomm AEC + NS take about 20–25 dB of lesson out of the recording, and the user heard their voice clearly and the speaker almost not at all. The other sources keep the lesson about as loud as the voice. Adding `AcousticEchoCanceler` by hand was not needed.
- **Sharing the mic**: a system client (uid 1000, BYD voice) records `VOICE_RECOGNITION` from the same mic all the time; every source of ours ran alongside it, never silenced (`isClientSilenced=false`, 0 silent chunks). "Hi BYD" is not this car's wake word, so a wake-word clash was not tested.
- **Audio focus** never changed while recording: the app stayed on top of the focus stack with no loss, and the lesson never paused or ducked.
- **Starting**: a recording started from the visible screen **keeps capturing after going Home** (30 s with voice, no silencing). A recording started while the app was in the background is **allowed to start but muted**: the car logs `Foreground service started from background can not have … microphone access` and `App op 27 missing, silencing record`; the clip was silent until the app came back to the screen, then the voice reappeared at once. The app's `mediaPlayback` service is what lets the background start through, so the feature cannot rely on the mic starting from the wheel.
- Format the car delivers: 16 kHz mono 16-bit PCM was accepted by every source.
- Not tested: screen off, a phone call while recording (no phone paired), a moving car with road noise.
