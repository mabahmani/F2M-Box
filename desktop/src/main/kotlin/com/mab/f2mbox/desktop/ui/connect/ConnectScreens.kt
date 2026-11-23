package com.mab.f2mbox.desktop.ui.connect

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mab.f2mbox.desktop.platform.Strings
import com.mab.f2mbox.desktop.ui.common.ScraperLogFeed

/**
 * Splash screen: brand front and center the whole time, with the
 * connection status (loading dots / error + retry) anchored below.
 * Desktop port of the TV splash: the Android "Back" (finish activity)
 * becomes quit-app via [onQuit].
 */
@Composable
fun SplashScreen(
    state: ConnectState,
    onRetry: () -> Unit,
    onManualDomain: (String) -> Unit = {},
    onOpenLogs: () -> Unit = {},
    onQuit: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Brand on top, everything else below, all centered.
            Text(
                text = Strings.appName,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
            )

            when (state) {
                ConnectState.Connecting -> {
                    LoadingDots()
                    Spacer(Modifier.height(12.dp))
                    ScraperLogFeed()
                }

                is ConnectState.Failed -> CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Ltr
                ) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = onQuit) {
                            Text(Strings.back)
                        }
                        Button(onClick = onRetry) {
                            Text(Strings.retry)
                        }
                        Button(onClick = onOpenLogs) {
                            Text(Strings.viewLogs)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    ManualDomainInput(
                        enabled = true,
                        onSubmit = onManualDomain,
                    )
                }

                ConnectState.Ready -> Unit
            }
        }
    }
}

/**
 * Last-fallback input: the user types the site's current domain (no
 * scheme). Submission is validated locally and handed to [onSubmit];
 * [error] shows a local validation failure.
 */
@Composable
internal fun ManualDomainInput(
    enabled: Boolean,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

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
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = Strings.errorDomainHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { value ->
                    text = value
                    error = false
                },
                placeholder = { Text(Strings.errorDomainField) },
                singleLine = true,
                isError = error,
                enabled = enabled,
                modifier = Modifier.width(260.dp)
            )
            if (error) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = Strings.errorDomainInvalid,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = ::submit, enabled = enabled) {
                Text(Strings.errorDomainSubmit)
            }
        }
    }
}

/** Three brand-colored dots pulsing in sequence. */
@Composable
internal fun LoadingDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "loading")
    val dotColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.primary
    )
    val alphas = dotColors.mapIndexed { i, _ ->
        transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 550, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(i * 180)
            ),
            label = "dot$i"
        )
    }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        alphas.forEachIndexed { i, alpha ->
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(dotColors[i].copy(alpha = alpha.value))
            )
        }
    }
}
