package app.bydplayer.talk

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * One Gemini Live connection over an OkHttp WebSocket (spec §2, §3). Sends `setup` as soon as the
 * socket opens and hands each server message to [onEvent] as [LiveEvent]s, and a failure or close
 * to [onLost], once. Callbacks run on OkHttp's thread; the caller moves them where it needs them.
 *
 * The URL carries the key, so neither it nor OkHttp's `Response` (whose toString has the URL) is
 * ever logged: only codes, timings and exception class names.
 */
class LiveSession(
    private val apiKey: String,
    private val prompt: String,
    private val onEvent: (LiveEvent) -> Unit,
    private val onLost: (LiveLoss) -> Unit,
    /** Resumes an earlier session (spec §3); null for a new Talk. Never logged. */
    private val handle: String? = null,
) : LiveConnection {
    private var socket: WebSocket? = null
    @Volatile private var closed = false
    private var openedAt = 0L
    @Volatile private var setUp = false

    override fun open() {
        openedAt = System.nanoTime()
        val request = Request.Builder().url("${TalkConfig.LIVE_ENDPOINT}?key=$apiKey").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "socket open in ${ms()} ms")
                webSocket.send(LiveProtocol.setup(prompt, handle))
            }

            override fun onMessage(webSocket: WebSocket, text: String) = deliver(text)

            // Live sends its JSON in binary frames.
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = deliver(bytes.utf8())

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "server closing: $code after ${ms()} ms")
                webSocket.close(1000, null)
                lost(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = lost(code, reason)

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "socket failed: ${t.javaClass.simpleName}, HTTP ${response?.code} after ${ms()} ms")
                lost(null, null)
            }
        })
    }

    /** Sends one client message built by [LiveProtocol]. */
    override fun send(message: String) {
        socket?.send(message)
    }

    /** Closes the socket. [onLost] is not called for a close the app asked for. */
    override fun close() {
        closed = true
        socket?.close(1000, null)
        socket = null
    }

    private fun deliver(message: String) {
        if (closed) return
        val events = try {
            LiveProtocol.parse(message)
        } catch (e: Exception) {
            Log.w(TAG, "unreadable server message: ${e.javaClass.simpleName}")
            return
        }
        if (LiveEvent.SetupComplete in events) {
            setUp = true
            Log.i(TAG, "setupComplete after ${ms()} ms")
        }
        events.forEach(onEvent)
    }

    /** [code] and [reason] from the server's close frame, null for a failure. The reason is never logged. */
    private fun lost(code: Int?, reason: String?) {
        if (closed) return
        closed = true
        onLost(LiveProtocol.classifyLoss(code, reason, resuming = handle != null, setUp = setUp))
    }

    private fun ms() = (System.nanoTime() - openedAt) / 1_000_000

    private companion object {
        const val TAG = "TalkLive"

        /** One client for the app: OkHttp shares its threads and connection pool. */
        val client: OkHttpClient by lazy {
            OkHttpClient.Builder().pingInterval(15, TimeUnit.SECONDS).build()
        }
    }
}
