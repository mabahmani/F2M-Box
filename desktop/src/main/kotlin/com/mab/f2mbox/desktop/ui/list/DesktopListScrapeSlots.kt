package com.mab.f2mbox.desktop.ui.list

import com.mab.f2mbox.desktop.ui.detail.MovieDetail
import com.mab.f2mbox.desktop.ui.person.PersonScreen
import com.mab.f2mbox.ui.list.ListScrapeSlots

fun desktopListScrapeSlots(): ListScrapeSlots = ListScrapeSlots(
    movie = { data, fetchPage, onOpenUrl, onPlay, onNavigateBack, modifier ->
        MovieDetail(
            data = data,
            fetchPage = fetchPage,
            onOpenUrl = onOpenUrl,
            onPlay = onPlay,
            onNavigateBack = onNavigateBack,
            modifier = modifier,
        )
    },
    person = { data, onNavigateBack, onOpenUrl, focusTracker, hasPrevious, onPrevious, hasNext, onNext, modifier ->
        PersonScreen(
            data = data,
            onNavigateBack = onNavigateBack,
            onOpenUrl = onOpenUrl,
            focusTracker = focusTracker,
            hasPrevious = hasPrevious,
            onPrevious = onPrevious,
            hasNext = hasNext,
            onNext = onNext,
            modifier = modifier,
        )
    },
)