# Can the app record from the DiLink microphone while a lesson plays?

Researched 2026-11-21 for ticket [01-dilink-mic-access](../issues/01-dilink-mic-access.md). Target: the owner's 2025+ Song Plus EV, China firmware, **DiLink 5.0 on Android 12 (SDK 32)** as read over ADB in [byd-player ticket 09](../../byd-player/issues/09-car-button-check.md). The ticket says "Android 13"; the car reported SDK 32, so this file uses Android 12 rules. The app builds with targetSdk 33 and Media3 1.5.0, and `PlaybackService` declares only `foregroundServiceType="mediaPlayback"`.

**How much to trust this.** BYD publishes no developer documentation about the microphone, audio sources, echo cancellation or its voice assistant. The Android rules below come from Google and AOSP docs and are solid. BYD's own behaviour comes from a couple of press reports and has to be checked in the car. Tags:

- **[Android docs]**: developer.android.com, source.android.com, or the Media3 source code
- **[BYD, secondhand]**: a BYD statement or announcement, as reported by the press
- **[Inference]**: follows from the above, not stated anywhere
- **[Unknown]**: no source found; settle it on the car

---

## 1. Is `RECORD_AUDIO` with a normal prompt enough?

**Probably yes.**

- On stock Android, `RECORD_AUDIO` is a normal runtime ("dangerous") permission. An installed app declares it and asks at runtime, and no privileged permission is needed for the built-in mic sources (`MIC`, `VOICE_RECOGNITION`, `VOICE_COMMUNICATION`, `CAMCORDER`, `UNPROCESSED`). Only the call and submix sources (`VOICE_CALL`, `VOICE_UPLINK`, `VOICE_DOWNLINK`, `REMOTE_SUBMIX`) need `CAPTURE_AUDIO_OUTPUT`, which "is not available to third-party applications" ([MediaRecorder.AudioSource](https://developer.android.com/reference/android/media/MediaRecorder.AudioSource)). **[Android docs]**
- **BYD shows the normal prompt to a sideloaded app.** In the Shark 6 case (October 2026), BYD's engineers installed a third-party app through ADB. When it asked for location or microphone access, "the infotainment system displayed a permission prompt that had to be approved manually on the screen" ([Autotrader NZ, 3 Oct 2026](https://autotrader.co.nz/news/byd-confirms-shark-6-software-fault-after-tv-hack-fix-coming-over-the-air)). That is a different model and region, but it is BYD's own description of DiLink. **[BYD, secondhand]**
- **Third-party apps use the cabin mic.** BYD's "mic-free karaoke" OTA (August 2025, all brands) lets KuGou, Kuwo, QQ Music and WeSing sing through the roof microphone, with zoned per-seat mics on some models ([IT之家, 27 Aug 2025](https://www.ithome.com/0/878/371.htm)). Those are partner apps, possibly with a special integration, so this proves the hardware is reachable by non-BYD apps, not that a plain `AudioRecord` gets it. **[BYD, secondhand]**
- **Two system switches can silence it.** Android 12 adds a device-wide mic toggle in Quick Settings and the Privacy screen ([Android 12 behaviour changes](https://developer.android.com/about/versions/12/behavior-changes-all#mic-camera-toggles)). When it is off, apps still record but get silence. A car help page also describes a one-tap "vehicle microphone" switch plus per-app mic permissions, but it does not name the make or DiLink version ([bitauto](https://www.bitauto.com/ask/10019850927/)). **[Android docs]** / **[Unknown]** for BYD's own switch.

**Implication:** add `RECORD_AUDIO`, ask for it from the Activity when the user turns on "Record my answers", and treat "granted but silent" as a real outcome (see §4, `isClientSilenced`, and an on-screen level meter).

## 2. Which audio source, and is there echo cancellation?

| Source | What Android says it is | Pre-processing (AOSP guidance) | Fit for Attempts |
|---|---|---|---|
| `MIC` | "Microphone audio source" | OEM-defined, often none | Baseline. Try first. |
| `VOICE_RECOGNITION` | "tuned for voice recognition" | Effects **off by default**; noise suppression must not be on; flat ±3 dB response 100 Hz–4 kHz ([AOSP pre-processing](https://source.android.com/docs/core/audio/implement-pre-processing)) | Most predictable, natural-sounding voice. Likely default. |
| `VOICE_COMMUNICATION` | "tuned for voice communications such as VoIP … echo cancellation or automatic gain control if available" | AEC "should" be provided here and be controllable through `AcousticEchoCanceler`; the AOSP example enables AEC + NS | The only source likely to remove speaker bleed, but it is **privacy-sensitive** (see §3) |
| `UNPROCESSED` | raw if available, else behaves like `DEFAULT` | none | Check `AudioManager.getProperty(PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)`; useful only to compare |
| `CAMCORDER` | tuned for video | AGC in the AOSP example | Not useful; also privacy-sensitive |

Sources: [MediaRecorder.AudioSource](https://developer.android.com/reference/android/media/MediaRecorder.AudioSource), [AOSP: implement pre-processing](https://source.android.com/docs/core/audio/implement-pre-processing). **[Android docs]**

**Echo cancellation:**

- `AcousticEchoCanceler.isAvailable()` "checks if the device implements acoustic echo cancellation". `create(sessionId)` attaches it to one `AudioRecord` and returns null if the device has none. The platform may already insert AEC by source, so read `getEnabled()` after creating it ([AcousticEchoCanceler](https://developer.android.com/reference/android/media/audiofx/AcousticEchoCanceler)). **[Android docs]**
- Whether the BYD audio HAL implements AEC, and whether it uses the cabin speaker output as its reference, is **[Unknown]**. OEMs list per-source effects in `/vendor/etc/audio_effects.xml` ([AOSP](https://source.android.com/docs/core/audio/implement-pre-processing)). Over ADB, `adb shell cat /vendor/etc/audio_effects.xml` and `adb shell dumpsys media.audio_flinger` show what is configured.
- In cars, hands-free echo cancellation often runs in the amplifier or a DSP for Bluetooth calls, outside Android. If so, Android-side AEC on the head unit may not hear the cabin speakers as its reference at all. **[Inference]**
- A software reference from the app itself (feed the lesson PCM into an app-side AEC) is possible in principle but heavy; trimming Attempts to the speaking pause and fading the edges is the cheap first answer to speaker bleed. **[Inference]**

## 3. Does opening the mic change playback or clash with "Hi BYD"?

**Audio focus: no, if the app does not ask for it.** Android's input-sharing scheme is separate from audio focus: "audio focus is managed by programmatic requests to gain and release focus, while the input switching scheme … is applied automatically whenever a new app starts to capture audio" ([Sharing audio input](https://developer.android.com/media/platform/sharing-audio-input)). Starting an `AudioRecord` therefore does not by itself pause or duck ExoPlayer. **[Android docs]**

- Do **not** follow the Android for Cars guide here. It tells `CarAudioRecord` apps to take `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` first, which "stops any ongoing media" ([Record from the car microphone](https://developer.android.com/training/cars/apps/library/car-microphone)). That is for Android Auto / AAOS Car App Library hosts, and in this app it would make ExoPlayer (which handles focus) pause the lesson. **[Android docs]** / **[Inference]**
- Do **not** call `AudioManager.setMode(MODE_IN_COMMUNICATION)`. Android treats that mode as a voice call for capture priority ([Sharing audio input](https://developer.android.com/media/platform/sharing-audio-input#voice-call-ordinary-app)), and car HALs commonly reroute or duck media during calls. **[Android docs]** / **[Inference]**
- Whether BYD's HAL changes routing or volume when *any* capture opens (for example switching the cabin to a "voice" sound profile) is **[Unknown]**. Listen for it in the car check.

**Sharing the mic with other apps (Android 10+ rules, unchanged in 11–12):**

- Pre-installed apps that hold the assistant role are "privileged" and outrank ordinary apps. The assistant keeps receiving audio in the background "unless another app using a privacy-sensitive audio source is already capturing". The ordinary app keeps receiving audio "unless the Assistant has a visible UI component on top of the screen" ([Sharing audio input](https://developer.android.com/media/platform/sharing-audio-input#assistant-ordinary-app)). **[Android docs]**
- Two ordinary apps never capture at once; the one with UI on top wins, or the privacy-sensitive one, or the most recent one. The loser keeps running and **gets silence** ([same page](https://developer.android.com/media/platform/sharing-audio-input#two-ordinary-apps)). **[Android docs]**
- An active call (`MODE_IN_CALL` / `MODE_IN_COMMUNICATION`) "always receives audio"; an ordinary app gets silence. **[Android docs]**
- `VOICE_COMMUNICATION` and `CAMCORDER` are privacy-sensitive by default; `setPrivacySensitive(true)` (API 30+) makes any source private so that "even a privileged Assistant cannot capture concurrently" ([same page](https://developer.android.com/media/platform/sharing-audio-input#android-11-behavior)). **[Android docs]**

**What that means here:**

- With `MIC` or `VOICE_RECOGNITION` and privacy-sensitive off, "Hi BYD" should keep working while the app records, and the app should get silence only while the assistant's dialog is on screen. **[Inference]** This assumes BYD's assistant goes through the assistant role. If it listens through a DSP hotword path or an exclusive HAL stream instead, the result could differ. **[Unknown]**
- With `VOICE_COMMUNICATION` the app could take the mic away from "Hi BYD" for the whole lesson. Unless the car check shows real echo cancellation on that source, avoid it for a driving feature. **[Inference]**
- Phone calls over Bluetooth will silence the app during the call. That is fine, since the lesson is interrupted anyway.
- Register `AudioRecord.registerAudioRecordingCallback()` before `startRecording()` and check `AudioRecordingConfiguration.isClientSilenced()`, so the app knows which pauses were silenced and does not save them as Attempts ([same page](https://developer.android.com/media/platform/sharing-audio-input#configuration-changes)). `getEffects()` there shows which pre-processing is really active. **[Android docs]**

## 4. Foreground-service rules (Android 12, targetSdk 33)

- **The `microphone` type is required.** For targetSdk 30+, an app that "accesses the camera or microphone in a foreground service" must include the `microphone` type ([Android 11 FGS changes](https://developer.android.com/about/versions/11/privacy/foreground-services)). The `FOREGROUND_SERVICE_MICROPHONE` manifest permission and the startup type check become mandatory only for targetSdk 34+ ([FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone)). Declare it anyway; it costs nothing and is needed if targetSdk ever rises. **[Android docs]**
- **While-in-use rule.** `RECORD_AUDIO` is a while-in-use permission. If the foreground service is started while the app is in the background, it "cannot access the microphone" ([Android 11 FGS changes](https://developer.android.com/about/versions/11/privacy/foreground-services)). Below Android 14 the service starts but gets no mic access ([background start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start#wiu-restrictions)); logcat shows "Foreground service started from background can not have location/camera/microphone access". Exemptions include a start from a notification, a widget, or a system component. **[Android docs]**
- **Once started from the foreground, it keeps working in the background.** The `microphone` type exists to "continue microphone capture from the background" ([FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone)), so going Home, opening Navigation over the app, or turning the screen off should not stop capture by itself. The earlier rule "only apps in the foreground or a foreground service can capture … otherwise silence" still holds ([Sharing audio input](https://developer.android.com/media/platform/sharing-audio-input#pre-android-10-behavior)). **[Android docs]**
- **Media3 only passes `mediaPlayback`.** Media3 1.5.0's `MediaNotificationManager.startForeground()` calls `startForeground(..., FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)` explicitly ([source, 1.5.0](https://github.com/androidx/media/blob/1.5.0/libraries/session/src/main/java/androidx/media3/session/MediaNotificationManager.java)), and it re-enters the foreground on each play after a pause. Adding `|microphone` to `PlaybackService` in the manifest is therefore not enough: Media3 would keep resetting the type to media playback only. **[Android docs (source)]**
- **Design that follows:** a small separate `RecorderService` (`foregroundServiceType="microphone"`) in the same process, started by the Activity while it is visible (when the lesson starts with "Record my answers" on), stopped when the lesson stops. It avoids fighting Media3 over `startForeground`. **[Inference]**
- **The steering-wheel resume case.** If playback is resumed from the wheel or the DiLink music widget while the app is in the background, a recorder started at that moment is a background start and gets no mic access. Either keep the recorder service running across pauses (started once from the UI), or accept that recording only starts from the player screen. **[Inference]**
- Unlike location (`ACCESS_BACKGROUND_LOCATION`), the docs name no background permission for the microphone; the only exemptions are the start paths listed above ([background start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start#wiu-restrictions-exemptions)). **[Android docs]**

---

## On-car checklist (for ticket 03)

Run with the car parked, normal lesson volume, windows closed, fans low. A debug "Mic probe" screen in the test build should show, for each run: the source, a live input level meter, `isClientSilenced`, `getEffects()`, the active device, and a Save/Play button. Keep ADB over Wi-Fi connected (see byd-player ticket 09).

**A. Before recording (ADB)**

1. `adb shell getprop ro.build.version.sdk` (expect 32) and `adb shell pm list features | grep -i automotive` (is it AAOS or phone-style Android?).
2. `adb shell cat /vendor/etc/audio_effects.xml` (or `/vendor/etc/audio_effects.conf`): which effects are listed under `<preprocess>` for `voice_communication` and `voice_recognition`?
3. `adb shell dumpsys media.audio_policy | grep -iA3 "input\|mic"`: list the input devices (built-in mic, mic array, Bluetooth).
4. In the probe: `AcousticEchoCanceler.isAvailable()`, `NoiseSuppressor.isAvailable()`, `AutomaticGainControl.isAvailable()`, and `PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED`.

**B. Permission flow**

5. Turn on "Record my answers". Does the standard Android permission dialog appear, or a BYD-styled one? Grant "While using the app".
6. Look for a mic indicator in the status bar and a BYD "vehicle microphone" switch in Settings (Privacy / Sound / Voice). Note where they are. Turn each off once and confirm the probe shows silence, then turn them back on.

**C. Sources, with a lesson playing**

For each of `MIC`, `VOICE_RECOGNITION`, `VOICE_COMMUNICATION` (and `UNPROCESSED` if supported): play a lesson, record 30 s while you answer in the speaking pauses, then play the clip back when parked.

7. Is your voice clear and loud enough? Is the speaker's voice in the recording, and how loud next to yours (much quieter, about the same, louder)?
8. Did the lesson change when recording started: pause, volume drop (ducking), different sound (thinner, "phone call" sound, one speaker only)? Does it change back when recording stops?
9. With `VOICE_COMMUNICATION` only: is the speaker's voice clearly weaker than with `MIC` (real echo cancellation)? Does `getEffects()` list AEC?
10. Rate each source: voice quality, bleed, road/fan noise.

**D. Sharing and background**

11. While recording with `VOICE_RECOGNITION`, say "Hi BYD". Does the assistant wake up? Does the probe show `isClientSilenced = true` while its dialog is open, and recover afterwards?
12. Repeat 11 with `VOICE_COMMUNICATION`. Does "Hi BYD" still wake up?
13. Start recording from the player screen, then press Home and open Navigation. Keep answering for 30 s. Did audio keep coming (meter, clip)?
14. Turn the screen off if DiLink allows it, and repeat 13.
15. Stop recording, go Home, resume the lesson from the steering wheel, and try to start recording from the background (debug button in the notification, or automatic on play). Does the probe get audio or silence? Look in logcat for "can not have location/camera/microphone access".
16. If a phone is paired, place a short call while recording. Expect silence during the call; check recording recovers after it.

**Record the results** as a table: source × (voice clarity, bleed level, playback changed?, "Hi BYD" works?, effects listed). That table settles the speaker-bleed decision in the map.
