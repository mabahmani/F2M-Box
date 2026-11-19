package com.mab.f2mbox.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Payload of `GET /home`. Only the fields the app consumes are modeled;
 * `ignoreUnknownKeys` drops the rest (json_ld, navigation, open_graph, …).
 */
@Serializable
data class HomeData(
    val title: String? = null,
    @SerialName("hero_slider") val heroSlider: List<HeroSlide> = emptyList(),
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
