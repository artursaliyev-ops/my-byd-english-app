# DiLink capabilities for a sideloaded Pimsleur player (BYD Song Plus)

Researched 2026-11-21. Target: a Kotlin + Jetpack Compose + Media3 APK running directly on the Song Plus head unit (landscape).

**How much to trust this.** BYD does not publish developer documentation, Android versions, or a sideloading policy for DiLink. Its own support pages only say DiLink is "Android-based" ([BYD DiLink support](https://www.byd.com/mea/support/dlink)). Almost everything below therefore comes from community reverse-engineering repos, owner forums and vendor pages. Each claim is tagged:

- **[Official]**: BYD or a manufacturer spec page
- **[Corroborated]**: two or more independent community sources agree
- **[Single source]**: one community report or vendor page. Treat it as a lead to check in the car.

Behaviour varies by **DiLink generation, firmware build and sales region**. The checklist at the end is the real source of truth for *this* car.

---

## 1. Android version and minSdk

### Song Plus variants mapped to DiLink generation

This is the community-maintained table at [byd.forum: DiLink Versions](https://byd.forum/dilink-versions.html/). It is the most complete mapping found, but it is **[Single source]**. Note that BYD's internal "DiLink 50/100" names differ from the marketing "3.0/4.0/5.0" labels.

| Song Plus variant (China model year) | DiLink (marketing / internal) | Android | SoC |
|---|---|---|---|
| Song Plus (ICE, 2020) | 2.x / "DiLink 20" | 7 or 9 depending on controller | Snapdragon 625 (some controllers) |
| Song Plus **DM-i** (2021) | **3.0** / "DiLink 50", ctrl 13 | **10** | Snapdragon 665, 8+128 GB |
| Song Plus **EV** (2021) | 3.0 / "DiLink 50", ctrl 15 | not listed (likely 10) | not listed |
| Song Plus 5G (2022) | 4.0 / "DiLink 50P", ctrl 17 | not listed | Snapdragon 690 family |
| Song Plus / **Champion Edition** (2023–24) | 4.0 / "DiLink 50" ctrl 18, or "50P" ctrl 21 on the high trim | not listed | not listed |
| Song Plus (2025) | 5.0 / "DiLink 100", ctrl 23 | **12** | Snapdragon 778G/782G, 12+256 GB |

Export names: the 2023+ Song Plus (Champion facelift) is sold as the **Seal U** in Europe and the **Sealion 6** in Australia, NZ, Thailand, the Philippines and other markets ([Wikipedia: BYD Song Plus](https://en.wikipedia.org/wiki/BYD_Song_Plus)). Export cars are generally DiLink 4.0 or later.

### Corroborating evidence

- **DiLink 3.0 = Android 10 (API 29)** **[Corroborated]**: a Dolphin DiLink 3.0 teardown reports Android 10, a Qualcomm QCM6125, ARM64 and about 3.5 GB RAM ([wheregoes/byd-dolphin-hacking](https://github.com/wheregoes/byd-dolphin-hacking)). An ABRP bug report describes a Tang head unit as "Android 10 based, API 29" ([ABRP bug tracker](https://abrp.upvoty.com/b/report-a-bug/gps-unavailable-after-427)). byd.forum also lists DiLink 3.0 as Android 10. One vendor page instead claims DiLink 3.0 is "Android 9-based" (anexplorer.io), which conflicts with the teardown. Trust the teardown.
- **DiLink 5.0 = Android 12** **[Corroborated]**: byd.forum, plus two independent apps tested on a DiLink 5 Tang running Android 12 ([spotify-android-head-units test.13](https://github.com/romanchukg-cloud/spotify-android-head-units/releases/tag/v5.5.0-headunit-test.13), [DiPlay PR #8](https://github.com/shihabal3amri/DiPlay/pull/8)).
- **DiLink 5.1 = Android 13 or 14** **[Single source]** (byd.forum). It is not used on the Song Plus.
- **DiLink 4.0's Android version is unclear.** byd.forum leaves it blank. A vendor install guide says "Android 10 on older DiLink, Android 12 on newer Song Plus" ([dotix.dev](https://dotix.dev/byd-install-guide), a vendor page). Expect Android 10 or 12.
- **APK constraints seen on DiLink 3.0** **[Single source]**: for the USB "Third Party Apps" auto-installer, the Dolphin repo lists ARM64, minSdk ≤ 29 and **targetSdk ≤ 33**. The targetSdk cap is worth respecting until it has been tested on this car.

### Recommendation

- **minSdk 28 (Android 9).** Compose and Media3 both support far lower levels, so this costs nothing. It covers every Song Plus DM-i and EV (Android 10 or later) and also older DiLink 2.x controllers that run Android 9.
- If the in-car check confirms Android 10 or higher, minSdk 29 is also fine.
- **targetSdk 33 at first**, because of the DiLink 3.0 installer anecdote. Raise it only after an install with a higher target has been tested on the car.
- **ABI:** build `arm64-v8a`. All reported units are 64-bit Qualcomm.

---

## 2. Screen size, resolution and density

| Fitment | Size | Native resolution | Confidence |
|---|---|---|---|
| Song Plus DM-i / EV 2021–24, most China trims | **12.8"** rotating | likely 1920×1080 | size **[Official/Corroborated]** ([Car News China 2024 Song Plus DM spec table](https://data.carnewschina.com/database/byd/byd-song-plus-dm/2024/params), Wikipedia). Resolution **[Single source]**: a parts marketplace gives 1920×1080 for the Atto 3's 12.8" screen ([accio.com](https://www.accio.com/plp/byd-atto-3-screen)). |
| Song Plus top/5G trim, Champion 2023+, Seal U, Sealion 6 | **15.6"** rotating | **1920×1080** likely | size **[Corroborated]** ([Car News China](https://data.carnewschina.com/database/byd/byd-song-plus-dm/2024/params), [Motor1 Seal U](https://www.motor1.com/news/724166/byd-seal-u-interior-analysis/)). 1920×1080 is **[Official]** for the BYD Seal's 15.6" rotating screen ([BYD Caribbean: Seal](https://www.byd.com/caribbean/car/seal-LHD)). That is very probably the same panel family, but it is not confirmed for the Song Plus. |

- **Rotation:** both screens rotate to portrait, by button or voice ([BYD DiLink support](https://www.byd.com/sc/support/dlink)). The app must handle portrait (for example 1080×1920) as a configuration change and must not lock to landscape blindly. Third-party apps reportedly need an explicit "rotation / split-screen" setting to behave well (DiPlay compatibility notes).
- **Density / dp width: unknown, no source found.** BYD sets `ro.sf.lcd_density` per build. For a 1920 px-wide landscape screen:
  - at 160 dpi (mdpi): 1920 dp wide
  - at 240 dpi (hdpi): 1280 dp wide
  - at 320 dpi: 960 dp wide
- **Design implication:** use a responsive Compose layout. Treat the screen as at least 960 dp wide in landscape (expanded window size class), and a compact or medium width in portrait.
- The first test build should show `DisplayMetrics` and `Configuration.screenWidthDp` on screen (see the checklist).

---

## 3. Installing third-party APKs

### Methods reported by owners

1. **USB stick plus the built-in File Manager** **[Corroborated]**
   - Copy the APK to a USB drive, plug it into the front data USB-A port, open it in the car's File Manager and install.
   - Format the drive FAT32 with an MBR partition table. exFAT is also reported to work.
   - Sources: [dotix.dev](https://dotix.dev/byd-install-guide), anexplorer.io, and Chinese owner guides that describe copying the APK to a USB drive and opening it in File Manager ([Zhihu: Amap in-car install guide for Song Plus](https://www.zhihu.com/tardis/jm/art/2054163252083532531)).
   - A "for your driving safety" prompt may appear. Park in P with the handbrake on, then allow "install unknown apps" for File Manager.
2. **USB "Third Party Apps" folder auto-install** (DiLink 3.0 era) **[Corroborated]**
   - Put APKs in a folder named like `Third Party Apps` (some regions add a country suffix) on the USB drive. The system installs them silently.
   - Sources: [byd-dolphin-hacking](https://github.com/wheregoes/byd-dolphin-hacking), [Seal downgrade write-up](https://juanmackie.substack.com/p/downgrading-byd-seal-from-v11-2310).
   - The Dolphin repo lists per-region restrictions:
     - Kazakhstan: a 14-app whitelist
     - India: Mappls only
     - Europe, Japan and Australia: online verification
3. **Engineering mode plus ADB, then an unlocked PackageInstaller** **[Single source, but widely referenced]**
   - On firmware after build 2407, BYD "disabled standard developer tools", which stops APK installs ([ahmada3mar/BYD](https://github.com/ahmada3mar/BYD)).
   - The workaround: open a hidden engineering/verification menu from the dial pad, generate a code from the car's IMEI with a third-party tool, enable ADB over Wi-Fi, then `adb install` a replacement PackageInstaller.
   - Codes are deliberately not reproduced here. They are firmware-specific, and the tools are unvetted third-party binaries.
4. **Google Play** **[Single source / variable]**: some DiLink 4.0+ export cars and the newest China models ship Google Automotive Services with Play ([Canaltech](https://canaltech.com.br/software/qual-e-o-sistema-usado-nos-carros-byd), anexplorer.io). This does not help with a private APK, except that the Play Files app could serve as a file manager.

### BYD closing these routes

- **Dolphin, 2024:** after an OTA, the "Third Party Apps" folder and the silent installer were locked. The suggested fix was to downgrade, install, then update again (XDA thread "BYD Dolphin 1.2 update APK locked", via search snippet; the page itself refused fetches). **[Single source]**
- **Seagull, DiLink 3.0F:** reported locked down after an OTA (XDA "BYD Multimedia - Install APK", via snippet). **[Single source]**
- **Shark 6, Australia, October 2026:** BYD Australia says an OTA will close an ADB-via-infotainment route and tighten "debug-interface management and app permission controls". Sideloaded apps "may be lost as a side effect" ([The Auto Wire](https://theautowire.com/2026/10/05/byd-shark-6-security-fix-ota-adb-aftermarket-app/)). **[Official statement, reported secondhand]**
- **Europe, 2026:** a vendor blog claims EU firmware 2.0/2.1 blocks some third-party apps ([evparts4x4](https://evparts4x4.com/blogs/news/how-to-fix-byd-atto-3-software-update-problems), a low-trust vendor).
- **Overall trend:** China-market firmware is the most permissive. Export firmware (EU, AU, SEA, LatAm) is more often locked out of the box, and each new OTA tends to close routes.

### Risks

- **OTA wiping apps.** Minor OTAs are generally said to keep user apps, while major ones (for example DiLink 4 to 5) may wipe them (dotix.dev, vendor). Security OTAs may remove sideloaded apps deliberately (Shark 6).
- **Plan for reinstalling.** Keep the APK and the audio files on the USB drive, so reinstalling takes one step.
- **Warranty.** BYD publishes no policy. USB File Manager installs use a stock UI path and are low risk. Engineering-mode/ADB routes and firmware downgrades go around manufacturer controls, can leave the head unit in an unsupported state, and could be grounds to deny an infotainment warranty claim.
- **Security.** Unlocked PackageInstaller APKs and code generators come from unvetted third parties.

---

## 4. USB storage through the Storage Access Framework (ACTION_OPEN_DOCUMENT_TREE)

**No source found, either way.** Nothing public documents whether DiLink ships DocumentsUI (`com.android.documentsui` / `com.google.android.documentsui`), or whether USB volumes show up in it.

What is known or can be inferred:

- **DocumentsUI usually ships.** It is part of AOSP and is normally present even on builds without Google services. OEM automotive builds sometimes remove or hide it. Generic Android reports show apps then fail with "No Activity found to handle Intent { act=android.intent.action.OPEN_DOCUMENT_TREE }" ([MaterialFiles #1674](https://github.com/zhanghai/MaterialFiles/issues/1674)).
- **Weak hint that a picker exists:** DiPlay's notes say that on Android 9 head units, diagnostics are "saved through a document picker" ([DiPlay COMPATIBILITY.md](https://raw.githubusercontent.com/shihabal3amri/DiPlay/master/docs/COMPATIBILITY.md)). **[Single source]**
- **BYD's own players use their own access path.** BYD's music and video apps and the File Manager read USB drives, but these are privileged system apps. Their access proves nothing about SAF.
- **USB may not be visible through MediaStore or raw paths.** On AOSP, USB OTG drives are "public volumes" that are often not visible to apps by raw path, and may not be indexed by MediaStore ([MaterialFiles PR #1](https://github.com/mtt0000/MaterialFiles/pull/1)).

**Design implication:** build storage access as a chain of fallbacks, and detect at runtime which one works:

1. `ACTION_OPEN_DOCUMENT_TREE`. Check `intent.resolveActivity()` first, then persist the URI permission.
2. MediaStore audio query across `MediaStore.getExternalVolumeNames()`.
3. Direct path scan of `/storage/*` with `READ_EXTERNAL_STORAGE`. On API 29 also set `requestLegacyExternalStorage="true"`.
4. Import into app-private storage: `adb push`, a one-time copy, or bundling a lesson pack.

---

## 5. Other relevant behaviour

- **Google Play Services: assume absent.**
  - China-market DiLink 3.0/4.0 has no GMS **[Corroborated]**: the ABRP Tang report, plus Chinese owners installing separate in-car app builds.
  - Some export and newer cars have Google Automotive Services. Never depend on GMS: no Firebase, no Play Integrity, no FusedLocation. Media3 and Compose do not need GMS.
- **Steering-wheel media buttons work through a standard MediaSession on DiLink 5.0 / Android 12** **[Corroborated by two apps]**.
  - The wheel sends `KEYCODE_MEDIA_NEXT`, `KEYCODE_MEDIA_PREVIOUS` and `KEYCODE_AUTO_MEDIA_PLAY_PAUSE` (353, which BYD rewrites to play or pause).
  - BYD's `MediaSessionService` routes them to **the package holding audio focus**. Without focus, they go to `com.byd.mediacenter` ([DiPlay PR #8](https://github.com/shihabal3amri/DiPlay/pull/8)). The Spotify head-unit build confirms wheel buttons work on DiLink 5 ([release test.13](https://github.com/romanchukg-cloud/spotify-android-head-units/releases/tag/v5.5.0-headunit-test.13)).
  - For DiLink 3.0/4.0 (Android 10) no data was found; it is probably the same. A Media3 `MediaSessionService` with `ExoPlayer` (`handleAudioFocus = true`) is exactly what's needed.
  - Not relevant here: a report that BYD wheel keys arrive as proprietary serial broadcasts ([open-headunit PR #917](https://github.com/andreknieriem/open-headunit/pull/917)) concerns an aftermarket Android 4.4 unit, not DiLink.
- **Audio focus** **[Single source]**:
  - If the car's own radio, Bluetooth music or Spotify starts, it takes focus, and the wheel then controls that source.
  - Request focus again on every play, not just once (DiPlay PR #8).
  - Expect navigation prompts as transient ducking. Pimsleur is speech, so use `AUDIO_CONTENT_TYPE_SPEECH`, which makes Media3 pause rather than duck on transient focus loss.
- **Background killing:** no data was found. The head unit has 3.5–8 GB RAM, and BYD's own apps hold focus aggressively.
  - Run playback as a foreground `MediaSessionService` with a media notification (Media3 does this by default).
  - Save the playback position often (every few seconds and on pause), so a kill or an ignition cycle can resume.
  - Do not rely on autostart at ignition. That depends on firmware and permissions (DiPlay notes).
- **Built-in browser downloads are blocked** on at least one DiLink 3.0 firmware ("Download proibido"; Dolphin repo). Do not plan on downloading the APK from the car's browser.

---

## Checklist to verify in the car

Menu labels differ by firmware and language. Paths are approximate where marked "~".

1. **DiLink and Android version.** Settings → System (~"System / General") → About / Version information.
   - Note the DiLink/UI version, the software build string (for example `…2407…`) and the Android version if shown.
   - Note the region the car was built for: China import or local export.
2. **Screen.** Note 12.8" or 15.6" (15.6" is roughly the width of a laptop screen), whether it rotates, and the trim. Exact resolution and density are confirmed by item 6.
3. **File Manager install path.**
   - Check that a "File Manager" (文件管理) app is in the app drawer.
   - Plug in a FAT32 (MBR) USB stick holding any small test APK, using the front data port.
   - In File Manager, open the USB drive, tap the APK and record what appears: installer, "unknown sources" prompt, "for your driving safety" block, or nothing. Do this parked in P with the handbrake on.
4. **Silent-install folder** (only if item 3 fails): create a `Third Party Apps` folder at the USB root with the APK, plug in, wait about 1 minute, then check the app drawer.
5. **Quick SAF check without our app:**
   - Settings → Apps (~"Application management") → show system apps. Look for **"Files"**, **"Documents"** or **"DocumentsUI"** (`com.android.documentsui`). Note whether it exists and is enabled.
   - If any preinstalled or Play app has an "Open folder" or "Choose file" action, use it with the USB stick plugged in. Check whether the system picker opens, and whether the USB drive appears in its side menu (☰).
6. **Definitive probe** (the first sideloaded build should be a tiny probe screen that shows):
   - `Build.VERSION.SDK_INT` and `Build.DISPLAY`
   - `DisplayMetrics` (pixels and `densityDpi`) and `Configuration.screenWidthDp/HeightDp` in both rotations
   - whether `Intent(ACTION_OPEN_DOCUMENT_TREE).resolveActivity()` is non-null, plus a button that launches it so you can see whether the USB drive is listed and can be picked
   - `MediaStore.getExternalVolumeNames()` and a listing of `/storage/`
   - whether `com.google.android.gms` is installed
7. **Media buttons and audio focus.** In the probe or a Media3 test build, play audio and press the wheel Next, Previous and Play/Pause buttons. Then start BYD radio or Bluetooth music, switch back, and check that the wheel follows our app again after pressing Play in it.
8. **Survival.**
   - Turn the car fully off and on (lock, wait about 5 minutes, unlock). Check that the app is still installed and resumes at the saved position.
   - After any OTA, recheck item 3 and whether the app survived.
