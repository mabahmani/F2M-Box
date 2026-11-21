package com.mab.f2mbox.scraper

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Fetches pages with the same robustness guarantees as the Python
 * `Film2MediaClient`:
 *
 * - retry with back-off on transient statuses and IO errors;
 * - hard cap on response size;
 * - redirects followed natively by OkHttp (307/308 keep method+body,
 *   301/302/303 downgrade to GET on POST — the standard behavior the
 *   Python code emulated by hand).
 */
class Film2MediaClient(
    headers: Map<String, String> = emptyMap(),
) {

    /** Number of pages successfully fetched, as in `client.pages_fetched`. */
    var pagesFetched: Int = 0
        private set

    private val baseHeaders: Map<String, String> = buildMap {
        putAll(headers)
    }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(ScraperDefaults.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(ScraperDefaults.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /** Raw access for the config endpoint (no scraping semantics). */
    internal fun httpClient(): OkHttpClient = http

    // ------------------------------------------------------------------
    // fetch — HTML pages
    // ------------------------------------------------------------------

    /**
     * Fetches an HTML page, returning `(final url, body)`. OkHttp
     * resolves the final url after any redirects.
     */
    suspend fun fetch(url: String): Pair<String, String> =
        send("GET", url, data = null).use { res ->
            if (!res.isSuccessful) {
                throw ScrapeException("HTTP ${res.code} for ${res.request.url}")
            }
            val body = res.readCappedBody()
            pagesFetched++
            res.request.url.toString() to body
        }

    // ------------------------------------------------------------------
    // fetchJson — XHR endpoints (quick search)
    // ------------------------------------------------------------------

    /**
     * Fetches a JSON endpoint with the same headers, following redirects
     * like `curl --location`.
     */
    suspend fun fetchJson(
        url: String,
        method: String = "POST",
        data: Map<String, String>? = null,
        headers: Map<String, String> = emptyMap(),
    ): String =
        send(method, url, data, headers).use { res ->
            if (!res.isSuccessful) {
                throw ScrapeException("HTTP ${res.code} for ${res.request.url}")
            }
            val body = res.readCappedBody()
            pagesFetched++
            body
        }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * Sends a request, retrying transient timeouts, connection errors
     * and server errors with linear back-off — the port of `_send`.
     */
    private suspend fun send(
        method: String,
        url: String,
        data: Map<String, String>? = null,
        headers: Map<String, String> = emptyMap(),
    ): Response = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        repeat(ScraperDefaults.MAX_RETRIES + 1) { attempt ->
            try {
                ScraperLog.log("fetch: $method $url")
                val response = http.newCall(buildRequest(method, url, data, headers)).execute()
                // response.request is the FINAL request of the redirect
                // chain, so this is the url we actually landed on.
                ScraperLog.log("fetch: ← HTTP ${response.code} ${response.request.url}")
                if (
                    response.code in ScraperDefaults.RETRYABLE_STATUSES &&
                    attempt < ScraperDefaults.MAX_RETRIES
                ) {
                    response.close()
                    ScraperLog.log("fetch: retry ${attempt + 1} (HTTP ${response.code})")
                    delay((ScraperDefaults.RETRY_DELAY_SECONDS * (attempt + 1) * 1000).toLong())
                    lastError = ScrapeException("Server returned ${response.code}")
                    return@repeat
                }
                return@withContext response
            } catch (e: IOException) {
                lastError = e
                if (attempt < ScraperDefaults.MAX_RETRIES) {
                    ScraperLog.log("fetch: retry ${attempt + 1} (${e.message})")
                    delay((ScraperDefaults.RETRY_DELAY_SECONDS * (attempt + 1) * 1000).toLong())
                }
            }
        }
        if (lastError is ScrapeException) throw lastError as ScrapeException
        throw (lastError ?: ScrapeException("Request failed"))
    }

    private fun buildRequest(
        method: String,
        url: String,
        data: Map<String, String>?,
        headers: Map<String, String>,
    ): Request {
        val builder = Request.Builder().url(url)
        baseHeaders.forEach { (k, v) -> builder.header(k, v) }
        headers.forEach { (k, v) -> builder.header(k, v) }
        when (method.uppercase()) {
            "POST" -> builder.post(formBody(data))
            else -> builder.get()
        }
        return builder.build()
    }

    private fun formBody(data: Map<String, String>?): FormBody {
        val body = FormBody.Builder()
        data?.forEach { (k, v) -> body.add(k, v) }
        return body.build()
    }

    private fun Response.readCappedBody(): String {
        val body = body ?: return ""
        val bytes = body.byteStream().use { stream ->
            val cap = ScraperDefaults.MAX_RESPONSE_BYTES
            val out = ByteArray((cap + 1).toInt())
            var read = 0
            while (read < out.size) {
                val chunk = stream.read(out, read, out.size - read)
                if (chunk < 0) break
                read += chunk
            }
            if (read == out.size && stream.read() != -1) {
                throw ScrapeException(
                    "Response exceeded ${ScraperDefaults.MAX_RESPONSE_BYTES} bytes",
                )
            }
            out.copyOf(read)
        }
        return String(bytes, Charsets.UTF_8)
    }
}

/** Failure raised by the scraper engine. */
class ScrapeException(message: String, cause: Throwable? = null) : IOException(message, cause)
