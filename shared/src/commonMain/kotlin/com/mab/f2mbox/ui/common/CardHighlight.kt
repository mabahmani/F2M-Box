package com.mab.f2mbox.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Focus and (on desktop) hover highlight for poster rows and chips. */
class CardHighlight internal constructor(
    val highlighted: Boolean,
    val interactionSource: MutableInteractionSource,
    val modifier: Modifier,
)

@Composable
expect fun rememberCardHighlight(onFocus: (() -> Unit)? = null): CardHighlight