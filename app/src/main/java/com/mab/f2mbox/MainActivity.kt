@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mab.f2mbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.NonInteractiveSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.mab.f2mbox.data.F2MRepository
import com.mab.f2mbox.ui.connect.ConnectState
import com.mab.f2mbox.ui.connect.SplashScreen
import com.mab.f2mbox.ui.connect.describe
import com.mab.f2mbox.ui.home.HomeScreen
import com.mab.f2mbox.ui.theme.F2MBoxTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = F2MRepository()
        setContent {
            F2MBoxTheme {
                // The whole app is RTL by default: the content is Persian and
                // every layout mirrors (rows flow right-to-left, start = right).
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RectangleShape,
                        colors = NonInteractiveSurfaceDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    ) {
                        ConnectGate(repository = repository)
                    }
                }
            }
        }
    }
}

/**
 * Gates the app on the health check, surfaced through the splash screen:
 * auto-retries a few times (free-tier servers can be asleep), then shows
 * a retry action if the server never comes up.
 */
@Composable
private fun ConnectGate(repository: F2MRepository) {
    var state by remember { mutableStateOf<ConnectState>(ConnectState.Connecting) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        state = ConnectState.Connecting
        var lastError: Throwable? = null
        for (i in 0 until MAX_AUTO_RETRIES) {
            if (i > 0) delay(RETRY_DELAY_MS)
            repository.health().fold(
                onSuccess = {
                    state = ConnectState.Ready
                    return@LaunchedEffect
                },
                onFailure = { lastError = it }
            )
        }
        state = ConnectState.Failed(describe(lastError))
    }

    when (state) {
        ConnectState.Ready -> HomeScreen(repository = repository)
        else -> SplashScreen(
            state = state,
            onRetry = { attempt++ }
        )
    }
}

private const val MAX_AUTO_RETRIES = 3
private const val RETRY_DELAY_MS = 2_500L
