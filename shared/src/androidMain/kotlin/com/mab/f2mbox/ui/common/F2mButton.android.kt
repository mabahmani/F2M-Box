@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mab.f2mbox.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.mab.f2mbox.ui.theme.BgNavy
import com.mab.f2mbox.ui.theme.SurfaceNavyVariant
import com.mab.f2mbox.ui.theme.TextBright

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
    // Pin resting colors to brand tokens — shared UI sits under nested M3 theme
    // and default tv button colors were easy to misread as the wrong unfocused fill.
    val colors = ButtonDefaults.colors(
        containerColor = SurfaceNavyVariant.copy(alpha = 0.8f),
        contentColor = TextBright.copy(alpha = 0.8f),
        focusedContainerColor = TextBright,
        focusedContentColor = BgNavy,
        disabledContainerColor = SurfaceNavyVariant.copy(alpha = 0.4f),
        disabledContentColor = TextBright.copy(alpha = 0.4f),
    )
    val labelColor = when {
        !enabled -> TextBright.copy(alpha = 0.4f)
        focused -> BgNavy
        else -> TextBright.copy(alpha = 0.8f)
    }
    Button(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        enabled = enabled,
        interactionSource = interactionSource,
        shape = if (shape != null) {
            ButtonDefaults.shape(shape = shape)
        } else {
            ButtonDefaults.shape()
        },
        colors = colors,
        content = {
            // Shared labels use compose Material 3 Text (M3 locals), not tv Text.
            CompositionLocalProvider(LocalContentColor provides labelColor) {
                content()
            }
        },
    )
}