package com.mab.f2mbox.scraper

import com.mab.f2mbox.data.remote.GenreLink
import com.mab.f2mbox.data.remote.HeroSlide
import com.mab.f2mbox.data.remote.HomeData
import com.mab.f2mbox.data.remote.HomeNavigation
import com.mab.f2mbox.data.remote.HomeSection
import com.mab.f2mbox.data.remote.NavSection
import com.mab.f2mbox.data.remote.SectionItem
import org.jsoup.nodes.Element

/**
 * Homepage extraction — the port of `homepage.py`, producing the app's
 * [HomeData] model directly (the unmodeled `json_ld` / `open_graph`
 * fields of the Python output are dropped, matching the app's
 * `ignoreUnknownKeys` behavior).
 */
object HomepageExtractor {

    private val COUNT = Regex("""\((\d+)\)""")
    private val YEAR = Regex("""(?:19|20)\d{2}""")

    /** Port of `_icon_name`: reads the svg `<use>` id of an icon span. */
    private fun iconName(span: Element): String {
        val use = span.selectFirst("use") ?: return ""
        val href = use.attr("xlink:href").ifEmpty { use.attr("href") }
        return href.substringAfterLast('#')
    }

    /** Port of `_extract_nav_list`. */
    private fun navList(root: Element, baseUrl: String, withCount: Boolean): List<Any> {
        val entries = mutableListOf<Any>()
        for (li in root.select("ul.list > li")) {
            val anchor = li.selectFirst("a") ?: continue
            val name = HtmlUtils.cleanText(anchor) ?: continue
            if (withCount) {
                val text = HtmlUtils.cleanText(li) ?: ""
                val match = COUNT.find(text)
                entries.add(
                    GenreLink(
                        name = name,
                        url = HtmlUtils.absoluteUrl(baseUrl, anchor.attr("href")) ?: "",
                        count = match?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                    ),
                )
            } else {
                entries.add(
                    NavSection(
                        name = name,
                        url = HtmlUtils.absoluteUrl(baseUrl, anchor.attr("href")) ?: "",
                    ),
                )
            }
        }
        return entries
    }

    /** Port of `extract_hero_slider`. */
    fun heroSlider(doc: Element, baseUrl: String): List<HeroSlide> {
        val section = doc.selectFirst("#suggested-movies") ?: return emptyList()
        val slides = section.select("#cover-suggested-movies .splide__list > div.splide__slide")
        val thumbs = section.select("#suggested-thumbnails .splide__list > li.splide__slide")

        return slides.mapIndexed { index, slide ->
            val coverImg = slide.selectFirst(".cover img")
            val link = slide.selectFirst("a.stretched-link")

            var year: Int? = null
            var rating: Double? = null
            for (span in slide.select(".content > span")) {
                when (iconName(span)) {
                    "icon-calendar" -> {
                        val text = HtmlUtils.cleanText(span) ?: ""
                        YEAR.matchEntire(text)?.let { year = it.value.toInt() }
                    }
                    "icon-imdb" -> {
                        rating = HtmlUtils.cleanText(span.selectFirst("strong"))
                            ?.toDoubleOrNull()
                    }
                }
            }

            val thumbnail = thumbs.getOrNull(index)
                ?.let { HtmlUtils.imageUrl(baseUrl, it.selectFirst("img")) }

            HeroSlide(
                title = HtmlUtils.cleanText(slide.selectFirst("h2")).orEmpty(),
                url = link?.let { HtmlUtils.absoluteUrl(baseUrl, it.attr("href")) }.orEmpty(),
                poster = thumbnail,
                backdrop = com.mab.f2mbox.data.remote.ImageRef(
                    url = HtmlUtils.imageUrl(baseUrl, coverImg),
                    alt = coverImg?.attr("alt")?.takeIf { it.isNotBlank() },
                ),
                year = year,
                rating = rating,
            )
        }
    }

    /** Port of `extract_navigation`. */
    fun navigation(doc: Element, baseUrl: String): HomeNavigation {
        var sections: List<NavSection> = emptyList()
        var movieGenres: List<GenreLink> = emptyList()
        var seriesGenres: List<GenreLink> = emptyList()

        for ((id, withCount) in listOf(
            "sitehead-sections" to false,
            "genres-movies" to true,
            "genres-series" to true,
        )) {
            val root = doc.selectFirst("#$id") ?: continue
            if (withCount) {
                val entries = navList(root, baseUrl, true).filterIsInstance<GenreLink>()
                when (id) {
                    "genres-movies" -> movieGenres = entries
                    "genres-series" -> seriesGenres = entries
                }
            } else {
                sections = navList(root, baseUrl, false).filterIsInstance<NavSection>()
            }
        }
        return HomeNavigation(
            sections = sections,
            movieGenres = movieGenres,
            seriesGenres = seriesGenres,
        )
    }

    /** Port of `extract_homepage`. */
    fun extractHomepage(url: String, html: String): HomeData {
        val doc = HtmlUtils.parse(html)
        val canonicalNode = doc.selectFirst("link[rel=canonical]")

        val main = doc.selectFirst("main") ?: doc.body() ?: doc
        val sectionNodes = main.select("section.list-section")

        val sections: List<HomeSection> = sectionNodes.map { section ->
            val heading = section.selectFirst("[id\$=-title]")
                ?: section.selectFirst(".row h2")
                ?: section.selectFirst(".row h3")
                ?: section.selectFirst(".row p")
            val moreLink = section.selectFirst(".row a.btn-toPrimary")
            val cards = section.select("article.entry")
                .map { CardExtractor.extractCard(it, url) }
            HomeSection(
                id = section.attr("id").ifEmpty { null },
                title = HtmlUtils.cleanText(heading),
                url = moreLink?.let { HtmlUtils.absoluteUrl(url, it.attr("href")) },
                items = cards,
            )
        }

        // When no list-sections exist the Python code dumps loose items.
        val fallbackItems: List<SectionItem> =
            if (sections.isEmpty()) {
                main.select("article.entry").map { CardExtractor.extractCard(it, url) }
            } else {
                emptyList()
            }

        return HomeData(
            title = doc.selectFirst("title")?.text()?.trim()?.ifEmpty { null },
            heroSlider = heroSlider(doc, url),
            sections = if (sections.isEmpty()) {
                listOf(
                    HomeSection(
                        id = null,
                        title = null,
                        url = null,
                        items = fallbackItems,
                    ),
                )
            } else {
                sections
            },
            navigation = navigation(doc, url),
        )
    }
}
