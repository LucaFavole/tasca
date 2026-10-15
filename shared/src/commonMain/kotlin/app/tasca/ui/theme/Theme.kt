package app.tasca.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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

/** The colours picked in Settings › Appearance, each in a light and a dark version unless [darkOnly]. */
enum class ColorTheme(val label: String, val darkOnly: Boolean = false) {
    VIOLET("Violet"), PROFESSIONAL("Professional"), FOREST("Forest"), GRAPHITE("Graphite"),
    WALLPAPER("Wallpaper"), MIDNIGHT("Midnight", darkOnly = true),
}

data class MoneyColors(val income: Color, val expense: Color, val transfer: Color)

val LocalMoneyColors = staticCompositionLocalOf { MoneyColors(Color.Green, Color.Red, Color.Blue) }

/** The wallpaper's colours where the system offers them, else null. */
@Composable
internal expect fun wallpaperLook(dark: Boolean): Look?

/** Makes the status and navigation bar icons readable on the app's background when it is [dark] or light. */
@Composable
internal expect fun SystemBarIcons(dark: Boolean)

/** The few colours that make a theme; [ground] runs from the lowest surface up to the highest. */
internal class Look(
    val accent: Color, val onAccent: Color, val accentBox: Color, val onAccentBox: Color,
    val second: Color, val onSecond: Color, val secondBox: Color, val third: Color,
    val lowest: Color, val ground: Color, val low: Color, val mid: Color, val high: Color, val highest: Color, val variant: Color,
    val text: Color, val subtext: Color, val line: Color, val faintLine: Color,
) {
    fun scheme(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
        primary = accent, onPrimary = onAccent, primaryContainer = accentBox, onPrimaryContainer = onAccentBox,
        secondary = second, onSecondary = onSecond, secondaryContainer = secondBox, onSecondaryContainer = onAccentBox,
        tertiary = third, background = ground, onBackground = text, surface = ground, onSurface = text,
        surfaceVariant = variant, onSurfaceVariant = subtext, surfaceContainerLowest = lowest, surfaceContainerLow = low,
        surfaceContainer = mid, surfaceContainerHigh = high, surfaceContainerHighest = highest,
        outline = line, outlineVariant = faintLine, error = Color(0xFFFF6B6B),
    ) else lightColorScheme(
        primary = accent, onPrimary = onAccent, primaryContainer = accentBox, onPrimaryContainer = onAccentBox,
        secondary = second, onSecondary = onSecond, secondaryContainer = secondBox, onSecondaryContainer = onAccentBox,
        tertiary = third, background = ground, onBackground = text, surface = ground, onSurface = text,
        surfaceVariant = variant, onSurfaceVariant = subtext, surfaceContainerLowest = lowest, surfaceContainerLow = low,
        surfaceContainer = mid, surfaceContainerHigh = high, surfaceContainerHighest = highest,
        outline = line, outlineVariant = faintLine, error = Color(0xFFD93F3F),
    )
}

private fun c(argb: Long) = Color(argb)

private val VioletDark = Look(
    c(0xFF8B7CFF), c(0xFF14102E), c(0xFF2E2766), c(0xFFE4DFFF), c(0xFFFF8A65), c(0xFF2B1006), c(0xFF342D6E), c(0xFF4ECDC4),
    c(0xFF0A0B14), c(0xFF0E0F1A), c(0xFF151626), c(0xFF1A1B2D), c(0xFF222338), c(0xFF2B2C43), c(0xFF242538),
    c(0xFFE6E6F0), c(0xFFB3B4C8), c(0xFF4A4B63), c(0xFF2F3046),
)
private val VioletLight = Look(
    c(0xFF5B4BE0), Color.White, c(0xFFE4DFFF), c(0xFF1A1060), c(0xFFE8613C), Color.White, c(0xFFE4DFFF), c(0xFF14998F),
    Color.White, c(0xFFF7F6FC), c(0xFFF1F0F8), Color.White, c(0xFFEDEBF7), c(0xFFE5E3F1), c(0xFFE6E4F2),
    c(0xFF1B1B26), c(0xFF5A5A6E), c(0xFFB9B8CC), c(0xFFDCDAEA),
)
private val ProfessionalDark = Look(
    c(0xFF7FA7D9), c(0xFF0B1A2E), c(0xFF1E3550), c(0xFFD6E4F5), c(0xFFC9A961), c(0xFF241A05), c(0xFF25364A), c(0xFF6FB3A8),
    c(0xFF0A0E14), c(0xFF0F141B), c(0xFF151B24), c(0xFF1A212B), c(0xFF212934), c(0xFF29323E), c(0xFF232B36),
    c(0xFFE3E7ED), c(0xFFA9B2BF), c(0xFF46505D), c(0xFF2C3540),
)
private val ProfessionalLight = Look(
    c(0xFF1F4E79), Color.White, c(0xFFD6E4F5), c(0xFF0B2540), c(0xFF9C7A2E), Color.White, c(0xFFDDE6F0), c(0xFF2E7D73),
    Color.White, c(0xFFF5F7FA), c(0xFFEEF1F5), Color.White, c(0xFFE8ECF1), c(0xFFE0E5EC), c(0xFFE1E6ED),
    c(0xFF18202A), c(0xFF56606D), c(0xFFB4BCC7), c(0xFFD9DEE5),
)
private val ForestDark = Look(
    c(0xFF7CC59A), c(0xFF06210F), c(0xFF1E3D2B), c(0xFFCDEBD7), c(0xFFE0A96D), c(0xFF2A1604), c(0xFF263D30), c(0xFF8FB8D6),
    c(0xFF0A0F0C), c(0xFF0F1511), c(0xFF151C17), c(0xFF1A221C), c(0xFF212A23), c(0xFF29332B), c(0xFF242C26),
    c(0xFFE3E9E4), c(0xFFA8B5AB), c(0xFF46524A), c(0xFF2D3830),
)
private val ForestLight = Look(
    c(0xFF2E6B47), Color.White, c(0xFFCDEBD7), c(0xFF0A2716), c(0xFFB06A2C), Color.White, c(0xFFDCEADF), c(0xFF3C6E8F),
    Color.White, c(0xFFF6F7F2), c(0xFFEFF1EA), Color.White, c(0xFFE9ECE3), c(0xFFE1E5DA), c(0xFFE2E6DC),
    c(0xFF1A201B), c(0xFF58615A), c(0xFFB6BDB2), c(0xFFDADFD5),
)
private val GraphiteDark = Look(
    c(0xFFEDEDED), c(0xFF111111), c(0xFF333333), c(0xFFEDEDED), c(0xFFFF9F43), c(0xFF2A1400), c(0xFF2E2E2E), c(0xFF9E9E9E),
    c(0xFF0B0B0B), c(0xFF121212), c(0xFF181818), c(0xFF1E1E1E), c(0xFF252525), c(0xFF2E2E2E), c(0xFF282828),
    c(0xFFEDEDED), c(0xFFAAAAAA), c(0xFF4D4D4D), c(0xFF303030),
)
private val GraphiteLight = Look(
    c(0xFF1C1C1C), Color.White, c(0xFFE4E4E4), c(0xFF1C1C1C), c(0xFFE07A1F), Color.White, c(0xFFE8E8E8), c(0xFF6E6E6E),
    Color.White, c(0xFFF7F7F7), c(0xFFF0F0F0), Color.White, c(0xFFEAEAEA), c(0xFFE2E2E2), c(0xFFE4E4E4),
    c(0xFF1A1A1A), c(0xFF5E5E5E), c(0xFFBDBDBD), c(0xFFDDDDDD),
)
private val Midnight = Look(
    c(0xFF6FA8FF), c(0xFF001A40), c(0xFF132A4D), c(0xFFD6E6FF), c(0xFFFF8A65), c(0xFF2B1006), c(0xFF16233A), c(0xFF4ECDC4),
    Color.Black, Color.Black, c(0xFF0A0A0C), c(0xFF111114), c(0xFF18181C), c(0xFF202025), c(0xFF1A1A1F),
    c(0xFFE8E8EE), c(0xFFA0A0AC), c(0xFF3E3E48), c(0xFF24242A),
)

/** This theme's colours; [wallpaper] is used by the Wallpaper theme, which falls back to Violet without it. */
internal fun ColorTheme.look(dark: Boolean, wallpaper: Look?): Look = when (this) {
    ColorTheme.VIOLET -> if (dark) VioletDark else VioletLight
    ColorTheme.PROFESSIONAL -> if (dark) ProfessionalDark else ProfessionalLight
    ColorTheme.FOREST -> if (dark) ForestDark else ForestLight
    ColorTheme.GRAPHITE -> if (dark) GraphiteDark else GraphiteLight
    ColorTheme.WALLPAPER -> wallpaper ?: ColorTheme.VIOLET.look(dark, null)
    ColorTheme.MIDNIGHT -> Midnight
}

@Composable
fun ColorTheme.scheme(dark: Boolean): ColorScheme {
    val wallpaper = if (this == ColorTheme.WALLPAPER) wallpaperLook(dark) else null
    return look(dark, wallpaper).scheme(dark || darkOnly)
}

/** Whether the app is dark under [mode] when the phone is [systemDark]. */
fun ColorTheme.isDark(mode: ThemeMode, systemDark: Boolean) = darkOnly || when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

internal fun incomeColor(dark: Boolean) = if (dark) Color(0xFF3DDC97) else Color(0xFF12A36B)
internal fun expenseColor(dark: Boolean) = if (dark) Color(0xFFFF7A7A) else Color(0xFFD93F3F)

/** The colour themes this device can show. */
@Composable
fun availableColorThemes(): List<ColorTheme> {
    val wallpaper = wallpaperLook(false) != null
    return ColorTheme.entries.filter { it != ColorTheme.WALLPAPER || wallpaper }
}

@Composable
fun TascaTheme(mode: ThemeMode, colorTheme: ColorTheme = ColorTheme.VIOLET, content: @Composable () -> Unit) {
    val dark = colorTheme.isDark(mode, isSystemInDarkTheme())
    val colors = colorTheme.scheme(dark)
    val money = MoneyColors(incomeColor(dark), expenseColor(dark), colors.primary)
    SystemBarIcons(dark)
    androidx.compose.runtime.CompositionLocalProvider(LocalMoneyColors provides money) {
        MaterialTheme(
            colorScheme = colors,
            shapes = Shapes(
                small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(24.dp),
            ),
            content = content,
        )
    }
}
