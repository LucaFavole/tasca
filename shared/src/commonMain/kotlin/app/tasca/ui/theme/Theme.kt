package app.tasca.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class MoneyColors(val income: Color, val expense: Color, val transfer: Color)

val LocalMoneyColors = staticCompositionLocalOf { MoneyColors(Color.Green, Color.Red, Color.Blue) }

private val Dark = darkColorScheme(
    primary = Color(0xFF8B7CFF),
    onPrimary = Color(0xFF14102E),
    primaryContainer = Color(0xFF2E2766),
    onPrimaryContainer = Color(0xFFE4DFFF),
    secondary = Color(0xFFFF8A65),
    onSecondary = Color(0xFF2B1006),
    secondaryContainer = Color(0xFF342D6E),
    onSecondaryContainer = Color(0xFFE4DFFF),
    tertiary = Color(0xFF4ECDC4),
    background = Color(0xFF0E0F1A),
    onBackground = Color(0xFFE6E6F0),
    surface = Color(0xFF0E0F1A),
    onSurface = Color(0xFFE6E6F0),
    surfaceVariant = Color(0xFF242538),
    onSurfaceVariant = Color(0xFFB3B4C8),
    surfaceContainerLowest = Color(0xFF0A0B14),
    surfaceContainerLow = Color(0xFF151626),
    surfaceContainer = Color(0xFF1A1B2D),
    surfaceContainerHigh = Color(0xFF222338),
    surfaceContainerHighest = Color(0xFF2B2C43),
    outline = Color(0xFF4A4B63),
    outlineVariant = Color(0xFF2F3046),
    error = Color(0xFFFF6B6B),
)

private val Light = lightColorScheme(
    primary = Color(0xFF5B4BE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4DFFF),
    onPrimaryContainer = Color(0xFF1A1060),
    secondary = Color(0xFFE8613C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4DFFF),
    onSecondaryContainer = Color(0xFF1A1060),
    tertiary = Color(0xFF14998F),
    background = Color(0xFFF7F6FC),
    onBackground = Color(0xFF1B1B26),
    surface = Color(0xFFF7F6FC),
    onSurface = Color(0xFF1B1B26),
    surfaceVariant = Color(0xFFE6E4F2),
    onSurfaceVariant = Color(0xFF5A5A6E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F0F8),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDEBF7),
    surfaceContainerHighest = Color(0xFFE5E3F1),
    outline = Color(0xFFB9B8CC),
    outlineVariant = Color(0xFFDCDAEA),
    error = Color(0xFFD93F3F),
)

@Composable
fun TascaTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val money = if (dark) MoneyColors(Color(0xFF3DDC97), Color(0xFFFF7A7A), Color(0xFF8B7CFF))
    else MoneyColors(Color(0xFF12A36B), Color(0xFFD93F3F), Color(0xFF5B4BE0))
    androidx.compose.runtime.CompositionLocalProvider(LocalMoneyColors provides money) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            shapes = Shapes(
                small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(24.dp),
            ),
            content = content,
        )
    }
}
