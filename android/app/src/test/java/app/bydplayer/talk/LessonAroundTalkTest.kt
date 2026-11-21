package app.bydplayer.talk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec §9: after a Talk the lesson plays again 5 s back, but only if it was playing before. */
class LessonAroundTalkTest {
    @Test
    fun `a lesson that was playing resumes 5 s back from where it paused`() {
        assertEquals(60_000L, resumeAfterTalk(wasPlaying = true, pausedAtMs = 65_000L))
    }

    @Test
    fun `a lesson that was paused stays paused`() {
        assertNull(resumeAfterTalk(wasPlaying = false, pausedAtMs = 65_000L))
    }

    @Test
    fun `near the start it resumes from the very start`() {
        assertEquals(0L, resumeAfterTalk(wasPlaying = true, pausedAtMs = 3_000L))
    }
}
