package com.mab.f2mbox.desktop.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Desktop replacement for the arrow-key focus navigation the Android build
 * gets for free: on Android, `AndroidComposeView` turns an unconsumed
 * arrow press into a directional focus move; on desktop, Compose maps only
 * Tab/Enter/Back to focus movement and hands every other key to the
 * focused element — so the D-pad-style navigation of this TV UI (Up/Down
 * between the hero, the chips and the poster rows, Left/Right between
 * posters) never happens and the arrows are dead keys.
 *
 * This modifier sits on the app root and performs the missing bridge in
 * the bubble phase — after every focused element had the chance to handle
 * the key itself (the hero's slide navigation, the search field's cursor
 * movement, the player's seek keys). The key is consumed only when the
 * focus actually moved, so anything else keeps its default behavior.
 */
@Composable
fun Modifier.arrowKeyFocusNavigation(
    focusManager: FocusManager = LocalFocusManager.current
): Modifier = onKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
    when (event.key) {
        Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
        Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
        Key.DirectionLeft -> focusManager.moveFocus(FocusDirection.Left)
        Key.DirectionRight -> focusManager.moveFocus(FocusDirection.Right)
        else -> false
    }
}
