
package com.mab.f2mbox.desktop.ui.home

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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mab.f2mbox.desktop.ui.common.AppButton
import com.mab.f2mbox.desktop.ui.common.AppIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.mab.f2mbox.scraper.F2MScraper
import com.mab.f2mbox.desktop.ui.common.LocalWatched
import com.mab.f2mbox.storage.WatchedHistory
import com.mab.f2mbox.data.remote.HomeData
import com.mab.f2mbox.data.remote.HomeSection
import com.mab.f2mbox.scraper.ScraperLog
import com.mab.f2mbox.desktop.ui.common.FocusTracker
import com.mab.f2mbox.desktop.ui.common.IntStateSaver
import com.mab.f2mbox.desktop.ui.common.ScraperLogFeed
import com.mab.f2mbox.desktop.ui.common.rememberFocusTracker
import com.mab.f2mbox.desktop.ui.connect.LoadingDots
import com.mab.f2mbox.desktop.ui.connect.describe
import kotlinx.coroutines.delay
import com.mab.f2mbox.desktop.platform.Strings

/**
 * Home screen. Loads the homepage payload once and renders the hero slider,
 * the navigation block and the carousels. Tapping any navigation target
 * (hero slide, poster card, section chip, genre chip, see-more card) hands
 * its url to [onOpenUrl]; submitting the inline search field calls
 * [onSearch] with the query.
 *
 * The screen keeps its scroll position, its hero slide and its focus
 * position across navigation, so coming back to it shows the home page as
 * the user left it.
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
    onQuit: () -> Unit = {},
) {
    var state by remember {
        mutableStateOf<HomeUiState>(
            if (initialHome != null) HomeUiState.Ready(initialHome) else HomeUiState.Loading
        )
    }
    // Counts the fetches of the home page: it is raised by the clear-cache
    // button and by a retry, and compared with [fetchedToken] to tell
    // whether a fetch is due. Both are saved, so coming back to the home
    // screen does not fetch it again.
    var fetchToken by rememberSaveable(saver = IntStateSaver) { mutableIntStateOf(0) }
    var fetchedToken by rememberSaveable(saver = IntStateSaver) { mutableIntStateOf(0) }
    val watched = LocalWatched.current
    val watchedItems = remember(fetchToken) { WatchedHistory.getWatchedItems(watched) }
    val watchedSection = if (watchedItems.isNotEmpty()) {
        HomeSection(
            id = "continue-watching",
            title = "ادامه تماشا",
            items = watchedItems.map { it.toSectionItem() }
        )
    } else {
        null
    }
    // Tracks the focused element of the home screen, so the focus comes
    // back to it after the user visited a listing and came back.
    val focusTracker = rememberFocusTracker()

    // With initialHome the gate already fetched a healthy home page —
    // the first composition renders it directly with no loading state.
    // An explicit fetch (clear-cache or retry) goes through Loading so the
    // re-fetch is visible in the log feed.
    LaunchedEffect(fetchToken) {
        if (fetchToken == fetchedToken && initialHome != null) return@LaunchedEffect
        state = HomeUiState.Loading
        scraper.home().fold(
            onSuccess = {
                state = HomeUiState.Ready(it)
                fetchedToken = fetchToken
                onHomeLoaded(it)
            },
            onFailure = { state = HomeUiState.Error(describe(it)) }
        )
    }

    when (val current = state) {
        HomeUiState.Loading -> Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LoadingDots()
            Spacer(Modifier.height(16.dp))
            ScraperLogFeed()
        }

        is HomeUiState.Error -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = current.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Quit on desktop = the android build's activity finish.
                    AppButton(onClick = onQuit) {
                        Text(Strings.back)
                    }
                    AppButton(onClick = { fetchToken++ }) {
                        Text(Strings.retry)
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
            // Clear-cache action: drop cached pages + the session
            // resolution, then re-fetch home from the live site.
            onRefresh = {
                onClearCache()
                fetchToken++
            },
            onOpenLogs = onOpenLogs,
            modifier = modifier
        )
    }
}

/**
 * Vertical scroll of the hero on top, the navigation block (quick links +
 * genre filters) below it, and every home section (a horizontal poster row)
 * under that.
 */
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
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val heroHeight = maxHeight
        val listState = rememberLazyListState()

        // The hero is the fallback focus target of the home screen: it
        // takes the focus the first time the screen is shown, and whenever
        // the user did not focus anything else before leaving.
        val heroFocus = focusTracker.requesterFor(HeroFocusId)
        // Puts the focus back where the user left it — on the hero the
        // first time, on the focused poster or chip after that.
        LaunchedEffect(home) {
            focusTracker.restore(fallbackId = HeroFocusId)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            // Domain badge + clear-cache icon button as the first scroll
            // item: as a floating overlay it was unreachable with the
            // arrow keys (directional focus search finds nothing "above"
            // the hero it overlaps); as a list item it sits in the normal
            // focus chain — Up from the hero, Down back to it.
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
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
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    AppIconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = Strings.clearCacheRefresh,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    AppIconButton(
                        onClick = onOpenLogs,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Terminal,
                            contentDescription = Strings.viewLogs,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
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
                            .height(heroHeight)
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
                        focusTracker = focusTracker
                    )
                }
            }
            watchedSection?.let { section ->
                item(key = section.id) {
                    SectionRow(
                        section = section,
                        onOpenUrl = onOpenUrl,
                        focusTracker = focusTracker,
                        rowKey = WatchedSectionRowKey
                    )
                }
            }
            home.sections.forEachIndexed { index, section ->
                item(key = section.id) {
                    SectionRow(
                        section = section,
                        onOpenUrl = onOpenUrl,
                        focusTracker = focusTracker,
                        rowKey = "home:$index"
                    )
                }
            }
            // Breathing room below the last row so it clears the screen edge.
            item { Spacer(Modifier.height(48.dp)) }
        }
    }
}
