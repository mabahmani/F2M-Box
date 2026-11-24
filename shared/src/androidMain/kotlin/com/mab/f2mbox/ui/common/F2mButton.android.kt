package com.mab.f2mbox.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Outline-only at rest; fills with the brand colour on TV D-pad focus (same
 * visual language as desktop). Uses Material 3 so shared [Text] labels match.
 */
@Composable
actual fun F2mButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape?,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val scheme = MaterialTheme.colorScheme
    val buttonShape = shape ?: ButtonDefaults.shape
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = buttonShape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (focused) scheme.primary else Color.Transparent,
            contentColor = if (focused) scheme.onPrimary else scheme.primary,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = scheme.onSurface.copy(alpha = 0.38f),
        ),
        contentPadding = ButtonDefaults.ContentPadding,
        border = BorderStroke(
            width = 1.dp,
            color = when {
                !enabled -> scheme.onSurface.copy(alpha = 0.2f)
                focused -> scheme.primary
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