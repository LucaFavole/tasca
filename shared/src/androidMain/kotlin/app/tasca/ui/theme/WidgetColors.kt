package app.tasca.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import app.tasca.ui.Prefs

/** What a home-screen widget draws with, as ARGB ints: the app's colours in one of its light or dark versions. */
class WidgetColors(
    val background: Int, val text: Int, val subtext: Int, val income: Int, val expense: Int,
    val add: Int, val onAdd: Int, val voice: Int, val onVoice: Int,
)

/** The widget colours with the phone in light and in dark mode; the same twice when the settings fix one. */
fun widgetColors(context: Context, prefs: Prefs): Pair<WidgetColors, WidgetColors> {
    fun of(dark: Boolean): WidgetColors {
        val look = prefs.colors.look(dark, if (prefs.colors == ColorTheme.WALLPAPER) wallpaperLook(context, dark) else null)
        val back = when {
            prefs.colors.darkOnly -> look.ground
            dark -> look.high
            else -> look.lowest
        }
        return WidgetColors(
            back.copy(alpha = 0.9f).toArgb(), look.text.toArgb(), look.subtext.toArgb(),
            incomeColor(dark).toArgb(), expenseColor(dark).toArgb(),
            look.second.toArgb(), look.onSecond.toArgb(), look.accent.toArgb(), look.onAccent.toArgb(),
        )
    }
    val light = prefs.colors.isDark(prefs.theme, systemDark = false)
    val dark = prefs.colors.isDark(prefs.theme, systemDark = true)
    return of(light) to of(dark)
}
