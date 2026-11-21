package app.bydplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LessonFileNameTest {
    @Test fun levelsMapToCourseNumbers() {
        assertEquals(Level.I to 1, parseLessonFileName("Pimsleur_I_01.mp3"))
        assertEquals(Level.I to 30, parseLessonFileName("Pimsleur_I_30.mp3"))
        assertEquals(Level.II to 31, parseLessonFileName("Pimsleur_II_01.mp3"))
        assertEquals(Level.III to 90, parseLessonFileName("pimsleur_iii_30.MP3"))
    }

    @Test fun ignoresOtherFiles() {
        assertNull(parseLessonFileName("Pimsleur_Reading_Lesson_01.mp3"))
        assertNull(parseLessonFileName("Pimsleur_I_01.doc"))
        assertNull(parseLessonFileName("Pimsleur_IIII_01.mp3"))
        assertNull(parseLessonFileName("Pimsleur_I_31.mp3"))
        assertNull(parseLessonFileName("Pimsleur_I_00.mp3"))
        assertNull(parseLessonFileName("._Pimsleur_I_01.mp3"))
    }
}
