package app.bydplayer.talk

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.bydplayer.BuildConfig

/**
 * The app's side of a Talk: whether one can start, and what the Talk screen shows. [TalkService]
 * writes [screen] on the main thread; the UI only reads it and sends button presses.
 */
object Talk {
    /** No key built in, no Talk button (spec §2). */
    val available: Boolean get() = BuildConfig.GEMINI_API_KEY.isNotBlank()

    /** The Talk screen while a Talk runs, null otherwise. */
    var screen by mutableStateOf<TalkScreenState?>(null)
        internal set

    /** A Talk is running, so the media buttons follow the Talk rule (spec §10). Main thread. */
    val active: Boolean get() = screen != null

    /** A short note on the player after a Talk that couldn't connect or hit the free-tier limit (spec §8), null otherwise. */
    var note by mutableStateOf<String?>(null)
        internal set

    /** The player has shown [note] long enough. */
    fun clearNote() {
        note = null
    }

    /** The Talk button on the player. */
    fun start(context: Context) {
        if (!available || screen != null) return
        note = null
        screen = TalkScreenState(TurnState.Connecting, annaLine = "")
        TalkService.start(context)
    }

    /** The End Talk button: works in every state. */
    fun end(context: Context) {
        if (screen == null) return
        TalkService.end(context)
    }
}

/**
 * What the Talk screen draws (spec §7): the state, Anna's latest line, and in Your turn how full
 * the white silence ring is. Never the learner's words.
 */
data class TalkScreenState(val state: TurnState, val annaLine: String, val silence: Float = 0f)
