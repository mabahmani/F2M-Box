@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mab.f2mbox.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.mab.f2mbox.R
import com.mab.f2mbox.ui.home.HomeText

@Composable
fun rememberHomeStrings(): com.mab.f2mbox.ui.home.HomeStrings =
    com.mab.f2mbox.ui.home.HomeStrings(
        back = stringResource(R.string.back),
        retry = stringResource(R.string.retry),
        clearCacheRefresh = stringResource(R.string.clear_cache_refresh),
        viewLogs = stringResource(R.string.view_logs),
        seeMore = stringResource(R.string.see_more),
        ratingFormat = stringResource(R.string.rating_format),
        search = stringResource(R.string.search),
        searchHint = stringResource(R.string.search_hint),
        searchHintDetail = stringResource(R.string.search_hint_detail),
        continueWatchingSectionTitle = "ادامه تماشا",
        heroPreviousSlide = HomeText.heroPreviousSlide,
        heroNextSlide = HomeText.heroNextSlide,
        heroOpenSlide = HomeText.heroOpenSlide,
        noResults = stringResource(R.string.no_results),
        pageFormat = stringResource(R.string.page_format),
        previous = stringResource(R.string.previous),
        next = stringResource(R.string.next),
    )