# Check that the folder picker reaches a USB stick on the car
Type: task
Status: resolved
Blocked by: 05

## Question

Does the system folder picker (SAF, `com.android.documentsui`) on the car show a USB stick, and can the app keep a read grant to a lesson folder on it and play from it after a restart? This decides whether the USB fallback (MediaStore, raw `/storage` paths or import) has to be built.

HITL checklist:
1. Make a FAT32 or exFAT USB stick with a `Pimsleur` folder holding a few `Pimsleur_I_NN.mp3`.
2. Plug it into the car's front **data** USB port.
3. In the app: Change folder → the picker → the USB drive → `Pimsleur` → Use this folder → Allow.
4. Play a lesson, close the app (swipe it away), reopen it, and check it resumes from the USB.
5. Unplug the stick, then plug it back in. Record what the app shows.

## Comments
- 2026-11-21: The car has no USB stick plugged in, but it has a removable **SD card** (`public:8,97`, uuid `677A-84B6`, 29 GB, 16 GB free; the car's own SentryMode/Recorder folders live there). The user asked to use it instead.

## Answer

Checked on the car on 2026-11-21 with the SD card. A USB stick was not used.

- All 90 lesson mp3s (623 MB) were copied by ADB to `/storage/677A-84B6/Pimsleur/`. Lessons I_01–I_03 also sit in internal storage at `Music/Pimsleur`.
- The system picker showed the SD card. The user chose `Pimsleur` on it, and the app kept the grant `content://com.android.externalstorage.documents/tree/677A-84B6%3APimsleur` (persisted). It listed **90 of 90 lessons** and played Lesson 90 from the card.
- After `am force-stop` and a relaunch, the app resumed Lesson 90 at the saved position (8:54), paused, from the card ✅.
- Side fact: in `adb shell`, `/sdcard` points to internal storage (`/storage/self/primary`). `/storage/sdcard` is a link to the SD card.
- Not checked: removing and reinserting the card (it sits in the car's slot and SentryMode uses it), and a real USB stick on the front data port. USB sticks show up as the same kind of public volume through the same `externalstorage` provider, so they should behave the same, but that is untested.
- So the SAF folder picker works on the car for removable storage. The USB fallback (MediaStore, raw paths, import) is **not needed** for the SD card.
