package com.mab.f2mbox.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Payload of `GET /home`. Only the fields the app consumes are modeled;
 * `ignoreUnknownKeys` drops the rest (json_ld, open_graph, …).
 */
@Serializable
data class HomeData(
    val title: String? = null,
    @SerialName("hero_slider") val heroSlider: List<HeroSlide> = emptyList(),
    val sections: List<HomeSection> = emptyList(),
    val navigation: HomeNavigation? = null,
)

/**
 * The home payload's `navigation` block: quick links to the main listings
 * (movies, series, top charts) plus genre filters for each content type.
 */
@Serializable
data class HomeNavigation(
    val sections: List<NavSection> = emptyList(),
    @SerialName("movie_genres") val movieGenres: List<GenreLink> = emptyList(),
    @SerialName("series_genres") val seriesGenres: List<GenreLink> = emptyList(),
)

/** One quick link of the navigation block, e.g. "فیلم ها" or "250 فیلم برتر". */
@Serializable
data class NavSection(
    val name: String,
    val url: String,
)

/** One genre filter link with the number of titles filed under it. */
@Serializable
data class GenreLink(
    val name: String,
    val url: String,
    val count: Int = 0,
)

/**
 * One entry of the home hero slider: a full landscape backdrop plus a small
 * vertical poster, title, rating and year.
 */
@Serializable
data class HeroSlide(
    val title: String,
    val url: String,
    val poster: String? = null,
    val rating: Double? = null,
    val year: Int? = null,
    val backdrop: ImageRef? = null,
)

/** An image with its alt text (used for accessibility). */
@Serializable
data class ImageRef(
    val url: String? = null,
    val alt: String? = null,
)

/**
 * One home carousel ("carougsel-postsN"): a titled horizontal row of
 * posters, e.g. "فیلم‌های به روز شده" or "سریال‌های کره‌ای".
 */
@Serializable
data class HomeSection(
    val id: String? = null,
    val title: String? = null,
    val url: String? = null,
    val items: List<SectionItem> = emptyList(),
)

/** One poster card inside a [HomeSection]. */
@Serializable
data class SectionItem(
    val title: String,
    val url: String,
    @SerialName("link_title") val linkTitle: String? = null,
    val poster: ImageRef? = null,
    val rating: Double? = null,
    val year: Int? = null,
    @SerialName("status_text") val statusText: String? = null,
    val formats: List<String> = emptyList(),
    val genres: List<GenreRef> = emptyList(),
)

/** A genre tag attached to a [SectionItem]. */
@Serializable
data class GenreRef(
    val name: String? = null,
    val url: String? = null,
)
