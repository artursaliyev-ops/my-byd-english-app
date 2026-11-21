package app.bydplayer.talk

import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Gemini Live JSON protocol (spec §3), with no socket: builds the client messages and reads
 * the server's. The working reference is the probe `live_turns.py` on branch `probe/cloud-turn`.
 */
object LiveProtocol {
    /** The one `setup` message per connection. [handle] resumes an earlier session (spec §3). */
    fun setup(prompt: String, handle: String? = null): String = JSONObject().put(
        "setup",
        JSONObject()
            .put("model", "models/${TalkConfig.MODEL}")
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put(
                        "speechConfig",
                        JSONObject().put(
                            "voiceConfig",
                            JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", TalkConfig.VOICE)),
                        ),
                    ),
            )
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt))))
            // The head unit decides the end of the learner's speech, not the server.
            .put(
                "realtimeInputConfig",
                JSONObject().put("automaticActivityDetection", JSONObject().put("disabled", true)),
            )
            .put("contextWindowCompression", JSONObject().put("slidingWindow", JSONObject()))
            .put("sessionResumption", JSONObject().apply { if (handle != null) put("handle", handle) })
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject()),
    ).toString()

    /**
     * App control text (the opening and the cues, spec §4) as bare realtime text. Never inside
     * an activity: 3.1 closes the socket on that with 1007 "Precondition check failed".
     */
    fun text(text: String): String =
        JSONObject().put("realtimeInput", JSONObject().put("text", text)).toString()

    /** The learner starts speaking (spec §6 step 3): the server's own VAD is off. */
    fun activityStart(): String = realtime("activityStart", JSONObject())

    /** The learner's speech has ended (spec §6 step 4). */
    fun activityEnd(): String = realtime("activityEnd", JSONObject())

    /** A piece of the learner's speech: 16 kHz mono 16-bit PCM. */
    fun audio(pcm: ByteArray): String = realtime(
        "audio",
        JSONObject()
            .put("data", Base64.getEncoder().encodeToString(pcm))
            .put("mimeType", "audio/pcm;rate=${TalkConfig.INPUT_RATE}"),
    )

    private fun realtime(key: String, value: JSONObject): String =
        JSONObject().put("realtimeInput", JSONObject().put(key, value)).toString()

    /** One server message as the events it carries, in order. Unknown parts are skipped. */
    fun parse(message: String): List<LiveEvent> {
        val msg = JSONObject(message)
        val events = mutableListOf<LiveEvent>()
        if (msg.has("setupComplete")) events += LiveEvent.SetupComplete
        msg.optJSONObject("serverContent")?.let { content ->
            content.transcript("inputTranscription")?.let { events += LiveEvent.LearnerSaid(it) }
            val parts = content.optJSONObject("modelTurn")?.optJSONArray("parts")
            for (i in 0 until (parts?.length() ?: 0)) {
                val data = parts!!.getJSONObject(i).optJSONObject("inlineData")?.optString("data").orEmpty()
                if (data.isNotEmpty()) events += LiveEvent.Audio(Base64.getDecoder().decode(data))
            }
            content.transcript("outputTranscription")?.let { events += LiveEvent.AnnaSaid(it) }
            if (content.optBoolean("turnComplete")) events += LiveEvent.TurnComplete
        }
        msg.optJSONObject("sessionResumptionUpdate")?.optString("newHandle")?.takeIf { it.isNotEmpty() }
            ?.let { events += LiveEvent.ResumptionHandle(it) }
        if (msg.has("goAway")) events += LiveEvent.GoAway
        return events
    }

    /**
     * Why a session was lost (spec §8): the one place a loss is classified. [closeCode] and
     * [reason] come from the server's close frame, both null for a socket failure; [resuming]
     * says the session was opened with a handle, [setUp] that its `setupComplete` came.
     *
     * [httpStatus] is the handshake's HTTP status when the socket failed before opening.
     *
     * A free-tier refusal (quota or rate limit) is a [LiveLoss.Limit], told by the reason text
     * and never by the close code alone: 1011 also carries plain server errors. Google documents
     * HTTP `429 RESOURCE_EXHAUSTED` for the Gemini API in general, but no close frame for the Live
     * API, so the reason forms in [LIMIT_REASON] are assumed, taken from what developers report
     * (1011 "You exceeded your current quota…", 1011 "Resource has been exhausted", a "Maximum
     * concurrent sessions" close), plus "rate limit" wordings.
     *
     * Otherwise, a server that closes a resumed session before setting it up has refused the
     * handle; any other loss is the network.
     */
    fun classifyLoss(
        closeCode: Int?,
        reason: String?,
        resuming: Boolean,
        setUp: Boolean,
        httpStatus: Int? = null,
    ): LiveLoss = when {
        httpStatus == 429 -> LiveLoss.Limit
        reason != null && LIMIT_REASON.containsMatchIn(reason) -> LiveLoss.Limit
        closeCode != null && resuming && !setUp -> LiveLoss.HandleRefused
        else -> LiveLoss.Network
    }

    /** Close reasons that mean a quota or rate limit (assumed forms, see [classifyLoss]). */
    private val LIMIT_REASON = Regex(
        "resource[_ ]exhausted|resource has been exhausted|quota|rate[- ]?limit|concurrent sessions",
        RegexOption.IGNORE_CASE,
    )

    private fun JSONObject.transcript(key: String): String? =
        optJSONObject(key)?.optString("text")?.takeIf { it.isNotEmpty() }
}

/** Why a Live session was lost, as [LiveProtocol.classifyLoss] tells it. */
enum class LiveLoss {
    /** A weak or lost connection: retry, No signal (spec §8). */
    Network,

    /** The server wouldn't resume with the handle: a new session (spec §8). */
    HandleRefused,

    /** Gemini's free tier refused: a quota or rate limit, never shown as the network (spec §8). */
    Limit,
}

/** What the Live server tells the Talk (spec §3). */
sealed interface LiveEvent {
    data object SetupComplete : LiveEvent

    /** A piece of Anna's answer: 24 kHz mono 16-bit PCM. */
    class Audio(val pcm: ByteArray) : LiveEvent

    /** A fragment of Anna's output transcript. In memory only, never stored (spec §5). */
    data class AnnaSaid(val text: String) : LiveEvent

    /** A fragment of what the server heard from the learner. Never shown or stored (spec §7). */
    data class LearnerSaid(val text: String) : LiveEvent

    /** Anna's answer is complete (her audio may still be playing). */
    data object TurnComplete : LiveEvent

    /** The latest handle to resume this session with (spec §3). */
    data class ResumptionHandle(val handle: String) : LiveEvent

    /** The server will close this connection soon. */
    data object GoAway : LiveEvent
}
