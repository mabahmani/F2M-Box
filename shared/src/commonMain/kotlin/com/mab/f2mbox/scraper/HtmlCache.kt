package com.mab.f2mbox.scraper

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache of fetched HTML pages, keyed by the requested url.
 *
 * Lifecycle: lives for one app open — cleared in `MainActivity.onCreate`
 * and by the home screen's clear-cache button. Its purpose is to stop
 * redundant crawls within a session (back navigation, the health check
 * and `home()` fetching the same page, revisiting a listing) without
 * ever serving stale content across launches.
 *
 * Health checks bypass the cache: they must always touch the live site.
 */
object HtmlCache {

    /** Maximum number of pages kept; evicts the least-recently used. */
    private const val MAX_ENTRIES = 24

    private class Entry(val finalUrl: String, val html: String)

    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > MAX_ENTRIES
    }

    /** Returns the cached page for [url], or null. */
    fun get(url: String): Pair<String, String>? = synchronized(entries) {
        entries[url]?.let { it.finalUrl to it.html }
    }

    /** Stores [html] for [url] under its post-redirect [finalUrl]. */
    fun put(url: String, finalUrl: String, html: String) {
        synchronized(entries) { entries[url] = Entry(finalUrl, html) }
    }

    /** Drops every cached page. */
    fun clear() {
        synchronized(entries) { entries.clear() }
        ScraperLog.log("cache: cleared")
    }
}
