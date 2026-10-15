package app.tasca.ui.theme

import androidx.compose.runtime.Composable

/** iOS gives apps no wallpaper colours, so Settings leaves the Wallpaper theme out. */
@Composable
internal actual fun wallpaperLook(dark: Boolean): Look? = null

// To verify on a Mac: the status bar follows the phone's own light or dark setting, so Light, Dark or Midnight may clash with it.
@Composable
internal actual fun SystemBarIcons(dark: Boolean) = Unit
