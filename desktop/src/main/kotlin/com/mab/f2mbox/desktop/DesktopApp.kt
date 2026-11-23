package com.mab.f2mbox.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.mab.f2mbox.data.PageCache
import com.mab.f2mbox.data.remote.HomeData
import com.mab.f2mbox.desktop.platform.Strings
import com.mab.f2mbox.desktop.ui.common.arrowKeyFocusNavigation
import com.mab.f2mbox.desktop.ui.common.backKeyHandler
import com.mab.f2mbox.desktop.ui.connect.ConnectState
import com.mab.f2mbox.desktop.ui.connect.SplashScreen
import com.mab.f2mbox.desktop.ui.connect.describe
import com.mab.f2mbox.desktop.ui.connect.isHomeHealthy
import com.mab.f2mbox.desktop.ui.home.HomeScreen
import com.mab.f2mbox.desktop.ui.list.ListScreen
import com.mab.f2mbox.desktop.ui.logs.LogsScreen
import com.mab.f2mbox.desktop.ui.player.PlayerScreen
import com.mab.f2mbox.scraper.F2MScraper
import com.mab.f2mbox.scraper.ScraperLog
import kotlinx.coroutines.delay

/**
 * Route structure and connect gate, ported one-to-one from the Android
 * `MainActivity`: the back stack, the saved-state scopes per route and the
 * retry/health loop are identical. Desktop differences: no Android Log
 * mirrors (ScraperLog is the source of truth), ESC/Backspace/Delete handle
 * Back, and the connect gate treats a home page without a hero slider as
 * unhealthy ([com.mab.f2mbox.desktop.ui.connect.isHomeHealthy]).
 */

/** Destinations reachable from the connected state. */
sealed interface Route {
    data object Home : Route

    /** Any scraped page: listing grid or movie detail, decided by the response type. */
    data class Scrape(val url: String) : Route

    /** Results of one search query, rendered like any listing. */
    data class Results(val query: String) : Route

    /** In-app playback of one direct video url. */
    data class Player(val url: String, val title: String) : Route

    /** Session log of the scraper engine, reached from the home screen. */
    data object Logs : Route
}

/**
 * Stable key of a route, used to keep its saved state while it is off
 * screen: the scroll position, the pagination and the focus position of a
 * route are restored when the user navigates back to it.
 */
private fun Route.stateKey(): String = when (this) {
    Route.Home -> "home"
    is Route.Scrape -> "scrape:$url"
    is Route.Results -> "results:$query"
    is Route.Player -> "player:$url"
    Route.Logs -> "logs"
}

/** How the connect gate reports its outcome. */
sealed interface ConnectOutcome {
    data class Ready(val home: HomeData, val domain: String?) : ConnectOutcome
    data class Failed(val message: String) : ConnectOutcome
}

/**
 * The connect loop: verifies a manually typed domain first, then retries
 * [maxAutoRetries] times with [retryDelayMs] between attempts. A "healthy"
 * home requires a parsed hero slider — see [isHomeHealthy].
 */
suspend fun connectFlow(
    scraper: F2MScraper,
    manualDomain: String?,
    maxAutoRetries: Int = 3,
    retryDelayMs: Long = 2_500L,
): ConnectOutcome {
    ScraperLog.clear()
    ScraperLog.log("connect attempt 0 starting")

    // Silent bootstrap refresh on every open — fire and forget.
    scraper.launchBootstrapRefresh()

    if (manualDomain != null) {
        val accepted = scraper.submitManualDomain(manualDomain)
        ScraperLog.log("connect: manual domain '$manualDomain' accepted=$accepted")
        if (!accepted) {
            return ConnectOutcome.Failed(Strings.errorDomainInvalid)
        }
    }

    var lastError: Throwable? = null
    for (i in 0 until maxAutoRetries) {
        if (i > 0) delay(retryDelayMs)
        scraper.home().fold(
            onSuccess = { home ->
                if (!isHomeHealthy(home)) {
                    ScraperLog.log(
                        "connect: attempt $i parsed a page with no hero slider → unhealthy",
                    )
                    lastError = IllegalStateException("home page has no hero slider")
                } else {
                    ScraperLog.log("connect: attempt $i succeeded ✓")
                    ScraperLog.log("connect: ready — hero slides: ${home.heroSlider.size}")
                    return ConnectOutcome.Ready(home, scraper.activeDomain())
                }
            },
            onFailure = { error ->
                ScraperLog.log("connect: attempt $i failed — ${error.message}")
                lastError = error
            }
        )
    }
    return ConnectOutcome.Failed(describe(lastError))
}

/**
 * Gates the app on the local scraper engine: stays loading until a
 * healthy home page (title + hero slider) has been fetched, auto-retries
 * a few times, then shows a retry action. Every step is logged.
 */
@Composable
fun DesktopApp(scraper: F2MScraper) {
    // ESC/Backspace/Delete go Back anywhere inside the connected app; the
    // splash handles its own actions and ignores them here (state != Ready).
    val rootModifier = Modifier.fillMaxSize()

    var state by remember { mutableStateOf<ConnectState>(ConnectState.Connecting) }
    var attempt by remember { mutableIntStateOf(0) }
    var route by remember { mutableStateOf<Route>(Route.Home) }
    // The logs screen opened from the error screen: the error state sits
    // outside the route system (it precedes the connected state), so the
    // logs are shown over the splash until the user backs out.
    var showingErrorLogs by remember { mutableStateOf(false) }
    // Screens visited before the current one, for the Back key. Home is the
    // root and never sits on the stack.
    val backStack = remember { mutableStateListOf<Route>() }
    // Hoisted so returning from results keeps the typed query.
    var searchQuery by remember { mutableStateOf("") }
    // Loaded only after the gate passes; null while connecting.
    var homeData by remember { mutableStateOf<HomeData?>(null) }
    // Domain the healthy home page was fetched from, shown on home.
    var activeDomain by remember { mutableStateOf<String?>(null) }
    // Domain typed on the error screen, consumed by the connect loop.
    var pendingManualDomain by remember { mutableStateOf<String?>(null) }
    // Keeps the saved state of a screen that is not on screen — scroll
    // position, pagination, expanded rows — and restores it on the way back.
    val stateHolder = rememberSaveableStateHolder()
    // Parsed pages of this session, so Back renders the screen we left with
    // the data it already had instead of re-fetching it.
    val pageCache = remember { PageCache() }
    // The visible screen's own Back/ESC handler — a listing screen pops one
    // page per ESC before the route goes back. Null = plain route back.
    var escHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
    // The screen that was just popped; its saved state is dropped once the
    // new route is on screen.
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

    // Drops the saved state of the popped screen. Runs after the new route
    // is on screen, so the holder is no longer tracking that key.
    LaunchedEffect(route) {
        poppedStateKey?.let { stateHolder.removeState(it) }
        poppedStateKey = null
    }

    val connect: suspend () -> Unit = {
        state = ConnectState.Connecting
        val manual = pendingManualDomain
        pendingManualDomain = null
        when (val outcome = connectFlow(scraper, manual)) {
            is ConnectOutcome.Ready -> {
                homeData = outcome.home
                activeDomain = outcome.domain
                state = ConnectState.Ready
            }
            is ConnectOutcome.Failed -> state = ConnectState.Failed(outcome.message)
        }
    }

    LaunchedEffect(attempt) { connect() }

    Box(
        modifier = rootModifier
            // Unconsumed arrow presses move the focus — the desktop
            // equivalent of Android's built-in D-pad focus search (see
            // [arrowKeyFocusNavigation]).
            .arrowKeyFocusNavigation()
            // Backspace/Delete go Back for presses no focused element
            // consumed (see [backKeyHandler]).
            .backKeyHandler(enabled = state == ConnectState.Ready) {
                (escHandler ?: goBack).invoke()
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && state == ConnectState.Ready) {
                    (escHandler ?: goBack).invoke()
                    true
                } else {
                    false
                }
            }
    ) {
        when (state) {
            ConnectState.Ready -> {
                // Every route gets its own saved-state scope, so a screen keeps
                // its scroll position, pagination and focus while it is off
                // screen and gets them back when the user returns to it.
                stateHolder.SaveableStateProvider(route.stateKey()) {
                    when (val current = route) {
                        Route.Home -> HomeScreen(
                            scraper = scraper,
                            initialHome = homeData,
                            activeDomain = activeDomain,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onOpenUrl = { navigate(Route.Scrape(it)) },
                            onSearch = { navigate(Route.Results(it)) },
                            // The home page the screen has loaded, kept here so
                            // the home screen comes back with the same data
                            // after a refresh.
                            onHomeLoaded = { homeData = it },
                            // Clearing the cache drops the cached pages too.
                            onClearCache = {
                                scraper.clearCache()
                                pageCache.clear()
                            },
                            onOpenLogs = { navigate(Route.Logs) },
                            onQuit = { exitProcessZero() }
                        )
                        is Route.Scrape -> ListScreen(
                            routeKey = current.url,
                            fetchPage = { pageUrl -> scraper.scrape(pageUrl ?: current.url) },
                            pageCache = pageCache,
                            onNavigateBack = goBack,
                            onOpenUrl = { navigate(Route.Scrape(it)) },
                            onPlay = { url, title -> navigate(Route.Player(url, title)) },
                            registerEsc = { escHandler = it }
                        )
                        is Route.Results -> ListScreen(
                            routeKey = "search:${current.query}",
                            fetchPage = { pageUrl ->
                                // Pagination links carry the ?s= query, so they
                                // classify as list pages; the first page uses the
                                // search builder.
                                pageUrl?.let { scraper.scrape(it) } ?: scraper.searchList(current.query)
                                    .map { com.mab.f2mbox.data.remote.ScrapeResult.Listing(it) }
                            },
                            pageCache = pageCache,
                            onNavigateBack = goBack,
                            onOpenUrl = { navigate(Route.Scrape(it)) },
                            onPlay = { url, title -> navigate(Route.Player(url, title)) },
                            registerEsc = { escHandler = it }
                        )
                        is Route.Player -> PlayerScreen(
                            url = current.url,
                            title = current.title,
                            onNavigateBack = goBack,
                            registerEsc = { escHandler = it }
                        )
                        Route.Logs -> LogsScreen(onNavigateBack = goBack)
                    }
                }
            }
            else -> {
                if (showingErrorLogs) {
                    LogsScreen(onNavigateBack = { showingErrorLogs = false })
                } else {
                    SplashScreen(
                        state = state,
                        onRetry = { attempt++ },
                        onManualDomain = { domain ->
                            state = ConnectState.Connecting
                            attempt++ // LaunchedEffect re-runs; the manual domain is picked up
                            pendingManualDomain = domain
                        },
                        onOpenLogs = { showingErrorLogs = true },
                        onQuit = { exitProcessZero() }
                    )
                }
            }
        }
    }
}

private fun exitProcessZero() {
    // Clean exit from the error screen's Back button.
    kotlin.system.exitProcess(0)
}

private const val TAG = "F2MBox"
