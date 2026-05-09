package fi.kotinaytto.tv.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KotiDark = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D1B2A),
    secondary = Color(0xFF81D4FA),
    tertiary = Color(0xFFB39DDB),
    background = Color(0xFF050810),
    surface = Color(0xFF0B1220),
    onBackground = Color(0xFFE8EEF7),
    onSurface = Color(0xFFE8EEF7),
)

@Composable
fun KotiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KotiDark,
        typography = Typography(),
        content = content,
    )
}
