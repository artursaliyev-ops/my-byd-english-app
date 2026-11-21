package app.bydplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
 * Settings: the Button mode for the media buttons, and a **Last button** readout of the
 * signals the app received, so the user can see in the car what the steering wheel sends.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    var mode by remember { mutableStateOf(store.buttonMode()) }
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
