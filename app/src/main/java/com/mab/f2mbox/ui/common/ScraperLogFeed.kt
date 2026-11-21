@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mab.f2mbox.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mab.f2mbox.scraper.ScraperLog

/**
 * Live feed of the scraper engine's log (fetches with their full urls,
 * config fetches, health checks, …) shown while a screen is loading.
 * Forced LTR — log lines are latin/urls while the app is RTL — and wide
 * enough that urls wrap instead of being cut off.
 */
@Composable
fun ScraperLogFeed(modifier: Modifier = Modifier) {
    val lines by ScraperLog.lines.collectAsState()
    val visible = lines.takeLast(7)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier
                .fillMaxWidth(0.82f)
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState())
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = MaterialTheme.shapes.small,
                )
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            visible.forEachIndexed { index, line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelSmall,
                    // The newest line stands out; older ones stay dim.
                    color = if (index == visible.lastIndex) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
