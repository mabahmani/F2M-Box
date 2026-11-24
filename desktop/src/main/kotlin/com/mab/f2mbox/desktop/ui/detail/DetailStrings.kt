package com.mab.f2mbox.desktop.ui.detail

import com.mab.f2mbox.desktop.platform.Strings
import com.mab.f2mbox.ui.detail.DetailStrings
import com.mab.f2mbox.ui.detail.defaultDetailStrings

fun desktopDetailStrings(): DetailStrings = defaultDetailStrings(
    back = Strings.back,
    previous = Strings.previous,
).copy(
    loading = Strings.loading,
    castAndCrew = Strings.castAndCrew,
    dubbing = Strings.dubbing,
    voiceActors = Strings.voiceActors,
    downloads = Strings.downloads,
    suggested = Strings.suggested,
    comments = Strings.comments,
    moreComments = Strings.moreComments,
    commentsError = Strings.commentsError,
    likeCount = Strings.likeCount,
    dislikeCount = Strings.dislikeCount,
    infoRelease = Strings.infoRelease,
    infoPublished = Strings.infoPublished,
    infoDuration = Strings.infoDuration,
    infoDurationValue = Strings.infoDurationValue,
    infoCountries = Strings.infoCountries,
    infoLanguage = Strings.infoLanguage,
    playerPlay = Strings.playerPlay,
    trailer = Strings.trailer,
    watchTrailer = Strings.watchTrailer,
)