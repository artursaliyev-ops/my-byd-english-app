package app.bydplayer

import android.os.Bundle
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

val SPEEDS = listOf(0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f)

/** What the player screen shows, read from the controller a few times a second. */
private data class PlayerUi(
    val title: String,
    val subtitle: String,
    val playing: Boolean,
    val loading: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val speed: Float,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val volume: Int,
    val maxVolume: Int,
    val abA: Long?,
    val abB: Long?,
    /** 1-90 course number of the current lesson, from the media item id. */
    val lessonNumber: Int?,
)

private fun MediaController.readUi(): PlayerUi {
    val meta = mediaMetadata
    val deviceVolume = deviceVolumeAvailable()
    val extras: Bundle = sessionExtras
    return PlayerUi(
        title = meta.title?.toString() ?: "No lesson",
        subtitle = meta.albumTitle?.toString().orEmpty(),
        // Show Pause while the user means to play, even during buffering.
        playing = playWhenReady && playbackState != Player.STATE_ENDED && playbackState != Player.STATE_IDLE,
        loading = playbackState == Player.STATE_BUFFERING,
        positionMs = currentPosition.coerceAtLeast(0L),
        durationMs = duration.takeIf { it != C.TIME_UNSET } ?: 0L,
        speed = playbackParameters.speed,
        hasPrevious = hasPreviousMediaItem(),
        hasNext = hasNextMediaItem(),
        volume = if (deviceVolume) this.deviceVolume else (volume * 10).toInt(),
        maxVolume = if (deviceVolume) deviceInfo.maxVolume.coerceAtLeast(1) else 10,
        abA = extras.getLong(PlaybackService.EXTRA_AB_A, -1L).takeIf { it >= 0 },
        abB = extras.getLong(PlaybackService.EXTRA_AB_B, -1L).takeIf { it >= 0 },
        lessonNumber = currentMediaItem?.mediaId?.toIntOrNull(),
    )
}

private fun MediaController.deviceVolumeAvailable() =
    isCommandAvailable(Player.COMMAND_GET_DEVICE_VOLUME) &&
        isCommandAvailable(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)

private fun MediaController.changeVolume(up: Boolean) {
    if (deviceVolumeAvailable()) {
        if (up) increaseDeviceVolume(C.VOLUME_FLAG_SHOW_UI) else decreaseDeviceVolume(C.VOLUME_FLAG_SHOW_UI)
    } else {
        // No system volume through the session: fall back to the player's own gain.
        volume = (volume + if (up) 0.1f else -0.1f).coerceIn(0f, 1f)
    }
}

/**
 * Main screen while a lesson is loaded. Controls on the left; the right side is kept for
 * the synced transcript ("Synced transcript panel" ticket).
 */
@Composable
fun PlayerScreen(controller: MediaController, onShowLessons: () -> Unit) {
    var ui by remember { mutableStateOf(controller.readUi()) }
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                ui = controller.readUi()
            }
        }
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }
    // Position and session extras (A-B points) don't fire events; poll them.
    LaunchedEffect(controller) {
        while (true) {
            ui = controller.readUi()
            delay(250)
        }
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp)) {
        Column(Modifier.weight(0.46f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Header(ui, onShowLessons)
            Progress(ui, onSeek = controller::seekTo)
            Transport(ui, controller)
            SpeedRow(ui.speed, onSpeed = controller::setPlaybackSpeed)
            Row(verticalAlignment = Alignment.CenterVertically) {
                VolumeControl(ui, onChange = controller::changeVolume, Modifier.weight(1f))
                Spacer(Modifier.width(24.dp))
                AbButton(ui, onMark = { controller.sendCustomCommand(PlaybackService.CMD_AB_MARK, Bundle.EMPTY) })
            }
        }
        Spacer(Modifier.width(40.dp))
        TranscriptArea(
            lessonNumber = ui.lessonNumber,
            positionMs = ui.positionMs,
            onSeek = { controller.seekTo(it); if (!ui.playing) controller.play() },
            modifier = Modifier.weight(0.54f).fillMaxHeight(),
        )
    }
}

@Composable
private fun Header(ui: PlayerUi, onShowLessons: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onShowLessons, modifier = Modifier.heightIn(min = 72.dp)) {
            Text("‹  Lessons", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(28.dp))
        Column {
            Text(ui.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(ui.subtitle, style = MaterialTheme.typography.bodyLarge, color = dim())
        }
    }
}

@Composable
private fun Progress(ui: PlayerUi, onSeek: (Long) -> Unit) {
    // While the thumb is dragged, show the drag position; seek once on release.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = ui.durationMs.coerceAtLeast(1L)
    val shown = dragging ?: (ui.positionMs.toFloat() / duration)
    Column {
        Slider(
            value = shown.coerceIn(0f, 1f),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * duration).toLong()) }
                dragging = null
            },
            enabled = ui.durationMs > 0,
            colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().height(64.dp),
        )
        Row {
            Text(formatTime((shown * duration).toLong()), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Text(
                if (ui.durationMs > 0) formatTime(ui.durationMs) else "--:--",
                style = MaterialTheme.typography.titleLarge,
                color = dim(),
            )
        }
    }
}

@Composable
private fun Transport(ui: PlayerUi, controller: MediaController) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIcon(PlayerIcons.Previous, "Previous lesson", enabled = ui.hasPrevious) { controller.seekToPreviousMediaItem() }
        RoundText("−5 s") { controller.seekBack() }
        Box(contentAlignment = Alignment.Center) {
            FilledIconButton(
                onClick = { if (ui.playing) controller.pause() else controller.play() },
                modifier = Modifier.size(140.dp),
                shape = CircleShape,
            ) {
                Icon(
                    if (ui.playing) PlayerIcons.Pause else PlayerIcons.Play,
                    contentDescription = if (ui.playing) "Pause" else "Play",
                    modifier = Modifier.size(80.dp),
                )
            }
        }
        RoundText("+5 s") { controller.seekForward() }
        RoundIcon(PlayerIcons.Next, "Next lesson", enabled = ui.hasNext) { controller.seekToNextMediaItem() }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(104.dp), shape = CircleShape) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(56.dp))
    }
}

@Composable
private fun RoundText(text: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.size(104.dp), shape = CircleShape) {
        Text(text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun SpeedRow(speed: Float, onSpeed: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Speed", style = MaterialTheme.typography.bodyLarge, color = dim())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SPEEDS.forEach { s ->
                val selected = kotlin.math.abs(s - speed) < 0.01f
                val label = formatSpeed(s)
                val mod = Modifier.weight(1f).heightIn(min = 72.dp)
                if (selected) {
                    Button(onClick = { onSpeed(s) }, modifier = mod, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                } else {
                    OutlinedButton(onClick = { onSpeed(s) }, modifier = mod, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun VolumeControl(ui: PlayerUi, onChange: (up: Boolean) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Volume  ${ui.volume}/${ui.maxVolume}", style = MaterialTheme.typography.bodyLarge, color = dim())
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = { onChange(false) }, modifier = Modifier.size(width = 96.dp, height = 72.dp)) {
                Text("−", fontSize = 34.sp)
            }
            LinearProgressIndicator(
                progress = { ui.volume.toFloat() / ui.maxVolume },
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp).height(12.dp),
                trackColor = MaterialTheme.colorScheme.surface,
                drawStopIndicator = {},
            )
            FilledTonalButton(onClick = { onChange(true) }, modifier = Modifier.size(width = 96.dp, height = 72.dp)) {
                Text("+", fontSize = 34.sp)
            }
        }
    }
}

@Composable
private fun AbButton(ui: PlayerUi, onMark: () -> Unit) {
    val (label, hint) = when {
        ui.abA == null -> "A-B" to "Repeat a part"
        ui.abB == null -> "Set B" to "A at ${formatTime(ui.abA)}"
        else -> "Stop A-B" to "${formatTime(ui.abA)}–${formatTime(ui.abB)}"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(hint, style = MaterialTheme.typography.bodyLarge, color = dim(), maxLines = 1)
        val mod = Modifier.width(220.dp).heightIn(min = 72.dp)
        if (ui.abA != null) {
            Button(onClick = onMark, modifier = mod) { Text(label, style = MaterialTheme.typography.labelLarge) }
        } else {
            OutlinedButton(onClick = onMark, modifier = mod) { Text(label, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/**
 * The synced transcript: Whisper's own segments for the current lesson. The segment being
 * spoken is highlighted and kept in view; tapping a segment seeks there and plays.
 * If the user scrolls the list by hand, following pauses for [FOLLOW_PAUSE_MS].
 */
@Composable
private fun TranscriptArea(lessonNumber: Int?, positionMs: Long, onSeek: (Long) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val segments by produceState<List<Segment>?>(null, lessonNumber) {
        value = lessonNumber?.let { n -> withContext(Dispatchers.IO) { loadTranscript(context, n) } }
    }
    val box = modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
    val segs = segments
    if (segs.isNullOrEmpty()) {
        Box(box.padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Transcript isn't available for this lesson yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = dim(),
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val active = activeSegmentIndex(segs, positionMs)
    val listState = rememberLazyListState()
    var lastTouchMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect {
            if (it is DragInteraction.Start || it is DragInteraction.Stop) lastTouchMs = SystemClock.uptimeMillis()
        }
    }
    // A new lesson starts at the top (or wherever resume put it) without animation.
    LaunchedEffect(segs) { listState.scrollToItem((active - 1).coerceAtLeast(0)) }
    LaunchedEffect(active) {
        if (active < 0 || listState.isScrollInProgress) return@LaunchedEffect
        if (SystemClock.uptimeMillis() - lastTouchMs < FOLLOW_PAUSE_MS) return@LaunchedEffect
        // Keep one earlier line above the active one, so the reader sees what came before.
        listState.animateScrollToItem((active - 1).coerceAtLeast(0))
    }

    LazyColumn(
        box,
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(segs) { i, seg ->
            SegmentLine(seg, isActive = i == active, onClick = {
                lastTouchMs = 0L
                onSeek(seg.startMs)
            })
        }
        // Room for the last lines to scroll up to the top.
        item { Spacer(Modifier.height(320.dp)) }
    }
}

private const val FOLLOW_PAUSE_MS = 6_000L

@Composable
private fun SegmentLine(seg: Segment, isActive: Boolean, onClick: () -> Unit) {
    // English lines are what the listener repeats, so they read brighter than the Russian narration.
    val color = when {
        isActive -> MaterialTheme.colorScheme.onPrimary
        seg.english -> MaterialTheme.colorScheme.onSurface
        else -> dim()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(
                if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            formatTime(seg.startMs),
            style = MaterialTheme.typography.bodyLarge,
            color = color.copy(alpha = color.alpha * 0.7f),
            modifier = Modifier.width(88.dp),
        )
        Text(
            seg.text,
            fontSize = 26.sp,
            lineHeight = 34.sp,
            fontWeight = if (seg.english || isActive) FontWeight.Medium else FontWeight.Normal,
            color = color,
        )
    }
}

@Composable
private fun dim() = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)

/** 754_000 → "12:34"; an hour or more → "1:02:03". */
fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** 1f → "1x", 0.75f → "0.75x", 1.1f → "1.1x". */
fun formatSpeed(speed: Float): String =
    "%.2f".format(java.util.Locale.US, speed).trimEnd('0').trimEnd('.') + "x"
