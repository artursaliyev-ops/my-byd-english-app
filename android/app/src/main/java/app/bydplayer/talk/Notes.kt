package app.bydplayer.talk

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Anna's **Notes** (Notes spec §2): a few short facts about the learner and the topics of their
 * last Talks, exactly as Settings shows them. Pure Kotlin: the file format, the prompt block, the
 * writer request and the check of its reply all live here, so they are unit-tested without
 * Android. The file itself is [NotesStore]'s; the call is [NotesWriter]'s.
 */
data class Notes(val facts: List<String> = emptyList(), val talks: List<TalkLine> = emptyList()) {
    val isEmpty: Boolean get() = facts.isEmpty() && talks.isEmpty()

    /** One past Talk: the day it started (head-unit local time) and its topics, "work, the weekend". */
    data class TalkLine(val date: LocalDate, val topics: String) {
        /** As Settings and the prompt show it: "22 Nov — work, the weekend." */
        val shown: String get() = "${date.format(DAY)} — ${topics.trimEnd('.')}."
    }

    companion object {
        const val MAX_FACTS = 20
        const val MAX_TALKS = 10
        const val MAX_LINE = 60
        private val DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
        private val TODAY = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)
        private val TODAY_FULL = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale.ENGLISH)

        // ---- The file, `filesDir/anna-notes.json` (spec §5) ----

        fun toJson(notes: Notes): String = JSONObject()
            .put("facts", JSONArray(notes.facts))
            .put("talks", JSONArray(notes.talks.map { JSONObject().put("date", it.date.toString()).put("topics", it.topics) }))
            .toString()

        /** A file that can't be read counts as no Notes. */
        fun fromJson(text: String): Notes = runCatching {
            val o = JSONObject(text)
            val facts = o.getJSONArray("facts").let { a -> List(a.length()) { a.getString(it) } }
            val talks = o.getJSONArray("talks").let { a ->
                List(a.length()) { a.getJSONObject(it).let { t -> TalkLine(LocalDate.parse(t.getString("date")), t.getString("topics")) } }
            }
            Notes(facts, talks)
        }.getOrDefault(Notes())

        // ---- Notes in the Talk (spec §6) ----

        /**
         * Anna's system instruction: the base prompt, plus the Notes block when there are Notes.
         * [block] is the `talk/anna-notes-block.txt` asset with `{today}`, `{facts}` and `{talks}`.
         */
        fun prompt(base: String, block: String, notes: Notes, today: LocalDate): String {
            if (notes.isEmpty) return base
            fun lines(items: List<String>) = if (items.isEmpty()) "- (none yet)" else items.joinToString("\n") { "- $it" }
            return base.trimEnd() + "\n\n" + block.trim()
                .replace("{today}", today.format(TODAY))
                .replace("{facts}", lines(notes.facts))
                .replace("{talks}", lines(notes.talks.map { it.shown }))
        }

        /**
         * The opening of a Talk (spec §6): on every second Talk that has Notes the app asks for one
         * note in the hello. [recallNext] is the stored flag; returns the opening and the new flag.
         */
        fun opening(notes: Notes, recallNext: Boolean): Pair<String, Boolean> = when {
            notes.isEmpty -> TalkConfig.Text.OPENING to recallNext
            recallNext -> TalkConfig.Text.OPENING_RECALL to false
            else -> TalkConfig.Text.OPENING to true
        }

        // ---- Writing (spec §4) ----

        /** The Talk adds nothing with fewer than this many complete Turns. */
        const val MIN_TURNS = 3

        fun completeTurns(transcript: List<SaidTurn>): Int = transcript.count { it.learner != null }

        /** The `generateContent` body: writer instruction, today's date, old facts, the Talk. */
        fun request(instruction: String, old: Notes, transcript: List<SaidTurn>, today: LocalDate): String {
            val talk = transcript.joinToString("\n") { t ->
                (t.learner?.let { "Learner: $it\n" } ?: "") + "Anna: ${t.anna}"
            }
            val oldFacts = old.facts.joinToString("\n").ifEmpty { "(none)" }
            val text = "Today is ${today.format(TODAY_FULL)}.\n\nOld facts:\n$oldFacts\n\nToday's Talk:\n$talk"
            val schema = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()
                    .put("facts", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")).put("maxItems", MAX_FACTS))
                    .put("topics", JSONObject().put("type", "string")))
                .put("required", JSONArray(listOf("facts", "topics")))
            return JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instruction.trim()))))
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", text)))))
                .put("generationConfig", JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("responseJsonSchema", schema)
                    .put("thinkingConfig", JSONObject().put("thinkingLevel", "low")))
                .toString()
        }

        /**
         * The new Notes from a `generateContent` response, or null if it doesn't validate (spec §4).
         * The model's facts replace the old ones whole; this Talk's line goes first, the newest 10 kept.
         */
        fun fromReply(response: String, old: Notes, talkDate: LocalDate): Notes? = runCatching {
            val text = JSONObject(response).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").let { parts ->
                    (0 until parts.length()).map { parts.getJSONObject(it) }
                        .filterNot { it.optBoolean("thought") }
                        .joinToString("") { it.optString("text") }
                }
            val reply = JSONObject(text)
            val raw = reply.getJSONArray("facts")
            val facts = (0 until raw.length()).map { raw.getString(it).trim().take(MAX_LINE).trim() }
                .filter { it.isNotEmpty() }
                .take(MAX_FACTS)
            val topics = reply.getString("topics").trim().take(MAX_LINE).trim()
            if (topics.isEmpty()) return null
            Notes(facts, (listOf(TalkLine(talkDate, topics)) + old.talks).take(MAX_TALKS))
        }.getOrNull()
    }
}

/**
 * One exchange of the Talk transcript (Notes spec §3), in memory only: what the learner said (null
 * for Anna's answer to the app's control text) and Anna's answer.
 */
data class SaidTurn(val learner: String?, val anna: String)
