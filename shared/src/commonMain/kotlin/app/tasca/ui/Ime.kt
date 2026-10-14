package app.tasca.ui

import androidx.compose.runtime.Composable

/** Whether the on-screen keyboard is showing, so the keypad can give it the room. */
@Composable
internal expect fun imeVisible(): Boolean
