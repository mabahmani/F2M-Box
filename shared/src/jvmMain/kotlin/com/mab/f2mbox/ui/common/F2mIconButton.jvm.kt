package com.mab.f2mbox.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
actual fun F2mIconButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    showRestingOutline: Boolean,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val outline = if (showRestingOutline) {
        Modifier.outlineWhenUnfocused(
            CircleShape,
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
        )
    } else {
        Modifier
    }
    IconButton(
        onClick = onClick,
        modifier = modifier.then(outline),
        enabled = enabled,
    ) {
        Box { content() }
    }
}

private fun Modifier.outlineWhenUnfocused(
    shape: Shape,
    color: Color,
    width: Dp = 1.dp,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this
        .onFocusChanged { focused = it.isFocused }
        .drawWithContent {
            drawContent()
            if (!focused) {
                drawOutline(
                    outline = shape.createOutline(size, layoutDirection, this),
                    color = color,
                    style = Stroke(width = width.toPx()),
                )
            }
        }
}