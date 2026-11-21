# Choose how Attempts are stored
Type: grilling
Status: open
Blocked by: 05, 06

## Question

How is an Attempt written to app-internal storage: codec and bitrate (the car delivers 16 kHz mono PCM from `VOICE_COMMUNICATION`), one file per Attempt or one file per lesson with offsets, file naming and the index that ties each file to its lesson and speaking pause (answer or echo)? Budget: up to 5 lessons × ~280 speaking pauses ≈ 65 min of kept voice; Attempts are replaced one speaking pause at a time, deleted a lesson at a time and all at once by "Delete my answers". Also: what happens to Attempts when the transcript for a lesson changes in a later app version.
