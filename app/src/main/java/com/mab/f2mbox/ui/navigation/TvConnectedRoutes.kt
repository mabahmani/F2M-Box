package com.mab.f2mbox.ui.navigation

import androidx.compose.runtime.Composable
import com.mab.f2mbox.data.remote.ScrapeResult
import com.mab.f2mbox.navigation.F2mAppSession
import com.mab.f2mbox.navigation.Route
import com.mab.f2mbox.ui.home.HomeScreen
import com.mab.f2mbox.ui.list.ListScreen
import com.mab.f2mbox.ui.logs.LogsScreen
import com.mab.f2mbox.ui.player.PlayerScreen

@Composable
fun TvConnectedRoutes(session: F2mAppSession, route: Route) {
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
        )
        is Route.Scrape -> ListScreen(
            routeKey = current.url,
            fetchPage = { pageUrl -> session.scraper.scrape(pageUrl ?: current.url) },
            pageCache = session.pageCache,
            onNavigateBack = session.goBack,
            onOpenUrl = { session.navigate(Route.Scrape(it)) },
            onPlay = { url, title -> session.navigate(Route.Player(url, title)) },
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
        )
        is Route.Player -> PlayerScreen(
            url = current.url,
            title = current.title,
            onNavigateBack = session.goBack,
        )
        Route.Logs -> LogsScreen(onNavigateBack = session.goBack)
    }
}