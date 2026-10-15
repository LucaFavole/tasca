package app.tasca.ui.theme

import android.R.color.*
import android.os.Build
import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Built from the wallpaper's tonal palettes rather than Material's dynamic scheme, whose dark containers can be
 * mid-grey and leave the red and green amounts unreadable. In the resource names 100 is a light tone, 900 a dark one.
 */
@Composable
internal actual fun wallpaperLook(dark: Boolean): Look? = wallpaperLook(LocalContext.current, dark)

internal fun wallpaperLook(context: Context, dark: Boolean): Look? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    fun c(id: Int) = Color(context.getColor(id))
    return if (dark) Look(
        c(system_accent1_200), c(system_accent1_800), c(system_accent1_700), c(system_accent1_100),
        c(system_accent3_200), c(system_accent3_800), c(system_accent2_700), c(system_accent3_300),
        lerp(Color.Black, c(system_neutral1_900), 0.4f), lerp(Color.Black, c(system_neutral1_900), 0.6f), c(system_neutral1_900),
        lerp(c(system_neutral1_900), c(system_neutral1_800), 0.2f), lerp(c(system_neutral1_900), c(system_neutral1_800), 0.6f),
        c(system_neutral1_800), c(system_neutral2_800),
        c(system_neutral1_100), c(system_neutral2_200), c(system_neutral2_400), c(system_neutral2_700),
    ) else Look(
        c(system_accent1_600), Color.White, c(system_accent1_100), c(system_accent1_900),
        c(system_accent3_600), Color.White, c(system_accent2_100), c(system_accent3_500),
        Color.White, c(system_neutral1_10), c(system_neutral1_50), Color.White,
        lerp(c(system_neutral1_50), c(system_neutral1_100), 0.5f), c(system_neutral1_100), c(system_neutral2_100),
        c(system_neutral1_900), c(system_neutral2_700), c(system_neutral2_400), c(system_neutral2_200),
    )
}

@Composable
internal actual fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
