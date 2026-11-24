@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mab.f2mbox.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.IconButton as TvIconButton

@Composable
actual fun F2mIconButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    showRestingOutline: Boolean,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    if (LocalUsesTouchInput.current) {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            Box { content() }
        }
        return
    }
    TvIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        content = content,
    )
}