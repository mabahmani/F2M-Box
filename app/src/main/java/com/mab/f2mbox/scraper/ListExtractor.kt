package com.mab.f2mbox.scraper

import com.mab.f2mbox.data.remote.ListData
import com.mab.f2mbox.data.remote.PageLink
import com.mab.f2mbox.data.remote.Pagination
import org.jsoup.nodes.Element
/**
 * Pagination and listing extraction — the port of `list.py`, producing
 * the app's [ListData] model directly.
 */
object ListExtractor {

    /** Port of `_pagination`: reads `.pagination` page links and next url. */
    fun pagination(root: Element, baseUrl: String): Pagination {
        val pages = mutableListOf<PageLink>()
        var current: Int? = null
        val container = root.selectFirst(".pagination")
        val seen = mutableSetOf<Int>()

        if (container != null) {
            for (node in container.select(".page-numbers")) {
                val classes = node.classNames()
                val text = HtmlUtils.cleanText(node)
                if (node.tagName() == "span") {
                    if ("dots" in classes) continue
                    val number = pageNumber(text)
                    if (number != null) current = number
                    continue
                }
                val number = pageNumber(text)
                val url = HtmlUtils.absoluteUrl(baseUrl, node.attr("href"))
                if (number == null || url == null || number in seen) continue
                seen.add(number)
                pages.add(PageLink(page = number, url = url))
            }
        }

        pages.sortBy { it.page }

        var next: String? = null
        val cur = current
        if (cur != null) {
            next = pages.firstOrNull { it.page > cur }?.url
        }
        if (next == null) {
            val nextLink = root.selectFirst("link[rel=next]")
            next = nextLink?.let { HtmlUtils.absoluteUrl(baseUrl, it.attr("href")) }
        }
        return Pagination(current = current, next = next, pages = pages)
    }

    private fun pageNumber(text: String?): Int? = text?.trim()?.toIntOrNull()

    /**
     * Extracts one listing page — the port of `extract_list`, minus the
     * fields the app does not model (`json_ld`, `open_graph`).
     */
    fun extractList(url: String, html: String): ListData {
        val doc = HtmlUtils.parse(html)
        val canonicalNode = doc.selectFirst("link[rel=canonical]")
        val canonical = canonicalNode?.let { HtmlUtils.absoluteUrl(url, it.attr("href")) }

        val main = doc.selectFirst("main") ?: doc.body() ?: doc
        val cards = main.select(".posts article.entry")
            .map { CardExtractor.extractCard(it, url) }

        return ListData(
            title = doc.selectFirst("title")?.text()?.trim()?.ifEmpty { null },
            pageTitle = HtmlUtils.cleanText(doc.selectFirst("h1.page-title")),
            description = HtmlUtils.metaContent(doc, name = "description"),
            url = url,
            canonicalUrl = canonical,
            canonicalDomain = HtmlUtils.domainOf(canonical),
            items = cards,
            pagination = pagination(doc, url),
        )
    }
}
