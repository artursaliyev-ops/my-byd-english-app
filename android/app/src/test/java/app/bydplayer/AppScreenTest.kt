package app.bydplayer

import org.junit.Assert.assertEquals
import org.junit.Test

/** Talk spec §7 "Coming back to the app" and §9 "Car switched off": which screen the app shows. */
class AppScreenTest {
    @Test
    fun `while a Talk runs the app opens on the Talk screen, whatever was shown before`() {
        for (settings in listOf(true, false)) for (player in listOf(true, false)) for (lesson in listOf(true, false)) {
            assertEquals(Screen.Talk, appScreen(talkActive = true, showSettings = settings, showPlayer = player, hasLesson = lesson))
        }
    }

    @Test
    fun `with no Talk left, a launch that resumed the last lesson opens on the player`() {
        assertEquals(Screen.Player, appScreen(talkActive = false, showSettings = false, showPlayer = true, hasLesson = true))
    }

    @Test
    fun `with no Talk, Settings and the lesson list show as before`() {
        assertEquals(Screen.Settings, appScreen(talkActive = false, showSettings = true, showPlayer = true, hasLesson = true))
        assertEquals(Screen.Lessons, appScreen(talkActive = false, showSettings = false, showPlayer = true, hasLesson = false))
        assertEquals(Screen.Lessons, appScreen(talkActive = false, showSettings = false, showPlayer = false, hasLesson = true))
    }
}
