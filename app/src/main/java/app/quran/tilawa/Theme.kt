package app.quran.tilawa

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

/** خط "أميري قرآن" المصمم خصيصاً للرسم العثماني وعلامات الوقف وأرقام الآيات */
val QuranFont = FontFamily(Font(R.font.amiri_quran))

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E5A3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EADF),
    onPrimaryContainer = Color(0xFF06301F),
    secondary = Color(0xFFA67C1E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E7BF),
    onSecondaryContainer = Color(0xFF3D2E05),
    background = Color(0xFFFBF7EE),
    onBackground = Color(0xFF1E1C17),
    surface = Color(0xFFFFFDF8),
    onSurface = Color(0xFF1E1C17),
    surfaceVariant = Color(0xFFF1EADB),
    onSurfaceVariant = Color(0xFF5B5446),
    outline = Color(0xFFD8CBAE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF0E5A3A),
    onPrimary = Color(0xFFEFF7F2),
    primaryContainer = Color(0xFF1E4B37),
    onPrimaryContainer = Color(0xFFD6EADF),
    secondary = Color(0xFFE2C26E),
    onSecondary = Color(0xFF3D2E05),
    secondaryContainer = Color(0xFF4E3C12),
    onSecondaryContainer = Color(0xFFF6E7BF),
    background = Color(0xFF121410),
    onBackground = Color(0xFFE9E4D8),
    surface = Color(0xFF1A1D17),
    onSurface = Color(0xFFE9E4D8),
    surfaceVariant = Color(0xFF262A22),
    onSurfaceVariant = Color(0xFFBDB6A6),
    outline = Color(0xFF45493F),
)

@Composable
fun TilawaTheme(themeMode: Int = 0, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
