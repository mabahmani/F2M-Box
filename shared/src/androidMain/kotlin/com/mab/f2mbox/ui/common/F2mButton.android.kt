@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mab.f2mbox.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button as M3Button
import androidx.compose.material3.ButtonDefaults as M3ButtonDefaults
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
    if (LocalUsesTouchInput.current) {
        TouchF2mButton(onClick, modifier, enabled, shape, content)
        return
    }
    TvF2mButton(onClick, modifier, enabled, shape, content)
}

@Composable
private fun TouchF2mButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape?,
    content: @Composable RowScope.() -> Unit,
) {
    M3Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape ?: M3ButtonDefaults.shape,
        colors = M3ButtonDefaults.buttonColors(
            containerColor = SurfaceNavyVariant.copy(alpha = 0.8f),
            contentColor = TextBright.copy(alpha = 0.8f),
            disabledContainerColor = SurfaceNavyVariant.copy(alpha = 0.4f),
            disabledContentColor = TextBright.copy(alpha = 0.4f),
        ),
        content = content,
    )
}

@Composable
private fun TvF2mButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape?,
    content: @Composable RowScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
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
            CompositionLocalProvider(LocalContentColor provides labelColor) {
                content()
            }
        },
    )
}