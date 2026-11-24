package com.mab.f2mbox.ui.list

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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.mab.f2mbox.ui.common.F2mLazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mab.f2mbox.connect.describeConnectError
import com.mab.f2mbox.data.PageCache
import com.mab.f2mbox.data.remote.ListData
import com.mab.f2mbox.data.remote.ScrapeResult
import com.mab.f2mbox.ui.common.F2mButton
import com.mab.f2mbox.ui.common.FocusTracker
import com.mab.f2mbox.ui.common.GridPosterId
import com.mab.f2mbox.ui.common.IntStateSaver
import com.mab.f2mbox.ui.common.NullableStringListStateSaver
import com.mab.f2mbox.ui.common.PagerNextId
import com.mab.f2mbox.ui.common.PagerPreviousId
import com.mab.f2mbox.ui.common.ScraperLogFeed
import com.mab.f2mbox.ui.common.RestoreFocusOnEnter
import com.mab.f2mbox.ui.common.rememberFocusTracker
import com.mab.f2mbox.ui.connect.LoadingDots
import com.mab.f2mbox.ui.home.LocalHomeStrings
import com.mab.f2mbox.ui.home.PosterCard
import com.mab.f2mbox.ui.detail.MovieDetail
import com.mab.f2mbox.ui.home.formatPage
import com.mab.f2mbox.ui.person.PersonScreen

private val ContentMargin = 48.dp
private val GridColumns = 4
private val GridTopPadding = 28.dp
private val GridItemPadding = 16.dp

sealed interface ListUiState {
    data object Loading : ListUiState
    data class Error(val message: String) : ListUiState
    data class Ready(val result: ScrapeResult) : ListUiState
}

@Composable
fun ListScreen(
    routeKey: String,
    fetchPage: suspend (pageUrl: String?) -> Result<ScrapeResult>,
    pageCache: PageCache,
    onNavigateBack: () -> Unit,
    onOpenUrl: (String) -> Unit = {},
    onPlay: (url: String, title: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    registerEsc: ((() -> Unit)?) -> Unit = {},
) {
    val strings = LocalHomeStrings.current
    var pages by rememberSaveable(routeKey, saver = NullableStringListStateSaver) {
        mutableStateOf(listOf<String?>(null))
    }
    var attempt by rememberSaveable(routeKey, saver = IntStateSaver) { mutableIntStateOf(0) }
    var state by remember(routeKey) {
        mutableStateOf<ListUiState>(
            pageCache[pages.last() ?: routeKey]
                ?.let { ListUiState.Ready(it) }
                ?: ListUiState.Loading,
        )
    }
    val currentPage = pages.last()
    val cacheKey = currentPage ?: routeKey
    val focusTracker = rememberFocusTracker()

    LaunchedEffect(routeKey, currentPage, attempt) {
        val cached = pageCache[cacheKey]
        if (cached != null) {
            state = ListUiState.Ready(cached)
            return@LaunchedEffect
        }
        state = ListUiState.Loading
        fetchPage(currentPage).fold(
            onSuccess = {
                state = ListUiState.Ready(it)
                pageCache.put(cacheKey, it)
            },
            onFailure = { state = ListUiState.Error(describeConnectError(it)) },
        )
    }

    RememberListPageBackHandler(
        routeKey = routeKey,
        canPopPage = pages.size > 1,
        onPopPage = { pages = pages.dropLast(1) },
        onNavigateBack = onNavigateBack,
        registerEsc = registerEsc,
    )

    when (val current = state) {
        ListUiState.Loading -> Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LoadingDots()
            Spacer(Modifier.height(16.dp))
            ScraperLogFeed()
        }

        is ListUiState.Error -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = current.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    F2mButton(onClick = onNavigateBack) {
                        Text(strings.back)
                    }
                    F2mButton(onClick = { attempt++ }) {
                        Text(strings.retry)
                    }
                }
            }
        }

        is ListUiState.Ready -> when (val result = current.result) {
            is ScrapeResult.Listing -> ListContent(
                data = result.data,
                onNavigateBack = onNavigateBack,
                onOpenUrl = onOpenUrl,
                focusTracker = focusTracker,
                hasPrevious = pages.size > 1,
                onPrevious = { pages = pages.dropLast(1) },
                hasNext = result.data.pagination.next != null,
                onNext = { result.data.pagination.next?.let { pages = pages + it } },
                modifier = modifier,
            )
            is ScrapeResult.Movie -> MovieDetail(
                data = result.data,
                fetchPage = fetchPage,
                onOpenUrl = onOpenUrl,
                onPlay = onPlay,
                onNavigateBack = onNavigateBack,
                modifier = modifier,
            )
            is ScrapeResult.Person -> PersonScreen(
                data = result.data,
                onNavigateBack = onNavigateBack,
                onOpenUrl = onOpenUrl,
                focusTracker = focusTracker,
                hasPrevious = pages.size > 1,
                onPrevious = { pages = pages.dropLast(1) },
                hasNext = result.data.pagination.next != null,
                onNext = { result.data.pagination.next?.let { pages = pages + it } },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun ListContent(
    data: ListData,
    onNavigateBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    focusTracker: FocusTracker,
    hasPrevious: Boolean,
    onPrevious: () -> Unit,
    hasNext: Boolean,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalHomeStrings.current
    RestoreFocusOnEnter(data) {
        focusTracker.restore(fallbackId = GridPosterId(0))
    }

    Column(modifier = modifier.fillMaxSize()) {
        ListHeader(data = data, onNavigateBack = onNavigateBack)

        if (data.items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = strings.noResults,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        val gridState = rememberLazyGridState()
        F2mLazyVerticalGrid(
            columns = GridCells.Fixed(GridColumns),
            state = gridState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(
                start = ContentMargin,
                end = ContentMargin,
                top = GridTopPadding,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(data.items.size, key = { data.items[it].url }) { index ->
                val posterId = GridPosterId(index)
                PosterCard(
                    item = data.items[index],
                    onClick = { onOpenUrl(data.items[index].url) },
                    onFocus = { focusTracker.onFocused(posterId) },
                    modifier = Modifier
                        .focusRequester(focusTracker.requesterFor(posterId))
                        .fillMaxWidth()
                        .padding(GridItemPadding),
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                PagerControls(
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    focusTracker = focusTracker,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ListHeader(
    data: ListData,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalHomeStrings.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ContentMargin, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        F2mButton(onClick = onNavigateBack) {
            Text(strings.back)
        }
        Text(
            text = data.pageTitle ?: data.title.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        data.pagination.current?.let { page ->
            Text(
                text = strings.formatPage(page),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun PagerControls(
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    focusTracker: FocusTracker? = null,
    modifier: Modifier = Modifier,
) {
    val strings = LocalHomeStrings.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    ) {
        F2mButton(
            onClick = onPrevious,
            enabled = hasPrevious,
            modifier = pagerButtonModifier(focusTracker, PagerPreviousId),
        ) {
            Text(strings.previous)
        }
        F2mButton(
            onClick = onNext,
            enabled = hasNext,
            modifier = pagerButtonModifier(focusTracker, PagerNextId),
        ) {
            Text(strings.next)
        }
    }
}

private fun pagerButtonModifier(focusTracker: FocusTracker?, id: String): Modifier {
    if (focusTracker == null) return Modifier
    return Modifier
        .focusRequester(focusTracker.requesterFor(id))
        .onFocusChanged { if (it.isFocused) focusTracker.onFocused(id) }
}