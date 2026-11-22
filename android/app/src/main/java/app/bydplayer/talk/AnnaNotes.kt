package app.bydplayer.talk

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.bydplayer.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The app's side of Anna's Notes (Notes spec): the one [NotesStore], what a Talk starts with, and
 * the call after it ends. App-wide, not tied to [TalkService], which stops with the Talk while the
 * call still runs in the background. Logs carry timings and errors only, never Notes text.
 */
object AnnaNotes {
    private const val TAG = "TalkNotes"
    private const val PREFS = "talk"
    private const val KEY_RECALL_NEXT = "notes_recall_next"

    private val main = Handler(Looper.getMainLooper())
    private val calls = Executors.newSingleThreadExecutor { r -> Thread(r, "anna-notes").apply { isDaemon = true } }
    private val client by lazy { OkHttpClient.Builder().build() }
    @Volatile private var store: NotesStore? = null

    /** The stored Notes, for Settings. Main thread. */
    var current by mutableStateOf(Notes())
        private set

    fun store(context: Context): NotesStore = store ?: synchronized(this) {
        store ?: NotesStore(File(context.applicationContext.filesDir, TalkConfig.NotesCall.NOTES_FILE)) { notes ->
            main.post { current = notes }
        }.also {
            store = it
            current = it.read()
        }
    }

    /** What a Talk starts with (spec §6): its prompt and opening, and the store's wipe generation. */
    class Start(val prompt: String, val opening: String, val generation: Long, val date: LocalDate)

    fun start(context: Context): Start {
        val s = store(context)
        val generation = s.generation()
        val notes = s.read()
        val today = LocalDate.now()
        val base = asset(context, TalkConfig.Protocol.PROMPT_ASSET)
        val prompt = Notes.prompt(base, asset(context, TalkConfig.NotesCall.BLOCK_ASSET), notes, today)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val (opening, recallNext) = Notes.opening(notes, prefs.getBoolean(KEY_RECALL_NEXT, false))
        prefs.edit().putBoolean(KEY_RECALL_NEXT, recallNext).apply()
        Log.i(TAG, "Talk starts with ${notes.facts.size} facts, ${notes.talks.size} Talks, recall hello ${opening != TalkConfig.Text.OPENING}")
        return Start(prompt, opening, generation, today)
    }

    /** After the Talk ends (spec §4): in the background; the transcript is dropped once the call is done. */
    fun afterTalk(context: Context, start: Start, transcript: List<SaidTurn>) {
        val s = store(context)
        val instruction = asset(context, TalkConfig.NotesCall.WRITER_ASSET)
        val key = BuildConfig.GEMINI_API_KEY
        calls.execute {
            NotesWriter(s, instruction, post = { model, body, timeout -> post(key, model, body, timeout) }, log = { Log.i(TAG, it) })
                .write(transcript, start.date, LocalDate.now(), start.generation)
        }
    }

    /** "Delete Anna's notes". */
    fun wipe(context: Context) = store(context).wipe()

    /** One `generateContent` request; null on any failure. The key goes in a header, never in a log. */
    private fun post(key: String, model: String, body: String, timeoutMs: Long): String? = try {
        val request = Request.Builder()
            .url("${TalkConfig.NotesCall.ENDPOINT}/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build().newCall(request).execute().use { r ->
            if (r.isSuccessful) r.body?.string() else {
                Log.w(TAG, "Notes call: HTTP ${r.code} from $model")
                null
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Notes call: ${e.javaClass.simpleName} from $model")
        null
    }

    private fun asset(context: Context, path: String) =
        context.assets.open(path).bufferedReader().use { it.readText() }.trim()
}
