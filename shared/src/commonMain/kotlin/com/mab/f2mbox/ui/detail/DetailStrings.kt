package com.mab.f2mbox.ui.detail

import androidx.compose.runtime.staticCompositionLocalOf

data class DetailStrings(
    val back: String,
    val loading: String,
    val castAndCrew: String,
    val dubbing: String,
    val voiceActors: String,
    val downloads: String,
    val suggested: String,
    val comments: String,
    val moreComments: String,
    val commentsError: String,
    val likeCount: String,
    val dislikeCount: String,
    val infoRelease: String,
    val infoPublished: String,
    val infoDuration: String,
    val infoDurationValue: String,
    val infoCountries: String,
    val infoLanguage: String,
    val previous: String,
    val playerPlay: String,
    val trailer: String,
    val watchTrailer: String,
)

object DetailText {
    const val loading = "Loading…\u200E"
    const val castAndCrew = "Cast & crew"
    const val dubbing = "Dubbing"
    const val voiceActors = "Voice actors"
    const val downloads = "Play"
    const val suggested = "Suggested"
    const val comments = "Comments (%1\$d)\u200E"
    const val moreComments = "More comments"
    const val commentsError = "Couldn't load comments.\u200E"
    const val likeCount = "👍 %1\$d"
    const val dislikeCount = "👎 %1\$d"
    const val infoRelease = "Release"
    const val infoPublished = "Published"
    const val infoDuration = "Duration"
    const val infoDurationValue = "%1\$d min\u200E"
    const val infoCountries = "Countries"
    const val infoLanguage = "Language"
    const val playerPlay = "Play"
    const val trailer = "Trailer"
    const val watchTrailer = "Watch trailer"
}

fun defaultDetailStrings(back: String, previous: String): DetailStrings = DetailStrings(
    back = back,
    loading = DetailText.loading,
    castAndCrew = DetailText.castAndCrew,
    dubbing = DetailText.dubbing,
    voiceActors = DetailText.voiceActors,
    downloads = DetailText.downloads,
    suggested = DetailText.suggested,
    comments = DetailText.comments,
    moreComments = DetailText.moreComments,
    commentsError = DetailText.commentsError,
    likeCount = DetailText.likeCount,
    dislikeCount = DetailText.dislikeCount,
    infoRelease = DetailText.infoRelease,
    infoPublished = DetailText.infoPublished,
    infoDuration = DetailText.infoDuration,
    infoDurationValue = DetailText.infoDurationValue,
    infoCountries = DetailText.infoCountries,
    infoLanguage = DetailText.infoLanguage,
    previous = previous,
    playerPlay = DetailText.playerPlay,
    trailer = DetailText.trailer,
    watchTrailer = DetailText.watchTrailer,
)

val LocalDetailStrings = staticCompositionLocalOf<DetailStrings> {
    error("LocalDetailStrings not provided")
}

fun DetailStrings.formatComments(count: Int): String =
    comments.replace("%1\$d", count.toString())

fun DetailStrings.formatLikeCount(count: Int): String =
    likeCount.replace("%1\$d", count.toString())

fun DetailStrings.formatDislikeCount(count: Int): String =
    dislikeCount.replace("%1\$d", count.toString())

fun DetailStrings.formatDurationMinutes(minutes: Int): String =
    infoDurationValue.replace("%1\$d", minutes.toString())