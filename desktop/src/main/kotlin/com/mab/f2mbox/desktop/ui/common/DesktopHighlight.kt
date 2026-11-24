package com.mab.f2mbox.desktop.ui.common

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

/**
 * Keyboard focus and mouse hover share one highlight state so desktop cards
 * and chips look the same under the pointer as under the D-pad.
 */
class DesktopHighlight internal constructor(
    val highlighted: Boolean,
    val interactionSource: MutableInteractionSource,
    val modifier: Modifier,
)

/**
 * @param onFocus runs only when the control takes keyboard focus, not on hover.
 */
@Composable
fun rememberDesktopHighlight(onFocus: (() -> Unit)? = null): DesktopHighlight {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val modifier = Modifier
        .hoverable(interactionSource = interactionSource)
        .onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onFocus?.invoke()
        }
    return DesktopHighlight(
        highlighted = focused || hovered,
        interactionSource = interactionSource,
        modifier = modifier,
    )
}