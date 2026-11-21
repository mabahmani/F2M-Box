@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mab.f2mbox

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.NonInteractiveSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.mab.f2mbox.data.PageCache
import com.mab.f2mbox.data.remote.HomeData
import com.mab.f2mbox.scraper.BaseUrlResolver
import com.mab.f2mbox.scraper.F2MScraper
import com.mab.f2mbox.scraper.HtmlCache
import com.mab.f2mbox.scraper.ScraperLog
import com.mab.f2mbox.scraper.SharedPreferencesStore
import com.mab.f2mbox.ui.connect.ConnectState
import com.mab.f2mbox.ui.connect.SplashScreen
import com.mab.f2mbox.ui.connect.describe
import com.mab.f2mbox.ui.home.HomeScreen
import com.mab.f2mbox.ui.list.ListScreen
import com.mab.f2mbox.ui.logs.LogsScreen
import com.mab.f2mbox.ui.player.PlayerScreen
import com.mab.f2mbox.ui.theme.F2MBoxTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The html cache lives for one app open — drop whatever a previous
        // open left behind (the process can survive between launches).
        HtmlCache.clear()
        val resolver = BaseUrlResolver(BaseUrlResolver.KeyValueStorage(SharedPreferencesStore(this)))
        val scraper = F2MScraper(resolver = resolver)
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
                        ConnectGate(scraper = scraper)
                    }
                }
            }
        }
    }
}

/** Destinations reachable from the connected state. */
private sealed interface Route {
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

/**
 * Gates the app on the local scraper engine: stays loading until a
 * healthy home page (title + hero slider) has been fetched, auto-retries
 * a few times, then shows a retry action. Every step is logged.
 */
@Composable
private fun ConnectGate(scraper: F2MScraper) {
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

    LaunchedEffect(attempt) {
        state = ConnectState.Connecting
        ScraperLog.clear()
        ScraperLog.log("connect attempt $attempt starting")
        Log.i(TAG, "connect attempt $attempt starting")

        // Silent bootstrap refresh on every open — fire and forget.
        scraper.launchBootstrapRefresh()

        // A domain typed on the error screen is verified first: reject
        // it directly (back to Failed) or continue the normal chain.
        val manual = pendingManualDomain
        pendingManualDomain = null
        if (manual != null) {
            val accepted = scraper.submitManualDomain(manual)
            ScraperLog.log("connect: manual domain '$manual' accepted=$accepted")
            Log.i(TAG, "connect: manual domain '$manual' accepted=$accepted")
            if (!accepted) {
                state = ConnectState.Failed(R.string.error_domain_invalid)
                return@LaunchedEffect
            }
        }

        var lastError: Throwable? = null
        for (i in 0 until MAX_AUTO_RETRIES) {
            if (i > 0) delay(RETRY_DELAY_MS)
            scraper.home().fold(
                onSuccess = { home ->
                    // "Success" means a healthy page: parsing alone can
                    // succeed on an error/parked page. Without the hero
                    // slider the site is not really reachable.
                    if (home.heroSlider.isEmpty()) {
                        ScraperLog.log(
                            "connect: attempt $i parsed a page with no hero slider → unhealthy",
                        )
                        lastError = IllegalStateException("home page has no hero slider")
                    } else {
                        ScraperLog.log("connect: attempt $i succeeded ✓")
                        ScraperLog.log("connect: ready — hero slides: ${home.heroSlider.size}")
                        Log.i(TAG, "connect attempt $i: home page healthy ✓")
                        homeData = home
                        activeDomain = scraper.activeDomain()
                        state = ConnectState.Ready
                        return@LaunchedEffect
                    }
                },
                onFailure = { error ->
                    ScraperLog.log("connect: attempt $i failed — ${error.message}")
                    Log.w(TAG, "connect attempt $i failed: ${error.message}")
                    lastError = error
                }
            )
        }
        state = ConnectState.Failed(describe(lastError))
    }

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
                        onOpenLogs = { navigate(Route.Logs) }
                    )
                    is Route.Scrape -> ListScreen(
                        routeKey = current.url,
                        fetchPage = { pageUrl -> scraper.scrape(pageUrl ?: current.url) },
                        pageCache = pageCache,
                        onNavigateBack = goBack,
                        onOpenUrl = { navigate(Route.Scrape(it)) },
                        onPlay = { url, title -> navigate(Route.Player(url, title)) }
                    )
                    is Route.Results -> ListScreen(
                        routeKey = "search:${current.query}",
                        fetchPage = { pageUrl ->
                            // Pagination links carry the ?s= query, so they classify
                            // as list pages; the first page uses the search builder.
                            pageUrl?.let { scraper.scrape(it) } ?: scraper.searchList(current.query)
                                .map { com.mab.f2mbox.data.remote.ScrapeResult.Listing(it) }
                        },
                        pageCache = pageCache,
                        onNavigateBack = goBack,
                        onOpenUrl = { navigate(Route.Scrape(it)) },
                        onPlay = { url, title -> navigate(Route.Player(url, title)) }
                    )
                    is Route.Player -> PlayerScreen(
                        url = current.url,
                        title = current.title,
                        onNavigateBack = goBack
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
                        attempt++ // LaunchedEffect re-runs resolve(); step 0 picks the domain up
                        pendingManualDomain = domain
                    },
                    onOpenLogs = { showingErrorLogs = true }
                )
            }
        }
    }
}

private const val TAG = "F2MBox"
private const val MAX_AUTO_RETRIES = 3
private const val RETRY_DELAY_MS = 2_500L
