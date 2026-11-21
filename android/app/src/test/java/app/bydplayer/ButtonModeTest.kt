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
}
