package com.mab.f2mbox.ui.home

import androidx.compose.runtime.staticCompositionLocalOf

/** Copy for the home screen and its child widgets. */
data class HomeStrings(
    val back: String,
    val retry: String,
    val clearCacheRefresh: String,
    val viewLogs: String,
    val seeMore: String,
    val ratingFormat: String,
    val search: String,
    val searchHint: String,
    val searchHintDetail: String,
    val continueWatchingSectionTitle: String,
    val heroPreviousSlide: String,
    val heroNextSlide: String,
    val heroOpenSlide: String,
    val noResults: String,
    val pageFormat: String,
    val previous: String,
    val next: String,
)

/** Defaults aligned with `app/src/main/res/values/strings.xml`. */
object HomeText {
    const val back = "Back"
    const val retry = "Retry"
    const val clearCacheRefresh = "Clear cache & refresh\u200E"
    const val viewLogs = "Logs"
    const val seeMore = "See more"
    const val ratingFormat = "★ %1\$s"
    const val search = "Search"
    const val searchHint = "Movie or series name…\u200E"
    const val searchHintDetail = "Type a title and press Search.\u200E"
    const val continueWatchingSectionTitle = "ادامه تماشا"
    const val heroPreviousSlide = "Previous hero slide"
    const val heroNextSlide = "Next hero slide"
    const val heroOpenSlide = "Open featured title"
    const val noResults = "No results found"
    const val pageFormat = "Page %1\$d\u200E"
    const val previous = "Previous"
    const val next = "Next"
}

fun defaultHomeStrings(): HomeStrings = HomeStrings(
    back = HomeText.back,
    retry = HomeText.retry,
    clearCacheRefresh = HomeText.clearCacheRefresh,
    viewLogs = HomeText.viewLogs,
    seeMore = HomeText.seeMore,
    ratingFormat = HomeText.ratingFormat,
    search = HomeText.search,
    searchHint = HomeText.searchHint,
    searchHintDetail = HomeText.searchHintDetail,
    continueWatchingSectionTitle = HomeText.continueWatchingSectionTitle,
    heroPreviousSlide = HomeText.heroPreviousSlide,
    heroNextSlide = HomeText.heroNextSlide,
    heroOpenSlide = HomeText.heroOpenSlide,
    noResults = HomeText.noResults,
    pageFormat = HomeText.pageFormat,
    previous = HomeText.previous,
    next = HomeText.next,
)

val LocalHomeStrings = staticCompositionLocalOf { defaultHomeStrings() }

fun HomeStrings.formatRating(rating: Float): String =
    ratingFormat.replace("%1\$s", rating.toString()).replace("%s", rating.toString())

fun HomeStrings.formatPage(page: Int): String =
    pageFormat.replace("%1\$d", page.toString())