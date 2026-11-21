package app.bydplayer

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class ButtonModeTest {
    @Test fun defaultIsSkip() {
        assertEquals(ButtonMode.Skip, ButtonMode.DEFAULT)
    }

    @Test fun skipMode() {
        assertEquals(ButtonEffect.Back5, ButtonMode.Skip.effectOf(forward = false))
        assertEquals(ButtonEffect.Forward5, ButtonMode.Skip.effectOf(forward = true))
    }

    @Test fun lessonMode() {
        assertEquals(ButtonEffect.PreviousLesson, ButtonMode.Lessons.effectOf(forward = false))
        assertEquals(ButtonEffect.NextLesson, ButtonMode.Lessons.effectOf(forward = true))
    }

    @Test fun holdDoesTheOtherMode() {
        assertEquals(ButtonEffect.PreviousLesson, ButtonMode.Skip.holdEffectOf(forward = false))
        assertEquals(ButtonEffect.NextLesson, ButtonMode.Skip.holdEffectOf(forward = true))
        assertEquals(ButtonEffect.Back5, ButtonMode.Lessons.holdEffectOf(forward = false))
        assertEquals(ButtonEffect.Forward5, ButtonMode.Lessons.holdEffectOf(forward = true))
    }

    @Test fun recognisesWheelHoldKeys() {
        assertEquals(false, wheelHoldDirection("KEYCODE_AUTO_MEDIA_PREVIOUS_LP"))
        assertEquals(true, wheelHoldDirection("KEYCODE_AUTO_MEDIA_NEXT_LP"))
        assertEquals(null, wheelHoldDirection("KEYCODE_MEDIA_NEXT"))
        assertEquals(null, wheelHoldDirection("KEYCODE_AUTO_VOLUME_UP_LP"))
        assertEquals(null, wheelHoldDirection("1234"))
    }

    @Test fun describesKeys() {
        assertEquals(
            "key MEDIA_NEXT down · repeat 3 · long-press · via screen",
            describeKey("KEYCODE_MEDIA_NEXT", KeyEvent.ACTION_DOWN, 3, true, "screen"),
        )
        assertEquals(
            "key MEDIA_PREVIOUS up · repeat 0 · via session, com.example",
            describeKey("KEYCODE_MEDIA_PREVIOUS", KeyEvent.ACTION_UP, 0, false, "session, com.example"),
        )
    }

    @Test fun describesCommands() {
        assertEquals("cmd next → +5 s · from com.byd.launcher", describeCommand("next", ButtonEffect.Forward5, "com.byd.launcher"))
    }

    @Test fun withNoTalkTheMediaButtonsBehaveAsBefore() {
        for (mode in ButtonMode.entries) {
            assertEquals(ButtonAction.Do(mode.effectOf(false)), mode.actionOf(MediaPress.Previous, talking = false))
            assertEquals(ButtonAction.Do(mode.effectOf(true)), mode.actionOf(MediaPress.Next, talking = false))
            assertEquals(ButtonAction.Do(mode.holdEffectOf(false)), mode.actionOf(MediaPress.HoldPrevious, talking = false))
            assertEquals(ButtonAction.Do(mode.holdEffectOf(true)), mode.actionOf(MediaPress.HoldNext, talking = false))
            assertEquals(ButtonAction.AsUsual, mode.actionOf(MediaPress.PlayPause, talking = false))
        }
    }

    @Test fun duringATalkPlayPauseEndsItAndTheRestDoNothing() {
        for (mode in ButtonMode.entries) {
            assertEquals(ButtonAction.EndTalk, mode.actionOf(MediaPress.PlayPause, talking = true))
            for (press in listOf(MediaPress.Previous, MediaPress.Next, MediaPress.HoldPrevious, MediaPress.HoldNext)) {
                assertEquals(ButtonAction.Ignore, mode.actionOf(press, talking = true))
            }
        }
    }
}
