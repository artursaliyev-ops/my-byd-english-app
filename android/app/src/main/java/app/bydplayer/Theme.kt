package app.bydplayer

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

private val Colors = darkColorScheme(
    primary = Color(0xFF7CC4FF),
    onPrimary = Color(0xFF00203A),
    background = Color(0xFF101214),
    onBackground = Color(0xFFE6E8EA),
    surface = Color(0xFF1A1D21),
    onSurface = Color(0xFFE6E8EA),
)

// Larger than phone defaults: the screen is read at arm's length in a moving car.
private val Type = Typography(
    headlineMedium = TextStyle(fontSize = 34.sp),
    titleLarge = TextStyle(fontSize = 28.sp),
    bodyLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp),
    labelLarge = TextStyle(fontSize = 22.sp),
)

@Composable
fun BydTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}
