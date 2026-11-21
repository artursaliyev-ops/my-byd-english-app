package app.bydplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
 * Settings: the Button mode for the media buttons, and a **Last button** readout of the
 * signals the app received, so the user can see in the car what the steering wheel sends.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    var mode by remember { mutableStateOf(store.buttonMode()) }
    val dim = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 72.dp)) {
                Text("‹ Lessons", style = MaterialTheme.typography.labelLarge)
            }
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }

        Text("Button mode", style = MaterialTheme.typography.titleLarge)
        Text(
            "What ⏮ ⏭ do on the steering wheel, the car's now-playing widget and Bluetooth keys. " +
                "The buttons on the player screen always stay the same.",
            style = MaterialTheme.typography.bodyLarge,
            color = dim,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ButtonMode.entries.forEach { m ->
                val mod = Modifier.widthIn(min = 360.dp).heightIn(min = 80.dp)
                val pick = {
                    store.setButtonMode(m)
                    mode = m
                }
                if (m == mode) {
                    Button(onClick = pick, modifier = mod) { Text("✓  ${m.label}", style = MaterialTheme.typography.labelLarge) }
                } else {
                    OutlinedButton(onClick = pick, modifier = mod) { Text(m.label, style = MaterialTheme.typography.labelLarge) }
                }
            }
        }

        Text("Last button", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
        Text(
            "Press or hold a steering-wheel button with this screen open. Newest at the top.",
            style = MaterialTheme.typography.bodyLarge,
            color = dim,
        )
        Column(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
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
                    color = if (i == 0) MaterialTheme.colorScheme.onSurface else dim,
                )
            }
        }
    }
}
