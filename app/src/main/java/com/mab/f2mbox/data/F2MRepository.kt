package com.mab.f2mbox.data

import com.mab.f2mbox.data.remote.Api
import com.mab.f2mbox.data.remote.F2MApi
import com.mab.f2mbox.data.remote.HealthData
import com.mab.f2mbox.data.remote.HomeData
import com.mab.f2mbox.data.remote.ListData
import com.mab.f2mbox.data.remote.ScrapeResult
import kotlinx.serialization.json.decodeFromJsonElement

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
     * Scrapes any url and decodes the payload by the envelope's type:
     * "list" → [ScrapeResult.Listing], "movie" → [ScrapeResult.Movie].
     * Other types are reported as a failure until they are modeled.
     */
    suspend fun scrape(url: String): Result<ScrapeResult> = runCatching {
        val envelope = api.scrape(url)
        check(envelope.ok) { envelope.error?.message ?: "Server reported an error" }
        val data = checkNotNull(envelope.data) { "Empty scrape response" }
        when (envelope.type) {
            "list" -> ScrapeResult.Listing(Api.json.decodeFromJsonElement(data))
            "movie" -> ScrapeResult.Movie(Api.json.decodeFromJsonElement(data))
            else -> error("Unsupported scrape response type: ${envelope.type}")
        }
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
