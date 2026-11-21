package app.bydplayer.talk

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.FloatBuffer

/**
 * Silero VAD v4 (the k2-fsa export, 16 kHz only) on the head unit through ONNX Runtime
 * (spec §6 step 3). Inputs `x` [1, 512] float, `h` and `c` [2, 1, 64]; outputs `prob`, `new_h`,
 * `new_c`. The LSTM state carries from frame to frame and is cleared by [reset]. Use it from
 * one thread (the mic's).
 */
class SileroVad(context: Context) : SpeechDetector, AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(
        context.assets.open(TalkConfig.Audio.VAD_ASSET).use { it.readBytes() },
        OrtSession.SessionOptions().apply { setIntraOpNumThreads(1) },
    )
    private var h = FloatArray(STATE)
    private var c = FloatArray(STATE)
    private val x = FloatArray(TalkConfig.Audio.VAD_FRAME)

    override fun reset() {
        h = FloatArray(STATE)
        c = FloatArray(STATE)
    }

    override fun speechProbability(frame: ShortArray): Float {
        for (i in x.indices) x[i] = if (i < frame.size) frame[i] / 32768f else 0f
        val shape = longArrayOf(2, 1, 64)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(x), longArrayOf(1, TalkConfig.Audio.VAD_FRAME.toLong())).use { xt ->
            OnnxTensor.createTensor(env, FloatBuffer.wrap(h), shape).use { ht ->
                OnnxTensor.createTensor(env, FloatBuffer.wrap(c), shape).use { ct ->
                    session.run(mapOf("x" to xt, "h" to ht, "c" to ct)).use { out ->
                        val prob = (out.get("prob").get() as OnnxTensor).floatBuffer.get(0)
                        h = (out.get("new_h").get() as OnnxTensor).floatBuffer.toArray()
                        c = (out.get("new_c").get() as OnnxTensor).floatBuffer.toArray()
                        return prob
                    }
                }
            }
        }
    }

    override fun close() {
        session.close()
    }

    private fun FloatBuffer.toArray() = FloatArray(remaining()).also { get(it) }

    private companion object {
        const val STATE = 2 * 1 * 64
    }
}
