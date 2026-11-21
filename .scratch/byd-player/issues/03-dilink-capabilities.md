# Find out DiLink capabilities on the user's BYD Song Plus
Type: research
Status: resolved

Needs the user in the car. Find: Android version (Settings → About), screen resolution, how to install an APK (USB file manager / developer mode), and whether apps can read a USB drive through the system folder picker.

## Comments

**Research pass (2026-11-21).** Public sources are in [research/dilink-capabilities.md](../research/dilink-capabilities.md). Summary so far:
- Android 10 on DiLink 3.0 (2021) and Android 12 on DiLink 5.0 (2025). DiLink 4.0 (2022–24) is unconfirmed. Leaning toward minSdk 28 and targetSdk 33, because the USB auto-installer reportedly rejects higher targets. Build arm64.
- Screen is 12.8" or 15.6" and rotates. 1920×1080 is likely, but no source gives the density, so the layout must be responsive.
- Install from a FAT32 USB stick through File Manager, or older firmware's "Third Party Apps" folder. Newer and export firmware may block this, and OTAs may remove sideloaded apps.
- No evidence either way on whether the SAF folder picker or USB access works. Plan fallbacks.

Still open: the owner has to verify on the actual car with the checklist at the end of the research file. The ticket resolves when those answers are in.

**Owner's car (2026-11-21):** BYD Song Plus EV with "God's Eye" (DiPilot), so a 2025 or later model. Research points to DiLink 5.0 on Android 12, probably with a 15.6" screen. Not yet confirmed in the car. The firmware may be China or global, and that decides how sideloading works.

**Owner's answers (2026-11-21):** China-market car with the UI set to English. 15.6" screen that does **not** rotate. Installing apps from a USB stick **works**. **Developer mode is on.** The Android version and the SAF picker were not checked.

## Answer

- **Device:** 2025+ BYD Song Plus EV (God's Eye), China firmware with English UI, fixed 15.6" landscape screen. Per research it is probably DiLink 5.0 on Android 12 at 1920×1080. Not yet read off the device.
- **SDK:** **minSdk 28, targetSdk 33, arm64-v8a.** minSdk 28 is safe whether the unit runs Android 10 or 12, so the exact version does not block anything. targetSdk 33 keeps clear of the reported installer rejection of higher targets.
- **Layout:** **landscape only** (`screenOrientation="landscape"`), no portrait. Design for about 1920×1080, but keep it responsive because the density is unknown.
- **Install:** release and test builds go in as an APK on a FAT32 USB stick through File Manager. Developer mode is on, so the dev loop can try ADB (USB cable or wireless). If ADB works, it can also read the SDK level, `wm size`/`wm density`, and test the SAF picker directly.
- **SAF / USB access:** **still unverified.** Handled as a first check in Android project skeleton (via ADB or a probe screen), with the fallbacks below in Folder picker, library scan, lesson list.
- **Other:** assume no Google Play Services. Media3 MediaSession should receive the steering-wheel buttons as long as it requests audio focus on each play. Save the playback position often in case DiLink kills the app in the background.
