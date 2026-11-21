# Folder picker, library scan, lesson list
Type: task
Status: open
Blocked by: 04

Use the SAF tree picker and keep the URI permission across restarts. Scan the folder for the I/II/III/Reading mp3 files, then show the grouped lesson list with completed marks.

## Comments

**From Find out DiLink capabilities (2026-11-21):** SAF/USB access on this car is unverified. Wait for the SAF check in Android project skeleton. If the picker is missing or can't see USB, fall back to MediaStore audio, then raw `/storage/<volume>` paths with storage permission, then a one-time import into app storage.
