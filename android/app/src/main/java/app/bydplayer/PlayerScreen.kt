package app.bydplayer

import android.Manifest
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import app.bydplayer.talk.Talk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

val SPEEDS = listOf(0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f)

/** The step in [SPEEDS] nearest to [speed]. */
fun speedIndex(speed: Float): Int = SPEEDS.indices.minBy { kotlin.math.abs(SPEEDS[it] - speed) }

/** The next speed up or down from [speed], held at the ends of [SPEEDS]. */
fun stepSpeed(speed: Float, up: Boolean): Float =
    SPEEDS[(speedIndex(speed) + if (up) 1 else -1).coerceIn(0, SPEEDS.lastIndex)]

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
    DisposableEffect(Unit) { onDispose { AttemptPlayer.stop() } }
    // Position and session extras (A-B points) don't fire events; poll them.
    LaunchedEffect(controller) {
        while (true) {
            ui = controller.readUi()
            delay(250)
        }
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp)) {
        Column(Modifier.weight(0.46f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            val context = LocalContext.current
            Header(ui, onShowLessons) {
                // Read each recomposition (every 250 ms): Settings may have changed it.
                if (SettingsStore(context).recordAnswers()) {
                    RecordingDot(recording = ui.playing && AttemptPlayer.now == null, capturing = VoicePractice.capturing)
                }
            }
            Progress(ui, onSeek = { AttemptPlayer.stop(); controller.seekTo(it) })
            Transport(ui, controller)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                SpeedControl(ui.speed, onSpeed = { AttemptPlayer.stop(); controller.setPlaybackSpeed(it) }, Modifier.weight(1f))
                VolumeControl(ui, onChange = controller::changeVolume, Modifier.weight(1f))
                AbButton(ui, onMark = { AttemptPlayer.stop(); controller.sendCustomCommand(PlaybackService.CMD_AB_MARK, Bundle.EMPTY) }, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.width(40.dp))
        TranscriptArea(
            lessonNumber = ui.lessonNumber,
            positionMs = ui.positionMs,
            controller = controller,
            onSeek = { AttemptPlayer.stop(); controller.seekTo(it); if (!ui.playing) controller.play() },
            modifier = Modifier.weight(0.54f).fillMaxHeight(),
        )
    }
}

@Composable
private fun Header(ui: PlayerUi, onShowLessons: () -> Unit, dot: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GlassButton(onClick = onShowLessons, modifier = Modifier.heightIn(min = 72.dp)) { GlassLabel("‹  Lessons") }
        // Talk lives only here, not on the lesson list, and only with a key built in (Talk spec §2, §7).
        if (Talk.available) {
            val context = LocalContext.current
            var micDenied by remember { mutableStateOf(false) }
            // No mic permission: the normal prompt; denied, no Talk (Talk spec §5).
            val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                micDenied = !granted
                if (granted) Talk.start(context)
            }
            Spacer(Modifier.width(12.dp))
            GlassButton(
                onClick = {
                    if (VoicePractice.hasMicPermission(context)) {
                        micDenied = false
                        Talk.start(context)
                    } else {
                        askMic.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                blue = true,
                modifier = Modifier.heightIn(min = 72.dp),
            ) { GlassLabel("Talk") }
            // Talk couldn't connect (Talk spec §8): a short note, then it goes.
            val note = Talk.note
            if (note != null) {
                LaunchedEffect(note) {
                    delay(5_000)
                    Talk.clearNote()
                }
                Spacer(Modifier.width(12.dp))
                Text(note, style = MaterialTheme.typography.bodyLarge, color = dim())
            }
            if (micDenied) {
                Spacer(Modifier.width(12.dp))
                Text("Microphone permission not granted", style = MaterialTheme.typography.bodyLarge, color = dim())
            }
        }
        Spacer(Modifier.width(28.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(ui.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                dot()
            }
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
            colors = SliderDefaults.colors(
                thumbColor = Ui6.colors.blue,
                activeTrackColor = Ui6.colors.blue,
                inactiveTrackColor = Ui6.colors.glass,
            ),
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
        RoundIcon(PlayerIcons.Previous, "Previous lesson", enabled = ui.hasPrevious) { AttemptPlayer.stop(); controller.seekToPreviousMediaItem() }
        RoundText("−5 s") { AttemptPlayer.stop(); controller.seekBack() }
        GlassButton(
            onClick = {
                // During "▶ Me" this only stops it; the lesson stays paused where it was.
                if (AttemptPlayer.now != null) AttemptPlayer.stop() else if (ui.playing) controller.pause() else controller.play()
            },
            modifier = Modifier.size(104.dp),
            blue = true,
            shape = CircleShape,
            contentPadding = 0.dp,
        ) {
            Crossfade(ui.playing, animationSpec = Ui6.tween(Ui6.PLAY_PAUSE_MS), label = "play/pause") { playing ->
                Icon(
                    if (playing) PlayerIcons.Pause else PlayerIcons.Play,
                    contentDescription = if (playing) "Pause" else "Play",
                    modifier = Modifier.size(52.dp),
                )
            }
        }
        RoundText("+5 s") { AttemptPlayer.stop(); controller.seekForward() }
        RoundIcon(PlayerIcons.Next, "Next lesson", enabled = ui.hasNext) { AttemptPlayer.stop(); controller.seekToNextMediaItem() }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    BareControl(onClick, enabled) { Icon(icon, contentDescription = label, modifier = Modifier.size(44.dp)) }
}

@Composable
private fun RoundText(text: String, onClick: () -> Unit) {
    BareControl(onClick) { Text(text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false) }
}

/**
 * A control with no glass behind it, like a music player's: just the glyph, which dims while
 * pressed and turns BYD blue while [active]. The touch area stays large for use while driving.
 */
@Composable
private fun BareControl(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.size(TRANSPORT_TOUCH),
    active: Boolean = false,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val color by animateColorAsState(
        targetValue = when {
            !enabled -> Ui6.colors.textDisabled
            pressed -> Ui6.colors.text.copy(alpha = 0.5f)
            active -> Ui6.colors.blue
            else -> Ui6.colors.text
        },
        animationSpec = Ui6.tween(if (pressed) Ui6.PRESS_IN_MS else Ui6.PRESS_OUT_MS),
        label = "bare control",
    )
    Box(
        modifier
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides color) { content() }
    }
}

private val TRANSPORT_TOUCH = 88.dp
private val STEPPER_TOUCH = Modifier.size(width = 64.dp, height = 64.dp)

/** A small dim caption over a compact control in the bottom row. */
@Composable
private fun Captioned(caption: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(caption, style = MaterialTheme.typography.bodyLarge, color = dim(), maxLines = 1)
        content()
    }
}

/** A compact "−  value  +" stepper. */
@Composable
private fun Stepper(
    value: String,
    onDown: () -> Unit,
    onUp: () -> Unit,
    canDown: Boolean = true,
    canUp: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BareControl(onClick = onDown, enabled = canDown, modifier = STEPPER_TOUCH) { Text("−", fontSize = 30.sp) }
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(76.dp),
        )
        BareControl(onClick = onUp, enabled = canUp, modifier = STEPPER_TOUCH) { Text("+", fontSize = 30.sp) }
    }
}

/** Speed as a −/+ stepper through [SPEEDS]. */
@Composable
private fun SpeedControl(speed: Float, onSpeed: (Float) -> Unit, modifier: Modifier) {
    val index = speedIndex(speed)
    Captioned("Speed", modifier) {
        Stepper(
            value = formatSpeed(SPEEDS[index]),
            onDown = { onSpeed(stepSpeed(speed, up = false)) },
            onUp = { onSpeed(stepSpeed(speed, up = true)) },
            canDown = index > 0,
            canUp = index < SPEEDS.lastIndex,
        )
    }
}

@Composable
private fun VolumeControl(ui: PlayerUi, onChange: (up: Boolean) -> Unit, modifier: Modifier) {
    Captioned("Volume", modifier) {
        Stepper(
            value = "${ui.volume}/${ui.maxVolume}",
            onDown = { onChange(false) },
            onUp = { onChange(true) },
            canDown = ui.volume > 0,
            canUp = ui.volume < ui.maxVolume,
        )
    }
}

@Composable
private fun AbButton(ui: PlayerUi, onMark: () -> Unit, modifier: Modifier) {
    val (label, hint) = when {
        ui.abA == null -> "A-B" to "Repeat a part"
        ui.abB == null -> "Set B" to "A at ${formatTime(ui.abA)}"
        else -> "Stop A-B" to "${formatTime(ui.abA)}–${formatTime(ui.abB)}"
    }
    Captioned(hint, modifier) {
        BareControl(onClick = onMark, modifier = Modifier.width(160.dp).height(64.dp), active = ui.abA != null) {
            Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/**
 * The synced transcript: Whisper's own segments for the current lesson. The segment being
 * spoken is highlighted and kept in view; tapping a segment seeks there and plays.
 * If the user scrolls the list by hand, following pauses for [FOLLOW_PAUSE_MS].
 */
@Composable
private fun TranscriptArea(lessonNumber: Int?, positionMs: Long, controller: MediaController, onSeek: (Long) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val segments by produceState<List<Segment>?>(null, lessonNumber) {
        value = lessonNumber?.let { n -> withContext(Dispatchers.IO) { loadTranscript(context, n) } }
    }
    // Voice practice: this lesson's Attempts, by the transcript line each is compared with.
    val attempts by produceState(emptyMap<Int, List<Pair<AttemptKind, java.io.File>>>(), segments, VoicePractice.storeVersion) {
        val segs = segments
        val id = lessonNumber?.let(::transcriptId)
        value = if (segs == null || id == null) emptyMap() else withContext(Dispatchers.IO) { attemptsByLine(context, id, segs) }
    }
    val box = modifier.background(Ui6.colors.glass, RoundedCornerShape(Ui6.Radius))
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
            }) {
                attempts[i]?.let { mine ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        mine.forEach { (kind, file) -> AttemptChip(kind, seg) { AttemptPlayer.play(controller, file, kind, seg) } }
                    }
                }
            }
        }
        // Room for the last lines to scroll up to the top.
        item { Spacer(Modifier.height(320.dp)) }
    }
}

private const val FOLLOW_PAUSE_MS = 6_000L

@Composable
private fun SegmentLine(seg: Segment, isActive: Boolean, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    // English lines are what the listener repeats, so they read brighter than the Russian narration.
    val t = Ui6.colors
    val color by animateColorAsState(
        when {
            isActive -> t.blueOn
            seg.english -> t.text
            else -> t.textSecondary
        },
        Ui6.tween(Ui6.ACTIVE_LINE_MS),
        label = "line text",
    )
    val fill by animateColorAsState(if (isActive) t.blue else Color.Transparent, Ui6.tween(Ui6.ACTIVE_LINE_MS), label = "line fill")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(fill, RoundedCornerShape(Ui6.Radius))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
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
            fontWeight = when {
                isActive -> FontWeight.SemiBold
                seg.english -> FontWeight.Medium
                else -> FontWeight.Normal
            },
            color = color,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** Saved Attempts of a lesson, keyed by the transcript line they are compared with; Me before Echo. */
private fun attemptsByLine(context: android.content.Context, lessonId: String, segs: List<Segment>): Map<Int, List<Pair<AttemptKind, java.io.File>>> {
    val store = VoicePractice.store(context)
    val saved = store.list(lessonId, transcriptFingerprint(segs))
    if (saved.isEmpty()) return emptyMap()
    return speakingPauses(segs)
        .mapNotNull { p -> AttemptKey(p.kind, p.startMs).takeIf { it in saved }?.let { p.compareIndex to (p.kind to store.file(lessonId, it)) } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, list) -> list.sortedBy { it.first.ordinal } }
}

@Composable
private fun dim() = Ui6.colors.textSecondary

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
