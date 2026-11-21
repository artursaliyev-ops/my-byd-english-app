package app.bydplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakingPausesTest {
    private fun seg(start: Long, end: Long, text: String) = Segment(start, end, text, english = false)

    @Test fun russianPromptThenEnglishAnswerComparesWithTheAnswer() {
        val p = speakingPauses(listOf(seg(0, 2_000, "Как сказать?"), seg(5_000, 6_000, "Excuse me.")))
        assertEquals(1, p.size)
        assertEquals(AttemptKind.Answer, p[0].kind)
        assertEquals(1, p[0].compareIndex)
        assertEquals(2_000, p[0].startMs)
        assertEquals(2_200, p[0].keepStartMs)
        assertEquals(4_900, p[0].keepEndMs)
    }

    @Test fun englishThenRussianIsAnEchoOfTheLineBefore() {
        val p = speakingPauses(listOf(seg(0, 2_000, "Excuse me."), seg(4_000, 6_000, "Теперь скажите")))
        assertEquals(AttemptKind.Echo, p.single().kind)
        assertEquals(0, p.single().compareIndex)
    }

    @Test fun languageIsByAlphabetNotWhisperTag() {
        // Whisper tagged this English line as Russian (english = false); the alphabet decides.
        val p = speakingPauses(listOf(seg(0, 2_000, "Sir."), seg(4_000, 5_000, "Sir.")))
        assertEquals(AttemptKind.Answer, p.single().kind)
        assertEquals(1, p.single().compareIndex)
    }

    @Test fun russianToRussianShortGapsAndTinyWindowsAreNotSpeakingPauses() {
        assertTrue(speakingPauses(listOf(seg(0, 2_000, "Слушайте."), seg(5_000, 6_000, "Скажите."))).isEmpty())
        assertTrue(speakingPauses(listOf(seg(0, 2_000, "Как?"), seg(2_999, 4_000, "Sir."))).isEmpty())
        // 1.0 s gap qualifies, leaving exactly 0.7 s after trimming.
        assertEquals(1, speakingPauses(listOf(seg(0, 2_000, "Как?"), seg(3_000, 4_000, "Sir."))).size)
    }

    @Test fun oneLineCanHaveAnAnswerAndAnEcho() {
        val p = speakingPauses(
            listOf(seg(0, 2_000, "Как сказать?"), seg(5_000, 6_000, "Excuse me."), seg(9_000, 10_000, "Ещё раз.")),
        )
        assertEquals(listOf(AttemptKind.Answer, AttemptKind.Echo), p.map { it.kind })
        assertEquals(listOf(1, 1), p.map { it.compareIndex })
    }

    @Test fun fingerprintChangesWithTheTranscript() {
        val a = listOf(seg(0, 2_000, "Sir."))
        assertEquals(transcriptFingerprint(a), transcriptFingerprint(listOf(seg(0, 2_000, "Sir."))))
        assertNotEquals(transcriptFingerprint(a), transcriptFingerprint(listOf(seg(0, 2_100, "Sir."))))
    }
}
