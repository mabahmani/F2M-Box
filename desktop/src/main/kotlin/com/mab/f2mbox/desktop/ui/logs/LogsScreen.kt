package com.mab.f2mbox.desktop.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import com.mab.f2mbox.desktop.ui.common.AppButton
import com.mab.f2mbox.desktop.ui.common.AppIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mab.f2mbox.desktop.platform.Strings
import com.mab.f2mbox.scraper.ScraperLog

/**
 * Full-screen viewer of the scraper engine's log ([ScraperLog]): every
 * connect attempt, fetch, cache hit and parse event of this session with
 * its timestamp. Auto-scrolls to the latest log line when updated.
 *
 * Desktop port: the Android build also shows the previous session's crash
 * from CrashReporter; that reporter is Android-specific and has no desktop
 * twin yet, so this screen shows the session log only.
 */
@Composable
fun LogsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lines by ScraperLog.lines.collectAsState()
    val listState = rememberLazyListState()
    val backFocus = remember { FocusRequester() }

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
                AppButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.focusRequester(backFocus)
                ) {
                    Text(Strings.back)
                }
                Text(
                    text = Strings.viewLogs,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                AppIconButton(
                    onClick = { ScraperLog.clear() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = Strings.clearLogs,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (lines.isEmpty()) {
                Text(
                    text = Strings.logsEmpty,
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
