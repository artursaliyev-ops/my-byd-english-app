package app.bydplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun time() {
        assertEquals("0:00", formatTime(0))
        assertEquals("0:05", formatTime(5_999))
        assertEquals("12:34", formatTime(754_000))
        assertEquals("1:02:03", formatTime(3_723_000))
        assertEquals("0:00", formatTime(-200))
    }

    @Test fun speed() {
        assertEquals(listOf("0.75x", "0.9x", "1x", "1.1x", "1.25x", "1.5x"), SPEEDS.map(::formatSpeed))
    }
}
