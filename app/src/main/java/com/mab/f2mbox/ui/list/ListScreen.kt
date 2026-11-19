@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mab.f2mbox.ui.list

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mab.f2mbox.R
import com.mab.f2mbox.data.remote.ListData
import com.mab.f2mbox.ui.connect.describe
import com.mab.f2mbox.ui.home.PosterCard
import kotlinx.coroutines.delay

private val ContentMargin = 48.dp
private val GridColumns = 4
// Focused cards scale up 1.08x; this plus each cell's own padding clears
// the grid's top clip edge so the enlarged card isn't cut off.
private val GridTopPadding = 28.dp
// Breathing room inside every grid cell, so the wide cards keep an even
// gutter: column gap is 2x this, row gap 2x + the 4dp between rows.
private val GridItemPadding = 8.dp

/** UI state of the listing screen. */
sealed interface ListUiState {
    data object Loading : ListUiState
    data class Error(@StringRes val messageRes: Int) : ListUiState
    data class Ready(val data: ListData) : ListUiState
}

/**
 * Listing screen: a grid of posters for one page of a listing, with
 * previous/next pagination.
 *
 * The first page comes from [fetchPage] with a null url (a scrape for a
 * listing, a search for a query); every later page is fetched by passing
 * its pagination url. Backward navigation is handled by the screen's own
 * visited-url stack (the payload's page list is partial), so the remote's
 * Back key pops one page before leaving via [onNavigateBack]. [routeKey]
 * identifies the origin — it keys the page stack and retry state.
 */
@Composable
fun ListScreen(
    routeKey: String,
    fetchPage: suspend (pageUrl: String?) -> Result<ListData>,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Visited page urls, newest last; the origin page is a null marker.
    // Keyed on the route key so a new listing/search starts a fresh stack.
    var pages by remember(routeKey) { mutableStateOf(listOf<String?>(null as String?)) }
    var attempt by remember(routeKey) { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<ListUiState>(ListUiState.Loading) }
    val currentPage = pages.last()

    LaunchedEffect(currentPage, attempt) {
        state = ListUiState.Loading
        fetchPage(currentPage).fold(
            onSuccess = { state = ListUiState.Ready(it) },
            onFailure = { state = ListUiState.Error(describe(it)) }
        )
    }

    BackHandler {
        if (pages.size > 1) pages = pages.dropLast(1) else onNavigateBack()
    }

    when (val current = state) {
        ListUiState.Loading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.loading),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        is ListUiState.Error -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(current.messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = { attempt++ }) {
                    Text(stringResource(R.string.retry))
                }
            }
        }

        is ListUiState.Ready -> ListContent(
            data = current.data,
            onNavigateBack = onNavigateBack,
            hasPrevious = pages.size > 1,
            onPrevious = { pages = pages.dropLast(1) },
            hasNext = current.data.pagination.next != null,
            onNext = { current.data.pagination.next?.let { pages = pages + it } },
            modifier = modifier
        )
    }
}

/**
 * One listing page: a title bar with a back-to-home button and the page
 * number on top, the poster grid, and previous/next controls spanning the
 * last grid row so the D-pad reaches them right after the final poster.
 */
@Composable
private fun ListContent(
    data: ListData,
    onNavigateBack: () -> Unit,
    hasPrevious: Boolean,
    onPrevious: () -> Unit,
    hasNext: Boolean,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Grabs focus for the first poster once the grid attaches, the same way
    // the hero does on home.
    val firstPosterFocus = remember { FocusRequester() }
    LaunchedEffect(data) {
        while (!firstPosterFocus.requestFocus()) delay(50)
    }

    Column(modifier = modifier.fillMaxSize()) {
        ListHeader(data = data, onNavigateBack = onNavigateBack)

        if (data.items.isEmpty()) {
            // Mostly for searches: the backend sometimes returns no matches.
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_results),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(GridColumns),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(
                start = ContentMargin,
                end = ContentMargin,
                top = GridTopPadding,
                bottom = 32.dp
            ),
            // Cells sit flush against each other; each card stretches to
            // fill its cell, inset by [GridItemPadding] on every side, so
            // the row width mostly goes to the posters with an even gutter.
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(data.items.size, key = { data.items[it].url }) { index ->
                PosterCard(
                    item = data.items[index],
                    modifier = (if (index == 0) {
                        Modifier.focusRequester(firstPosterFocus)
                    } else {
                        Modifier
                    })
                        .fillMaxWidth()
                        .padding(GridItemPadding)
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                PagerControls(
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/** Title bar: back-to-home button, the archive title and the page number. */
@Composable
private fun ListHeader(
    data: ListData,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ContentMargin, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(onClick = onNavigateBack) {
            Text(stringResource(R.string.back_to_home))
        }
        Text(
            text = data.pageTitle ?: data.title.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        data.pagination.current?.let { page ->
            Text(
                text = stringResource(R.string.page_format, page),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

/** Centered previous/next buttons; each is disabled when unavailable. */
@Composable
private fun PagerControls(
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
    ) {
        Button(onClick = onPrevious, enabled = hasPrevious) {
            Text(stringResource(R.string.previous))
        }
        Button(onClick = onNext, enabled = hasNext) {
            Text(stringResource(R.string.next))
        }
    }
}
