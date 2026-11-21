# BYD Pimsleur Player

An Android app on the BYD DiLink head unit for listening to the Pimsleur I/II/III audio lessons with a synced transcript.

## Language

**Media buttons**:
Every ⏮ ⏭ control outside the app's own screen: the steering-wheel left/right buttons, the DiLink "now playing" widget, and Bluetooth or other external media keys.
_Avoid_: Wheel buttons (too narrow), hardware keys

**Button mode**:
The setting that decides what the media buttons do: switch lesson, or skip 5 s back/forward. The on-screen controls never follow it.
_Avoid_: Skip mode, wheel mode

**Wheel hold**:
Holding the steering-wheel ◀ or ▶. The car sends it as its own BYD key, not as a repeated media button, and it does the action of the other Button mode.
_Avoid_: Long-press (Android's long-press flag never arrives)

**Speaking pause**:
A silence in a lesson where the learner speaks out loud: answering a Russian prompt before the speaker's English answer, repeating a line the speaker says twice, or echoing the answer just heard.
_Avoid_: Gap, silence

**Attempt**:
The learner's own voice recorded during one speaking pause. It is played back next to the speaker's line it is compared with: usually the line after the pause, but the line before it when the Attempt echoes the answer just heard. A speaking pause has at most one Attempt: a newer one replaces it.
_Avoid_: Recording, take, pass (there is no lesson-wide pass)

**Talk**:
A spoken English conversation between the learner and the app's talk partner, started from the screen and carried on hands-free. Lessons are paused for the whole Talk.
_Avoid_: Chat, conversation, dialogue

**Turn**:
One exchange inside a Talk: the learner says something, then the talk partner answers out loud.
_Avoid_: Message, round

**Talk partner**:
The one voice the learner talks with during a Talk: a friendly acquaintance, not a teacher. It speaks simple English and keeps the conversation going with a question back.
_Avoid_: Bot, assistant, AI, tutor

**Recast**:
The talk partner's way of handling a mistake: it uses the correct form naturally in its own reply and carries on, without pointing out or explaining the mistake.
_Avoid_: Correction, feedback
