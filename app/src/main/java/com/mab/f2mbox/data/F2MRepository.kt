package com.mab.f2mbox.data

import com.mab.f2mbox.data.remote.F2MApi
import com.mab.f2mbox.data.remote.HealthData
import com.mab.f2mbox.data.remote.HomeData
import com.mab.f2mbox.data.remote.ListData

/**
 * Single entry point for F2M Scrapper data. Each call returns [Result] so
 * the UI layer only deals with success/failure.
 */
class F2MRepository(private val api: F2MApi = com.mab.f2mbox.data.remote.Api.client) {

    /** Checks server liveness; succeeds only when the server reports ok. */
    suspend fun health(): Result<HealthData> = runCatching {
        val envelope = api.health()
        check(envelope.ok) { envelope.error?.message ?: "Server reported an error" }
        checkNotNull(envelope.data) { "Empty health response" }
    }

    /** Fetches the homepage payload (hero slider, carousels, …). */
    suspend fun home(): Result<HomeData> = runCatching {
        val envelope = api.home()
        check(envelope.ok) { envelope.error?.message ?: "Server reported an error" }
        checkNotNull(envelope.data) { "Empty home response" }
    }

    /**
     * Scrapes a listing url. Only the "list" scrape type is supported for
     * now; anything else is reported as a failure.
     */
    suspend fun scrape(url: String): Result<ListData> = runCatching {
        val envelope = api.scrape(url)
        check(envelope.ok) { envelope.error?.message ?: "Server reported an error" }
        check(envelope.type == "list") { "Unsupported scrape response type: ${envelope.type}" }
        checkNotNull(envelope.data) { "Empty scrape response" }
    }

    /**
     * Searches titles (html source), so the payload is a [ListData] and
     * renders in the listing grid like any other page.
     */
    suspend fun search(query: String): Result<ListData> = runCatching {
        val envelope = api.search(query)
        check(envelope.ok) { envelope.error?.message ?: "Server reported an error" }
        check(envelope.type == "list") { "Unsupported search response type: ${envelope.type}" }
        checkNotNull(envelope.data) { "Empty search response" }
    }
}
