package com.mab.f2mbox.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mab.f2mbox.connect.describeConnectError
import com.mab.f2mbox.data.remote.HomeData
import com.mab.f2mbox.data.remote.HomeSection
import com.mab.f2mbox.scraper.F2MScraper
import com.mab.f2mbox.ui.common.F2mButton
import com.mab.f2mbox.ui.common.F2mIconButton
import com.mab.f2mbox.ui.common.FocusTracker
import com.mab.f2mbox.ui.common.IntStateSaver
import com.mab.f2mbox.ui.common.LocalWatchedItems
import com.mab.f2mbox.ui.common.ScraperLogFeed
import com.mab.f2mbox.ui.common.continueWatchingSection
import com.mab.f2mbox.ui.common.LocalUsesTouchInput
import com.mab.f2mbox.ui.common.RestoreFocusOnEnter
import com.mab.f2mbox.ui.common.rememberFocusTracker
import com.mab.f2mbox.ui.connect.LoadingDots

/**
 * Home screen. Loads the homepage payload once and renders the hero slider,
 * the navigation block and the carousels.
 */
@Composable
fun HomeScreen(
    scraper: F2MScraper,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenUrl: (String) -> Unit = {},
    onSearch: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    initialHome: HomeData? = null,
    activeDomain: String? = null,
    onHomeLoaded: (HomeData) -> Unit = {},
    onClearCache: () -> Unit = { scraper.clearCache() },
    onOpenLogs: () -> Unit = {},
    onExit: () -> Unit = {},
) {
    val homeStrings = LocalHomeStrings.current
    var state by remember {
        mutableStateOf<HomeUiState>(
            if (initialHome != null) HomeUiState.Ready(initialHome) else HomeUiState.Loading,
        )
    }
    var fetchToken by rememberSaveable(saver = IntStateSaver) { mutableIntStateOf(0) }
    var fetchedToken by rememberSaveable(saver = IntStateSaver) { mutableIntStateOf(0) }
    val watchedReader = LocalWatchedItems.current
    val watchedSection = remember(fetchToken) {
        continueWatchingSection(
            watchedReader.load(),
            homeStrings.continueWatchingSectionTitle,
        )
    }
    val focusTracker = rememberFocusTracker()

    LaunchedEffect(fetchToken) {
        if (fetchToken == fetchedToken && initialHome != null) return@LaunchedEffect
        state = HomeUiState.Loading
        scraper.home().fold(
            onSuccess = {
                state = HomeUiState.Ready(it)
                fetchedToken = fetchToken
                onHomeLoaded(it)
            },
            onFailure = { state = HomeUiState.Error(describeConnectError(it)) },
        )
    }

    when (val current = state) {
        HomeUiState.Loading -> Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LoadingDots()
            Spacer(Modifier.height(16.dp))
            ScraperLogFeed()
        }

        is HomeUiState.Error -> Box(
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
                    F2mButton(onClick = onExit) {
                        Text(homeStrings.back)
                    }
                    F2mButton(onClick = { fetchToken++ }) {
                        Text(homeStrings.retry)
                    }
                }
            }
        }

        is HomeUiState.Ready -> HomeContent(
            home = current.home,
            watchedSection = watchedSection,
            activeDomain = activeDomain,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onOpenUrl = onOpenUrl,
            onSearch = onSearch,
            focusTracker = focusTracker,
            onRefresh = {
                onClearCache()
                fetchToken++
            },
            onOpenLogs = onOpenLogs,
            modifier = modifier,
        )
    }
}

@Composable
private fun HomeContent(
    home: HomeData,
    watchedSection: HomeSection?,
    activeDomain: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onSearch: (String) -> Unit,
    focusTracker: FocusTracker,
    onRefresh: () -> Unit,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val homeStrings = LocalHomeStrings.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val usesTouch = LocalUsesTouchInput.current
        val heroHeight = if (usesTouch) {
            (maxHeight * 0.45f).coerceAtMost(420.dp)
        } else {
            maxHeight
        }
        val listState = rememberLazyListState()

        val heroFocus = focusTracker.requesterFor(HeroFocusId)
        RestoreFocusOnEnter(home) {
            focusTracker.restore(fallbackId = HeroFocusId)
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!activeDomain.isNullOrBlank()) {
                        Text(
                            text = activeDomain,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                    MaterialTheme.shapes.small,
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    F2mIconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(36.dp),
                        showRestingOutline = false,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = homeStrings.clearCacheRefresh,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    F2mIconButton(
                        onClick = onOpenLogs,
                        modifier = Modifier.size(36.dp),
                        showRestingOutline = false,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Terminal,
                            contentDescription = homeStrings.viewLogs,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            if (home.heroSlider.isNotEmpty()) {
                item {
                    HeroSlider(
                        items = home.heroSlider,
                        focusRequester = heroFocus,
                        onSlideClick = onOpenUrl,
                        onFocused = { focusTracker.onFocused(HeroFocusId) },
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .height(heroHeight),
                    )
                }
            }
            home.navigation?.let { nav ->
                item {
                    NavigationBlock(
                        navigation = nav,
                        searchQuery = searchQuery,
                        onSearchQueryChange = onSearchQueryChange,
                        onOpenUrl = onOpenUrl,
                        onSearch = onSearch,
                        focusTracker = focusTracker,
                    )
                }
            }
            watchedSection?.let { section ->
                item(key = section.id) {
                    SectionRow(
                        section = section,
                        onOpenUrl = onOpenUrl,
                        focusTracker = focusTracker,
                        rowKey = WatchedSectionRowKey,
                    )
                }
            }
            home.sections.forEachIndexed { index, section ->
                item(key = section.id) {
                    SectionRow(
                        section = section,
                        onOpenUrl = onOpenUrl,
                        focusTracker = focusTracker,
                        rowKey = "home:$index",
                    )
                }
            }
            item { Spacer(Modifier.height(48.dp)) }
        }
    }
}