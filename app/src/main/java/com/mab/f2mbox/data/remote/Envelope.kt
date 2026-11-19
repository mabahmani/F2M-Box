package com.mab.f2mbox.data.remote

import kotlinx.serialization.Serializable

/**
 * Error details returned inside an [Envelope] when [Envelope.ok] is false.
 */
@Serializable
data class ApiError(
    val code: String? = null,
    val message: String? = null,
)

/**
 * Common response wrapper used by every F2M Scrapper endpoint.
 *
 * Example (health):
 * `{"data":{"status":"up","time":"..."},"ok":true,"type":"health"}`
 */
@Serializable
data class Envelope<T>(
    val ok: Boolean = false,
    val type: String? = null,
    val data: T? = null,
    val error: ApiError? = null,
    val generatedAt: String? = null,
)

@Serializable
data class HealthData(
    val status: String,
    val time: String,
)
