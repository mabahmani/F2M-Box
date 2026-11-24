package com.mab.f2mbox.scraper

/**
 * Hard-coded defaults that replace the Python crawler's `config.json`.
 *
 * Only client behaviour (timeouts, retries, response cap) and the
 * base-url resolution chain live here. Nothing reads a file, so the
 * engine works with zero configuration.
 */
object ScraperDefaults {

    // ------------------------------------------------------------------
    // Client behaviour (client.py constants)
    // ------------------------------------------------------------------

    /** OkHttp connect timeout, seconds. */
    const val CONNECT_TIMEOUT_SECONDS = 30L

    /** OkHttp read timeout, seconds. */
    const val READ_TIMEOUT_SECONDS = 30L

    /** Rejected when the response body exceeds this size. */
    const val MAX_RESPONSE_BYTES = 5L * 1024 * 1024

    /** Maximum redirects followed per fetch (client.py: MAX_REDIRECTS). */
    const val MAX_REDIRECTS = 5

    /** Retries per request on retryable statuses / IO errors. */
    const val MAX_RETRIES = 2

    /** Base back-off between retries, seconds. */
    const val RETRY_DELAY_SECONDS = 1.5

    /** HTTP statuses that trigger a retry. */
    val RETRYABLE_STATUSES: Set<Int> = setOf(500, 502, 503, 504)

    /** Default User-Agent sent with requests to avoid CDN / WAF blocks. */
    const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    // ------------------------------------------------------------------
    // Base-url resolution
    // ------------------------------------------------------------------

    /** Remote config endpoint fetched when no bootstrap url is cached. */
    const val CONFIG_URL: String = "https://f2mbox.aminbahmani10-u3hea.arvanedge.ir/"

    /** Used when the config endpoint fails. */
    const val FALLBACK_BOOTSTRAP_URL = "https://www.tejaratban.top"

    // ------------------------------------------------------------------
    // Search (search.py)
    // ------------------------------------------------------------------

    const val SEARCH_PATH = "/quick-search"

    const val DEFAULT_SORT = "modified_at:desc"

    /** XHR headers sent with quick-search requests (search.py: XHR_HEADERS). */
    val XHR_HEADERS: Map<String, String> = mapOf(
        "Accept" to "*/*",
        "X-Requested-With" to "XMLHttpRequest",
    )
}
