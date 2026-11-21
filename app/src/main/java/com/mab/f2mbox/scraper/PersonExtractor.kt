package com.mab.f2mbox.scraper

import com.mab.f2mbox.data.remote.ImageRef
import com.mab.f2mbox.data.remote.ListData
import com.mab.f2mbox.data.remote.Pagination
import com.mab.f2mbox.data.remote.PersonData
import com.mab.f2mbox.data.remote.PersonProfile
import java.net.URI
import org.jsoup.nodes.Element

/**
 * Person-page extraction — the port of `person.py`, producing the app's
 * [PersonData] model directly. Shares the listing shape with
 * [ListExtractor], so it is just the person header plus a [ListData]
 * body.
 */
object PersonExtractor {

    /** Port of `_person_kind`. */
    private fun personKind(url: String): String {
        val path = URI(url).path ?: ""
        return when {
            "/directors/" in path -> "director"
            "/actors/" in path -> "actor"
            else -> "person"
        }
    }

    /** Port of `_person_slug`. */
    private fun personSlug(url: String): String? {
        val path = URI(url).path ?: ""
        for (marker in listOf("/directors/", "/actors/")) {
            if (marker in path) {
                return path.substringAfter(marker).trim('/')
                .ifEmpty { null }
            }
        }
        return null
    }

    /** Port of `_person_header`. */
    private fun personHeader(doc: Element, baseUrl: String): PersonProfile {
        val header = doc.selectFirst("article.person-description")
        val name = HtmlUtils.cleanText(doc.selectFirst("h1.page-title"))
        val photo = header?.selectFirst("figure img")
        return PersonProfile(
            kind = personKind(baseUrl),
            name = name,
            slug = personSlug(baseUrl),
            photo = ImageRef(
                url = HtmlUtils.imageUrl(baseUrl, photo),
                alt = photo?.attr("alt")?.takeIf { it.isNotBlank() },
            ),
        )
    }

    /** Port of `extract_person`. */
    fun extractPerson(url: String, html: String): PersonData {
        val doc = HtmlUtils.parse(html)
        val canonicalNode = doc.selectFirst("link[rel=canonical]")
        val canonical = canonicalNode?.let { HtmlUtils.absoluteUrl(url, it.attr("href")) }
        val listData = listBody(doc, url)
        return PersonData(
            title = listData.title,
            description = HtmlUtils.metaContent(doc, name = "description"),
            url = url,
            canonicalUrl = canonical,
            canonicalDomain = HtmlUtils.domainOf(canonical),
            person = personHeader(doc, url),
            items = listData.items,
            pagination = listData.pagination,
        )
    }

    /**
     * The shared listing body (`title`, `items`, `pagination`) used by
     * both person and list pages; [ListExtractor.extractList] reuses it
     * by calling into this through the list pipeline.
     */
    internal fun listBody(doc: Element, url: String): ListData {
        val canonicalNode = doc.selectFirst("link[rel=canonical]")
        val canonical = canonicalNode?.let { HtmlUtils.absoluteUrl(url, it.attr("href")) }
        val main = doc.selectFirst("main") ?: doc.ownerDocument()?.body() ?: doc
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
            pagination = ListExtractor.pagination(doc, url),
        )
    }
}
