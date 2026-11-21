# Android project skeleton
Type: task
Status: resolved
Blocked by: 03

Kotlin + Compose + Media3 project with a MediaSessionService for background playback. Landscape, dark theme, English UI. minSdk comes from 03. Builds a debug APK from the command line.

## Comments

**From Find out DiLink capabilities (2026-11-21):** minSdk 28, targetSdk 33, arm64-v8a, `screenOrientation="landscape"`, designed for 15.6" at about 1920×1080. Developer mode is on, so first try connecting ADB. If it works, record `getprop ro.build.version.release`, `wm size`, `wm density`, and whether `am start -a android.intent.action.OPEN_DOCUMENT_TREE` opens a picker that shows the USB stick. Otherwise install the debug APK from a USB stick and add a small debug screen that shows the same facts. Record the results here, because Folder picker, library scan, lesson list depends on the SAF result.

**Progress (2026-11-21):** Skeleton is in `android/` and builds a debug APK from the CLI.
- Toolchain (Homebrew): openjdk@17, android-commandlinetools (SDK root `/opt/homebrew/share/android-commandlinetools`, licenses accepted), platform-tools (adb 37), android-35, build-tools 34/35. The "Java is installed" note on the map was wrong: there was no JDK before.
- Versions: Gradle wrapper 8.10.2, AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, Media3 1.5.0. compileSdk 35, minSdk 28, targetSdk 33, arm64-v8a only, landscape.
- Build: `cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew assembleDebug` produces `app/build/outputs/apk/debug/app-debug.apk` (about 31 MB).
- `PlaybackService` (MediaSessionService + ExoPlayer, speech audio attributes, audio focus, becoming-noisy) is wired up through a MediaController.
- A temporary **Device check** screen shows Android version, display px/dpi, the OPEN_DOCUMENT_TREE handler, and storage volumes. **Pick audio folder** opens the SAF picker, persists the permission, and counts mp3s. **Play first mp3** checks SAF URI playback and background audio. **Save report to folder** writes `byd-check.txt` into the picked folder and also tests write access. Everything is logged under the tag `BydCheck`.
- Still open: the run on the car. No device showed up in `adb devices` on the Mac.

**Emulator run (2026-11-21):** AVD `byd_dilink` (Android 13, arm64, 1920×1080 at 160 dpi, Google DocumentsUI). The whole flow works: the picker opens, the Music folder can be picked, the persistable permission is granted, 3 mp3s are found recursively, the mp3 plays from its SAF URI, playback continues after HOME (MediaSession state=PLAYING, position advancing), and `byd-check.txt` is written into the tree. Usable layout height is 1020 px because the system bar takes 60 px.
- Fixed a bug in the check screen: on Android 11+, `resolveActivity(OPEN_DOCUMENT_TREE)` returned NONE because of package visibility. The manifest now has a `<queries>` entry for it. Use the rebuilt `byd-player-debug.apk` for the car.
- Finding: Android 11+ DocumentsUI refuses the **root** of a storage volume ("Can't use this folder"). On the USB stick, lessons must be in a subfolder (e.g. `Pimsleur/`), not at the root.
- The emulator doesn't settle the car question: DiLink's picker build and its USB visibility are still unverified.

## Answer

**Done (2026-11-21).** The skeleton is in `android/`: Kotlin + Compose + Media3, with a `PlaybackService` (MediaSessionService) for background playback, landscape-only, dark theme, English UI, minSdk 28 / targetSdk 33, arm64-v8a. `./gradlew assembleDebug` builds the debug APK (JDK 17 from Homebrew; see Progress above for the toolchain and versions).

**SAF result:** the system folder picker is the chosen route for "Folder picker, library scan, lesson list".
- On the car: the user has seen a folder-picking permission screen in other apps on their DiLink, so a SAF picker exists there. Whether it lists the **USB stick** has not been checked on the car itself.
- On the emulator (Android 13, 1920×1080): the whole flow works, including picking, persistable permission, recursive mp3 scan, playback from SAF URIs, background playback, and writing into the tree.
- Constraint: you can't pick the root of a volume, so lessons go in a subfolder on the stick.
- Remaining risk: if the car's picker doesn't show the USB stick, "Folder picker, library scan, lesson list" falls back as already noted there (MediaStore, then raw paths, then one-time import). The device-check APK can still be run on the car whenever it's convenient.

