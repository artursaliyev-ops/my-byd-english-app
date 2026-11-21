package app.bydplayer.talk

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec §3: the JSON the Talk sends to Gemini Live and what it reads back. */
class LiveProtocolTest {
    private val setup = JSONObject(LiveProtocol.setup(prompt = "You are Anna.")).getJSONObject("setup")

    @Test
    fun `setup names the 3_1 Flash Live model`() {
        assertEquals("models/gemini-3.1-flash-live-preview", setup.getString("model"))
    }

    @Test
    fun `setup asks for audio in the Aoede voice`() {
        val gen = setup.getJSONObject("generationConfig")
        assertEquals("AUDIO", gen.getJSONArray("responseModalities").getString(0))
        assertEquals(
            "Aoede",
            gen.getJSONObject("speechConfig").getJSONObject("voiceConfig")
                .getJSONObject("prebuiltVoiceConfig").getString("voiceName"),
        )
    }

    @Test
    fun `setup carries the prompt as the system instruction`() {
        val text = setup.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text")
        assertEquals("You are Anna.", text)
    }

    @Test
    fun `server activity detection is off`() {
        val aad = setup.getJSONObject("realtimeInputConfig").getJSONObject("automaticActivityDetection")
        assertTrue(aad.getBoolean("disabled"))
    }

    @Test
    fun `compression, resumption and both transcriptions are on`() {
        assertTrue(setup.getJSONObject("contextWindowCompression").has("slidingWindow"))
        assertTrue(setup.has("sessionResumption"))
        assertFalse(setup.getJSONObject("sessionResumption").has("handle"))
        assertTrue(setup.has("inputAudioTranscription"))
        assertTrue(setup.has("outputAudioTranscription"))
    }

    @Test
    fun `control text goes as bare realtime text, with no activity around it`() {
        val msg = JSONObject(LiveProtocol.text("(The learner pressed Talk. Start the Talk.)"))
        assertEquals(setOf("realtimeInput"), msg.keys().asSequence().toSet())
        val input = msg.getJSONObject("realtimeInput")
        assertEquals(setOf("text"), input.keys().asSequence().toSet())
        assertEquals("(The learner pressed Talk. Start the Talk.)", input.getString("text"))
    }

    @Test
    fun `setupComplete reads as the session being ready`() {
        assertEquals(listOf(LiveEvent.SetupComplete), LiveProtocol.parse("""{"setupComplete":{}}"""))
    }

    @Test
    fun `Anna's audio arrives as base64 PCM in the model turn`() {
        // "AAEC/w==" is the bytes 0, 1, 2, 255.
        val msg = """{"serverContent":{"modelTurn":{"parts":[
            {"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"AAEC/w=="}}]}}}"""
        val audio = LiveProtocol.parse(msg).single() as LiveEvent.Audio
        assertEquals(listOf<Byte>(0, 1, 2, -1), audio.pcm.toList())
    }

    @Test
    fun `Anna's words and the end of her turn are read from serverContent`() {
        val msg = """{"serverContent":{"outputTranscription":{"text":"Hi! How"},"turnComplete":true}}"""
        assertEquals(listOf(LiveEvent.AnnaSaid("Hi! How"), LiveEvent.TurnComplete), LiveProtocol.parse(msg))
    }

    @Test
    fun `what the server heard is read but kept apart from Anna's words`() {
        val msg = """{"serverContent":{"inputTranscription":{"text":"I go to work"}}}"""
        assertEquals(listOf(LiveEvent.LearnerSaid("I go to work")), LiveProtocol.parse(msg))
    }

    @Test
    fun `a new resumption handle is kept, an empty update is not`() {
        assertEquals(
            listOf(LiveEvent.ResumptionHandle("h-42")),
            LiveProtocol.parse("""{"sessionResumptionUpdate":{"newHandle":"h-42","resumable":true}}"""),
        )
        assertEquals(emptyList<LiveEvent>(), LiveProtocol.parse("""{"sessionResumptionUpdate":{"resumable":false}}"""))
    }

    @Test
    fun `goAway warns the session will close`() {
        assertEquals(listOf(LiveEvent.GoAway), LiveProtocol.parse("""{"goAway":{"timeLeft":"50s"}}"""))
    }

    @Test
    fun `usage metadata alone carries no event`() {
        assertEquals(emptyList<LiveEvent>(), LiveProtocol.parse("""{"usageMetadata":{"totalTokenCount":12}}"""))
    }

    @Test
    fun `a learner utterance is wrapped in activityStart and activityEnd`() {
        assertTrue(JSONObject(LiveProtocol.activityStart()).getJSONObject("realtimeInput").has("activityStart"))
        assertTrue(JSONObject(LiveProtocol.activityEnd()).getJSONObject("realtimeInput").has("activityEnd"))
    }

    @Test
    fun `the learner's audio goes as base64 16 kHz PCM`() {
        val audio = JSONObject(LiveProtocol.audio(byteArrayOf(1, 2, 3)))
            .getJSONObject("realtimeInput").getJSONObject("audio")
        assertEquals("audio/pcm;rate=16000", audio.getString("mimeType"))
        assertEquals("AQID", audio.getString("data"))
    }

    @Test
    fun `a server close of a resumed session before setupComplete is a refused handle`() {
        assertEquals(LiveLoss.HandleRefused, LiveProtocol.classifyLoss(closeCode = 1008, reason = "", resuming = true, setUp = false))
    }

    @Test
    fun `a socket failure, a close after setup, or a close of a new session is the network`() {
        assertEquals(LiveLoss.Network, LiveProtocol.classifyLoss(closeCode = null, reason = null, resuming = true, setUp = false))
        assertEquals(LiveLoss.Network, LiveProtocol.classifyLoss(closeCode = 1011, reason = "", resuming = true, setUp = true))
        assertEquals(LiveLoss.Network, LiveProtocol.classifyLoss(closeCode = 1008, reason = "", resuming = false, setUp = false))
    }
}
