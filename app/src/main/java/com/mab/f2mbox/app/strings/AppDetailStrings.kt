@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mab.f2mbox.app.strings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.mab.f2mbox.R
import com.mab.f2mbox.ui.detail.DetailStrings
import com.mab.f2mbox.ui.detail.defaultDetailStrings

@Composable
fun rememberAppDetailStrings(): DetailStrings = defaultDetailStrings(
    back = stringResource(R.string.back),
    previous = stringResource(R.string.previous),
).copy(
    castAndCrew = stringResource(R.string.cast_and_crew),
    dubbing = stringResource(R.string.dubbing),
    voiceActors = stringResource(R.string.voice_actors),
    downloads = stringResource(R.string.downloads),
    suggested = stringResource(R.string.suggested),
    comments = stringResource(R.string.comments),
    moreComments = stringResource(R.string.more_comments),
    commentsError = stringResource(R.string.comments_error),
    likeCount = stringResource(R.string.like_count),
    dislikeCount = stringResource(R.string.dislike_count),
    infoRelease = stringResource(R.string.info_release),
    infoPublished = stringResource(R.string.info_published),
    infoDuration = stringResource(R.string.info_duration),
    infoDurationValue = stringResource(R.string.info_duration_value),
    infoCountries = stringResource(R.string.info_countries),
    infoLanguage = stringResource(R.string.info_language),
    playerPlay = stringResource(R.string.player_play),
    trailer = stringResource(R.string.trailer),
    watchTrailer = stringResource(R.string.watch_trailer),
    loading = stringResource(R.string.loading),
)