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
    private val onLost: () -> Unit,
) {
    private var socket: WebSocket? = null
    @Volatile private var closed = false
    private var openedAt = 0L

    fun open() {
        openedAt = System.nanoTime()
        val request = Request.Builder().url("${TalkConfig.LIVE_ENDPOINT}?key=$apiKey").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "socket open in ${ms()} ms")
                webSocket.send(LiveProtocol.setup(prompt))
            }

            override fun onMessage(webSocket: WebSocket, text: String) = deliver(text)

            // Live sends its JSON in binary frames.
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = deliver(bytes.utf8())

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "server closing: $code after ${ms()} ms")
                webSocket.close(1000, null)
                lost()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = lost()

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "socket failed: ${t.javaClass.simpleName}, HTTP ${response?.code} after ${ms()} ms")
                lost()
            }
        })
    }

    /** Sends one client message built by [LiveProtocol]. */
    fun send(message: String) {
        socket?.send(message)
    }

    /** Closes the socket. [onLost] is not called for a close the app asked for. */
    fun close() {
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
        if (LiveEvent.SetupComplete in events) Log.i(TAG, "setupComplete after ${ms()} ms")
        events.forEach(onEvent)
    }

    private fun lost() {
        if (closed) return
        closed = true
        onLost()
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
