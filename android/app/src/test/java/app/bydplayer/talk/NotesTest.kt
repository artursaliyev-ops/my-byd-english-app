package app.bydplayer.talk

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

/** Notes spec §2-§6: the format, the prompt block, the opening, the reply check and the transcript. */
class NotesTest {
    private val day = LocalDate.of(2026, 11, 22)
    private val block = "Today is {today}.\nAbout the learner:\n{facts}\nLast Talks:\n{talks}"
    private val two = Notes(listOf("You have two kids."), listOf(Notes.TalkLine(LocalDate.of(2026, 11, 20), "the traffic, food")))

    @Test
    fun `a Talk line shows as Settings does`() {
        assertEquals("20 Nov — the traffic, food.", two.talks[0].shown)
        assertEquals("1 Dec — work.", Notes.TalkLine(LocalDate.of(2026, 12, 1), "work.").shown)
    }

    @Test
    fun `the file round-trips, and a broken one counts as no Notes`() {
        assertEquals(two, Notes.fromJson(Notes.toJson(two)))
        assertEquals(Notes(), Notes.fromJson("{not json"))
        assertEquals(Notes(), Notes.fromJson("{\"facts\": []}"))
    }

    @Test
    fun `no Notes, no block`() {
        assertEquals("BASE", Notes.prompt("BASE", block, Notes(), day))
    }

    @Test
    fun `the block follows the prompt with today, the facts and the Talks`() {
        assertEquals(
            "BASE\n\nToday is Sunday, 22 Nov.\nAbout the learner:\n- You have two kids.\nLast Talks:\n- 20 Nov — the traffic, food.",
            Notes.prompt("BASE\n", block, two, day),
        )
    }

    @Test
    fun `an empty group says none yet`() {
        val p = Notes.prompt("B", block, Notes(facts = listOf("You like jazz.")), day)
        assertTrue(p.endsWith("Last Talks:\n- (none yet)"))
        val q = Notes.prompt("B", block, Notes(talks = two.talks), day)
        assertTrue(q.contains("About the learner:\n- (none yet)\n"))
    }

    @Test
    fun `every second Talk with Notes asks for a note in the hello`() {
        assertEquals(TalkConfig.Text.OPENING to true, Notes.opening(two, recallNext = false))
        assertEquals(TalkConfig.Text.OPENING_RECALL to false, Notes.opening(two, recallNext = true))
        // A Talk without Notes leaves the flag alone.
        assertEquals(TalkConfig.Text.OPENING to true, Notes.opening(Notes(), recallNext = true))
        assertEquals(TalkConfig.Text.OPENING to false, Notes.opening(Notes(), recallNext = false))
    }

    @Test
    fun `the request carries the date, the old facts, both sides of the Talk and the schema`() {
        val body = JSONObject(Notes.request("RULES", two, listOf(SaidTurn(null, "Hi!"), SaidTurn("I have three kids.", "Three!")), day))
        assertEquals("RULES", body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"))
        val text = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text")
        assertEquals(
            "Today is Sunday, 22 Nov 2026.\n\nOld facts:\nYou have two kids.\n\nToday's Talk:\nAnna: Hi!\nLearner: I have three kids.\nAnna: Three!",
            text,
        )
        val config = body.getJSONObject("generationConfig")
        assertEquals("application/json", config.getString("responseMimeType"))
        assertEquals(20, config.getJSONObject("responseJsonSchema").getJSONObject("properties").getJSONObject("facts").getInt("maxItems"))
        assertTrue(Notes.request("R", Notes(), emptyList(), day).contains("Old facts:\\n(none)"))
    }

    private fun reply(json: String) = JSONObject().put(
        "candidates",
        org.json.JSONArray().put(JSONObject().put("content", JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", json))))),
    ).toString()

    @Test
    fun `a valid reply replaces the facts and puts this Talk first`() {
        val next = Notes.fromReply(reply("""{"facts": [" You have three kids. ", ""], "topics": "family, work"}"""), two, day)!!
        assertEquals(listOf("You have three kids."), next.facts)
        assertEquals(listOf("22 Nov — family, work.", "20 Nov — the traffic, food."), next.talks.map { it.shown })
    }

    @Test
    fun `long lines are cut, at most 20 facts and 10 Talks are kept`() {
        val many = (1..25).joinToString(",") { "\"You like thing number $it and many more things that go on and on.\"" }
        val old = Notes(talks = (1..10).map { Notes.TalkLine(LocalDate.of(2026, 11, it), "t$it") })
        val next = Notes.fromReply(reply("""{"facts": [$many], "topics": "${"x".repeat(80)}"}"""), old, day)!!
        assertEquals(20, next.facts.size)
        assertTrue(next.facts.all { it.length <= Notes.MAX_LINE })
        assertEquals(60, next.talks[0].topics.length)
        assertEquals(10, next.talks.size)
        assertEquals(LocalDate.of(2026, 11, 9), next.talks.last().date)
    }

    @Test
    fun `two Talks on one day are two lines`() {
        val first = Notes.fromReply(reply("""{"facts": [], "topics": "work"}"""), Notes(), day)!!
        val second = Notes.fromReply(reply("""{"facts": [], "topics": "food"}"""), first, day)!!
        assertEquals(listOf("22 Nov — food.", "22 Nov — work."), second.talks.map { it.shown })
    }

    @Test
    fun `a reply that doesn't validate is no Notes`() {
        assertNull(Notes.fromReply("not json", two, day))
        assertNull(Notes.fromReply(reply("not json"), two, day))
        assertNull(Notes.fromReply(reply("""{"facts": []}"""), two, day))
        assertNull(Notes.fromReply(reply("""{"topics": "x"}"""), two, day))
        assertNull(Notes.fromReply(reply("""{"facts": [], "topics": "  "}"""), two, day))
        assertNull(Notes.fromReply("""{"candidates": []}""", two, day))
    }

    @Test
    fun `thought parts are skipped`() {
        val r = JSONObject().put("candidates", org.json.JSONArray().put(JSONObject().put("content", JSONObject().put("parts",
            org.json.JSONArray()
                .put(JSONObject().put("text", "thinking...").put("thought", true))
                .put(JSONObject().put("text", """{"facts": ["You like tea."], "topics": "tea"}""")))))).toString()
        assertEquals(listOf("You like tea."), Notes.fromReply(r, Notes(), day)!!.facts)
    }

    @Test
    fun `only exchanges where the learner spoke count as complete Turns`() {
        assertEquals(2, Notes.completeTurns(listOf(SaidTurn(null, "Hi"), SaidTurn("a", "b"), SaidTurn("c", "d"))))
    }
}

/** Notes spec §5: the file, its atomic write and the wipe generation. */
class NotesStoreTest {
    @get:Rule val dir = TemporaryFolder()
    private val notes = Notes(listOf("You have two kids."))

    @Test
    fun `no file and a broken file read as no Notes`() {
        val f = File(dir.root, "anna-notes.json")
        assertEquals(Notes(), NotesStore(f).read())
        f.writeText("garbage")
        assertEquals(Notes(), NotesStore(f).read())
    }

    @Test
    fun `an update writes the file and leaves no temp file`() {
        val f = File(dir.root, "anna-notes.json")
        val changes = mutableListOf<Notes>()
        val store = NotesStore(f) { changes += it }
        assertTrue(store.update(store.generation()) { notes })
        assertEquals(notes, NotesStore(f).read())
        assertFalse(File(dir.root, "anna-notes.json.tmp").exists())
        assertEquals(listOf(notes), changes)
    }

    @Test
    fun `a wipe deletes the file, and a Talk started before it writes nothing`() {
        val f = File(dir.root, "anna-notes.json")
        val store = NotesStore(f)
        store.update(store.generation()) { notes }
        val talkStartedAt = store.generation()
        store.wipe()
        assertFalse(f.exists())
        assertFalse(store.update(talkStartedAt) { notes })
        assertEquals(Notes(), store.read())
        // A Talk started after the wipe writes again.
        assertTrue(store.update(store.generation()) { notes })
    }

    @Test
    fun `a wipe while the reply is being built throws it away`() {
        val store = NotesStore(File(dir.root, "anna-notes.json"))
        val g = store.generation()
        assertFalse(store.update(g) { store.wipe(); notes })
        assertEquals(Notes(), store.read())
    }
}

/** Notes spec §4: the < 3 Turns rule, the retry on the fallback within 20 s, and failure. */
class NotesWriterTest {
    @get:Rule val dir = TemporaryFolder()
    private val day = LocalDate.of(2026, 11, 22)
    private val talk = listOf(SaidTurn(null, "Hi!"), SaidTurn("a", "b"), SaidTurn("c", "d"), SaidTurn("e", "f"))
    private val good = JSONObject().put("candidates", org.json.JSONArray().put(JSONObject().put("content", JSONObject().put("parts",
        org.json.JSONArray().put(JSONObject().put("text", """{"facts": ["You have two kids."], "topics": "family"}""")))))).toString()

    private fun store() = NotesStore(File(dir.root, "anna-notes.json"))

    @Test
    fun `fewer than 3 complete Turns make no call`() {
        var calls = 0
        val w = NotesWriter(store(), "R", post = { _, _, _ -> calls++; good })
        assertFalse(w.write(talk.take(3), day, day, 0))
        assertEquals(0, calls)
    }

    @Test
    fun `one good reply is written`() {
        val s = store()
        val tries = mutableListOf<Pair<String, Long>>()
        assertTrue(NotesWriter(s, "R", post = { m, _, t -> tries += m to t; good }, clock = { 0L }).write(talk, day, day, s.generation()))
        assertEquals(listOf(TalkConfig.NotesCall.NOTES_MODEL to 10_000L), tries)
        assertEquals(listOf("You have two kids."), s.read().facts)
    }

    @Test
    fun `a failed first try retries once on the fallback with the rest of the 20 s`() {
        val s = store()
        var now = 0L
        val tries = mutableListOf<Pair<String, Long>>()
        val w = NotesWriter(s, "R", clock = { now }, post = { m, _, t ->
            tries += m to t
            if (tries.size == 1) { now += 10_000; null } else good
        })
        assertTrue(w.write(talk, day, day, s.generation()))
        assertEquals(listOf(TalkConfig.NotesCall.NOTES_MODEL to 10_000L, TalkConfig.NotesCall.NOTES_FALLBACK_MODEL to 10_000L), tries)
    }

    @Test
    fun `both tries failing keep the old Notes`() {
        val s = store()
        val old = Notes(listOf("You like tea."))
        s.update(s.generation()) { old }
        var calls = 0
        assertFalse(NotesWriter(s, "R", post = { _, _, _ -> calls++; "{\"candidates\": []}" }).write(talk, day, day, s.generation()))
        assertEquals(2, calls)
        assertEquals(old, s.read())
    }

    @Test
    fun `a wipe during the call throws the reply away`() {
        val s = store()
        val g = s.generation()
        assertFalse(NotesWriter(s, "R", post = { _, _, _ -> s.wipe(); good }).write(talk, day, day, g))
        assertEquals(Notes(), s.read())
    }
}

/** Notes spec §3: the Talk transcript kept by [TalkTurns], in memory. */
class TalkTranscriptTest {
    private val talk = TalkTurns()
    private fun server(e: LiveEvent) = talk.on(TalkEvent.Server(e))

    private fun annaAnswers(text: String) {
        server(LiveEvent.Audio(byteArrayOf(1)))
        server(LiveEvent.AnnaSaid(text))
        server(LiveEvent.TurnComplete)
        talk.on(TalkEvent.PlaybackDrained)
        talk.on(TalkEvent.EchoGuardOver)
    }

    private fun learnerSays(text: String) {
        talk.on(TalkEvent.Mic(MicEvent.SpeechStarted(byteArrayOf(0))))
        talk.on(TalkEvent.Mic(MicEvent.SpeechEnded))
        server(LiveEvent.LearnerSaid(text))
    }

    @Test
    fun `the opening's answer and each Turn are kept with both sides`() {
        server(LiveEvent.SetupComplete)
        annaAnswers("Hi! How is your day?")
        learnerSays("I have two")
        server(LiveEvent.LearnerSaid(" kids."))
        annaAnswers("Two kids! Busy life.")
        assertEquals(
            listOf(SaidTurn(null, "Hi! How is your day?"), SaidTurn("I have two kids.", "Two kids! Busy life.")),
            talk.transcript,
        )
    }

    @Test
    fun `the opening can be the recall one`() {
        val t = TalkTurns(TalkConfig.Text.OPENING_RECALL)
        assertEquals(TalkEffect.SendText(TalkConfig.Text.OPENING_RECALL), t.on(TalkEvent.Server(LiveEvent.SetupComplete)).first())
    }
}
