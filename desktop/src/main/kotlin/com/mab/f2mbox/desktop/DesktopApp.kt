package com.mab.f2mbox.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.mab.f2mbox.connect.ConnectState
import com.mab.f2mbox.desktop.platform.LocalWindowFullscreen
import com.mab.f2mbox.desktop.ui.common.arrowKeyFocusNavigation
import com.mab.f2mbox.desktop.ui.common.backKeyHandler
import com.mab.f2mbox.desktop.strings.desktopConnectSplashStrings
import com.mab.f2mbox.ui.logs.LogsScreen
import com.mab.f2mbox.navigation.F2mAppHost
import com.mab.f2mbox.navigation.F2mConnectedRoutes
import com.mab.f2mbox.navigation.Route
import com.mab.f2mbox.scraper.F2MScraper

/**
 * Route structure and connect gate, ported one-to-one from the Android
 * `MainActivity`: the back stack, the saved-state scopes per route and the
 * retry/health loop are identical. Desktop differences: ESC/Backspace/Delete
 * handle Back, and fullscreen exits when leaving the player.
 */
@Composable
fun DesktopApp(scraper: F2MScraper) {
    var shellReady by remember { mutableStateOf(false) }
    var escHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
    var routeGoBack by remember { mutableStateOf<(() -> Unit)?>(null) }
    val windowFullscreen = LocalWindowFullscreen.current

    val handleBack: () -> Unit = {
        (escHandler ?: routeGoBack)?.invoke()
        Unit
    }

    val rootModifier = Modifier
        .fillMaxSize()
        .arrowKeyFocusNavigation()
        .backKeyHandler(enabled = shellReady, onBack = handleBack)
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && shellReady) {
                handleBack()
                true
            } else {
                false
            }
        }

    Box(rootModifier) {
        F2mAppHost(
            scraper = scraper,
            splashStrings = desktopConnectSplashStrings(),
            onSplashBack = { exitProcessZero() },
            modifier = Modifier.fillMaxSize(),
            onConnectStateChange = { shellReady = it == ConnectState.Ready },
            onNavigateBackAvailable = { routeGoBack = it },
            onRouteActive = { route ->
                escHandler = null
                if (route !is Route.Player) {
                    windowFullscreen?.exit()
                }
            },
            errorLogs = { onBack -> LogsScreen(onNavigateBack = onBack) },
            connected = { session, route ->
                F2mConnectedRoutes(
                    session = session,
                    route = route,
                    onExit = { exitProcessZero() },
                    registerEsc = { escHandler = it },
                )
            },
        )
    }
}

private fun exitProcessZero() {
    kotlin.system.exitProcess(0)
}