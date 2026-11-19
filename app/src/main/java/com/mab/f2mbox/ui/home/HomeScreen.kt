@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mab.f2mbox.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mab.f2mbox.R
import com.mab.f2mbox.data.F2MRepository
import com.mab.f2mbox.data.remote.HomeData
import com.mab.f2mbox.data.remote.clearNetworkCache
import com.mab.f2mbox.ui.connect.LoadingDots
import com.mab.f2mbox.ui.connect.describe
import kotlinx.coroutines.delay

/**
 * Home screen. Loads the homepage payload once and renders the hero slider,
 * the navigation block and the carousels. Tapping any navigation target
 * (hero slide, poster card, section chip, genre chip, see-more card) hands
 * its url to [onOpenUrl]; submitting the inline search field calls
 * [onSearch] with the query.
 */
@Composable
fun HomeScreen(
    repository: F2MRepository,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenUrl: (String) -> Unit = {},
    onSearch: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var state by remember { mutableStateOf<HomeUiState>(HomeUiState.Loading) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        state = HomeUiState.Loading
        repository.home().fold(
            onSuccess = { state = HomeUiState.Ready(it) },
            onFailure = { state = HomeUiState.Error(describe(it)) }
        )
    }

    when (val current = state) {
        HomeUiState.Loading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            LoadingDots()
        }

        is HomeUiState.Error -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val context = LocalContext.current
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(current.messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { (context as? android.app.Activity)?.finish() }) {
                        Text(stringResource(R.string.back))
                    }
                    Button(onClick = { attempt++ }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }

        is HomeUiState.Ready -> HomeContent(
            home = current.home,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onOpenUrl = onOpenUrl,
            onSearch = onSearch,
            onRefresh = { attempt++ },
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
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val heroHeight = maxHeight

        // Created here — outside the lazy list — so it survives scrolling.
        // The hero grabs focus exactly once, when the home screen first
        // appears; requesting it from inside the lazy item would re-run every
        // time the hero re-enters composition and yank the list back to the
        // top.
        val heroFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            // Lazy items compose during layout, so the hero node may not
            // exist yet on the first frame; retry until it attaches.
            while (!heroFocus.requestFocus()) delay(50)
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (home.heroSlider.isNotEmpty()) {
                item {
                    HeroSlider(
                        items = home.heroSlider,
                        focusRequester = heroFocus,
                        onSlideClick = onOpenUrl,
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
                        onClearCacheAndRefresh = {
                            clearNetworkCache()
                            onRefresh()
                        }
                    )
                }
            }
            home.sections.forEach { section ->
                item(key = section.id) {
                    SectionRow(section = section, onOpenUrl = onOpenUrl)
                }
            }
            // Breathing room below the last row so it clears the screen edge.
            item { Spacer(Modifier.height(48.dp)) }
        }
    }
}
