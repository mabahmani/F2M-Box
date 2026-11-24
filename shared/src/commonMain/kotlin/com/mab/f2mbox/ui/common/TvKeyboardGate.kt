package com.mab.f2mbox.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * On TV, text fields stay [TvKeyboardGateState.readOnly] until the user presses
 * OK/Enter so the IME does not open on focus. Touch and desktop edit immediately.
 */
@Composable
expect fun rememberDeferKeyboardUntilConfirm(): Boolean

class TvKeyboardGateState(
    val readOnly: Boolean,
    val modifier: Modifier,
)

@Composable
fun rememberTvKeyboardGateState(): TvKeyboardGateState {
    val defer = rememberDeferKeyboardUntilConfirm()
    var keyboardOpen by remember(defer) { mutableStateOf(!defer) }
    val keyboard = LocalSoftwareKeyboardController.current
    val readOnly = defer && !keyboardOpen
    val modifier = Modifier
        .onFocusChanged { state ->
            if (defer && !state.isFocused) {
                keyboardOpen = false
                keyboard?.hide()
            }
        }
        .onPreviewKeyEvent { event -> handleTvKeyboardConfirmKey(event, defer, keyboardOpen) {
            keyboardOpen = true
            keyboard?.show()
        } }
    return TvKeyboardGateState(readOnly = readOnly, modifier = modifier)
}

private fun handleTvKeyboardConfirmKey(
    event: KeyEvent,
    defer: Boolean,
    keyboardOpen: Boolean,
    openKeyboard: () -> Unit,
): Boolean {
    if (!defer || event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
            if (!keyboardOpen) {
                openKeyboard()
                true
            } else {
                false
            }
        }
        else -> false
    }
}