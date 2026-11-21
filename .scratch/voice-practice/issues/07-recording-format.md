# Choose how Attempts are stored
Type: grilling
Status: resolved
Blocked by: 05, 06

## Question

How is an Attempt written to app-internal storage: codec and bitrate (the car delivers 16 kHz mono PCM from `VOICE_COMMUNICATION`), one file per Attempt or one file per lesson with offsets, file naming and the index that ties each file to its lesson and speaking pause (answer or echo)? Budget: up to 5 lessons × ~280 speaking pauses ≈ 65 min of kept voice; Attempts are replaced one speaking pause at a time, deleted a lesson at a time and all at once by "Delete my answers". Also: what happens to Attempts when the transcript for a lesson changes in a later app version.

## Answer

Resolved 2026-11-21 with the user (HITL grilling, one round, all recommendations accepted).

- **Format**: AAC-LC, 32 kbit/s, 16 kHz mono, in `.m4a`. About 15 MB for the full budget of 5 lessons × ~280 speaking pauses (≈ 65 min), against ~125 MB as WAV. Opus was not chosen because its encoder on the car's Android 12 is unverified.
- **One file per Attempt**, so replacing one speaking pause's Attempt never touches the others.
- **Layout, no database**: one folder per lesson under app-internal storage, named by transcript id (`I_05/`); each file named by kind and the speaking pause's start time in lesson ms, e.g. `answer-0123450.m4a`, `echo-0125600.m4a`. A new Attempt overwrites the file with the same name. "The last 5 lessons" are the 5 folders with the newest file; the oldest folder is deleted when a 6th gets its first Attempt. "Delete my answers" deletes every folder.
- **Transcript changes**: each lesson folder keeps a short fingerprint of the transcript it was recorded against; when the bundled transcript no longer matches, that lesson's folder is deleted.
