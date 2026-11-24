package com.mab.f2mbox.ui.common

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Platform-styled icon control (TV icon button vs desktop outline icon button). */
@Composable
expect fun F2mIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showRestingOutline: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
)