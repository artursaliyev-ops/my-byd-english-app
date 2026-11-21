package app.bydplayer

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BydTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App()
                }
            }
        }
    }
}

/**
 * The lesson list, wired to the playback service. Tapping a lesson queues it and the
 * lessons after it. The full player screen (controls, resume) comes with the
 * "Main player screen and resume" ticket.
 */
@Composable
private fun App() {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var queue by remember { mutableStateOf<List<Lesson>>(emptyList()) }
    var nowPlaying by remember { mutableStateOf<Lesson?>(null) }

    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({ controller = future.get() }, context.mainExecutor)
        onDispose { MediaController.releaseFuture(future) }
    }
    DisposableEffect(controller, queue) {
        val c = controller
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                nowPlaying = queue.firstOrNull { it.number.toString() == item?.mediaId }
            }
        }
        c?.addListener(listener)
        onDispose { c?.removeListener(listener) }
    }

    LibraryScreen(nowPlaying = nowPlaying) { lesson, all ->
        val c = controller ?: return@LibraryScreen
        queue = all
        c.setMediaItems(all.map(::mediaItem), all.indexOf(lesson), 0L)
        c.prepare()
        c.play()
        nowPlaying = lesson
    }
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
