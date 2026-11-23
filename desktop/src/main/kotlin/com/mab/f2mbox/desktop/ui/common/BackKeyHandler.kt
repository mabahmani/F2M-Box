package com.mab.f2mbox.desktop.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Makes Backspace/Delete act as Back, the desktop replacement for the
 * Android build's system Back gesture/key.
 *
 * Sits on the app root in the bubble phase — after every focused element
 * had the chance to handle the key itself. A focused text field consumes
 * Backspace for text deletion even when the field is empty, so typing in
 * the search field never triggers Back; the player consumes Backspace for
 * its own layered-back behavior. Only unconsumed presses reach this
 * handler, and the key is consumed when Back fires.
 */
@Composable
fun Modifier.backKeyHandler(
    enabled: Boolean = true,
    onBack: () -> Unit
): Modifier = onKeyEvent { event ->
    if (enabled &&
        event.type == KeyEventType.KeyDown &&
        (event.key == Key.Backspace || event.key == Key.Delete)
    ) {
        onBack()
        true
    } else {
        false
    }
}
