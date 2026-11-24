package com.mab.f2mbox.navigation

/** Destinations reachable from the connected state. */
sealed interface Route {
    data object Home : Route

    /** Any scraped page: listing grid or movie detail, decided by the response type. */
    data class Scrape(val url: String) : Route

    /** Results of one search query, rendered like any listing. */
    data class Results(val query: String) : Route

    /** In-app playback of one direct video url, with an optional SRT subtitle URL or path. */
    data class Player(
        val url: String,
        val title: String,
        val subtitleUrl: String? = null,
    ) : Route

    /** Session log of the scraper engine, reached from the home screen. */
    data object Logs : Route
}

/**
 * Stable key of a route, used to keep its saved state while it is off
 * screen: scroll position, pagination, and focus restore when the user
 * navigates back.
 */
fun Route.stateKey(): String = when (this) {
    Route.Home -> "home"
    is Route.Scrape -> "scrape:$url"
    is Route.Results -> "results:$query"
    is Route.Player -> "player:$url"
    Route.Logs -> "logs"
}