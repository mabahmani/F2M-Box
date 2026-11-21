package com.mab.f2mbox.scraper

import java.net.URI
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Shared HTML parsing helpers — the port of `html_utils.py`. Every
 * BeautifulSoup idiom used by the Python code maps onto a Jsoup call
 * here.
 */
object HtmlUtils {

    /** Matches the `handleDownloadClick('...')` onclick handler. */
    private val DOWNLOAD_CLICK = Regex("""handleDownloadClick\('([^']+)'\)""")

    /**
     * Normalises a text node: descendant text with collapsed whitespace,
     * null when empty — the equivalent of
     * `" ".join(node.get_text(" ", strip=True).split())`.
     */
    fun cleanText(node: Element?): String? {
        if (node == null) return null
        val value = node.text().trim()
        return value.ifEmpty { null }
    }

    /** Reads `meta[name=…]` or `meta[property=…]` content. */
    fun metaContent(doc: Element, name: String? = null, prop: String? = null): String? {
        val selector = if (name != null) {
            """meta[name="$name"]"""
        } else {
            """meta[property="$prop"]"""
        }
        val node = doc.selectFirst(selector) ?: return null
        return node.attr("content").trim().ifEmpty { null }
    }

    /**
     * Resolves [value] against [baseUrl] and strips the fragment,
     * returning null for non-http(s) and pseudo-schemes (`data:`,
     * `javascript:`, `mailto:`, `tel:`) — the port of `absolute_url`.
     */
    fun absoluteUrl(baseUrl: String, value: String?): String? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("data:") ||
            trimmed.startsWith("javascript:") ||
            trimmed.startsWith("mailto:") ||
            trimmed.startsWith("tel:")
        ) {
            return null
        }
        val resolved = runCatching {
            URI.create(baseUrl).resolve(trimmed).toString()
        }.getOrNull() ?: return null
        val withoutFragment = resolved.substringBefore('#')
        val scheme = withoutFragment.substringBefore("://").lowercase()
        return if (scheme in setOf("http", "https")) withoutFragment else null
    }

    /**
     * Picks the first resolvable image url among the lazy-loading
     * attributes the site uses (port of `image_url`).
     */
    fun imageUrl(baseUrl: String, img: Element?): String? {
        if (img == null) return null
        for (attr in listOf("data-splide-lazy", "data-src", "data-lazy-src", "src")) {
            val candidate = img.attr(attr)
            val resolved = absoluteUrl(baseUrl, candidate)
            if (resolved != null) return resolved
        }
        return null
    }

    /**
     * Extracts every `script[type="application/ld+json"]` block. The
     * app's models drop `json_ld`, but the engine still surfaces the
     * raw strings so callers can decode them if needed.
     */
    fun extractJsonLd(doc: Element): List<String> =
        doc.select("script[type=\"application/ld+json\"]")
            .map { it.data() }
            .filter { it.isNotBlank() }

    /** Port of `download_url_from_anchor`. */
    fun downloadUrlFromAnchor(anchor: Element): String? {
        val onclick = anchor.attr("onclick") ?: ""
        DOWNLOAD_CLICK.find(onclick)?.let { return it.groupValues[1] }
        val href = anchor.attr("href")?.trim() ?: ""
        if (href.startsWith("http") && !href.contains("playit=")) return href
        return null
    }

    /** Port of `play_url_from_anchor`. */
    fun playUrlFromAnchor(anchor: Element, baseUrl: String): String? {
        val href = anchor.attr("href") ?: ""
        return if (href.contains("playit=")) absoluteUrl(baseUrl, href) else null
    }

    /**
     * Returns only the host of [url], e.g. `https://www.tejaratban.top/x`
     * → `www.tejaratban.top`; null when the url has no host.
     */
    fun domainOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching { url.toHttpUrl().host }.getOrNull()
    }

    /** Parses HTML into a Jsoup document (the BeautifulSoup entry point). */
    fun parse(html: String): org.jsoup.nodes.Document = Jsoup.parse(html)
}
