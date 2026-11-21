# 21: Talk off screen and with the screen off

**What to build:** the learner can leave the app or let the screen go dark and keep talking hands-free. Opening the app during a Talk goes straight to the Talk screen. If the car is switched off mid-Talk, nothing is kept and the app opens on the player next time. Spec §11, §9 "Car switched off", §7 "Coming back to the app".

**Blocked by:** 15 (A learner Turn)

**Status:** ready-for-agent

- [ ] With the app on Home or the map, Turns keep working on the one recording started from the visible screen (no new recording is started from the background).
- [ ] With the screen off, Turns keep working the same way.
- [ ] Opening the app while a Talk is active shows the Talk screen, not the player or the lesson list.
- [ ] After the process dies mid-Talk, the next launch opens on the player with no Talk state left behind.
- [ ] Barge-in stays off: the mic is closed while Anna speaks, and only End Talk interrupts her.
