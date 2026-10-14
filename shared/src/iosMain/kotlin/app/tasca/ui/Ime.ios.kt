package app.tasca.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity

// To verify on a Mac: the keypad hides while the keyboard is up, as on Android.
@Composable
internal actual fun imeVisible(): Boolean = WindowInsets.ime.getBottom(LocalDensity.current) > 0
