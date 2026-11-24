package com.mab.f2mbox.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mab.f2mbox.scraper.ScraperLog

/**
 * Live feed of the scraper engine's log shown while a screen is loading.
 * Forced LTR so URLs stay readable in the RTL app shell.
 */
@Composable
fun ScraperLogFeed(modifier: Modifier = Modifier) {
    val lines by ScraperLog.lines.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(lines.size, scrollState.maxValue) {
        if (lines.isNotEmpty()) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier
                .fillMaxWidth(0.82f)
                .heightIn(max = 220.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = MaterialTheme.shapes.small,
                )
                .verticalScroll(scrollState)
                .focusable()
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            lines.forEachIndexed { index, line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == lines.lastIndex) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}