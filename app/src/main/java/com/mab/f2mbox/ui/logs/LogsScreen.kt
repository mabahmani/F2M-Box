@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mab.f2mbox.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mab.f2mbox.R
import com.mab.f2mbox.data.CrashReporter
import com.mab.f2mbox.scraper.ScraperLog

/**
 * Full-screen viewer of the scraper engine's log ([ScraperLog]): every
 * connect attempt, fetch, cache hit and parse event of this session with
 * its timestamp. Auto-scrolls to the latest log line when updated, and allows
 * D-Pad / touch scrolling through all historical log items.
 */
@Composable
fun LogsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lines by ScraperLog.lines.collectAsState()
    val listState = rememberLazyListState()
    val backFocus = remember { FocusRequester() }
    val context = LocalContext.current
    // The uncaught exception that killed a previous session, kept on disk
    // until the user clears it — the TV's answer to logcat.
    var lastCrash by remember { mutableStateOf(CrashReporter.pending(context)) }

    // Auto-scroll to the bottom (last log) whenever new lines are added
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.scrollToItem(lines.lastIndex)
        }
    }
    LaunchedEffect(Unit) {
        backFocus.requestFocus()
    }

    // Logs are latin/urls while the app is RTL: force LTR like the
    // connect screen's log feed does.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier = modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onNavigateBack,
                    modifier = Modifier.focusRequester(backFocus)
                ) {
                    Text(stringResource(R.string.back))
                }
                Text(
                    text = stringResource(R.string.view_logs),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        ScraperLog.clear()
                        CrashReporter.clear(context)
                        lastCrash = null
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.clear_logs),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            lastCrash?.let { crash ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                            MaterialTheme.shapes.small,
                        )
                        .padding(12.dp)
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .focusable()
                ) {
                    Text(
                        text = stringResource(R.string.logs_last_crash),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = crash,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            if (lines.isEmpty()) {
                Text(
                    text = stringResource(R.string.logs_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    itemsIndexed(lines) { index, line ->
                        var isFocused by remember { mutableStateOf(false) }
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (isFocused || index == lines.lastIndex) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isFocused = it.isFocused }
                                .focusable()
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
