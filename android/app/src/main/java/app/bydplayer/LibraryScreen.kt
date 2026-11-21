package app.bydplayer

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "BydLibrary"
private val DoneGreen = Color(0xFF6FD08C)

sealed interface LibraryUi {
    data object NoFolder : LibraryUi
    data object AccessLost : LibraryUi
    data object Scanning : LibraryUi
    data class Failed(val message: String) : LibraryUi
    data class Ready(val scan: LibraryScan) : LibraryUi
}

/**
 * The picked folder, its scan and the completed marks. Held by the app (not the library
 * screen) so the scan survives a trip to the player and resume can look lessons up in it.
 * Completed marks follow [LibraryStore], which the playback service also writes.
 */
class LibraryModel(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    val store = LibraryStore(app)

    var folder by mutableStateOf(store.folder())
        private set
    var ui by mutableStateOf(
        when {
            folder != null -> LibraryUi.Scanning
            store.folderAccessLost() -> LibraryUi.AccessLost
            else -> LibraryUi.NoFolder
        },
    )
        private set
    var completed by mutableStateOf(store.completed())
        private set

    // Kept in a field: SharedPreferences holds change listeners weakly.
    private val completedListener = store.onCompletedChanged { completed = store.completed() }

    init {
        folder?.let(::rescan)
    }

    fun rescan() {
        folder?.let(::rescan)
    }

    private fun rescan(tree: Uri) {
        ui = LibraryUi.Scanning
        scope.launch {
            ui = withContext(Dispatchers.IO) {
                runCatching { scanLibrary(app, tree) }.fold(
                    { LibraryUi.Ready(it) },
                    {
                        Log.w(TAG, "Scan failed", it)
                        LibraryUi.Failed("Can't read the folder. Is the USB stick plugged in?")
                    },
                )
            }
        }
    }

    /** Keeps access to a newly picked folder and scans it. Fails if the grant can't be kept. */
    fun pick(uri: Uri): Result<Unit> = runCatching { store.setFolder(uri) }.onSuccess {
        folder = uri
        rescan(uri)
    }

    fun toggleDone(lesson: Lesson) = store.setCompleted(lesson.number, lesson.number !in completed)
}

/**
 * First screen: asks for the lesson folder once (SAF tree picker, permission persisted),
 * then lists the I/II/III lessons found there with completed marks.
 * Tap a lesson to play it; long-press to toggle its completed mark.
 */
@Composable
fun LibraryScreen(
    model: LibraryModel,
    nowPlaying: Int?,
    onOpenPlayer: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlay: (lesson: Lesson, all: List<Lesson>) -> Unit,
) {
    val folder = model.folder
    val completed = model.completed
    var pickerError by remember { mutableStateOf<String?>(null) }
    val rescan = { model.rescan() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        model.pick(uri)
            .onSuccess { pickerError = null }
            .onFailure {
                Log.w(TAG, "Can't keep access to $uri", it)
                pickerError = "This folder can't be used. Pick a folder inside the drive, not the drive itself."
            }
    }
    val pickFolder = {
        runCatching { picker.launch(null) }.onFailure {
            // TODO(fallback): no system picker on this head unit. Next options from the
            // DiLink ticket: MediaStore audio, then raw /storage paths, then import.
            Log.w(TAG, "Picker failed to open", it)
            pickerError = "The folder picker isn't available on this device."
        }
        Unit
    }

    when (val state = model.ui) {
        LibraryUi.NoFolder -> Message(
            title = "Choose your lesson folder",
            body = "Copy the Pimsleur I, II and III mp3 files into a folder on the USB stick or this device, " +
                "then choose that folder. The drive itself can't be chosen, so use a folder inside it.",
            action = "Choose folder",
            onAction = pickFolder,
            error = pickerError,
        )
        LibraryUi.AccessLost -> Message(
            title = "Folder access was lost",
            body = "Choose the lesson folder again to keep listening.",
            action = "Choose folder",
            onAction = pickFolder,
            error = pickerError,
        )
        LibraryUi.Scanning -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                CircularProgressIndicator(Modifier.padding(8.dp))
                Text("Looking for lessons…", style = MaterialTheme.typography.titleLarge)
            }
        }
        is LibraryUi.Failed -> Message(
            title = "Lessons unavailable",
            body = state.message,
            action = "Try again",
            onAction = rescan,
            secondary = "Choose another folder",
            onSecondary = pickFolder,
            error = pickerError,
        )
        is LibraryUi.Ready -> if (state.scan.lessons.isEmpty()) {
            Message(
                title = "No lessons in this folder",
                body = "Looked in ${folder?.let(::treeLabel)} for files named like Pimsleur_I_01.mp3 and found none.",
                action = "Choose another folder",
                onAction = pickFolder,
                secondary = "Scan again",
                onSecondary = rescan,
                error = pickerError,
            )
        } else {
            LessonList(
                scan = state.scan,
                folderLabel = folder?.let(::treeLabel).orEmpty(),
                completed = completed,
                nowPlaying = nowPlaying,
                onChangeFolder = pickFolder,
                onPlay = { onPlay(it, state.scan.lessons) },
                onOpenPlayer = onOpenPlayer,
                onOpenSettings = onOpenSettings,
                onToggleDone = model::toggleDone,
            )
        }
    }
}

@Composable
private fun LessonList(
    scan: LibraryScan,
    folderLabel: String,
    completed: Set<Int>,
    nowPlaying: Int?,
    onChangeFolder: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlay: (Lesson) -> Unit,
    onToggleDone: (Lesson) -> Unit,
) {
    // Open on the level of the first unfinished lesson.
    var levelIndex by remember {
        val firstOpen = scan.lessons.firstOrNull { it.number !in completed } ?: scan.lessons.first()
        mutableIntStateOf(firstOpen.level.ordinal)
    }
    val level = Level.entries[levelIndex]

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pimsleur English", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${scan.lessons.size} of 90 lessons in $folderLabel · ${completed.size} completed" +
                        " · Long-press a lesson to mark it done",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            }
            if (nowPlaying != null) {
                Button(onClick = onOpenPlayer, modifier = Modifier.heightIn(min = 72.dp)) {
                    Text("▶  Lesson $nowPlaying", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.widthIn(min = 16.dp))
            }
            OutlinedButton(onClick = onChangeFolder, modifier = Modifier.heightIn(min = 72.dp)) {
                Text("Change folder", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.widthIn(min = 16.dp))
            FilledTonalIconButton(onClick = onOpenSettings, modifier = Modifier.size(72.dp)) {
                Icon(PlayerIcons.Settings, contentDescription = "Settings", modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.heightIn(min = 16.dp))
        TabRow(selectedTabIndex = levelIndex, containerColor = MaterialTheme.colorScheme.background) {
            Level.entries.forEach { l ->
                val inLevel = scan.byLevel(l)
                val done = inLevel.count { it.number in completed }
                Tab(
                    selected = l == level,
                    onClick = { levelIndex = l.ordinal },
                    modifier = Modifier.heightIn(min = 80.dp),
                    text = {
                        Text(
                            "${l.label}   $done/${inLevel.size}",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                )
            }
        }
        val byNumber = scan.byLevel(level).associateBy { it.number }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items((level.first..level.last).toList(), key = { it }) { number ->
                LessonTile(
                    number = number,
                    lesson = byNumber[number],
                    done = number in completed,
                    playing = nowPlaying == number,
                    onPlay = onPlay,
                    onToggleDone = onToggleDone,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LessonTile(
    number: Int,
    lesson: Lesson?,
    done: Boolean,
    playing: Boolean,
    onPlay: (Lesson) -> Unit,
    onToggleDone: (Lesson) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val base = Modifier.aspectRatio(1.25f).fillMaxWidth()
    val tile = when {
        lesson == null -> base.border(2.dp, colors.onBackground.copy(alpha = 0.12f), shape)
        else -> base
            .background(if (playing) colors.primary.copy(alpha = 0.25f) else colors.surface, shape)
            .border(if (playing) 3.dp else 0.dp, if (playing) colors.primary else Color.Transparent, shape)
            .combinedClickable(onClick = { onPlay(lesson) }, onLongClick = { onToggleDone(lesson) })
    }
    Box(tile.padding(12.dp)) {
        Text(
            number.toString(),
            modifier = Modifier.align(Alignment.Center),
            fontSize = 44.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (lesson == null) colors.onBackground.copy(alpha = 0.25f) else colors.onSurface,
        )
        when {
            lesson == null -> Caption("missing", Modifier.align(Alignment.BottomCenter), colors.onBackground.copy(alpha = 0.35f))
            playing -> Caption("▶ playing", Modifier.align(Alignment.BottomCenter), colors.primary)
        }
        if (done) {
            Text("✓", Modifier.align(Alignment.TopEnd), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = DoneGreen)
        }
    }
}

@Composable
private fun Caption(text: String, modifier: Modifier, color: Color) {
    Text(text, modifier, style = MaterialTheme.typography.bodyLarge, color = color)
}

@Composable
private fun Message(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    error: String? = null,
) {
    Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 900.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            error?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = colorError(), textAlign = TextAlign.Center) }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Button(onClick = onAction, modifier = Modifier.heightIn(min = 80.dp).widthIn(min = 280.dp)) {
                    Text(action, style = MaterialTheme.typography.labelLarge)
                }
                if (secondary != null) {
                    OutlinedButton(onClick = onSecondary, modifier = Modifier.heightIn(min = 80.dp).widthIn(min = 280.dp)) {
                        Text(secondary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun colorError() = MaterialTheme.colorScheme.error
