package app.bydplayer

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Settings: the Button mode for the media buttons, "Record my answers" for voice practice,
 * and a **Last button** readout of the signals the app received, so the user can see in
 * the car what the steering wheel sends.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    var mode by remember { mutableStateOf(store.buttonMode()) }
    var recordAnswers by remember { mutableStateOf(store.recordAnswers() && VoicePractice.hasMicPermission(context)) }
    var permissionDenied by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun turnOn() {
        store.setRecordAnswers(true)
        recordAnswers = true
        permissionDenied = false
        // Settings is on screen, so the microphone may start now if a lesson is playing.
        VoicePractice.startIfNeeded(context, LessonClock.snapshot.playing)
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) turnOn() else {
            store.setRecordAnswers(false)
            recordAnswers = false
            permissionDenied = true
        }
    }
    val t = Ui6.colors
    val dim = t.textSecondary

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            GlassButton(onClick = onBack, modifier = Modifier.heightIn(min = 72.dp)) { GlassLabel("‹ Lessons") }
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }

        Text("Button mode", style = MaterialTheme.typography.titleLarge)
        Text(
            "What ⏮ ⏭ do on the steering wheel, the car's now-playing widget and Bluetooth keys. " +
                "Holding ◀ ▶ on the wheel does the other choice. " +
                "The buttons on the player screen always stay the same.",
            style = MaterialTheme.typography.bodyLarge,
            color = dim,
        )
        Segmented(
            count = ButtonMode.entries.size,
            selected = ButtonMode.entries.indexOf(mode),
            onSelect = { i ->
                val m = ButtonMode.entries[i]
                store.setButtonMode(m)
                mode = m
            },
            modifier = Modifier.widthIn(min = 760.dp).height(80.dp),
        ) { i ->
            Text(ButtonMode.entries[i].label, style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current)
        }

        Text("Record my answers", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
        Text(
            "While a lesson plays, the car's microphone keeps only what you say in the pauses where you answer. " +
                "Play it back next to the speaker with ▶ Me in the transcript. Kept for your last 5 lessons, on this car only.",
            style = MaterialTheme.typography.bodyLarge,
            color = dim,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Segmented(
                count = 2,
                selected = if (recordAnswers) 1 else 0,
                onSelect = { i ->
                    when {
                        i == 0 -> {
                            store.setRecordAnswers(false)
                            recordAnswers = false
                        }
                        VoicePractice.hasMicPermission(context) -> turnOn()
                        else -> askMic.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.width(480.dp).height(80.dp),
            ) { i ->
                Text(if (i == 0) "Off" else "On", style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current)
            }
            GlassButton(onClick = { confirmDelete = true }, modifier = Modifier.height(80.dp)) { GlassLabel("Delete my answers") }
        }
        if (permissionDenied) {
            Text("Microphone permission not granted.", style = MaterialTheme.typography.bodyLarge, color = dim)
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete my answers?") },
                text = { Text("All your recorded answers will be deleted. This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        VoicePractice.store(context).deleteAll()
                        VoicePractice.storeVersion++
                        confirmDelete = false
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            )
        }

        Text("Last button", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
        Text(
            "Press or hold a steering-wheel button with this screen open. Newest at the top.",
            style = MaterialTheme.typography.bodyLarge,
            color = dim,
        )
        Column(
            Modifier.fillMaxWidth()
                // A darker inset, so the dense monospace log reads on any wallpaper (spec §5).
                .background(t.inset, RoundedCornerShape(Ui6.Radius))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val entries = ButtonLog.entries
            if (entries.isEmpty()) {
                Text("Nothing received yet.", style = MaterialTheme.typography.bodyLarge, color = dim)
            }
            entries.forEachIndexed { i, line ->
                Text(
                    line,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp,
                    color = if (i == 0) t.text else t.text.copy(alpha = 0.65f),
                )
            }
        }
    }
}
