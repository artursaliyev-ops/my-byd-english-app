# Decide how Talk behaves around calls, buttons and the screen
Type: grilling
Status: resolved
Blocked by:

## Question

HITL. While a Talk runs in the car: what happens to Anna's audio and the mic when a phone call arrives or the learner places one (audio focus loss, pause vs end the Talk, what resumes after the call; the network side is settled in [Decide what Talk does when the network drops](11-network-drop.md)); what the steering-wheel and media buttons (play/pause, next/prev) do during a Talk; what happens if the screen turns off or the learner leaves the Talk screen (the mic keeps capturing once started on screen, per the voice practice car mic check); and whether barge-in stays off in the first build ([Shape the Talk screen and the feel of a Turn](06-talk-screen.md)), with the car echo check made a first-build check like road noise.

## Answer

Resolved 2026-11-21 (grilling with the user). The car handles phone calls itself (iPhone over Bluetooth), so the app only sees them as audio focus loss and may not tell a call from "Hi BYD" or a navigation prompt; the rules therefore follow the kind of focus loss, not its cause. They mirror [Decide what Talk does when the network drops](11-network-drop.md).

- **Transient focus loss** (a call rings or is placed, the BYD voice assistant, any short loss without ducking): the Talk pauses. Anna stops mid-line, the mic is closed, and the screen shows a grey "Paused" state with Anna's latest line kept. A learner's utterance in progress and any answer still arriving are dropped. When focus returns: the turn beep and the blue (your turn) state; Anna does not repeat her question. After **60 s** without focus, the Talk ends by itself and the lesson resumes by the usual rule (5 s back only if it was playing) once focus is back. The 30-min cap keeps counting while paused.
- **Ducking** (e.g. a navigation prompt that allows it): Anna just gets quieter; the Talk does not pause.
- **Permanent focus loss** (another media app starts playing): the Talk ends at once; the lesson stays paused, since focus now belongs to the other app.
- **Media buttons** during a Talk: play/pause = End Talk (the lesson resumes by the usual rule). ⏮ ⏭ and Wheel hold are ignored, whatever the Button mode, so the lesson never moves behind the learner's back.
- **Leaving the Talk screen** (Home, the map): the Talk carries on hands-free in the background. The mic is **one recording held open for the whole Talk**, started from the visible screen; "mic closed" means its samples are dropped in software, never a stop and restart (a restart from the background is muted on the car). Opening the app during a Talk goes straight to the Talk screen.
- **Screen off**: as above, the Talk carries on. Car switched off: the Talk is simply gone (nothing is kept), and the app opens on the player next time.
- **Barge-in**: stays off in the first build (mic closed while Anna speaks). Anna's echo into the mic is a first-car-build check like road noise; turning barge-in on is decided after that check.
- **First-car-build checks added**: the mic keeps capturing across Turns with the app in the background and with the screen off; what focus loss a real call and "Hi BYD" actually give.
