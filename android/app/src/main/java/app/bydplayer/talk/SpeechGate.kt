package app.bydplayer.talk

/**
 * How likely one mic frame ([TalkConfig.VAD_FRAME] samples of 16 kHz PCM) is speech, 0 to 1.
 * The real one is Silero ([SileroVad]); tests use fakes.
 */
interface SpeechDetector {
    fun speechProbability(frame: ShortArray): Float

    /** Forget what came before: a new learner turn starts. */
    fun reset() {}
}

/**
 * The learner's side of a Turn as the head unit hears it (spec §6 steps 3-4). Pure Kotlin: fed
 * one mic frame at a time on the mic thread, it turns them into [MicEvent]s. While closed it
 * drops every frame (the recording itself never stops, spec §5). While open it keeps the last
 * [TalkConfig.PRE_ROLL_MS] in a ring; on speech it hands that over, then streams the speech in
 * ~[TalkConfig.CHUNK_MS] pieces until [TalkConfig.SILENCE_MS] of silence or
 * [TalkConfig.MAX_UTTERANCE_MS] of utterance, and then closes itself. Not thread-safe.
 */
class SpeechGate(private val detector: SpeechDetector) {
    private var open = false
    private var speaking = false
    private val ring = ArrayDeque<ShortArray>()
    private var ringSamples = 0
    private val pending = ArrayList<ShortArray>()
    private var pendingSamples = 0
    private var utteranceMs = 0L
    private var silenceMs = 0L

    /** Opens the mic: the learner's turn has begun. Nothing heard before counts. */
    fun open() {
        reset()
        detector.reset()
        open = true
    }

    /** Closes the mic: frames are dropped from now on. */
    fun close() {
        reset()
        open = false
    }

    val isOpen: Boolean get() = open

    fun feed(frame: ShortArray): List<MicEvent> {
        if (!open) return emptyList()
        val frameMs = frame.size * 1000L / TalkConfig.INPUT_RATE
        val p = detector.speechProbability(frame)
        if (!speaking) {
            if (p < TalkConfig.SPEECH_START) {
                remember(frame)
                return emptyList()
            }
            speaking = true
            val events = mutableListOf<MicEvent>(MicEvent.SpeechStarted(toBytes(ring.toList(), ringSamples)))
            ring.clear(); ringSamples = 0
            return events + live(frame, frameMs, p)
        }
        return live(frame, frameMs, p)
    }

    private fun live(frame: ShortArray, frameMs: Long, p: Float): List<MicEvent> {
        val events = mutableListOf<MicEvent>()
        pending += frame; pendingSamples += frame.size
        utteranceMs += frameMs
        val wasSilent = silenceMs
        silenceMs = if (p < TalkConfig.SPEECH_END) silenceMs + frameMs else 0L
        val ended = silenceMs >= TalkConfig.SILENCE_MS || utteranceMs >= TalkConfig.MAX_UTTERANCE_MS
        if (ended || pendingSamples * 1000L / TalkConfig.INPUT_RATE >= TalkConfig.CHUNK_MS) {
            events += MicEvent.Audio(toBytes(pending, pendingSamples))
            pending.clear(); pendingSamples = 0
        }
        if (silenceMs != wasSilent) {
            events += MicEvent.Silence((silenceMs.toFloat() / TalkConfig.SILENCE_MS).coerceAtMost(1f))
        }
        if (ended) {
            events += MicEvent.SpeechEnded
            close()
        }
        return events
    }

    private fun remember(frame: ShortArray) {
        ring.addLast(frame); ringSamples += frame.size
        val keep = TalkConfig.PRE_ROLL_MS * TalkConfig.INPUT_RATE / 1000
        while (ringSamples - ring.first().size >= keep) ringSamples -= ring.removeFirst().size
        // Trim the oldest frame so the pre-roll is exactly PRE_ROLL_MS.
        if (ringSamples > keep) {
            val first = ring.removeFirst()
            val cut = ringSamples - keep
            ring.addFirst(first.copyOfRange(cut.toInt(), first.size))
            ringSamples = keep.toInt()
        }
    }

    private fun reset() {
        speaking = false
        ring.clear(); ringSamples = 0
        pending.clear(); pendingSamples = 0
        utteranceMs = 0; silenceMs = 0
    }

    private fun toBytes(frames: List<ShortArray>, samples: Int): ByteArray {
        val out = ByteArray(samples * 2)
        var i = 0
        for (f in frames) for (s in f) {
            out[i++] = s.toByte()
            out[i++] = (s.toInt() shr 8).toByte()
        }
        return out
    }
}

/** What [SpeechGate] hears. */
sealed interface MicEvent {
    /** The learner began to speak; [preRoll] is the [TalkConfig.PRE_ROLL_MS] just before. */
    class SpeechStarted(val preRoll: ByteArray) : MicEvent

    /** A live piece of the learner's speech, 16 kHz mono 16-bit PCM. */
    class Audio(val pcm: ByteArray) : MicEvent

    /** How full the white silence ring is (spec §6 step 4), 0 to 1. */
    data class Silence(val fraction: Float) : MicEvent

    /** The utterance is over: silence window reached, or the 30 s cap. The mic is closed. */
    data object SpeechEnded : MicEvent
}
