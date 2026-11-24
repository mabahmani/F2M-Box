
package com.mab.f2mbox.desktop.ui.person

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mab.f2mbox.desktop.ui.common.AppButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import coil3.compose.AsyncImage
import com.mab.f2mbox.data.remote.PersonData
import com.mab.f2mbox.data.remote.PersonProfile
import com.mab.f2mbox.ui.common.FocusTracker
import com.mab.f2mbox.ui.common.GridPosterId
import com.mab.f2mbox.ui.home.PosterCard
import com.mab.f2mbox.desktop.ui.list.PagerControls
import com.mab.f2mbox.desktop.platform.Strings

private val ContentMargin = 48.dp
private val GridColumns = 4
// Focused cards scale up 1.08x; this plus each cell's own padding clears
// the grid's top clip edge so the enlarged card isn't cut off.
private val GridTopPadding = 28.dp
// Breathing room inside every grid cell, so the wide cards keep an even
// gutter: column gap is 2x this, row gap 2x + spacing between rows.
private val GridItemPadding = 16.dp

/**
 * A person's archive screen (`type: "person"` scrape): a header with the
 * person's photo, name and role kind, then the poster grid of their titles
 * with previous/next pagination — the same grid the listing screens use.
 * Clicking a poster or genre hands its url to [onOpenUrl]; Back leaves via
 * [onNavigateBack] (page history is kept by the owning scrape screen).
 */
@Composable
fun PersonScreen(
    data: PersonData,
    onNavigateBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    focusTracker: FocusTracker,
    hasPrevious: Boolean,
    onPrevious: () -> Unit,
    hasNext: Boolean,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Puts the focus back on the poster the user left — on the first poster
    // the first time the screen is shown.
    LaunchedEffect(data) {
        focusTracker.restore(fallbackId = GridPosterId(0))
    }

    Column(modifier = modifier.fillMaxSize()) {
        PersonHeader(person = data.person, onNavigateBack = onNavigateBack)

        if (data.items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = Strings.noResults,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        val gridState = rememberLazyGridState()
        LazyVerticalGrid(
            columns = GridCells.Fixed(GridColumns),
            state = gridState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(
                start = ContentMargin,
                end = ContentMargin,
                top = GridTopPadding,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                        .padding(GridItemPadding)
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                PagerControls(
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    focusTracker = focusTracker,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/** Header row: back button, the person's photo, name and role-kind chip. */
@Composable
private fun PersonHeader(
    person: PersonProfile?,
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
        AppButton(onClick = onNavigateBack) {
            Text(Strings.back)
        }
        person?.photo?.url?.let { photoUrl ->
            AsyncImage(
                model = photoUrl,
                contentDescription = person.photo?.alt ?: person.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = person?.name.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            kindLabel(person?.kind)?.let { label ->
                Text(
                    text = label,
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
}

/** Maps the payload's role kind to a display label; null hides the chip. */
@Composable
private fun kindLabel(kind: String?): String? = when (kind?.lowercase()) {
    "actor" -> Strings.personKindActor
    "director" -> Strings.personKindDirector
    "writer" -> Strings.personKindWriter
    "creator" -> Strings.personKindCreator
    null, "" -> null
    else -> kind.replaceFirstChar { it.uppercase() }
}
