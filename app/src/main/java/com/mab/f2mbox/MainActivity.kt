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
import com.mab.f2mbox.data.remote.HomeData
import com.mab.f2mbox.data.remote.ScrapeResult
import com.mab.f2mbox.scraper.BaseUrlResolver
import com.mab.f2mbox.scraper.F2MScraper
import com.mab.f2mbox.scraper.ScraperLog
import com.mab.f2mbox.scraper.SharedPreferencesStore
import com.mab.f2mbox.ui.connect.ConnectState
import com.mab.f2mbox.ui.connect.SplashScreen
import com.mab.f2mbox.ui.connect.describe
import com.mab.f2mbox.ui.home.HomeScreen
import com.mab.f2mbox.ui.list.ListScreen
import com.mab.f2mbox.ui.player.PlayerScreen
import com.mab.f2mbox.ui.theme.F2MBoxTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = F2MRepository()
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
                        ConnectGate(scraper = scraper, repository = repository)
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
}

/**
 * Gates the app on the local scraper engine: stays loading until a
 * healthy home page (title + hero slider) has been fetched, auto-retries
 * a few times, then shows a retry action. Every step is logged.
 */
@Composable
private fun ConnectGate(scraper: F2MScraper, repository: F2MRepository) {
    var state by remember { mutableStateOf<ConnectState>(ConnectState.Connecting) }
    var attempt by remember { mutableIntStateOf(0) }
    var route by remember { mutableStateOf<Route>(Route.Home) }
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

    val navigate: (Route) -> Unit = { target ->
        backStack.add(route)
        route = target
    }
    val goBack: () -> Unit = {
        route = backStack.removeLastOrNull() ?: Route.Home
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
        ConnectState.Ready -> when (val current = route) {
            Route.Home -> HomeScreen(
                repository = repository,
                initialHome = homeData,
                activeDomain = activeDomain,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onOpenUrl = { navigate(Route.Scrape(it)) },
                onSearch = { navigate(Route.Results(it)) }
            )
            is Route.Scrape -> ListScreen(
                routeKey = current.url,
                fetchPage = { pageUrl -> repository.scrape(pageUrl ?: current.url) },
                onNavigateBack = goBack,
                onOpenUrl = { navigate(Route.Scrape(it)) },
                onPlay = { url, title -> navigate(Route.Player(url, title)) }
            )
            is Route.Results -> ListScreen(
                routeKey = "search:${current.query}",
                fetchPage = { pageUrl ->
                    pageUrl?.let { repository.scrape(it) } ?: repository.search(current.query)
                        .map { ScrapeResult.Listing(it) }
                },
                onNavigateBack = goBack,
                onOpenUrl = { navigate(Route.Scrape(it)) },
                onPlay = { url, title -> navigate(Route.Player(url, title)) }
            )
            is Route.Player -> PlayerScreen(
                url = current.url,
                title = current.title,
                onNavigateBack = goBack
            )
        }
        else -> SplashScreen(
            state = state,
            onRetry = { attempt++ },
            onManualDomain = { domain ->
                state = ConnectState.Connecting
                attempt++ // LaunchedEffect re-runs resolve(); step 0 picks the domain up
                pendingManualDomain = domain
            }
        )
    }
}

private const val TAG = "F2MBox"
private const val MAX_AUTO_RETRIES = 3
private const val RETRY_DELAY_MS = 2_500L
