# Folder picker, library scan, lesson list
Type: task
Status: resolved
Blocked by: 04

Use the SAF tree picker and keep the URI permission across restarts. Scan the folder for the I/II/III/Reading mp3 files, then show the grouped lesson list with completed marks.

## Comments

**From Find out DiLink capabilities (2026-11-21):** SAF/USB access on this car is unverified. Wait for the SAF check in Android project skeleton. If the picker is missing or can't see USB, fall back to MediaStore audio, then raw `/storage/<volume>` paths with storage permission, then a one-time import into app storage.

**Scope change (2026-11-21):** Reading Lessons are out of scope (see map, Out of scope). Scan and group only I/II/III. Ignore the `Pimsleur_Reading_Lesson_*` files and the PDF.

**From Android project skeleton (2026-11-21):** Go with SAF (`OpenDocumentTree` plus `takePersistableUriPermission`). The user has seen the picker in other DiLink apps, and it all works on the emulator. Working code to reuse is in `MainActivity.kt` (`inspectTree`, recursive `DocumentsContract` walk). Tell the user to put lessons in a subfolder, because the volume root can't be picked. Keep the fallback ready in case the car's picker doesn't list the USB stick. Replace the temporary Device check screen with the real lesson list.


## Answer

Built in `android/` and checked on the `byd_dilink` emulator (2026-11-21). The temporary Device check screen is gone.

- **Folder**: on first launch the app asks for a folder with `OpenDocumentTree`. `LibraryStore` takes a persistable read grant, releases grants for older folders, and saves the URI in prefs. On launch the folder only counts if it's still in `persistedUriPermissions`. If the grant is gone, the app shows "Folder access was lost" and offers the picker again. If the drive root can't be used, the app says to pick a folder inside it.
- **Scan** (`Library.kt`, `scanLibrary`): the `inspectTree` DocumentsContract walk now goes up to 4 levels deep, so either the course folder or an I/II/III subfolder works. It keeps only `Pimsleur_(I|II|III)_NN.mp3` with NN from 1 to 30, case-insensitive, and maps them to course numbers 1–90 (II_01 → 31, III_30 → 90). Reading Lessons, .doc files and duplicates are skipped. A failed listing (for example, the USB stick was pulled) shows "Lessons unavailable" with Try again and Choose another folder. JVM test: `LessonFileNameTest`.
- **Lesson list** (`LibraryScreen.kt`): tabs I (1–30) / II (31–60) / III (61–90), each with a done/found count. Below is a grid of 30 large tiles. Missing lessons are dimmed, completed ones get a green ✓, and the playing one is outlined. The list opens on the level of the first unfinished lesson. Tapping a tile queues that lesson and the ones after it on the `PlaybackService` controller. Long-press toggles the completed mark, stored as a set of lesson numbers in `LibraryStore.setCompleted`. Ticket 06 should call `setCompleted` when a lesson ends.
- **Checked on the emulator**: picked `Music` (`Pimsleur_I` with 3 files, `Pimsleur_II/Pimsleur_II_05.mp3`, and a `Reading/` decoy). Result: "4 of 90", I 1–3, II 35, Reading ignored. Long-press marked lesson 2. Tapping lesson 1 started playback (media session state 3, "Lesson 1"). After force-stop and relaunch, the list opened straight away with the mark kept.
- **Not done**: the MediaStore / raw path / import fallback isn't built. The only hook is a `TODO(fallback)` where the picker fails to launch. Build it if the picker on the car can't see the USB stick.
