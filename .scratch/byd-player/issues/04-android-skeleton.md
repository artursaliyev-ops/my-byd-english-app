# Android project skeleton
Type: task
Status: open
Blocked by: 03

Kotlin + Compose + Media3 project with a MediaSessionService for background playback. Landscape, dark theme, English UI. minSdk comes from 03. Builds a debug APK from the command line.

## Comments

**From Find out DiLink capabilities (2026-11-21):** minSdk 28, targetSdk 33, arm64-v8a, `screenOrientation="landscape"`, designed for 15.6" at about 1920×1080. Developer mode is on, so first try connecting ADB. If it works, record `getprop ro.build.version.release`, `wm size`, `wm density`, and whether `am start -a android.intent.action.OPEN_DOCUMENT_TREE` opens a picker that shows the USB stick. Otherwise install the debug APK from a USB stick and add a small debug screen that shows the same facts. Record the results here, because Folder picker, library scan, lesson list depends on the SAF result.
