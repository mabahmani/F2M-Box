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
