package com.mab.f2mbox.ui.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.mab.f2mbox.data.remote.MovieData
import com.mab.f2mbox.data.remote.PersonData
import com.mab.f2mbox.data.remote.ScrapeResult
import com.mab.f2mbox.ui.common.FocusTracker

/** Platform movie/person UIs reached from a shared [ListScreen]. */
class ListScrapeSlots(
    val movie: @Composable (
        data: MovieData,
        fetchPage: suspend (pageUrl: String?) -> Result<ScrapeResult>,
        onOpenUrl: (String) -> Unit,
        onPlay: (url: String, title: String) -> Unit,
        onNavigateBack: () -> Unit,
        modifier: Modifier,
    ) -> Unit,
    val person: @Composable (
        data: PersonData,
        onNavigateBack: () -> Unit,
        onOpenUrl: (String) -> Unit,
        focusTracker: FocusTracker,
        hasPrevious: Boolean,
        onPrevious: () -> Unit,
        hasNext: Boolean,
        onNext: () -> Unit,
        modifier: Modifier,
    ) -> Unit,
)

val LocalListScrapeSlots = staticCompositionLocalOf<ListScrapeSlots> {
    error("LocalListScrapeSlots not provided")
}