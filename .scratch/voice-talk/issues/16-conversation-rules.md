# 16: Anna's conversation rules in the app

**What to build:** the parts of Anna's behaviour that the app, not the model, has to drive. If the learner says nothing, a slow ring fills toward ~9 s and Anna nudges once ("Take your time." or her question, simpler), then waits silently. After "wait", Anna says "OK." and doesn't nudge. On "bye", Anna says her goodbye and the Talk ends after it plays, and the lesson resumes by the usual rule. At 30 minutes, Anna says goodbye and the Talk ends. Spec §4 control text, §6 steps 6, 7, 9.

**Blocked by:** 15 (A learner Turn)

**Status:** ready-for-agent

- [ ] At ~9 s of silence in **You**, the app sends `(The learner is quiet.)` once; there's no second nudge until the learner has spoken. Silence never ends a Talk.
- [ ] When Anna's output transcript for a Turn is exactly "OK.", the nudge is skipped until the learner next speaks.
- [ ] When Anna's output transcript starts with "Bye!" and contains "Drive safe", the Talk ends after that audio finishes; "By the way" doesn't end it (left to the model).
- [ ] The 30-min cap is wall-clock from the Talk's start. When it's reached, the current Turn finishes, then `(Time is up.)` is sent instead of opening the learner's turn, and the Talk ends after her goodbye.
- [ ] Each rule is covered in the state machine's unit tests.
