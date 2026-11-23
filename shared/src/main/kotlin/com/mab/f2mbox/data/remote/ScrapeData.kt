package com.mab.f2mbox.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Payload of `GET /scrape` for the `list` response type: one page of a
 * listing (genre archive, movies, series, top charts, …) with its
 * pagination links. Items reuse [SectionItem], the same shape the home
 * carousels carry. Other scrape types (detail, …) are not modeled yet.
 */
@Serializable
data class ListData(
    val title: String? = null,
    @SerialName("page_title") val pageTitle: String? = null,
    val description: String? = null,
    val url: String? = null,
    @SerialName("canonical_url") val canonicalUrl: String? = null,
    /** Domain only of [canonicalUrl], e.g. `www.myf2ms.top`. */
    @SerialName("canonical_domain") val canonicalDomain: String? = null,
    val items: List<SectionItem> = emptyList(),
    val pagination: Pagination = Pagination(),
)

/**
 * Pagination of a [ListData] page: the current page number, the url of the
 * next page when there is one, and a (partial) list of known page links.
 * Going back a page is handled by the UI's own visited-url stack.
 */
@Serializable
data class Pagination(
    val current: Int? = null,
    val next: String? = null,
    val pages: List<PageLink> = emptyList(),
)

/** One known page of a [Pagination]: its number and listing url. */
@Serializable
data class PageLink(
    val page: Int = 0,
    val url: String,
)

/**
 * A scraped page, dispatched on the envelope's `type` field: listing pages
 * render as the poster grid, movie pages as the detail screen. New scrape
 * types (series, …) are added here as their models land.
 */
sealed interface ScrapeResult {
    /** `type: "list"` — a genre archive, search results, a chart, … */
    data class Listing(val data: ListData) : ScrapeResult

    /**
     * `type: "movie"` / `type: "series"` — a movie's or series' detail page
     * with its download links; the two share one payload shape (series
     * downloads carry seasons of episodes instead of per-file variants).
     */
    data class Movie(val data: MovieData) : ScrapeResult

    /** `type: "person"` — an actor's or director's archive of titles. */
    data class Person(val data: PersonData) : ScrapeResult
}

/**
 * Payload of a `type: "person"` scrape: the person's profile plus every
 * title they worked on, one page at a time. Items reuse [SectionItem], the
 * same shape listing pages carry.
 */
@Serializable
data class PersonData(
    val title: String? = null,
    val description: String? = null,
    val url: String? = null,
    @SerialName("canonical_url") val canonicalUrl: String? = null,
    /** Domain only of [canonicalUrl], e.g. `www.myf2ms.top`. */
    @SerialName("canonical_domain") val canonicalDomain: String? = null,
    val person: PersonProfile? = null,
    val items: List<SectionItem> = emptyList(),
    val pagination: Pagination = Pagination(),
)

/** The person a [PersonData] archive page is about. */
@Serializable
data class PersonProfile(
    /** Role kind, e.g. "actor" or "director". */
    val kind: String? = null,
    val name: String? = null,
    val photo: ImageRef? = null,
    val slug: String? = null,
)

/**
 * Payload of a `type: "movie"` scrape: the intro block (poster, synopsis,
 * meta) plus the download variants and their qualities/links.
 */
@Serializable
data class MovieData(
    val title: String? = null,
    @SerialName("page_title") val pageTitle: String? = null,
    @SerialName("content_type") val contentType: String? = null,
    val url: String? = null,
    @SerialName("canonical_url") val canonicalUrl: String? = null,
    /** Domain only of [canonicalUrl], e.g. `www.myf2ms.top`. */
    @SerialName("canonical_domain") val canonicalDomain: String? = null,
    val intro: MovieIntro? = null,
    val downloads: Downloads? = null,
    val directors: List<PersonRef> = emptyList(),
    val actors: List<PersonRef> = emptyList(),
    val suggested: SuggestedSection? = null,
    val comments: Comments? = null,
    val trailer: Trailer? = null,
    val reactions: Reactions? = null,
    val dubbing: Dubbing? = null,
)

/** A person on a [MovieData] detail page: director or cast member. */
@Serializable
data class PersonRef(
    val name: String,
    val role: String? = null,
    val image: String? = null,
    val url: String? = null,
)

/** A "suggested titles" row of a [MovieData]; items reuse [SectionItem]. */
@Serializable
data class SuggestedSection(
    val title: String? = null,
    val items: List<SectionItem> = emptyList(),
)

/** The comments block of a [MovieData]: one page of the comment thread. */
@Serializable
data class Comments(
    val count: Int? = null,
    val total: Int? = null,
    val items: List<Comment> = emptyList(),
    val pagination: Pagination = Pagination(),
)

/** One comment of a [Comments] page; [replies] nests one level deep. */
@Serializable
data class Comment(
    val id: String? = null,
    val author: String,
    val text: String,
    val date: String? = null,
    @SerialName("date_text") val dateText: String? = null,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("dislike_count") val dislikeCount: Int = 0,
    val replies: List<Comment> = emptyList(),
)

/** The intro block of a [MovieData]: poster, synopsis and metadata. */
@Serializable
data class MovieIntro(
    val title: String? = null,
    val poster: ImageRef? = null,
    val excerpt: String? = null,
    val formats: List<String> = emptyList(),
    val genres: List<GenreRef> = emptyList(),
    val info: MovieInfo? = null,
    val notes: List<String> = emptyList(),
    @SerialName("latest_episode_note") val latestEpisodeNote: String? = null,
    @SerialName("satisfaction_percent") val satisfactionPercent: String? = null,
    @SerialName("post_id") val postId: String? = null,
)

/** Release metadata of a [MovieIntro]. */
@Serializable
data class MovieInfo(
    val countries: String? = null,
    val duration: String? = null,
    @SerialName("duration_minutes") val durationMinutes: Int? = null,
    val language: String? = null,
    val published: String? = null,
    val release: String? = null,
)

/**
 * The download block of a [MovieData]: movies carry one entry per
 * dubbed/subbed [variant]; series instead carry [seasons] of episode links.
 */
@Serializable
data class Downloads(
    val type: String? = null,
    val variants: List<DownloadVariant> = emptyList(),
    val seasons: List<Season> = emptyList(),
)

/**
 * One download variant of a [Downloads] block, e.g. the hardsub or the
 * Farsi-dubbed cut, holding every quality it ships in.
 */
@Serializable
data class DownloadVariant(
    val title: String? = null,
    val variant: String? = null,
    val qualities: List<DownloadQuality> = emptyList(),
)

/** One quality of a [DownloadVariant], with the links that serve it. */
@Serializable
data class DownloadQuality(
    val quality: String? = null,
    val encoder: String? = null,
    val notes: String? = null,
    val links: List<DownloadLink> = emptyList(),
)

/**
 * One link of a [DownloadQuality]: either a direct file download or the
 * site's online-player page for that quality.
 */
@Serializable
data class DownloadLink(
    val label: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    @SerialName("play_url") val playUrl: String? = null,
)

/** One season of a series [Downloads] block: its title/status and variants. */
@Serializable
data class Season(
    val name: String? = null,
    val status: String? = null,
    @SerialName("episode_links_count") val episodeLinksCount: Int = 0,
    val variants: List<SeriesVariant> = emptyList(),
)

/**
 * One download variant of a [Season] (hardsub / dubbed cut), shaped like a
 * [DownloadVariant] but with per-episode qualities.
 */
@Serializable
data class SeriesVariant(
    val title: String? = null,
    val variant: String? = null,
    val qualities: List<SeriesQuality> = emptyList(),
)

/** One quality of a [SeriesVariant], holding one link per episode. */
@Serializable
data class SeriesQuality(
    val quality: String? = null,
    @SerialName("episode_count") val episodeCount: Int = 0,
    val episodes: List<EpisodeLink> = emptyList(),
)

/**
 * One episode of a [SeriesQuality]: its label ("قسمت 01"), file name and
 * its direct download / online-play urls.
 */
@Serializable
data class EpisodeLink(
    val label: String? = null,
    val filename: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    @SerialName("play_url") val playUrl: String? = null,
)

/** The trailer of a [MovieData]: its poster image and direct video url. */
@Serializable
data class Trailer(
    val poster: String? = null,
    val url: String? = null,
)

/** Like/dislike tallies of a [MovieData] page. */
@Serializable
data class Reactions(
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("dislike_count") val dislikeCount: Int = 0,
)

/** The dubbing-info block of a [MovieData]: one entry per Persian dub. */
@Serializable
data class Dubbing(
    val dubs: List<Dub> = emptyList(),
)

/** One dub of a [Dubbing] block: credits, notes and its voice actors. */
@Serializable
data class Dub(
    val title: String? = null,
    /** Credit labels to values, e.g. "مترجم" to the translator's name. */
    val fields: Map<String, String> = emptyMap(),
    val notes: List<String> = emptyList(),
    @SerialName("voice_actors") val voiceActors: List<VoiceActor> = emptyList(),
)

/** One voice actor of a [Dub]: their name and the roles they voice. */
@Serializable
data class VoiceActor(
    val name: String,
    val roles: List<String> = emptyList(),
)

/**
 * Normalizes image URLs (such as person/actor photos) replacing
 * `/images/`, `/images/M/`, etc., with `/wp-content/uploads/thumbs/`.
 */
fun normalizeImageUrl(url: String?): String? {
    if (url.isNullOrBlank()) return url
    return url
        .replace("/images/M/", "/wp-content/uploads/thumbs/")
        .replace("/images/", "/wp-content/uploads/thumbs/")
        .replace("/image/M/", "/wp-content/uploads/thumbs/")
        .replace("/image/", "/wp-content/uploads/thumbs/")
}
