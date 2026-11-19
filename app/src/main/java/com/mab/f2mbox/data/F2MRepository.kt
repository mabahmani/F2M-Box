package com.mab.f2mbox.data

import com.mab.f2mbox.data.remote.F2MApi
import com.mab.f2mbox.data.remote.HealthData

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
}
