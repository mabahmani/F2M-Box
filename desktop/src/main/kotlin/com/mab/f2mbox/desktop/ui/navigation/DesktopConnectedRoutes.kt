package com.mab.f2mbox.desktop.ui.navigation

import androidx.compose.runtime.Composable
import com.mab.f2mbox.data.remote.ScrapeResult
import com.mab.f2mbox.ui.home.HomeScreen
import com.mab.f2mbox.ui.list.ListScreen
import com.mab.f2mbox.desktop.ui.logs.LogsScreen
import com.mab.f2mbox.desktop.ui.player.PlayerScreen
import com.mab.f2mbox.navigation.F2mAppSession
import com.mab.f2mbox.navigation.Route

@Composable
fun DesktopConnectedRoutes(
    session: F2mAppSession,
    route: Route,
    onQuit: () -> Unit,
    registerEsc: ((() -> Unit)?) -> Unit,
) {
    when (val current = route) {
        Route.Home -> HomeScreen(
            scraper = session.scraper,
            initialHome = session.homeData,
            activeDomain = session.activeDomain,
            searchQuery = session.searchQuery,
            onSearchQueryChange = session.onSearchQueryChange,
            onOpenUrl = { session.navigate(Route.Scrape(it)) },
            onSearch = { session.navigate(Route.Results(it)) },
            onHomeLoaded = session.onHomeLoaded,
            onClearCache = session.clearCaches,
            onOpenLogs = { session.navigate(Route.Logs) },
            onExit = onQuit,
        )
        is Route.Scrape -> ListScreen(
            routeKey = current.url,
            fetchPage = { pageUrl -> session.scraper.scrape(pageUrl ?: current.url) },
            pageCache = session.pageCache,
            onNavigateBack = session.goBack,
            onOpenUrl = { session.navigate(Route.Scrape(it)) },
            onPlay = { url, title -> session.navigate(Route.Player(url, title)) },
            registerEsc = registerEsc,
        )
        is Route.Results -> ListScreen(
            routeKey = "search:${current.query}",
            fetchPage = { pageUrl ->
                pageUrl?.let { session.scraper.scrape(it) }
                    ?: session.scraper.searchList(current.query)
                        .map { ScrapeResult.Listing(it) }
            },
            pageCache = session.pageCache,
            onNavigateBack = session.goBack,
            onOpenUrl = { session.navigate(Route.Scrape(it)) },
            onPlay = { url, title -> session.navigate(Route.Player(url, title)) },
            registerEsc = registerEsc,
        )
        is Route.Player -> PlayerScreen(
            url = current.url,
            title = current.title,
            subtitleUrl = current.subtitleUrl,
            onNavigateBack = session.goBack,
            registerEsc = registerEsc,
        )
        Route.Logs -> LogsScreen(onNavigateBack = session.goBack)
    }
}