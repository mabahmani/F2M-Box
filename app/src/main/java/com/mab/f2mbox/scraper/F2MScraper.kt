package com.mab.f2mbox.scraper

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
/**
 * Pure-Kotlin scraper engine — the local replacement for the hosted
 * F2M Scrapper server.
 *
 * Each call fetches and parses the site directly (OkHttp + Jsoup) and
 * returns the same models the remote API deserializes into, so callers
 * can switch between `F2MRepository` and this engine without touching
 * the UI. Configuration-free: hosts, timeouts, retries and fallback
 * urls all come from [ScraperDefaults].
 *
 * Usage:
 * ```
 * val scraper = F2MScraper()
 * scraper.home()                                  // default homepage
 * scraper.detail(url)                             // movie/series page
 * scraper.list(url, pages = 3)                    // follow 3 pages
 * scraper.person(url)
 * scraper.search("interstellar")
 * ```
 */
class F2MScraper(
    private val client: Film2MediaClient = Film2MediaClient(),
    private val resolver: BaseUrlResolver? = null,
) {

    // ------------------------------------------------------------------
    // Base url
    // ------------------------------------------------------------------

    /**
     * Resolves the site's current base url (cached → config bootstrap →
     * hardcoded), verifying it against the live site. Pass an explicit
     * [url] to any other method to bypass this.
     */
    suspend fun baseUrl(): String {
        val resolved = resolver?.resolve()
            ?: ScraperLog.let {
                it.log("baseUrl: no resolver → hardcoded fallback")
                ScraperDefaults.FALLBACK_BOOTSTRAP_URL + "/"
            }
        return resolved
    }

    /**
     * The host of the resolved base url — e.g. `tejaratban.top` (the
     * `www.` prefix is stripped) — for display on the home screen.
     */
    suspend fun activeDomain(): String =
        HtmlUtils.domainOf(
            resolver?.lastHealthyPage?.first ?: baseUrl(),
        ).orEmpty().removePrefix("www.")

    /**
     * Fire-and-forget silent bootstrap refresh for app open; failures
     * are logged inside the resolver and never surface here.
     */
    fun launchBootstrapRefresh() {
        resolver ?: return
        CoroutineScope(Dispatchers.IO).launch {
            resolver.refreshBootstrap()
        }
    }

    /**
     * Verifies and stores a user-submitted domain (the last-fallback
     * error screen). Returns true when the domain is healthy and now
     * cached as the active url.
     */
    suspend fun submitManualDomain(domain: String): Boolean =
        resolver?.submitManualDomain(domain)
            ?: false.also { ScraperLog.log("manual: no resolver — rejected") }

    /**
     * Drops every cached page and the session's resolution result —
     * the home screen's clear-cache action. The next fetches hit the
     * live site and the resolver walks its full chain again.
     */
    fun clearCache() {
        HtmlCache.clear()
        resolver?.invalidate()
    }

    // ------------------------------------------------------------------
    // Homepage
    // ------------------------------------------------------------------

    /**
     * Scrapes the homepage; [url] defaults to the resolved base url.
     * A relative [url] is resolved against the base url.
     *
     * When the base-url resolution's health check just fetched a healthy
     * home page ([BaseUrlResolver.lastHealthyPage]), that HTML is reused
     * — no second fetch, and the caller sees the exact page the health
     * check validated.
     */
    suspend fun home(url: String? = null): Result<com.mab.f2mbox.data.remote.HomeData> =
        runCatching {
            // Reuse the health-check fetch when no explicit url is given.
            val cached = if (url == null) resolver?.lastHealthyPage else null
            if (cached != null) {
                val (finalUrl, html) = cached
                ScraperLog.log(
                    "home: reusing health-check fetch of $finalUrl " +
                        "(${html.length} bytes) → parsing",
                )
                val data = HomepageExtractor.extractHomepage(finalUrl, html)
                ScraperLog.log(
                    "home: parsed → ${data.heroSlider.size} hero slides, " +
                        "${data.sections.sumOf { it.items.size }} cards",
                )
                return@runCatching data
            }

            val target = resolveUrl(url)
            ScraperLog.log("home: fetching $target")
            val (finalUrl, html) = client.fetch(target)
            ScraperLog.log(
                "home: fetched ${html.length} bytes from $finalUrl → parsing",
            )
            val data = HomepageExtractor.extractHomepage(finalUrl, html)
            ScraperLog.log(
                "home: parsed → ${data.heroSlider.size} hero slides, " +
                    "${data.sections.sumOf { it.items.size }} cards",
            )
            data
        }

    // ------------------------------------------------------------------
    // Listings
    // ------------------------------------------------------------------

    /**
     * Scrapes a listing page (category, genre archive, chart, search
     * results), following pagination up to [pages] pages and merging the
     * results.
     */
    suspend fun list(url: String, pages: Int = 1): Result<com.mab.f2mbox.data.remote.ListData> =
        runCatching {
            require(pages >= 1) { "pages must be >= 1" }
            var current = url
            val visited = mutableSetOf<String>()
            var first: com.mab.f2mbox.data.remote.ListData? = null
            val items = mutableListOf<com.mab.f2mbox.data.remote.SectionItem>()

            repeat(pages) {
                val pageKey = current.substringBefore('#')
                if (pageKey in visited) return@repeat
                visited.add(pageKey)

                val (finalUrl, html) = client.fetch(current)
                val page = ListExtractor.extractList(finalUrl, html)
                if (first == null) first = page
                items += page.items

                val next = page.pagination.next ?: return@repeat
                current = next
            }

            first!!.copy(items = CardExtractor.dedupByKey(items))
        }

    // ------------------------------------------------------------------
    // Detail (movie / series)
    // ------------------------------------------------------------------

    /**
     * Scrapes a movie or series detail page. [commentPages] fetches the
     * most recent comment pages (default 1 = only what the page itself
     * renders).
     */
    suspend fun detail(
        url: String,
        commentPages: Int = 1,
    ): Result<com.mab.f2mbox.data.remote.MovieData> = runCatching {
        require(commentPages >= 1) { "commentPages must be >= 1" }
        val (finalUrl, html) = client.fetch(url)
        val data = DetailExtractor.extractDetail(finalUrl, html)
        if (commentPages > 1) {
            data.copy(
                comments = data.comments?.let { fetchCommentPages(it, commentPages) },
            )
        } else {
            data
        }
    }

    /**
     * Parses a detail page from an already-fetched HTML string — the
     * `--html-file` testing path of the Python CLI.
     */
    fun detailFromHtml(url: String, html: String): com.mab.f2mbox.data.remote.MovieData =
        DetailExtractor.extractDetail(url, html)

    // ------------------------------------------------------------------
    // Person
    // ------------------------------------------------------------------

    /** Scrapes an actor or director archive page, one page at a time. */
    suspend fun person(url: String, pages: Int = 1): Result<com.mab.f2mbox.data.remote.PersonData> =
        runCatching {
            require(pages >= 1) { "pages must be >= 1" }
            var current = url
            val visited = mutableSetOf<String>()
            var first: com.mab.f2mbox.data.remote.PersonData? = null
            val items = mutableListOf<com.mab.f2mbox.data.remote.SectionItem>()

            repeat(pages) {
                val pageKey = current.substringBefore('#')
                if (pageKey in visited) return@repeat
                visited.add(pageKey)

                val (finalUrl, html) = client.fetch(current)
                val page = PersonExtractor.extractPerson(finalUrl, html)
                if (first == null) first = page
                items += page.items

                val next = page.pagination.next ?: return@repeat
                current = next
            }

            first!!.copy(items = CardExtractor.dedupByKey(items))
        }

    // ------------------------------------------------------------------
    // Typed scrape (webapp.py: api_scrape + detect_url_type)
    // ------------------------------------------------------------------

    /**
     * Scrapes any site url and dispatches on the page kind, mirroring the
     * hosted server's auto-detection: listing archives render as
     * [ScrapeResult.Listing], movie/series detail pages as
     * [ScrapeResult.Movie] and person archives as [ScrapeResult.Person].
     * A relative [url] is resolved against the base url.
     */
    suspend fun scrape(url: String, pages: Int = 1): Result<com.mab.f2mbox.data.remote.ScrapeResult> =
        runCatching {
            val target = resolveUrl(url)
            when (detectUrlType(target)) {
                "list" -> com.mab.f2mbox.data.remote.ScrapeResult.Listing(list(target, pages).getOrThrow())
                "person" -> com.mab.f2mbox.data.remote.ScrapeResult.Person(person(target, pages).getOrThrow())
                "detail" -> com.mab.f2mbox.data.remote.ScrapeResult.Movie(detail(target).getOrThrow())
                else -> error("Unsupported scrape url: $target")
            }
        }

    /** Classifies a site url (webapp.py: detect_url_type). */
    internal fun detectUrlType(url: String): String {
        val httpUrl = runCatching { url.toHttpUrl() }.getOrNull()
        val path = httpUrl?.encodedPath ?: "/"
        val query = httpUrl?.queryParameterNames.orEmpty()
        return when {
            Regex("^/(category|genres|tag)/.+").containsMatchIn(path) -> "list"
            Regex("^/(movies|series)(/page/\\d+/?)?/?$").containsMatchIn(path) ||
                path.startsWith("/250t") -> "list"
            Regex("^/(actors|directors)/.+").containsMatchIn(path) -> "person"
            "/series/" in path || Regex("^/\\d+/.+").containsMatchIn(path) -> "detail"
            "s" in query -> "list" // WordPress-style search: /?s=query
            else -> "homepage"
        }
    }

    // ------------------------------------------------------------------
    // Search (html source — the site's own results page)
    // ------------------------------------------------------------------

    /**
     * Searches by scraping the site's WordPress-style results page
     * (`/?s=<query>`), so the payload is a [ListData] with pagination and
     * renders in the listing grid like any other page.
     */
    suspend fun searchList(query: String, pages: Int = 1): Result<com.mab.f2mbox.data.remote.ListData> {
        val base = baseUrl().trimEnd('/')
        val searchUrl = "$base/?s=${java.net.URLEncoder.encode(query, "UTF-8")}"
        return list(searchUrl, pages)
    }

    // ------------------------------------------------------------------
    // Search (quick-search XHR endpoint)
    // ------------------------------------------------------------------

    /** Runs a quick-search against the site's XHR endpoint. */
    suspend fun search(
        query: String,
        sort: String = ScraperDefaults.DEFAULT_SORT,
    ): Result<SearchExtractor.SearchData> = runCatching {
        val base = baseUrl()
        val endpoint = base.trimEnd('/') + ScraperDefaults.SEARCH_PATH
        val payload = client.fetchJson(
            url = endpoint,
            method = "POST",
            data = mapOf("q" to query, "sort" to sort),
            headers = ScraperDefaults.XHR_HEADERS,
        )
        SearchExtractor.extract(base, query, payload, sort)
    }

    // ------------------------------------------------------------------
    // Comment-page merging (detail.py: _fetch_comment_pages)
    // ------------------------------------------------------------------

    private suspend fun fetchCommentPages(
        comments: com.mab.f2mbox.data.remote.Comments,
        maxPages: Int,
    ): com.mab.f2mbox.data.remote.Comments {
        val listed = comments.pagination.pages
        if (listed.isEmpty() || maxPages <= 1) return comments

        val wanted = listed.takeLast(maxPages - 1)
        var fetchedPages = 1
        val items = mutableListOf<com.mab.f2mbox.data.remote.Comment>()
        val seen = mutableSetOf<String>()

        fun add(pageItems: List<com.mab.f2mbox.data.remote.Comment>) {
            for (entry in pageItems) {
                val key = entry.id ?: continue
                if (key in seen) continue
                seen.add(key)
                items.add(entry)
            }
        }

        for (page in wanted) {
            val (_, html) = client.fetch(page.url)
            val pageComments = DetailExtractor.comments(HtmlUtils.parse(html), page.url)
            add(pageComments.items)
            fetchedPages++
        }
        add(comments.items)

        return comments.copy(
            items = items,
            count = flattenWithReplies(items).size,
        )
    }

    private fun flattenWithReplies(
        items: List<com.mab.f2mbox.data.remote.Comment>,
    ): List<com.mab.f2mbox.data.remote.Comment> = items.flatMap {
        listOf(it) + flattenWithReplies(it.replies)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun baseUrlOf(url: String): String {
        val httpUrl = runCatching { url.toHttpUrl() }.getOrNull() ?: return url
        return "${httpUrl.scheme}://${httpUrl.host}"
    }

    /**
     * Resolves [url] to a fetchable address: an absolute url passes
     * through, null/blank uses the resolved base url, and a relative
     * path is resolved against it.
     */
    private suspend fun resolveUrl(url: String?): String {
        val base = baseUrl()
        val raw = url?.takeIf { it.isNotBlank() } ?: return base
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        return base.trimEnd('/') + "/" + raw.trimStart('/')
    }
}
