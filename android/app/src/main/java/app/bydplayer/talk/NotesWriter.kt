package app.bydplayer.talk

import java.time.LocalDate

/**
 * Writes a Talk's Notes after it ends (Notes spec §4): one call, 20 s in all, with one retry on
 * the fallback model. Pure Kotlin: [post] makes one `generateContent` request (model, body,
 * timeout in ms) and returns the response body, or null on any failure; [clock] is in ms.
 * Blocking: run it off the main thread. Any failure means the Talk is silently not kept.
 */
class NotesWriter(
    private val store: NotesStore,
    private val instruction: String,
    private val post: (model: String, body: String, timeoutMs: Long) -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
) {
    /** Writes the Notes for a Talk that started on [talkDate] while the store was at [generation]. Returns whether it wrote. */
    fun write(transcript: List<SaidTurn>, talkDate: LocalDate, today: LocalDate, generation: Long): Boolean {
        if (Notes.completeTurns(transcript) < Notes.MIN_TURNS) {
            log("Notes: fewer than ${Notes.MIN_TURNS} Turns, nothing to write")
            return false
        }
        val start = clock()
        val models = listOf(TalkConfig.NotesCall.NOTES_MODEL, TalkConfig.NotesCall.NOTES_FALLBACK_MODEL)
        for ((i, model) in models.withIndex()) {
            val left = TalkConfig.NotesCall.BUDGET_MS - (clock() - start)
            if (left <= 0) break
            val timeout = if (i == 0) minOf(left, TalkConfig.NotesCall.FIRST_TRY_MS) else left
            // Each try reads the latest stored Notes: an earlier Talk's call may have written meanwhile.
            val old = store.read()
            val reply = post(model, Notes.request(instruction, old, transcript, today), timeout)
            val next = reply?.let { Notes.fromReply(it, old, talkDate) }
            if (next == null) {
                log("Notes: try ${i + 1} failed after ${clock() - start} ms")
                continue
            }
            val wrote = store.update(generation) { latest ->
                // Keep any Talk line written since this try read the Notes.
                if (latest == old) next else next.copy(talks = (next.talks.take(1) + latest.talks).take(Notes.MAX_TALKS))
            }
            log(if (wrote) "Notes: written after ${clock() - start} ms" else "Notes: wiped meanwhile, reply dropped")
            return wrote
        }
        log("Notes: not kept")
        return false
    }
}
