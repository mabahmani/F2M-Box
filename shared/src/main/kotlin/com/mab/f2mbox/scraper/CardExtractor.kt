package com.mab.f2mbox.scraper

import com.mab.f2mbox.data.remote.GenreRef
import com.mab.f2mbox.data.remote.ImageRef
import com.mab.f2mbox.data.remote.SectionItem
import org.jsoup.nodes.Element

/**
 * Extracts one poster card (`article.entry`) — the port of `cards.py`,
 * producing the app's [SectionItem] model directly.
 */
object CardExtractor {

    private val YEAR = Regex("""(?:19|20)\d{2}""")

    /** Port of `extract_year`: meta year first, then the title's year. */
    fun extractYear(card: Element): Int? {
        for (span in card.select(".entry-meta span")) {
            val value = HtmlUtils.cleanText(span) ?: continue
            if (Regex("""^(?:19|20)\d{2}$""").matches(value)) return value.toInt()
        }
        val title = HtmlUtils.cleanText(card.selectFirst(".entry-title")) ?: ""
        return YEAR.find(title)?.value?.toInt()
    }

    /** Port of `extract_rating`. */
    fun extractRating(card: Element): Double? {
        val raw = HtmlUtils.cleanText(card.selectFirst(".entry-meta strong")) ?: return null
        return raw.toDoubleOrNull()
    }

    /** Port of `extract_card`. */
    fun extractCard(card: Element, baseUrl: String): SectionItem {
        val titleNode = card.selectFirst(".entry-title")
        val titleLink = card.selectFirst("a.stretched-link[rel~=bookmark]")
            ?: card.selectFirst("a[rel~=bookmark]")
        val img = card.selectFirst(".entry-cover img")

        val genres = card.select(".entry-ganers a")
            .mapNotNull { anchor ->
                val name = HtmlUtils.cleanText(anchor) ?: return@mapNotNull null
                GenreRef(name = name, url = HtmlUtils.absoluteUrl(baseUrl, anchor.attr("href")))
            }

        val formats = card.select(".entry-icons .icon[title]")
            .map { it.attr("title").trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        return SectionItem(
            title = HtmlUtils.cleanText(titleNode).orEmpty(),
            url = titleLink?.let { HtmlUtils.absoluteUrl(baseUrl, it.attr("href")) }.orEmpty(),
            linkTitle = titleLink?.attr("title")?.takeIf { it.isNotBlank() },
            poster = ImageRef(
                url = HtmlUtils.imageUrl(baseUrl, img),
                alt = img?.attr("alt")?.takeIf { it.isNotBlank() },
            ),
            year = extractYear(card),
            rating = extractRating(card),
            genres = genres,
            formats = formats,
            statusText = HtmlUtils.cleanText(card.selectFirst("p.text-muted")),
        )
    }

    /** Deduplicates [items] by url, falling back to title (list.py logic). */
    fun dedupByKey(items: List<SectionItem>): List<SectionItem> {
        val seen = mutableSetOf<String>()
        return items.filter { item ->
            val key = item.url.ifEmpty { item.title }
            if (key.isEmpty() || key in seen) return@filter false
            seen.add(key)
            true
        }
    }
}
