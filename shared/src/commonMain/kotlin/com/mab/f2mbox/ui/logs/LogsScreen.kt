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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import com.mab.f2mbox.ui.common.F2mLazyColumn
import com.mab.f2mbox.ui.common.F2mVerticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
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
import com.mab.f2mbox.scraper.ScraperLog
import com.mab.f2mbox.ui.common.F2mButton
import com.mab.f2mbox.ui.common.F2mIconButton
import com.mab.f2mbox.ui.common.LocalUsesTouchInput
import com.mab.f2mbox.ui.common.RequestInitialFocusOnEnter
import com.mab.f2mbox.ui.common.optionalDpadFocusable
import com.mab.f2mbox.ui.home.LocalHomeStrings

@Composable
fun LogsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalHomeStrings.current
    val crashStore = LocalSessionCrashStore.current
    val lines by ScraperLog.lines.collectAsState()
    val listState = rememberLazyListState()
    val crashScrollState = rememberScrollState()
    val backFocus = remember { FocusRequester() }
    var lastCrash by remember { mutableStateOf(crashStore?.pending()) }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.scrollToItem(lines.lastIndex)
        }
    }
    RequestInitialFocusOnEnter { backFocus.requestFocus() }
    val usesTouch = LocalUsesTouchInput.current

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier = modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                F2mButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.focusRequester(backFocus),
                ) {
                    Text(strings.back)
                }
                Text(
                    text = strings.viewLogs,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.weight(1f))
                F2mIconButton(
                    onClick = {
                        ScraperLog.clear()
                        crashStore?.clear()
                        lastCrash = null
                    },
                    modifier = Modifier.size(36.dp),
                    showRestingOutline = false,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = strings.clearLogs,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            lastCrash?.let { crash ->
                F2mVerticalScroll(
                    state = crashScrollState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                            MaterialTheme.shapes.small,
                        )
                        .padding(12.dp)
                        .heightIn(max = 280.dp)
                        .optionalDpadFocusable(),
                ) {
                    Text(
                        text = strings.logsLastCrash,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = crash,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            if (lines.isEmpty()) {
                Text(
                    text = strings.logsEmpty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            } else {
                F2mLazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    itemsIndexed(lines) { index, line ->
                        var isFocused by remember { mutableStateOf(false) }
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = if ((!usesTouch && isFocused) || index == lines.lastIndex) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (usesTouch) {
                                        Modifier
                                    } else {
                                        Modifier
                                            .onFocusChanged { isFocused = it.isFocused }
                                            .focusable()
                                    },
                                )
                                .padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}