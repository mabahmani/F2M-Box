package com.mab.f2mbox.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
actual fun F2mButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape?,
    content: @Composable RowScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val highlighted = focused || hovered
    val scheme = MaterialTheme.colorScheme
    val buttonShape = shape ?: ButtonDefaults.shape
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .hoverable(interactionSource = interactionSource)
            .onFocusChanged { focused = it.isFocused },
        enabled = enabled,
        shape = buttonShape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (highlighted) scheme.primary else Color.Transparent,
            contentColor = if (highlighted) scheme.onPrimary else scheme.primary,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = scheme.onSurface.copy(alpha = 0.38f),
        ),
        contentPadding = ButtonDefaults.ContentPadding,
        border = BorderStroke(
            width = 1.dp,
            color = when {
                !enabled -> scheme.onSurface.copy(alpha = 0.2f)
                highlighted -> scheme.primary
                else -> scheme.primary.copy(alpha = 0.6f)
            },
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        content = content,
    )
}