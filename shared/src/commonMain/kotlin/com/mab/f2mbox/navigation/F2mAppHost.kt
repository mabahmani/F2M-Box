package com.mab.f2mbox.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mab.f2mbox.connect.ConnectOutcome
import com.mab.f2mbox.connect.ConnectSplashStrings
import com.mab.f2mbox.connect.ConnectState
import com.mab.f2mbox.connect.connectFlow
import com.mab.f2mbox.data.PageCache
import com.mab.f2mbox.data.remote.HomeData
import com.mab.f2mbox.scraper.F2MScraper
import com.mab.f2mbox.ui.connect.SplashScreen

/**
 * Shared connect gate and in-app navigation shell. Platform code supplies
 * connected routes through [connected] and optional chrome via [modifier].
 */
@Composable
fun F2mAppHost(
    scraper: F2MScraper,
    splashStrings: ConnectSplashStrings,
    onSplashBack: () -> Unit,
    modifier: Modifier = Modifier,
    onConnectAttemptStart: (attempt: Int) -> Unit = {},
    onConnectReady: (attempt: Int) -> Unit = {},
    onConnectStateChange: (ConnectState) -> Unit = {},
    onRouteActive: (Route) -> Unit = {},
    onNavigateBackAvailable: (goBack: () -> Unit) -> Unit = {},
    errorLogs: @Composable (onNavigateBack: () -> Unit) -> Unit,
    connected: @Composable (F2mAppSession, Route) -> Unit,
) {
    var connectState by remember { mutableStateOf<ConnectState>(ConnectState.Connecting) }
    var attempt by remember { mutableIntStateOf(0) }
    var route by remember { mutableStateOf<Route>(Route.Home) }
    var showingErrorLogs by remember { mutableStateOf(false) }
    val backStack = remember { mutableStateListOf<Route>() }
    var searchQuery by remember { mutableStateOf("") }
    var homeData by remember { mutableStateOf<HomeData?>(null) }
    var activeDomain by remember { mutableStateOf<String?>(null) }
    var pendingManualDomain by remember { mutableStateOf<String?>(null) }
    val stateHolder = rememberSaveableStateHolder()
    val pageCache = remember { PageCache() }
    var poppedStateKey by remember { mutableStateOf<String?>(null) }

    val navigate: (Route) -> Unit = { target ->
        backStack.add(route)
        route = target
    }
    val goBack: () -> Unit = {
        val popped = route
        val previous = backStack.removeLastOrNull() ?: Route.Home
        if (previous != popped) {
            poppedStateKey = popped.stateKey()
            route = previous
        }
    }

    LaunchedEffect(route, connectState) {
        if (connectState != ConnectState.Ready) return@LaunchedEffect
        poppedStateKey?.let { stateHolder.removeState(it) }
        poppedStateKey = null
        onRouteActive(route)
    }

    LaunchedEffect(connectState) {
        onConnectStateChange(connectState)
    }

    if (connectState == ConnectState.Ready) {
        SideEffect { onNavigateBackAvailable(goBack) }
    }

    LaunchedEffect(attempt) {
        connectState = ConnectState.Connecting
        onConnectAttemptStart(attempt)
        val manual = pendingManualDomain
        pendingManualDomain = null
        when (val outcome = connectFlow(scraper, manual, attempt = attempt)) {
            is ConnectOutcome.Ready -> {
                homeData = outcome.home
                activeDomain = outcome.domain
                connectState = ConnectState.Ready
                onConnectReady(attempt)
            }
            is ConnectOutcome.Failed -> connectState = ConnectState.Failed(outcome.message)
        }
    }

    val session = F2mAppSession(
        scraper = scraper,
        pageCache = pageCache,
        homeData = homeData,
        onHomeLoaded = { homeData = it },
        activeDomain = activeDomain,
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        navigate = navigate,
        goBack = goBack,
        route = route,
        clearCaches = {
            scraper.clearCache()
            pageCache.clear()
        },
    )

    Box(modifier = modifier) {
        when (connectState) {
            ConnectState.Ready -> {
                stateHolder.SaveableStateProvider(route.stateKey()) {
                    connected(session, route)
                }
            }
            else -> {
                if (showingErrorLogs) {
                    errorLogs { showingErrorLogs = false }
                } else {
                    SplashScreen(
                        strings = splashStrings,
                        state = connectState,
                        onBack = onSplashBack,
                        onRetry = { attempt++ },
                        onManualDomain = { domain ->
                            connectState = ConnectState.Connecting
                            attempt++
                            pendingManualDomain = domain
                        },
                        onOpenLogs = { showingErrorLogs = true },
                    )
                }
            }
        }
    }
}

/** Read-only session context passed into connected route composables. */
data class F2mAppSession(
    val scraper: F2MScraper,
    val pageCache: PageCache,
    val homeData: HomeData?,
    val onHomeLoaded: (HomeData) -> Unit,
    val activeDomain: String?,
    val searchQuery: String,
    val onSearchQueryChange: (String) -> Unit,
    val navigate: (Route) -> Unit,
    val goBack: () -> Unit,
    val route: Route,
    val clearCaches: () -> Unit,
)