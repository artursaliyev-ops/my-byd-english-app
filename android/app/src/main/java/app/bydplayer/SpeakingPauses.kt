package app.bydplayer

/** Whether an Attempt answers the line after its pause, or echoes the line before it. */
enum class AttemptKind(val fileTag: String, val label: String) {
    Answer("answer", "Me"),
    Echo("echo", "Echo"),
}

/**
 * A silence in a lesson where the learner speaks out loud ("Find the speaking pauses in a
 * lesson"). [startMs] is where the line before ends; audio is kept from [keepStartMs] to
 * [keepEndMs], in lesson time. [compareIndex] is the transcript segment the Attempt is
 * played back next to.
 */
data class SpeakingPause(
    val startMs: Long,
    val keepStartMs: Long,
    val keepEndMs: Long,
    val kind: AttemptKind,
    val compareIndex: Int,
)

const val MIN_PAUSE_MS = 1_000L
const val KEEP_FROM_MS = 200L
const val KEEP_UNTIL_MS = 100L
const val MIN_KEPT_MS = 500L

private val CYRILLIC = Regex("[А-Яа-яЁё]")

/** Russian by alphabet: Whisper's own language tag often marks English lines as Russian. */
fun isRussian(text: String) = CYRILLIC.containsMatchIn(text)

/**
 * Every speaking pause in [segments] (sorted by start): a gap of at least 1 s between two
 * lines that isn't Russian → Russian, with at least 0.5 s left after trimming. English →
 * Russian is an echo of the line before; everything else answers the line after.
 */
fun speakingPauses(segments: List<Segment>): List<SpeakingPause> {
    val out = mutableListOf<SpeakingPause>()
    for (i in 0 until segments.size - 1) {
        val before = segments[i]
        val after = segments[i + 1]
        if (after.startMs - before.endMs < MIN_PAUSE_MS) continue
        val beforeRu = isRussian(before.text)
        val afterRu = isRussian(after.text)
        if (beforeRu && afterRu) continue
        val keepStart = before.endMs + KEEP_FROM_MS
        val keepEnd = after.startMs - KEEP_UNTIL_MS
        if (keepEnd - keepStart < MIN_KEPT_MS) continue
        val echo = !beforeRu && afterRu
        out += SpeakingPause(
            startMs = before.endMs,
            keepStartMs = keepStart,
            keepEndMs = keepEnd,
            kind = if (echo) AttemptKind.Echo else AttemptKind.Answer,
            compareIndex = if (echo) i else i + 1,
        )
    }
    return out
}

/** A stable fingerprint of a transcript, so Attempts cut against an older one can be dropped. */
fun transcriptFingerprint(segments: List<Segment>): String {
    val text = segments.joinToString("\n") { "${it.startMs}|${it.endMs}|${it.text}" }
    val digest = java.security.MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
    return digest.take(8).joinToString("") { "%02x".format(it) }
}
