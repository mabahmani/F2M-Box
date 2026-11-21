package com.mab.f2mbox.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

/**
 * Resolves the site's current base url through a fallback chain:
 *
 * 1. **Cached active url** — a previously resolved url whose home page
 *    still serves valid HTML.
 * 2. **Cached bootstrap url** — the bootstrap remembered from a
 *    previous config fetch (used only when there is no active url).
 * 3. **Config endpoint** — `values.bootstrapUrl` from BetterConfig;
 *    on any error falls back to the hardcoded
 *    [ScraperDefaults.FALLBACK_BOOTSTRAP_URL].
 *
 * The bootstrap url is then *verified* by crawling it: its canonical
 * link is followed, and if it redirects, the final redirect domain
 * becomes the active url. That url is persisted (via [Storage]) and
 * returned until it stops working.
 */
class BaseUrlResolver(
    private val storage: Storage,
    private val client: Film2MediaClient = Film2MediaClient(),
) {

    /** Persistence contract for the resolver's cached urls. */
    interface Storage {
        suspend fun activeUrl(): String?
        suspend fun bootstrapUrl(): String?
        suspend fun saveActive(url: String)
        suspend fun saveBootstrap(url: String)

        /** Epoch millis of the last config fetch (0 = never). */
        suspend fun lastConfigFetchAt(): Long
        suspend fun saveLastConfigFetchAt(timeMillis: Long)

        /** A manually entered domain (no scheme), or null. */
        suspend fun manualDomain(): String?
        suspend fun saveManualDomain(domain: String?)
    }

    /** [Storage] backed by an injectable key-value store. */
    class KeyValueStorage(private val store: KeyValueStore) : Storage {
        override suspend fun activeUrl(): String? = store.getString(KEY_ACTIVE)
        override suspend fun bootstrapUrl(): String? = store.getString(KEY_BOOTSTRAP)
        override suspend fun saveActive(url: String) = store.putString(KEY_ACTIVE, url)
        override suspend fun saveBootstrap(url: String) = store.putString(KEY_BOOTSTRAP, url)

        override suspend fun lastConfigFetchAt(): Long =
            store.getString(KEY_LAST_FETCH)?.toLongOrNull() ?: 0L

        override suspend fun saveLastConfigFetchAt(timeMillis: Long) =
            store.putString(KEY_LAST_FETCH, timeMillis.toString())

        override suspend fun manualDomain(): String? = store.getString(KEY_MANUAL)
        override suspend fun saveManualDomain(domain: String?) {
            if (domain == null) store.remove(KEY_MANUAL) else store.putString(KEY_MANUAL, domain)
        }

        private companion object {
            const val KEY_ACTIVE = "scraper.active_url"
            const val KEY_BOOTSTRAP = "scraper.bootstrap_url"
            const val KEY_LAST_FETCH = "scraper.config_fetched_at"
            const val KEY_MANUAL = "scraper.manual_domain"
        }
    }

    /** Minimal key-value contract so the app can pass SharedPreferences/DataStore. */
    interface KeyValueStore {
        suspend fun getString(key: String): String?
        suspend fun putString(key: String, value: String)

        /** Best-effort removal; unused by stores that can't delete. */
        suspend fun remove(key: String) {}
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * `(url, html)` of the healthy home page fetched during the most
     * recent [resolve] — lets callers parse the home payload without a
     * second fetch. Null when resolution never saw a healthy page.
     */
    var lastHealthyPage: Pair<String, String>? = null
        private set

    /**
     * Silent app-open refresh: fetches a fresh bootstrap url from the
     * config endpoint on **every** call — no freshness condition. Never
     * throws: any failure is swallowed and the cached value stays in
     * place.
     *
     * Call this once on app open (e.g. from `Application.onCreate` or a
     * root `LaunchedEffect`). It updates the cache only when the fetch
     * succeeds, so an offline launch keeps the last known bootstrap.
     */
    suspend fun refreshBootstrap() {
        log("refresh: silent bootstrap refresh starting")
        val fresh = fetchBootstrapFromConfig() ?: run {
            log("refresh: skipped (config fetch failed; cache kept)")
            return
        }
        log("refresh: cached new bootstrap $fresh")
        storage.saveBootstrap(fresh)
        storage.saveLastConfigFetchAt(System.currentTimeMillis())
    }

    /**
     * Called from the last-fallback error screen when the user supplies
     * the site's current domain (without scheme). Returns true when the
     * domain was accepted (fetchable home page with hero slider) and
     * cached as the active url; false leaves the old state untouched so
     * the error screen stays up.
     */
    suspend fun submitManualDomain(domain: String): Boolean {
        val cleaned = domain.trim()
            .removePrefix("https://").removePrefix("http://")
            .trimEnd('/')
        log("manual: submitting domain: $cleaned")
        if (cleaned.isEmpty() || !cleaned.contains('.')) {
            log("manual: rejected (not a domain)")
            return false
        }
        val url = "https://$cleaned/"
        if (!isHealthy(url)) {
            log("manual: domain failed the health check → rejected")
            return false
        }
        storage.saveManualDomain(cleaned)
        storage.saveActive(url)
        // The old session result (if any) points at the domain the user
        // just replaced — drop it and its health-check page.
        sessionResolved = null
        lastHealthyPage = null
        log("manual: accepted → active url cached as $url")
        return true
    }

    /**
     * Drops the session-scoped resolution result and health-check page
     * so the next [resolve] walks the full chain again. Used by the
     * clear-cache action.
     */
    fun invalidate() {
        sessionResolved = null
        lastHealthyPage = null
        log("resolve: session cache invalidated")
    }

    /**
     * Resolves the current active base url (ending in `/`), walking the
     * fallback chain and re-verifying the bootstrap url when needed.
     * A manually submitted domain ([submitManualDomain]) takes priority
     * over everything.
     *
     * The first successful result is remembered for the session
     * ([sessionResolved]) — a fresh [BaseUrlResolver] is created on
     * every app open, so this never outlives the launch, and
     * [invalidate] drops it.
     */
    suspend fun resolve(): String {
        // Session fast path: already resolved during this app open.
        sessionResolved?.let {
            log("resolve: session cache hit $it")
            return it
        }

        // 0. A manually entered domain (from the error screen) wins —
        //    it is health-verified before being cached, like any url.
        storage.manualDomain()?.let { manual ->
            log("step 0: trying manual domain: $manual")
            val manualUrl = "https://$manual/"
            if (isHealthy(manualUrl)) {
                log("step 0: manual domain healthy → using it")
                storage.saveActive(manualUrl)
                return manualUrl.also { sessionResolved = it }
            }
            log("step 0: manual domain unhealthy → clearing it")
            storage.saveManualDomain(null)
        } ?: log("step 0: no manual domain")
        // 1. Cached active url — valid while its home page serves HTML
        //    with the hero slider.
        storage.activeUrl()?.let { cached ->
            log("step 1: checking cached active url: $cached")
            if (isHealthy(cached)) {
                log("step 1: cached url is healthy → using it")
                return cached.also { sessionResolved = it }
            }
            log("step 1: cached url is unhealthy → falling through")
        } ?: log("step 1: no cached active url")

        // 2. Cached bootstrap url, if there is no active url. When there
        //    is none cached either, fetch config now (and remember it).
        val bootstrap = storage.bootstrapUrl()?.also {
            log("step 2: using cached bootstrap: $it")
        }
            ?: fetchBootstrapFromConfig()?.also {
                log("step 2: config fetch returned bootstrap: $it")
                storage.saveBootstrap(it)
                storage.saveLastConfigFetchAt(System.currentTimeMillis())
            }
            ?: ScraperDefaults.FALLBACK_BOOTSTRAP_URL.also {
                log("step 2: config failed → hardcoded fallback: $it")
            }

        // 3. Verify the bootstrap by crawling it; canonical + redirects
        //    decide the real active url.
        log("step 3: verifying bootstrap by crawling it")
        val result = verifyBootstrap(bootstrap)
        log("step 3: landed on ${result.activeUrl} (healthy=${result.healthy})")
        storage.saveBootstrap(bootstrap)
        if (result.healthy) {
            log("step 4: healthy → saving active url ${result.activeUrl}")
            storage.saveActive(result.activeUrl)
            sessionResolved = result.activeUrl
        } else {
            log("step 4: not healthy → active url NOT cached")
        }
        return result.activeUrl
    }

    /** The base url resolved during this app open, if any. */
    private var sessionResolved: String? = null

    private data class BootstrapResult(val activeUrl: String, val healthy: Boolean)

    /**
     * Crawls [bootstrap], reads its canonical link, and follows it. The
     * final domain the client lands on wins — that is the site's current
     * domain even when the bootstrap itself redirects. The result also
     * reports whether the landing page is a real home page (title +
     * hero slider); an unhealthy page is returned as a best effort but
     * not cached as active.
     */
    private suspend fun verifyBootstrap(bootstrap: String): BootstrapResult {
        log("verify: fetching bootstrap $bootstrap")
        // OkHttp resolves redirects transparently; fetching the bootstrap
        // lands on whatever domain it currently points at.
        val (finalUrl, html) = client.fetch(bootstrap, bypassCache = true)
        log("verify: landed on $finalUrl")
        val doc = HtmlUtils.parse(html)
        val title = doc.selectFirst("title")?.text()
        log("verify: page title = $title")

        // The canonical link may name a different domain than the page
        // was fetched from; fetch that too — again following redirects.
        val canonical = doc.selectFirst("link[rel=canonical]")
            ?.attr("href")
            ?.takeIf { it.isNotBlank() }
            ?.let { HtmlUtils.absoluteUrl(finalUrl, it) }

        if (canonical != null && HtmlUtils.domainOf(canonical) != HtmlUtils.domainOf(finalUrl)) {
            val canonicalDomain = HtmlUtils.domainOf(canonical)
            log("verify: canonical $canonical is on a different domain → using its domain root")
            return runCatching {
                val canonicalUrl = "https://$canonicalDomain/"
                val (finalCanonicalUrl, canonicalHtml) = client.fetch(canonicalUrl, bypassCache = true)
                log("verify: canonical domain landed on $finalCanonicalUrl")
                BootstrapResult(
                    finalCanonicalUrl,
                    rememberIfHealthy(finalCanonicalUrl, canonicalHtml),
                )
            }.onFailure { log("verify: canonical fetch failed: ${it.message}") }
                .getOrDefault(BootstrapResult(finalUrl, rememberIfHealthy(finalUrl, html)))
        }
        if (canonical != null) log("verify: canonical is same domain → skipping")
        return BootstrapResult(finalUrl, rememberIfHealthy(finalUrl, html))
    }

    /** Fetches [url] and checks it looks like a real home page. */
    private suspend fun isHealthy(url: String): Boolean = runCatching {
        val (finalUrl, html) = client.fetch(url, bypassCache = true)
        rememberIfHealthy(finalUrl, html)
    }.getOrDefault(false)

    /**
     * Checks [html] and, when it is a healthy home page, remembers it in
     * [lastHealthyPage] so callers can parse the home payload without a
     * second fetch.
     */
    private fun rememberIfHealthy(url: String, html: String): Boolean {
        val doc = HtmlUtils.parse(html)
        val title = doc.selectFirst("title") != null
        val hero = HomepageExtractor.heroSlider(doc, url).isNotEmpty()
        log("health check($url): title=$title heroSlider=$hero")
        val healthy = title && hero
        if (healthy) lastHealthyPage = url to html
        return healthy
    }

    private fun log(message: String) = ScraperLog.log(message)

    private companion object {
        const val TAG = "F2MScraper"
    }

    /**
     * Fetches `values.bootstrapUrl` from the config endpoint, returning
     * null on any error so callers fall back to the hardcoded url.
     */
    private suspend fun fetchBootstrapFromConfig(): String? = withContext(Dispatchers.IO) {
        log("config: fetching ${ScraperDefaults.CONFIG_URL}")
        runCatching {
            val request = Request.Builder()
                .url(ScraperDefaults.CONFIG_URL)
                .header("Authorization", ScraperDefaults.CONFIG_TOKEN)
                .build()
            client.httpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    log("config: HTTP ${response.code} → failing to fallback")
                    return@use null
                }
                val body = response.body?.string() ?: return@use null
                val bootstrap = json.parseToJsonElement(body)
                    .jsonObject["values"]
                    ?.jsonObject?.get("bootstrapUrl")
                    ?.jsonPrimitive?.content
                log("config: bootstrapUrl = $bootstrap")
                bootstrap
            }
        }.onFailure { log("config: failed → ${it.message}") }.getOrNull()
    }
}
