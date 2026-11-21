package app.bydplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AttemptStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private var clock = 1_000_000L
    private fun store() = AttemptStore(tmp.root) { clock }

    private fun AttemptStore.put(lesson: String, start: Long, text: String = "x", fp: String = "fp", kind: AttemptKind = AttemptKind.Answer) {
        clock += 1_000
        save(lesson, fp, AttemptKey(kind, start)) { it.writeText(text) }
    }

    @Test fun fileNamesCarryKindAndPauseStart() {
        assertEquals("answer-0123450.m4a", AttemptKey(AttemptKind.Answer, 123_450).fileName)
        assertEquals(AttemptKey(AttemptKind.Echo, 125_600), AttemptKey.parse("echo-0125600.m4a"))
        assertEquals(null, AttemptKey.parse("echo-0125600.m4a.tmp"))
    }

    @Test fun aNewAttemptReplacesTheOneForTheSamePause() {
        val s = store()
        s.put("I_05", 1_000, "old")
        s.put("I_05", 1_000, "new")
        s.put("I_05", 1_000, "echo", kind = AttemptKind.Echo)
        assertEquals(2, s.list("I_05", "fp").size)
        assertEquals("new", s.file("I_05", AttemptKey(AttemptKind.Answer, 1_000)).readText())
    }

    @Test fun keepsTheFiveLessonsRecordedMostRecently() {
        val s = store()
        for (n in 1..5) s.put("I_0$n", 1_000)
        s.put("I_01", 2_000) // I_01 is now the most recent
        s.put("I_06", 1_000)
        assertTrue(s.list("I_02", "fp").isEmpty())
        for (id in listOf("I_01", "I_03", "I_04", "I_05", "I_06")) assertEquals(id, 1, minOf(1, s.list(id, "fp").size))
    }

    @Test fun aChangedTranscriptDropsTheLessonsAttempts() {
        val s = store()
        s.put("I_05", 1_000, fp = "old")
        assertTrue(s.list("I_05", "new").isEmpty())
        assertTrue(s.list("I_05", "old").isEmpty())
    }

    @Test fun deleteAllRemovesEverything() {
        val s = store()
        s.put("I_05", 1_000)
        s.put("I_06", 1_000)
        s.deleteAll()
        assertTrue(s.list("I_05", "fp").isEmpty())
        assertTrue(s.list("I_06", "fp").isEmpty())
    }
}
