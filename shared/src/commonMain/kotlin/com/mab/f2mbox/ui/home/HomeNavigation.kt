
package com.mab.f2mbox.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.mab.f2mbox.data.remote.GenreLink
import com.mab.f2mbox.data.remote.HomeNavigation
import com.mab.f2mbox.data.remote.NavSection
import com.mab.f2mbox.ui.common.FocusTracker
import com.mab.f2mbox.ui.common.rememberCardHighlight
import com.mab.f2mbox.ui.common.IntStateSaver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ContentMargin = 48.dp
private val ChipShape = RoundedCornerShape(50)

/**
 * The home payload's navigation block: a row of quick links (movies, series,
 * top charts) with a genre-filter row underneath that follows the focused
 * link — movie genres under "فیلم ها", series genres under "سریال ها", and
 * nothing under the top charts, which have no genre facet. On TV the
 * selection follows focus, so browsing the links previews their genres;
 * pressing OK on a chip opens its listing through [onOpenUrl]. Down on a
 * quick link lands on its genre row when the section has one, so the search
 * field below is only reached by pressing Down again. A search field sits
 * under the filter rows; submitting a query calls [onSearch].
 *
 * The selected quick link and the focused chip are saved, so they are still
 * there when the user comes back to the home screen.
 */
@Composable
fun NavigationBlock(
    navigation: HomeNavigation,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onSearch: (String) -> Unit,
    focusTracker: FocusTracker,
    modifier: Modifier = Modifier
) {
    if (navigation.sections.isEmpty()) return

    var selectedSection by rememberSaveable(navigation, saver = IntStateSaver) {
        mutableIntStateOf(0)
    }
    val activeIndex = selectedSection.coerceAtMost(navigation.sections.lastIndex)
    val activeSection = navigation.sections[activeIndex]
    val activeGenres = genresFor(activeSection.url, navigation)
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth().padding(top = 28.dp)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = ContentMargin),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.onPreviewKeyEvent { event ->
                // Down from a quick link goes to its genre row when the
                // section has one; default focus search would jump straight
                // to the search field below.
                if (event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionDown &&
                    activeGenres.isNotEmpty()
                ) {
                    // Selection follows focus, so the crossfade may still be
                    // swapping the genre row in; retry until a chip attaches.
                    scope.launch {
                        val requester = focusTracker.requesterFor(GenreChipId(activeSection.url, 0))
                        while (!requester.requestFocus()) delay(50)
                    }
                    true
                } else {
                    false
                }
            }
        ) {
            items(navigation.sections.size) { index ->
                val chipId = NavChipId(index)
                SectionChip(
                    section = navigation.sections[index],
                    isSelected = index == selectedSection,
                    onFocus = {
                        selectedSection = index
                        focusTracker.onFocused(chipId)
                    },
                    onClick = { onOpenUrl(navigation.sections[index].url) },
                    modifier = Modifier.focusRequester(focusTracker.requesterFor(chipId))
                )
            }
        }

        // Keyed on the section url so switching links crossfades the genre row.
        Crossfade(
            targetState = activeSection.url,
            animationSpec = tween(durationMillis = 300),
            label = "genre-row",
            modifier = Modifier.padding(top = 12.dp)
        ) { url ->
            GenreRow(
                genres = genresFor(url, navigation),
                onOpenUrl = onOpenUrl,
                focusTracker = focusTracker,
                sectionUrl = url,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SearchField(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            onSubmit = onSearch,
            modifier = Modifier
                .padding(top = 16.dp)
                .padding(horizontal = ContentMargin)
        )
    }
}

/**
 * Genre links of the section whose listing url is given, if any.
 */
private fun genresFor(sectionUrl: String?, navigation: HomeNavigation): List<GenreLink> = when {
    sectionUrl?.contains("/movies") == true -> navigation.movieGenres
    sectionUrl?.contains("/series") == true -> navigation.seriesGenres
    else -> emptyList()
}

// Fixed height of the genre-row slot, so the search field below keeps its
// position whether the row is showing chips or empty.
private val GenreRowSlotHeight = 44.dp

@Composable
private fun GenreRow(
    genres: List<GenreLink>,
    onOpenUrl: (String) -> Unit,
    focusTracker: FocusTracker,
    sectionUrl: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.height(GenreRowSlotHeight),
        contentAlignment = Alignment.Center
    ) {
        if (genres.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = ContentMargin),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(genres.size) { index ->
                    val chipId = GenreChipId(sectionUrl, index)
                    GenreChip(
                        genre = genres[index],
                        onClick = { onOpenUrl(genres[index].url) },
                        onFocus = { focusTracker.onFocused(chipId) },
                        modifier = Modifier.focusRequester(focusTracker.requesterFor(chipId))
                    )
                }
            }
        }
    }
}

/**
 * A quick-link chip: pill-shaped, filled with the primary color while
 * selected so the active listing is obvious at a glance.
 */
@Composable
private fun SectionChip(
    section: NavSection,
    isSelected: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val highlight = rememberCardHighlight(onFocus = onFocus)
    val scale by animateFloatAsState(
        targetValue = if (highlight.highlighted) 1.08f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "section-chip-scale"
    )

    Box(
        modifier = modifier
            .then(highlight.modifier)
            .clickable(
                interactionSource = highlight.interactionSource,
                onClick = onClick,
            )
            // Inside the focusable node so the focus bounds stay at the
            // layout size while the chip animates — see PosterCard.
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(ChipShape)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    highlight.highlighted -> MaterialTheme.colorScheme.surfaceVariant
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (highlight.highlighted && !isSelected) 2.dp else 1.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = ChipShape
            )
            .padding(horizontal = 22.dp, vertical = 10.dp)
    ) {
        Text(
            text = section.name,
            style = MaterialTheme.typography.titleSmall,
            color = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A genre filter chip: neutral pill with the genre name and the number of
 * titles filed under it in a dimmer tone. Reports the focus through
 * [onFocus], so it can be restored later.
 */
@Composable
private fun GenreChip(
    genre: GenreLink,
    onClick: () -> Unit,
    onFocus: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val highlight = rememberCardHighlight(onFocus = onFocus)
    val scale by animateFloatAsState(
        targetValue = if (highlight.highlighted) 1.08f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "genre-chip-scale"
    )

    Row(
        modifier = modifier
            .then(highlight.modifier)
            .clickable(
                interactionSource = highlight.interactionSource,
                onClick = onClick,
            )
            // Inside the focusable node so the focus bounds stay at the
            // layout size while the chip animates — see PosterCard.
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(ChipShape)
            .background(
                if (highlight.highlighted) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                }
            )
            .border(
                width = if (highlight.highlighted) 2.dp else 1.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = ChipShape
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = genre.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (genre.count > 0) {
            Text(
                text = genre.count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
