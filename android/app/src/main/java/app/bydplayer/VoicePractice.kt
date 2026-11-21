package app.bydplayer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * Voice practice state shared by the recorder and the screens (they share the process).
 * Compose state is written on the main thread only.
 */
object VoicePractice {
    /** Inside a kept window right now: the recording dot pulses. */
    var capturing by mutableStateOf(false)

    /** Bumped whenever Attempts are saved or deleted, so the transcript reloads its chips. */
    var storeVersion by mutableIntStateOf(0)

    /** The recorder service is up (any thread may read it). */
    @Volatile var micRunning = false

    fun store(context: Context) = AttemptStore(File(context.applicationContext.filesDir, "attempts"))

    fun hasMicPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /**
     * Starts the microphone if voice practice should be recording: the setting is on, the
     * permission is granted and a lesson is playing. Call it only while the app is on
     * screen: Android mutes a microphone service started from the background.
     */
    fun startIfNeeded(context: Context, lessonPlaying: Boolean) {
        if (micRunning || !lessonPlaying) return
        // A Talk holds the mic; voice practice comes back after it (Talk spec §5).
        if (app.bydplayer.talk.Talk.screen != null) return
        if (!SettingsStore(context).recordAnswers() || !hasMicPermission(context)) return
        RecorderService.start(context)
    }
}
