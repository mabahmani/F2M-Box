package com.mab.f2mbox.data

import com.mab.f2mbox.data.remote.ScrapeResult

/**
 * Session cache of parsed pages, keyed by the url they were fetched from.
 *
 * Lives for one app open (like [com.mab.f2mbox.scraper.HtmlCache]) but holds
 * the parsed result instead of the html. Its purpose is to make Back
 * instant: the screen comes back with the data it already had, instead of
 * re-fetching and flashing a loading state.
 */
class PageCache {

    private val entries = mutableMapOf<String, ScrapeResult>()

    /** The result cached for [key], or null if there is none. */
    operator fun get(key: String): ScrapeResult? = entries[key]

    /** Caches [result] under [key]. */
    fun put(key: String, result: ScrapeResult) {
        entries[key] = result
    }

    /** Drops every cached page. */
    fun clear() {
        entries.clear()
    }
}
