package com.mab.f2mbox.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

@Composable
actual fun rememberCardHighlight(onFocus: (() -> Unit)?): CardHighlight {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val modifier = Modifier.onFocusChanged {
        focused = it.isFocused
        if (it.isFocused) onFocus?.invoke()
    }
    return CardHighlight(
        highlighted = focused,
        interactionSource = interactionSource,
        modifier = modifier,
    )
}