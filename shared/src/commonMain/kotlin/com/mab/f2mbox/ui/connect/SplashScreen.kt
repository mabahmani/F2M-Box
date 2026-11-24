package com.mab.f2mbox.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mab.f2mbox.connect.ConnectSplashStrings
import com.mab.f2mbox.connect.ConnectState
import com.mab.f2mbox.ui.common.F2mButton
import com.mab.f2mbox.ui.common.ScraperLogFeed
import com.mab.f2mbox.ui.common.rememberTvKeyboardGateState

/**
 * Splash while the scraper connects. [onBack] exits the app on desktop
 * or finishes the activity on TV.
 */
@Composable
fun SplashScreen(
    strings: ConnectSplashStrings,
    state: ConnectState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onManualDomain: (String) -> Unit = {},
    onOpenLogs: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = strings.appName,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            when (state) {
                ConnectState.Connecting -> {
                    LoadingDots()
                    Spacer(Modifier.height(12.dp))
                    ScraperLogFeed()
                }

                is ConnectState.Failed -> CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Ltr,
                ) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        F2mButton(onClick = onBack) {
                            Text(strings.back)
                        }
                        F2mButton(onClick = onRetry) {
                            Text(strings.retry)
                        }
                        F2mButton(onClick = onOpenLogs) {
                            Text(strings.viewLogs)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    ManualDomainInput(
                        strings = strings,
                        enabled = true,
                        onSubmit = onManualDomain,
                    )
                }

                ConnectState.Ready -> Unit
            }
        }
    }
}

@Composable
internal fun ManualDomainInput(
    strings: ConnectSplashStrings,
    enabled: Boolean,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val keyboardGate = rememberTvKeyboardGateState()

    fun submit() {
        val value = text.trim()
        if (!value.contains('.') || value.any { it.isWhitespace() }) {
            error = true
            return
        }
        error = false
        onSubmit(value.removePrefix("https://").removePrefix("http://").trimEnd('/'))
    }

    Column(
        modifier = modifier.widthIn(max = 420.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = strings.domainHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { value ->
                    text = value
                    error = false
                },
                placeholder = { Text(strings.domainFieldPlaceholder) },
                singleLine = true,
                isError = error,
                enabled = enabled,
                readOnly = keyboardGate.readOnly,
                modifier = Modifier
                    .width(260.dp)
                    .then(keyboardGate.modifier),
            )
            if (error) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = strings.domainInvalidLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.width(12.dp))
            F2mButton(onClick = ::submit, enabled = enabled) {
                Text(strings.domainSubmit)
            }
        }
    }
}