# Check the microphone on the car
Type: task
Status: claimed
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
