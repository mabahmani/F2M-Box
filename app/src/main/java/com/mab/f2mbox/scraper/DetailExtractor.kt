package com.mab.f2mbox.scraper

import com.mab.f2mbox.data.remote.Comment
import com.mab.f2mbox.data.remote.Comments
import com.mab.f2mbox.data.remote.DownloadLink
import com.mab.f2mbox.data.remote.DownloadQuality
import com.mab.f2mbox.data.remote.DownloadVariant
import com.mab.f2mbox.data.remote.Dub
import com.mab.f2mbox.data.remote.Dubbing
import com.mab.f2mbox.data.remote.Downloads
import com.mab.f2mbox.data.remote.EpisodeLink
import com.mab.f2mbox.data.remote.GenreRef
import com.mab.f2mbox.data.remote.ImageRef
import com.mab.f2mbox.data.remote.MovieData
import com.mab.f2mbox.data.remote.MovieInfo
import com.mab.f2mbox.data.remote.MovieIntro
import com.mab.f2mbox.data.remote.PersonRef
import com.mab.f2mbox.data.remote.Reactions
import com.mab.f2mbox.data.remote.Season
import com.mab.f2mbox.data.remote.SeriesQuality
import com.mab.f2mbox.data.remote.SeriesVariant
import com.mab.f2mbox.data.remote.SuggestedSection
import com.mab.f2mbox.data.remote.Trailer
import com.mab.f2mbox.data.remote.VoiceActor
import java.net.URI
import org.jsoup.nodes.Element

/**
 * Detail-page extraction — the port of `detail.py`, producing the app's
 * [MovieData] model directly (movies and series share one payload
 * shape).
 */
object DetailExtractor {

    private val SHORTLINK_POST = Regex("""[?&]p=(\d+)""")
    private val BACKGROUND_IMAGE = Regex("""url\((['"]?)(.*?)\1\)""")
    private val COUNT = Regex("""^([\d.]+)\s*([KMB])?$""", RegexOption.IGNORE_CASE)
    private val VOICE_CAST_SPLIT = Regex("""،|,""")
    private val VOICE_ROLE = Regex("""^(.*?)\s*\((.*)\)$""")

    /** A "..." line separating consecutive dubs inside the dubbed-info block. */
    private val DUB_SEPARATOR = Regex("""^[. …]+$""")
    private const val DUB_SEPARATOR_LINE = "..."

    private val PERSIAN_DIGITS = mapOf(
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
    )

    private fun translateDigits(value: String): String =
        value.map { PERSIAN_DIGITS[it] ?: it }.joinToString("")

    private fun digitsIn(value: String): List<Int> =
        Regex("""\d+""").findAll(translateDigits(value)).map { it.value.toInt() }.toList()

    // ------------------------------------------------------------------
    // Content kind
    // ------------------------------------------------------------------

    /** Port of `_content_kind`. */
    fun contentKind(doc: Element, url: String): String {
        if (doc.selectFirst("#downloads .download-season.bg-card.rounded") != null) {
            return "series"
        }
        val path = URI(url).path ?: ""
        return if ("/series/" in path) "series" else "movie"
    }

    // ------------------------------------------------------------------
    // Intro
    // ------------------------------------------------------------------

    /** Port of `_duration_minutes`. */
    private fun durationMinutes(value: String?): Int? {
        if (value == null) return null
        val text = translateDigits(value)
        val numbers = digitsIn(text)
        if (numbers.isEmpty()) return null
        if ("ساعت" in text && numbers.size >= 2) return numbers[0] * 60 + numbers[1]
        return numbers[0]
    }

    /** Port of `_genre_links`. */
    private fun genreLinks(intro: Element, baseUrl: String): List<GenreRef> {
        val seen = mutableSetOf<String>()
        return intro.select(".entry-genres a.btn").mapNotNull { anchor ->
            val name = HtmlUtils.cleanText(anchor) ?: return@mapNotNull null
            if (name in seen) return@mapNotNull null
            seen.add(name)
            GenreRef(name = name, url = HtmlUtils.absoluteUrl(baseUrl, anchor.attr("href")))
        }
    }

    /** Port of `_post_id`. */
    private fun postId(doc: Element): String? {
        val watch = doc.selectFirst("[data-watchlist]")
        if (watch != null) {
            val value = watch.attr("data-watchlist")
            if (value.isNotEmpty()) return value
        }
        val shortlink = doc.selectFirst("link[rel=shortlink]") ?: return null
        val href = shortlink.attr("href")
        return SHORTLINK_POST.find(href)?.groupValues?.getOrNull(1)
    }

    /** Port of `_extract_intro`. */
    fun extractIntro(doc: Element, baseUrl: String): MovieIntro {
        val intro = doc.selectFirst("#post-intro") ?: doc
        val title = HtmlUtils.cleanText(intro.selectFirst("h1.entry-title"))
            ?: HtmlUtils.cleanText(doc.selectFirst("title"))
        val posterImg = intro.selectFirst("figure.entry-poster img")
            ?: intro.selectFirst("img.wp-post-image")
        val excerptNode = intro.selectFirst(".entry-excerpt")
        val satisfaction = intro.selectFirst(".feedback-satisfaction")

        val pageNotes = mutableListOf<String>()
        for (note in intro.select(".note")) {
            val text = HtmlUtils.cleanText(note) ?: continue
            if (text !in pageNotes) pageNotes.add(text)
        }
        val latestEpisodeNote = pageNotes.firstOrNull { "فصل" in it || "قسمت" in it }

        val info = mutableMapOf<String, String?>()
        val labelToKey = mapOf(
            "زمان" to "duration",
            "اکران" to "release",
            "انتشار" to "published",
            "کشور سازنده" to "countries",
        )
        for (row in intro.select(".row.pt-4 [class*=col-]")) {
            val text = HtmlUtils.cleanText(row) ?: continue
            if (":" in text) {
                val idx = text.indexOf(':')
                val label = text.substring(0, idx)
                val value = text.substring(idx + 1)
                val key = labelToKey[label.trim()]
                if (key != null) {
                    info[key] = value.trim().ifEmpty { null }
                    continue
                }
            }
            if ("زبان" in text) {
                val langLink = row.selectFirst("a")
                info["language"] = langLink?.let { HtmlUtils.cleanText(it) }
                    ?: text.substringAfter(':').trim()
            }
        }

        var duration = info["duration"]
        if (duration != null && durationMinutes(duration) == null) duration = null
        info["duration"] = duration

        val formats = intro.select(".entry-icons .icon[title]")
            .map { it.attr("title").trim() }
            .filter { it.isNotEmpty() }

        return MovieIntro(
            title = title,
            poster = ImageRef(
                url = HtmlUtils.imageUrl(baseUrl, posterImg),
                alt = posterImg?.attr("alt")?.takeIf { it.isNotBlank() },
            ),
            excerpt = HtmlUtils.cleanText(excerptNode),
            formats = formats,
            genres = genreLinks(intro, baseUrl),
            info = MovieInfo(
                countries = info["countries"],
                duration = duration,
                durationMinutes = durationMinutes(duration),
                language = info["language"],
                published = info["published"],
                release = info["release"],
            ),
            notes = pageNotes,
            latestEpisodeNote = latestEpisodeNote,
            satisfactionPercent = HtmlUtils.cleanText(satisfaction),
            postId = postId(doc),
        )
    }

    // ------------------------------------------------------------------
    // Downloads — movie
    // ------------------------------------------------------------------

    /** Port of `_labeled_column_value`. */
    private fun labeledColumnValue(item: Element, label: String): String? {
        for (col in item.select(".col-6, .col-12")) {
            val text = HtmlUtils.cleanText(col) ?: continue
            if (label in text) return text.substringAfter(':').trim()
        }
        return null
    }

    /** Port of `_movie_variant`. */
    private fun movieVariant(block: Element): String? {
        val classes = block.classNames().toList()
        for (name in listOf("dubbled", "hardsub", "dubbed")) {
            if (name in classes) return name
        }
        return classes.lastOrNull()
    }

    /** Port of `_parse_movie_block`. */
    private fun parseMovieBlock(block: Element, baseUrl: String): DownloadVariant {
        val qualities = block.select("ul > li").map { item ->
            val links = item.select("a").mapNotNull { anchor ->
                val label = HtmlUtils.cleanText(anchor) ?: return@mapNotNull null
                val entry = DownloadLink(
                    label = label,
                    downloadUrl = HtmlUtils.downloadUrlFromAnchor(anchor),
                    playUrl = HtmlUtils.playUrlFromAnchor(anchor, baseUrl),
                )
                if (entry.downloadUrl != null || entry.playUrl != null) entry else null
            }
            DownloadQuality(
                quality = HtmlUtils.cleanText(item.selectFirst("span.text[dir=ltr]")),
                encoder = labeledColumnValue(item, "انکودر"),
                notes = labeledColumnValue(item, "توضیحات"),
                links = links,
            )
        }
        return DownloadVariant(
            variant = movieVariant(block),
            title = HtmlUtils.cleanText(block.selectFirst(".title span")),
            qualities = qualities,
        )
    }

    /** Port of `_movie_downloads`. */
    fun movieDownloads(doc: Element, baseUrl: String): List<DownloadVariant> {
        val root = doc.selectFirst("#downloads") ?: doc
        return root.select(".download-list")
            .filter { it.closest(".download-season") == null }
            .map { parseMovieBlock(it, baseUrl) }
    }

    // ------------------------------------------------------------------
    // Downloads — series
    // ------------------------------------------------------------------

    /** Port of `_parse_episode_row`. */
    private fun parseEpisodeRow(row: Element, baseUrl: String): EpisodeLink {
        var playUrl: String? = null
        var downloadUrl: String? = null
        var label: String? = null

        for (anchor in row.select("a")) {
            val href = anchor.attr("href") ?: ""
            when {
                anchor.selectFirst("img") != null && href.trim() == "#" -> {
                    HtmlUtils.downloadUrlFromAnchor(anchor)?.let { downloadUrl = it }
                }
                "playit=" in href -> {
                    playUrl = HtmlUtils.playUrlFromAnchor(anchor, baseUrl)
                }
                "btn-default" in anchor.classNames() -> {
                    label = HtmlUtils.cleanText(anchor)
                    HtmlUtils.downloadUrlFromAnchor(anchor)?.let { downloadUrl = it }
                }
            }
        }

        if (label == null) {
            for (anchor in row.select("a.btn")) {
                val text = HtmlUtils.cleanText(anchor) ?: continue
                if (text.startsWith("قسمت")) {
                    label = text
                    break
                }
            }
        }

        val filename = downloadUrl?.substringAfterLast('/')
            ?.substringBefore('#')?.ifEmpty { null }

        return EpisodeLink(
            label = label,
            filename = filename,
            downloadUrl = downloadUrl,
            playUrl = playUrl,
        )
    }

    /** Port of `_parse_series_quality_item`. */
    private fun parseSeriesQualityItem(item: Element, baseUrl: String): SeriesQuality {
        val collapse = item.selectFirst(".collapse")
        val episodes = collapse
            ?.select(".series-downloaditems > .d-flex")
            ?.map { parseEpisodeRow(it, baseUrl) }
            ?: emptyList()
        return SeriesQuality(
            quality = HtmlUtils.cleanText(item.selectFirst("span.text[dir=ltr]")),
            episodeCount = episodes.size,
            episodes = episodes,
        )
    }

    /** Port of `_parse_series_season`. */
    private fun parseSeriesSeason(seasonRoot: Element, baseUrl: String): Season {
        val toggle = seasonRoot.selectFirst("button[data-bs-toggle=collapse]")
        var status: String? = null
        var seasonName: String? = null
        if (toggle != null) {
            status = HtmlUtils.cleanText(toggle.selectFirst(".btn-lighten"))
            val parts = mutableListOf<String>()
            for (child in toggle.childNodes()) {
                if (child is org.jsoup.nodes.Element) {
                    if ("btn-lighten" in child.classNames()) continue
                    val text = HtmlUtils.cleanText(child)
                    if (text != null && text != status) parts.add(text)
                } else if (child.nodeName() == "#text") {
                    val text = child.toString().trim()
                    if (text.isNotEmpty()) parts.add(text)
                }
            }
            seasonName = parts.joinToString(" ").ifEmpty {
                HtmlUtils.cleanText(toggle)
            }
        }

        val panel = seasonRoot.selectFirst(".collapse") ?: seasonRoot
        val variants = panel.select(".download-list").map { block ->
            SeriesVariant(
                variant = movieVariant(block),
                title = HtmlUtils.cleanText(block.selectFirst(".title span")),
                qualities = block.select("ul > li.bg-body")
                    .map { parseSeriesQualityItem(it, baseUrl) },
            )
        }

        return Season(
            name = seasonName,
            status = status,
            variants = variants,
            episodeLinksCount = variants.sumOf { v ->
                v.qualities.sumOf { it.episodes.size }
            },
        )
    }

    /** Port of `_series_downloads`. */
    fun seriesDownloads(doc: Element, baseUrl: String): List<Season> {
        val root = doc.selectFirst("#downloads") ?: doc
        return root.select(".download-season.bg-card.rounded")
            .map { parseSeriesSeason(it, baseUrl) }
    }

    // ------------------------------------------------------------------
    // Cast & crew
    // ------------------------------------------------------------------

    /** Port of `_person_entry`. */
    private fun personEntry(anchor: Element, baseUrl: String): PersonRef {
        val thumb = anchor.selectFirst(".person-thumb")
        var image: String? = null
        val style = thumb?.attr("style")
        if (!style.isNullOrBlank()) {
            BACKGROUND_IMAGE.find(style)?.let {
                image = HtmlUtils.absoluteUrl(baseUrl, it.groupValues[2])
            }
        }
        return PersonRef(
            name = HtmlUtils.cleanText(anchor.selectFirst(".person-name")).orEmpty(),
            role = HtmlUtils.cleanText(anchor.selectFirst(".person-role")),
            url = HtmlUtils.absoluteUrl(baseUrl, anchor.attr("href")),
            image = image,
        )
    }

    /** Port of `_persons`. */
    fun persons(doc: Element, baseUrl: String): Pair<List<PersonRef>, List<PersonRef>> {
        val row = doc.selectFirst(".row.persons") ?: return emptyList<PersonRef>() to emptyList()
        var actors: List<PersonRef> = emptyList()
        var directors: List<PersonRef> = emptyList()
        for (listing in row.select(".persons-list")) {
            val heading = HtmlUtils.cleanText(listing.selectFirst("h3")) ?: ""
            val entries = listing.select(".person-item")
                .map { personEntry(it, baseUrl) }
                .filter { it.name.isNotEmpty() || it.url != null }
            if ("کارگردان" in heading) directors = entries else actors = entries
        }
        return actors to directors
    }

    // ------------------------------------------------------------------
    // Suggested
    // ------------------------------------------------------------------

    /** Port of `_suggested`. */
    fun suggested(doc: Element, baseUrl: String): SuggestedSection {
        val section = doc.selectFirst("#related-posts")
            ?: return SuggestedSection(title = null, items = emptyList())
        val seen = mutableSetOf<String>()
        val items = section.select("article.entry").mapNotNull { card ->
            val item = CardExtractor.extractCard(card, baseUrl)
            val key = item.url.ifEmpty { item.title }
            if (key.isNotEmpty()) {
                if (key in seen) return@mapNotNull null
                seen.add(key)
            }
            item
        }
        return SuggestedSection(
            title = HtmlUtils.cleanText(section.selectFirst("#related-posts-title")),
            items = items,
        )
    }

    // ------------------------------------------------------------------
    // Comments
    // ------------------------------------------------------------------

    /** Port of `_comment_entry`. */
    private fun commentEntry(node: Element): Comment {
        val body = node.selectFirst(".commnetbody") ?: node
        val authorNode = body.selectFirst("[itemprop=author]")
        val timeNode = body.selectFirst("time[datetime]")

        var likeCount: Int? = null
        var dislikeCount: Int? = null
        for (button in body.select(".like-dislike button")) {
            val raw = HtmlUtils.cleanText(button.selectFirst(".count")) ?: ""
            val value = digitsIn(raw).firstOrNull()
            when (button.attr("name")) {
                "like" -> likeCount = value
                "dislike" -> dislikeCount = value
            }
        }

        val nodeId = node.attr("id")
        val commentId =
            if (nodeId.startsWith("comment-")) nodeId.substringAfter("comment-") else null

        val replies = node.children()
            .firstOrNull { it.tagName() == "ul" && "children" in it.classNames() }
            ?.select("li.comment")
            ?.map { commentEntry(it) }
            ?: emptyList()

        return Comment(
            id = commentId,
            author = HtmlUtils.cleanText(authorNode).orEmpty(),
            date = timeNode?.attr("datetime")?.takeIf { it.isNotBlank() },
            dateText = HtmlUtils.cleanText(timeNode),
            text = HtmlUtils.cleanText(body.selectFirst("[itemprop=text]")).orEmpty(),
            likeCount = likeCount ?: 0,
            dislikeCount = dislikeCount ?: 0,
            replies = replies,
        )
    }

    /** Port of `_comment_total`. */
    private fun commentTotal(section: Element?): Int? {
        if (section == null) return null
        val header = HtmlUtils.cleanText(section.selectFirst("span.fsz-14.text-muted")) ?: ""
        val digits = translateDigits(header).filter { it.isDigit() }
        return digits.ifEmpty { null }?.toIntOrNull()
    }

    /** Port of `_parse_count` — parses "12.6K", "1,204", "۸". */
    private fun parseCount(text: String?): Int? {
        if (text == null) return null
        val raw = translateDigits(text).trim().replace(",", "")
        val match = COUNT.matchEntire(raw) ?: return null
        val number = match.groupValues[1].toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2].uppercase()) {
            "K" -> 1_000
            "M" -> 1_000_000
            "B" -> 1_000_000_000
            else -> 1
        }
        return (number * multiplier).toInt()
    }

    /** Port of `_comments`. */
    fun comments(doc: Element, baseUrl: String): Comments {
        val section = doc.selectFirst("#reviews") ?: return Comments(
            total = null,
            items = emptyList(),
            count = 0,
            pagination = ListExtractor.pagination(doc, baseUrl),
        )

        val items = section.select("li.comment")
            .filter { node ->
                val parent = node.parent()
                parent == null || "children" !in parent.classNames()
            }
            .map { commentEntry(it) }

        return Comments(
            total = commentTotal(section),
            items = items,
            count = section.select("li.comment").size,
            pagination = ListExtractor.pagination(section, baseUrl),
        )
    }

    // ------------------------------------------------------------------
    // Dubbing
    // ------------------------------------------------------------------

    /** Port of `_voice_cast`. */
    private fun voiceCast(value: String): List<VoiceActor> =
        VOICE_CAST_SPLIT.split(value).mapNotNull { partRaw ->
            val part = partRaw.trim(' ', '‌', '\u200c', '.')
            if (part.isEmpty()) return@mapNotNull null
            val match = VOICE_ROLE.matchEntire(part)
            if (match != null) {
                val roles = match.groupValues[2].split('/')
                    .map { it.trim(' ', '\u200c') }
                    .filter { it.isNotEmpty() }
                VoiceActor(
                    name = match.groupValues[1].trim(' ', '\u200c'),
                    roles = roles,
                )
            } else {
                VoiceActor(name = part, roles = emptyList())
            }
        }

    /** Lines of a dub paragraph: one per <br>. [Element.text] collapses
     *  whitespace — including the newlines substituted for the <br>s — so
     *  re-parse with real newlines and read them back via [Element.wholeText]. */
    private fun dubLines(paragraph: Element): List<String> =
        paragraph.html()
            .replace(Regex("""<br\s*/?>"""), "\n")
            .let(HtmlUtils::parse)
            .body()
            .wholeText()
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** Port of `_dub_block`. */
    private fun dubBlock(lines: List<String>): Dub {
        val title = lines.firstOrNull()
        val fields = mutableMapOf<String, String>()
        val notes = mutableListOf<String>()
        var voiceActors: List<VoiceActor> = emptyList()
        for (line in lines.drop(1)) {
            val idx = line.indexOf(':')
            if (idx < 0) {
                notes.add(line)
                continue
            }
            val key = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim()
            // "گوینده عنوان" (title narrator) is a credit, not the cast.
            if ("گویند" in key && "عنوان" !in key) {
                voiceActors = voiceCast(value)
            } else {
                fields[key] = value
            }
        }
        return Dub(
            title = title,
            fields = fields,
            voiceActors = voiceActors,
            notes = notes,
        )
    }

    /** Port of `_dubbing`. */
    fun dubbing(doc: Element): Dubbing? {
        val button = doc.selectFirst("#openDubbedInfo")
        // The info block ships either as an id or as a class on its root.
        val infoRoot = doc.selectFirst("#dubbed-info, .dubbed-info")
        if (button == null && infoRoot == null) return null

        // Dubs ship either as one <p> per dub or as a single <p> holding
        // every dub separated by "..." lines. Flatten all paragraphs — a
        // separator after each keeps per-paragraph dubs apart — then cut
        // the line stream into one [Dub] per block.
        val lines = infoRoot?.select("p")
            ?.flatMap { dubLines(it) + DUB_SEPARATOR_LINE }
            ?: emptyList()

        val dubs = mutableListOf<Dub>()
        var block = mutableListOf<String>()
        fun flushBlock() {
            if (block.isNotEmpty()) {
                dubs.add(dubBlock(block))
                block = mutableListOf()
            }
        }
        for (line in lines) {
            if (DUB_SEPARATOR.matches(line)) flushBlock() else block.add(line)
        }
        flushBlock()

        return Dubbing(dubs = dubs)
    }

    // ------------------------------------------------------------------
    // Reactions / trailer
    // ------------------------------------------------------------------

    /** Port of `_reactions`. */
    fun reactions(doc: Element): Reactions? {
        var like: Int? = null
        var dislike: Int? = null
        for (button in doc.select("button[ref=post]")) {
            when (button.attr("name")) {
                "like" -> if (like == null) like = parseCount(
                    HtmlUtils.cleanText(button.selectFirst(".count")),
                )
                "dislike" -> if (dislike == null) dislike = parseCount(
                    HtmlUtils.cleanText(button.selectFirst(".count")),
                )
            }
        }
        if (like == null && dislike == null) return null
        return Reactions(likeCount = like ?: 0, dislikeCount = dislike ?: 0)
    }

    /** Port of `_trailer`. */
    fun trailer(doc: Element, baseUrl: String): Trailer? {
        val video = doc.selectFirst("#trailerModal video[src]") ?: return null
        return Trailer(
            url = HtmlUtils.absoluteUrl(baseUrl, video.attr("src")),
            poster = HtmlUtils.absoluteUrl(baseUrl, video.attr("poster")),
        )
    }

    // ------------------------------------------------------------------
    // Whole page
    // ------------------------------------------------------------------

    /** Port of `extract_detail`. */
    fun extractDetail(url: String, html: String): MovieData {
        val doc = HtmlUtils.parse(html)
        val canonicalNode = doc.selectFirst("link[rel=canonical]")
        val canonical = canonicalNode?.let { HtmlUtils.absoluteUrl(url, it.attr("href")) }
        val kind = contentKind(doc, url)
        val (actors, directors) = persons(doc, url)

        val downloads = if (kind == "series") {
            Downloads(
                type = "series",
                seasons = seriesDownloads(doc, url),
            )
        } else {
            Downloads(
                type = "movie",
                variants = movieDownloads(doc, url),
            )
        }

        return MovieData(
            title = HtmlUtils.cleanText(doc.selectFirst("h1.entry-title")),
            pageTitle = HtmlUtils.cleanText(doc.selectFirst("title")),
            contentType = kind,
            url = url,
            canonicalUrl = canonical,
            canonicalDomain = HtmlUtils.domainOf(canonical),
            intro = extractIntro(doc, url),
            downloads = downloads,
            directors = directors,
            actors = actors,
            suggested = suggested(doc, url),
            comments = comments(doc, url),
            trailer = trailer(doc, url),
            reactions = reactions(doc),
            dubbing = dubbing(doc),
        )
    }
}
