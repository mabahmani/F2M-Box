package com.mab.f2mbox.desktop.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

/**
 * Resting outline for the small icon-only controls.
 *
 * The desktop port is D-pad/mouse driven, so a control shows a fill or a
 * scale when it takes focus. That leaves icon buttons with no affordance at
 * rest — on the dark theme an unfocused icon looks like decoration. This
 * modifier draws a 1 dp ring while the control is *not* focused and drops it
 * once the focus treatment takes over.
 *
 * The ring is painted on top of the content (via [drawWithContent]) so it
 * stays visible over a transparent surface. (Filled buttons use the Material
 * `border` parameter instead — see [AppButton].)
 */
fun Modifier.outlineWhenUnfocused(
    shape: Shape,
    color: Color,
    width: Dp = 1.dp,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this
        // `isFocused` works when this modifier precedes the button's own
        // focus target in the same chain (the Material IconButton layout),
        // which is the case for [AppIconButton].
        .onFocusChanged { focused = it.isFocused }
        .drawWithContent {
            drawContent()
            if (!focused) {
                drawOutline(
                    outline = shape.createOutline(size, layoutDirection, this),
                    color = color,
                    style = Stroke(width = width.toPx())
                )
            }
        }
}

/**
 * The desktop button style: a transparent, outline-only control.
 *
 * At rest it has no fill — just a 1 dp brand-coloured outline and brand text,
 * so a screen full of buttons stays calm on the dark theme. When it takes
 * focus the container fills with the brand colour and the text flips to the
 * on-primary tone, which is the focus cue on this D-pad/mouse desktop port.
 *
 * Drop-in replacement for `Button` across the desktop screens (the player
 * keeps its own transport buttons and is exempt).
 */
@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val highlighted = focused || hovered
    val scheme = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        // The border is a Material Surface parameter, so it is drawn at the
        // button's true bounds (a draw modifier would land on the outer
        // minimum-touch-target box instead).
        modifier = modifier
            .hoverable(interactionSource = interactionSource)
            .onFocusChanged { focused = it.isFocused },
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (highlighted) scheme.primary else Color.Transparent,
            contentColor = if (highlighted) scheme.onPrimary else scheme.primary,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = scheme.onSurface.copy(alpha = 0.38f)
        ),
        contentPadding = contentPadding,
        border = BorderStroke(
            width = 1.dp,
            color = when {
                !enabled -> scheme.onSurface.copy(alpha = 0.2f)
                highlighted -> scheme.primary
                else -> scheme.primary.copy(alpha = 0.6f)
            }
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        content = content
    )
}

/**
 * A Material [IconButton] that shows a resting circular outline while
 * unfocused, so the small icon-only controls stay discoverable at rest.
 */
@Composable
fun AppIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.outlineWhenUnfocused(
            CircleShape,
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        ),
        enabled = enabled,
        content = content
    )
}
