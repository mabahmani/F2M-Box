package com.mab.f2mbox.ui.common

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * When true, primary navigation is touch (tablet/phone). TV keeps this false
 * so D-pad focus restoration and focusable scroll anchors stay enabled.
 */
val LocalUsesTouchInput = staticCompositionLocalOf { false }

@Composable
fun Modifier.optionalDpadFocus(
    highlight: CardHighlight,
    enabled: Boolean = true,
): Modifier {
    if (LocalUsesTouchInput.current || !enabled) return this
    return this
        .then(highlight.modifier)
        .focusable(interactionSource = highlight.interactionSource)
}

@Composable
fun Modifier.optionalDpadFocusable(): Modifier {
    if (LocalUsesTouchInput.current) return this
    return focusable()
}