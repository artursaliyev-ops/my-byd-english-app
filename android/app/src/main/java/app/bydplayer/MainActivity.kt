package app.bydplayer

import android.content.ComponentName
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.bydplayer.talk.Talk
import app.bydplayer.talk.TalkScreen

class MainActivity : ComponentActivity() {
    private var disableBlur: () -> Unit = {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disableBlur = enableWallpaperBlur()
        setContent {
            BydTheme {
                Surface(Modifier.fillMaxSize(), color = Color.Transparent, contentColor = Ui6.colors.text) {
                    Ui6Backdrop { App() }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        onScreen = true
        // Voice practice: the microphone can only start while the app is on screen.
        VoicePractice.startIfNeeded(this, LessonClock.snapshot.playing)
    }

    override fun onPause() {
        onScreen = false
        super.onPause()
    }

    override fun onDestroy() {
        disableBlur()
        super.onDestroy()
    }

    /**
     * Records every key the screen sees for Settings → Last button, including keys that
     * aren't media keys (the car may send its own codes). The key then goes on as usual,
     * and media keys reach the playback session from there.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        ButtonLog.key(event, via = "screen")
        return super.dispatchKeyEvent(event)
    }
}

/**
 * Two screens over one playback service: the lesson list and the player. On launch it
 * resumes the last lesson at the saved position (paused, one tap to play) once the
 * library scan has found that lesson. If the service is still playing, it just shows it.
 */
@Composable
private fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library = remember { LibraryModel(context, scope) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var current by remember { mutableStateOf<Int?>(null) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var resumeHandled by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setConnectionHints(Bundle().apply { putBoolean(PlaybackService.HINT_APP_UI, true) })
            .buildAsync()
        future.addListener({ controller = future.get() }, context.mainExecutor)
        onDispose { MediaController.releaseFuture(future) }
    }
    DisposableEffect(controller) {
        val c = controller
        current = c?.currentMediaItem?.mediaId?.toIntOrNull()
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                current = item?.mediaId?.toIntOrNull()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (onScreen) VoicePractice.startIfNeeded(context, isPlaying)
            }
        }
        c?.addListener(listener)
        onDispose { c?.removeListener(listener) }
    }

    val ui = library.ui
    LaunchedEffect(controller, ui) {
        val c = controller ?: return@LaunchedEffect
        if (resumeHandled) return@LaunchedEffect
        if (c.mediaItemCount > 0) {
            resumeHandled = true
            showPlayer = true
            return@LaunchedEffect
        }
        // Wait for the scan: the saved lesson needs its file URI from the current folder.
        val scan = (ui as? LibraryUi.Ready)?.scan ?: return@LaunchedEffect
        resumeHandled = true
        val saved = PlaybackStore(context).resume() ?: return@LaunchedEffect
        val lesson = scan.lessons.firstOrNull { it.number == saved.lesson } ?: return@LaunchedEffect
        c.load(scan.lessons, lesson, saved.positionMs)
        showPlayer = true
    }

    val c = controller
    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = showPlayer && !showSettings) { showPlayer = false }
    // A running Talk covers everything, also when the app comes back on screen (Talk spec §7).
    val talk = Talk.screen
    val screen = appScreen(talk != null, showSettings, showPlayer, hasLesson = c != null && current != null)
    BackHandler(enabled = talk != null) {}
    if (talk != null) {
        TalkScreen(talk)
        return
    }
    // Screens crossfade, as UI6 pages do (spec §7).
    Crossfade(targetState = screen, animationSpec = Ui6.tween(Ui6.SCREEN_MS), label = "screen") { shown ->
        when (shown) {
            Screen.Settings -> SettingsScreen(onBack = { showSettings = false })
            // The outgoing player can outlive the controller during the fade; draw nothing then.
            Screen.Player -> controller?.let { PlayerScreen(it, onShowLessons = { showPlayer = false }) }
            // Drawn above, before the crossfade.
            Screen.Talk -> Unit
            Screen.Lessons -> LibraryScreen(
                model = library,
                nowPlaying = current,
                onOpenPlayer = { showPlayer = true },
                onOpenSettings = { showSettings = true },
            ) { lesson, all ->
                val player = controller ?: return@LibraryScreen
                player.load(all, lesson, 0L)
                player.play()
                current = lesson.number
                showPlayer = true
            }
        }
    }
}

/** The activity is resumed (on screen); the voice-practice microphone may only start then. */
private var onScreen = false

internal enum class Screen { Lessons, Player, Settings, Talk }

/**
 * Which screen the app shows. A running Talk wins over everything (Talk spec §7). Talk state
 * lives only in memory, so after the process dies (the car switched off mid-Talk) there is none,
 * and a launch that resumed the last lesson opens on the player (Talk spec §9).
 */
internal fun appScreen(talkActive: Boolean, showSettings: Boolean, showPlayer: Boolean, hasLesson: Boolean) = when {
    talkActive -> Screen.Talk
    showSettings -> Screen.Settings
    showPlayer && hasLesson -> Screen.Player
    else -> Screen.Lessons
}

/** Queues every found lesson, so prev/next and auto-advance move through the course. */
private fun Player.load(all: List<Lesson>, start: Lesson, positionMs: Long) {
    setMediaItems(all.map(::mediaItem), all.indexOf(start), positionMs)
    prepare()
}

private fun mediaItem(lesson: Lesson) = MediaItem.Builder()
    .setMediaId(lesson.number.toString())
    .setUri(lesson.uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(lesson.title)
            .setAlbumTitle("Pimsleur English ${lesson.level.roman}")
            .build(),
    )
    .build()
