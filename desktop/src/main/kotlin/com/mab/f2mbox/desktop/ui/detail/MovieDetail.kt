
package com.mab.f2mbox.desktop.ui.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import coil3.compose.AsyncImage
import com.mab.f2mbox.data.WatchedItem
import com.mab.f2mbox.storage.WatchedHistory
import com.mab.f2mbox.desktop.ui.common.AppButton
import com.mab.f2mbox.desktop.ui.common.LocalWatched
import com.mab.f2mbox.ui.common.FocusTracker
import com.mab.f2mbox.desktop.ui.common.rememberDesktopHighlight
import com.mab.f2mbox.ui.common.rememberFocusTracker
import com.mab.f2mbox.data.remote.Comments
import com.mab.f2mbox.data.remote.Comment
import com.mab.f2mbox.data.remote.DownloadQuality
import com.mab.f2mbox.data.remote.Dubbing
import com.mab.f2mbox.data.remote.Dub
import com.mab.f2mbox.data.remote.EpisodeLink
import com.mab.f2mbox.data.remote.GenreRef
import com.mab.f2mbox.data.remote.MovieData
import com.mab.f2mbox.data.remote.MovieInfo
import com.mab.f2mbox.data.remote.MovieIntro
import com.mab.f2mbox.data.remote.PersonRef
import com.mab.f2mbox.data.remote.Reactions
import com.mab.f2mbox.data.remote.ScrapeResult
import com.mab.f2mbox.data.remote.Trailer
import com.mab.f2mbox.data.remote.VoiceActor
import com.mab.f2mbox.data.remote.normalizeImageUrl
import com.mab.f2mbox.ui.common.IntSetStateSaver
import com.mab.f2mbox.ui.home.PosterCard
import com.mab.f2mbox.desktop.ui.theme.F2MBoxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.mab.f2mbox.desktop.platform.Strings

private val ContentMargin = 48.dp
private val PosterWidth = 200.dp
private val QualityCardWidth = 280.dp
private val QualityCardHeight = 150.dp
private val EpisodeCardWidth = 220.dp
private val EpisodeCardHeight = 110.dp
private val SuggestedCardWidth = 140.dp
private val PersonCardWidth = 120.dp
private val VoiceActorCardWidth = 200.dp
private val PersonImageSize = 96.dp
// Blurred intro backdrop: blur radius and the overscan factor that pushes
// the blur's soft out-of-bounds edge outside the clipped intro bounds.
private val BackdropBlur = 24.dp
private const val BackdropOverscan = 1.3f

/**
 * Detail page of a `type: "movie"` scrape. A blurred full-screen backdrop
 * (the trailer image when the title has one, else the poster, under a
 * darkening scrim) sits behind everything; on top, a
 * header (back button + title), an intro block (poster, notes, formats,
 * genres, release info), directors/cast rows, the synopsis, one horizontal
 * row of quality cards per download variant, a suggested-titles row and the
 * comment thread (paged through [fetchPage], which scrapes comment pages
 * that come back as movie payloads with the page's comments). Direct-
 * download links enqueue in [DownloadManager]; online-play links open in
 * the browser.
 *
 * [onOpenUrl] receives clicked genre/actor urls (they lead to listings);
 * [onNavigateBack] leaves the detail page.
 */
@Composable
fun MovieDetail(
    data: MovieData,
    fetchPage: suspend (pageUrl: String?) -> Result<ScrapeResult>,
    onOpenUrl: (String) -> Unit,
    onPlay: (url: String, title: String) -> Unit = { _, _ -> },
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val intro = data.intro ?: return
    val watched = LocalWatched.current
    val handlePlay: (String, String) -> Unit = { playUrl, playTitle ->
        WatchedHistory.save(
            storage = watched,
            item = WatchedItem(
                title = intro.title ?: data.title ?: playTitle,
                url = data.url ?: data.canonicalUrl.orEmpty().ifEmpty { playUrl },
                posterUrl = intro.poster?.url
            )
        )
        onPlay(playUrl, playTitle)
    }

    // Focus-restorable detail screen: restores focus on the back button
    // or the element the user left when coming back to this page.
    val focusTracker = rememberFocusTracker()
    val backFocusId = "detail:back"
    LaunchedEffect(data) {
        focusTracker.restore(fallbackId = backFocusId)
    }

    // The poster's play shortcut lands on the downloads section's first
    // play button; focusing it also scrolls the section into view.
    val downloadsFocusRequester = remember { FocusRequester() }
    val hasDownloads = data.downloads?.let {
        it.variants.isNotEmpty() || it.seasons.isNotEmpty()
    } == true

    // Comment thread paging: starts with the payload's own page; "more" and
    // "previous" fetch/pop whole comment pages through [fetchPage], since
    // comment-page urls scrape back as movie payloads with that page's
    // comments.
    var commentPages by remember(data) { mutableStateOf(listOf(data.comments)) }
    var commentLoading by remember(data) { mutableStateOf(false) }
    var commentError by remember(data) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Collapsible seasons: which season indexes are expanded (the last one
    // open by default, since that's the currently-airing season). Toggled
    // from the season header row itself. Saved, so the rows the user opened
    // are still open when the user comes back to this page.
    val seasons = data.downloads?.seasons.orEmpty()
    var expandedSeasons by rememberSaveable(data, saver = IntSetStateSaver) {
        mutableStateOf(if (seasons.isEmpty()) emptySet() else setOf(seasons.lastIndex))
    }
    // Collapsible dubs: which dub indexes are expanded (all collapsed by
    // default). Toggled from the dub header row itself.
    var expandedDubs by rememberSaveable(data, saver = IntSetStateSaver) {
        mutableStateOf<Set<Int>>(emptySet())
    }

    fun loadNextCommentPage(current: Comments) {
        val nextUrl = current.pagination.next ?: return
        if (commentLoading) return
        commentLoading = true
        commentError = false
        scope.launch {
            fetchPage(nextUrl).fold(
                onSuccess = { result ->
                    val page = (result as? ScrapeResult.Movie)?.data?.comments
                    if (page != null && page.items.isNotEmpty()) {
                        commentPages = commentPages + page
                    } else {
                        commentError = true
                    }
                },
                onFailure = { commentError = true }
            )
            commentLoading = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // The backdrop is oversized (see below) to hide the blur's soft
            // out-of-bounds edge; this clips it back to the screen bounds.
            .clipToBounds()
    ) {
        // Blurred trailer image (falling back to the poster when the title
        // has no trailer) as the full-screen backdrop, scaled up so the
        // blur's faded edges stay outside the visible bounds, under a scrim
        // to keep the foreground legible.
        AsyncImage(
            model = data.trailer?.poster ?: intro.poster?.url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = BackdropOverscan
                    scaleY = BackdropOverscan
                }
                .blur(BackdropBlur)
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black.copy(alpha = 0.75f)
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ContentMargin, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            AppButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .focusRequester(focusTracker.requesterFor(backFocusId))
                    .onFocusChanged { if (it.isFocused) focusTracker.onFocused(backFocusId) }
            ) {
                Text(Strings.back)
            }
            Text(
                text = intro.title ?: data.pageTitle ?: data.title.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // A regular scrolling Column, not a LazyColumn: every section is
        // always composed and in the focus graph, so the arrow keys can
        // always reach the next/previous item — with a lazy list, sections
        // beyond the viewport don't exist for focus search, and pressing
        // Down silently fails (worse on pages where some sections, like
        // the cast, are missing and the gaps grow). The content is bounded
        // (intro + a few rows + one comment page), so eager composition
        // costs nothing meaningful. Horizontal rows stay lazy.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ContentMargin),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            IntroBlock(
                intro = intro,
                reactions = data.reactions,
                onOpenUrl = onOpenUrl,
                onPlayRequest = if (hasDownloads) {
                    {
                        // Not attached when no quality carries a playable
                        // link, so guard the jump.
                        runCatching { downloadsFocusRequester.requestFocus() }
                    }
                } else {
                    null
                }
            )
            // Trailer card and the summary text share one row: the card
            // anchors the section and the summary fills the space beside it.
            val trailer = data.trailer?.takeIf { it.url != null }
            val excerpt = intro.excerpt?.takeIf { it.isNotBlank() }
            if (trailer != null || excerpt != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    excerpt?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    trailer?.let {
                        TrailerCard(
                            trailer = it,
                            title = intro.title.orEmpty(),
                            onPlay = handlePlay
                        )
                    }
                }
            }
            val people = buildList {
                addAll(data.directors)
                addAll(data.actors)
            }
            if (people.isNotEmpty()) {
                Text(
                    text = Strings.castAndCrew,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp)
                )
                LazyRow(
                    // Inset so the focus-scaled first/last cards aren't
                    // clipped at the row's bounds.
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(people.size) { index ->
                        PersonCard(person = people[index], onOpenUrl = onOpenUrl)
                    }
                }
            }
            data.dubbing?.takeIf { it.dubs.isNotEmpty() }?.let { dubbing ->
                Text(
                    text = Strings.dubbing,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp)
                )
                dubbing.dubs.forEachIndexed { dubIndex, dub ->
                    val isExpanded = dubIndex in expandedDubs
                    key(dubIndex) {
                        val dubHighlight = rememberDesktopHighlight()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(dubHighlight.modifier)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (dubHighlight.highlighted) {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable(
                                    interactionSource = dubHighlight.interactionSource,
                                ) {
                                    expandedDubs = if (isExpanded) {
                                        expandedDubs - dubIndex
                                    } else {
                                        expandedDubs + dubIndex
                                    }
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = if (isExpanded) "▾" else "◂",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = dub.title.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (isExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            dub.fields.forEach { (label, value) ->
                                Text(
                                    text = "$label: $value",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            dub.notes
                                .filter { it.isNotBlank() && it != "..." }
                                .forEach { note ->
                                    Text(
                                        text = "• $note",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                        }
                        if (dub.voiceActors.isNotEmpty()) {
                            Text(
                                text = Strings.voiceActors,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LazyRow(
                                // Inset so the focus-scaled first/last cards
                                // aren't clipped at the row's bounds.
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(dub.voiceActors.size) { index ->
                                    VoiceActorCard(actor = dub.voiceActors[index])
                                }
                            }
                        }
                    }
                }
            }
            data.downloads?.takeIf { it.variants.isNotEmpty() || it.seasons.isNotEmpty() }?.let {
                Text(
                    text = Strings.downloads,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            data.downloads?.variants?.forEachIndexed { variantIndex, variant ->
                Text(
                    text = variant.title.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(variant.qualities.size) { index ->
                        QualityCard(
                            quality = variant.qualities[index],
                            title = intro.title.orEmpty(),
                            onPlay = handlePlay,
                            playButtonModifier = if (variantIndex == 0 && index == 0) {
                                Modifier.focusRequester(downloadsFocusRequester)
                            } else {
                                Modifier
                            }
                        )
                    }
                }
            }
            val firstExpandedSeasonIndex = seasons.indices.firstOrNull { it in expandedSeasons }
            data.downloads?.seasons?.forEachIndexed { seasonIndex, season ->
                val isExpanded = seasonIndex in expandedSeasons
                key(seasonIndex) {
                    val seasonHighlight = rememberDesktopHighlight()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .then(seasonHighlight.modifier)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (seasonHighlight.highlighted) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    Color.Transparent
                                }
                            )
                            .clickable(
                                interactionSource = seasonHighlight.interactionSource,
                            ) {
                                expandedSeasons = if (isExpanded) {
                                    expandedSeasons - seasonIndex
                                } else {
                                    expandedSeasons + seasonIndex
                                }
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (isExpanded) "▾" else "◂",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = season.name.orEmpty(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        season.status?.takeIf { it.isNotBlank() }?.let { status ->
                            AccentChip(text = status, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                if (isExpanded) {
                    season.variants.forEachIndexed { variantIndex, variant ->
                        Text(
                            text = variant.title.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        variant.qualities.forEachIndexed { qualityIndex, quality ->
                            Text(
                                text = quality.quality.orEmpty(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LazyRow(
                                // Inset so the focus-scaled first/last cards
                                // aren't clipped at the row's bounds.
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(quality.episodes.size) { index ->
                                    EpisodeCard(
                                        episode = quality.episodes[index],
                                        title = intro.title.orEmpty(),
                                        onPlay = handlePlay,
                                        playButtonModifier = if (
                                            seasonIndex == firstExpandedSeasonIndex &&
                                            variantIndex == 0 &&
                                            qualityIndex == 0 &&
                                            index == 0
                                        ) {
                                            Modifier.focusRequester(downloadsFocusRequester)
                                        } else {
                                            Modifier
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            data.suggested?.takeIf { it.items.isNotEmpty() }?.let { suggested ->
                Text(
                    text = suggested.title ?: Strings.suggested,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp)
                )
                LazyRow(
                    // Inset so the focus-scaled first/last cards aren't
                    // clipped at the row's bounds.
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(suggested.items.size) { index ->
                        PosterCard(
                            item = suggested.items[index],
                            onClick = { onOpenUrl(suggested.items[index].url) },
                            modifier = Modifier.width(SuggestedCardWidth)
                        )
                    }
                }
            }
            data.comments?.takeIf { it.items.isNotEmpty() }?.let { comments ->
                Text(
                    text = Strings.comments.format(comments.total ?: comments.count ?: comments.items.size),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp)
                )
                comments.items.forEach { comment ->
                    CommentCard(comment = comment)
                }
                CommentsPager(
                    comments = comments,
                    onLoadNext = { loadNextCommentPage(comments) },
                    onLoadPrevious = { commentPages = commentPages.dropLast(1) },
                    canGoBack = commentPages.size > 1,
                    isLoading = commentLoading,
                    hasError = commentError,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            // Matches the lazy list's old bottom contentPadding.
            Spacer(Modifier.height(32.dp))
        }
        }
    }
}

/**
 * Poster plus the metadata column: badges, formats, genres, info rows.
 * Sits on top of the screen's full-bleed blurred-poster backdrop.
 */
@Composable
private fun IntroBlock(
    intro: MovieIntro,
    reactions: Reactions?,
    onOpenUrl: (String) -> Unit,
    onPlayRequest: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var posterFocused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // The poster doubles as a shortcut to the play section: when the
        // page has downloads, focusing it reveals a play overlay and OK
        // jumps to the downloads section's first play button.
        Box(
            modifier = Modifier
                .width(PosterWidth)
                .aspectRatio(2f / 3f)
                .then(
                    if (onPlayRequest != null) {
                        Modifier
                            .onFocusChanged { posterFocused = it.isFocused }
                            .clickable { onPlayRequest() }
                    } else {
                        Modifier
                    }
                )
        ) {
            AsyncImage(
                model = intro.poster?.url,
                contentDescription = intro.poster?.alt ?: intro.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            if (onPlayRequest != null && posterFocused) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = Strings.playerPlay,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val satisfaction = intro.satisfactionPercent
            if (satisfaction != null || intro.notes.isNotEmpty()) {
                LazyRow(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    satisfaction?.let {
                        item {
                            AccentChip(text = it, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    items(intro.notes.size) { index ->
                        AccentChip(
                            text = intro.notes[index],
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            reactions?.takeIf { it.likeCount > 0 || it.dislikeCount > 0 }?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = Strings.likeCount.format(it.likeCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = Strings.dislikeCount.format(it.dislikeCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val formats = intro.formats.distinct()
            if (formats.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(formats.size) { index ->
                        AccentChip(
                            text = formats[index],
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (intro.genres.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(intro.genres.size) { index ->
                        val genre = intro.genres[index]
                        val name = genre.name ?: return@items
                        val url = genre.url ?: return@items
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .clickable { onOpenUrl(url) }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            intro.info?.let { info ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow(Strings.infoRelease, info.release)
                    InfoRow(Strings.infoPublished, info.published)
                    // `duration` is often just the Persian word for minutes;
                    // prefer the numeric minute count when present.
                    val durationText = info.durationMinutes?.let {
                        Strings.infoDurationValue.format(it)
                    } ?: info.duration?.takeIf { value -> value.any { it.isDigit() } }
                    InfoRow(Strings.infoDuration, durationText)
                    InfoRow(Strings.infoCountries, info.countries)
                    InfoRow(Strings.infoLanguage, info.language)
                }
            }
        }
    }
}

/** Small pill used for the satisfaction badge, notes and format tags. */
@Composable
private fun AccentChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.25f))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

/** One label/value line of the release-info block; hidden when empty. */
@Composable
private fun InfoRow(label: String, value: String?, modifier: Modifier = Modifier) {
    if (value.isNullOrBlank()) return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Focus-scaled size shared by the person cards. */
private const val PersonCardScale = 1.06f

/**
 * A cast/crew member card: circular photo, name and role, opening the
 * person's archive page when pressed.
 */
@Composable
private fun PersonCard(person: PersonRef, onOpenUrl: (String) -> Unit, modifier: Modifier = Modifier) {
    val highlight = rememberDesktopHighlight()
    val scale by animateFloatAsState(
        targetValue = if (highlight.highlighted) PersonCardScale else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "person-scale"
    )

    Column(
        modifier = modifier
            .width(PersonCardWidth)
            // Same z-lift as PosterCard so the focused border isn't cut by
            // the neighboring cards' draw order.
            .zIndex(if (highlight.highlighted) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(highlight.modifier)
            .clickable(
                interactionSource = highlight.interactionSource,
                enabled = person.url != null,
            ) { person.url?.let(onOpenUrl) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = normalizeImageUrl(person.image),
            contentDescription = person.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(PersonImageSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = if (highlight.highlighted) 2.dp else 0.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                )
        )
        Text(
            text = person.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        person.role?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** One comment: author, relative date, text, reactions and its replies. */
@Composable
private fun CommentCard(comment: Comment, modifier: Modifier = Modifier) {
    // Focusable so the D-pad can traverse into the comment thread: the list
    // only scrolls toward focusable items, and with plain non-focusable
    // cards the search below the last row finds nothing and the comments
    // become unreachable.
    val highlight = rememberDesktopHighlight()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(highlight.modifier)
            .focusable(interactionSource = highlight.interactionSource)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
            .border(
                width = if (highlight.highlighted) 2.dp else 0.dp,
                color = if (highlight.highlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(10.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = comment.author,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            comment.dateText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = comment.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = Strings.likeCount.format(comment.likeCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = Strings.dislikeCount.format(comment.dislikeCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        comment.replies.forEach { reply ->
            CommentCard(
                comment = reply,
                modifier = Modifier.padding(start = 24.dp)
            )
        }
    }
}

/** Previous/next controls under the comment thread. */
@Composable
private fun CommentsPager(
    comments: Comments,
    canGoBack: Boolean,
    isLoading: Boolean,
    hasError: Boolean,
    onLoadNext: () -> Unit,
    onLoadPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hasError) {
            Text(
                text = Strings.commentsError,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            AppButton(onClick = onLoadPrevious, enabled = canGoBack && !isLoading) {
                Text(Strings.previous)
            }
            AppButton(onClick = onLoadNext, enabled = !isLoading && comments.pagination.next != null) {
                Text(
                    text = if (isLoading) {
                        Strings.loading
                    } else {
                        Strings.moreComments
                    }
                )
            }
        }
    }
}

/**
 * The play button on a quality/episode card: a rounded play bar with a
 * glyph, styled for a TV screen. At rest it is a quiet tint of the brand
 * color so a row of cards stays calm; when the D-pad lands on it, it fills
 * The play button on a quality/episode card: a rounded play bar, styled for
 * a TV screen. At rest it is an outline-only bar (transparent background) so
 * a row of cards stays calm; when the D-pad lands on it, the shared
 * [AppButton] focus treatment fills it with the brand color.
 */
@Composable
private fun PlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppButton(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        // The label fills the button and centers itself in it.
        Text(
            text = Strings.playerPlay,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * One quality of a download variant: the quality name, a single line that
 * merges its notes and encoder, and a play button that opens the in-app
 * player with the first usable link url (download url, falling back to the
 * online-play url).
 *
 * The card has a fixed height so every quality in a row lines up, whatever
 * extra metadata it carries.
 */
@Composable
private fun QualityCard(
    quality: DownloadQuality,
    title: String,
    onPlay: (url: String, title: String) -> Unit,
    playButtonModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    // Notes and encoder share one line: two optional texts made the cards
    // different heights.
    val details = listOfNotNull(
        quality.notes?.takeIf { it.isNotBlank() },
        quality.encoder?.takeIf { it.isNotBlank() && it != "unknown" }
    ).joinToString(" · ")
    Column(
        modifier = modifier
            .width(QualityCardWidth)
            .height(QualityCardHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = quality.quality.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = details,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Pushes the button to the bottom edge so it sits on the same
        // line in every card.
        Spacer(modifier = Modifier.weight(1f))
        // One play button per quality: the first link that carries a url
        // wins (prefer the download url, fall back to the online-play url).
        quality.links.firstNotNullOfOrNull { link ->
            link.downloadUrl ?: link.playUrl
        }?.let { url ->
            PlayButton(
                onClick = { onPlay(url, title) },
                modifier = playButtonModifier.fillMaxWidth()
            )
        }
    }
}

/**
 * One episode of a series quality: its label and quality, with a single
 * play button that opens the in-app player with the episode's download url
 * (falling back to its online-play url).
 */
@Composable
private fun EpisodeCard(
    episode: EpisodeLink,
    title: String,
    onPlay: (url: String, title: String) -> Unit,
    playButtonModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(EpisodeCardWidth)
            .height(EpisodeCardHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = episode.label.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Keeps the button on the same line in every episode card.
        Spacer(modifier = Modifier.weight(1f))
        val url = episode.downloadUrl ?: episode.playUrl
        if (url != null) {
            PlayButton(
                onClick = { onPlay(url, title) },
                modifier = playButtonModifier.fillMaxWidth()
            )
        }
    }
}

/**
 * The trailer card: the trailer's poster thumbnail next to a play button
 * that opens the in-app player via [onPlay].
 */
@Composable
private fun TrailerCard(
    trailer: Trailer,
    title: String,
    onPlay: (url: String, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AsyncImage(
            model = trailer.poster,
            contentDescription = Strings.trailer,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(200.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = Strings.trailer,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            AppButton(onClick = { trailer.url?.let { onPlay(it, title) } }) {
                Text(Strings.watchTrailer)
            }
        }
    }
}

/**
 * One voice actor of a dub: their name and the roles they voice. Focusable
 * with a focus border for the same reason as [CommentCard]: the list only
 * scrolls toward focusable items, so a plain card here would leave the
 * dubbing section unreachable from the cast row above it.
 */
@Composable
private fun VoiceActorCard(actor: VoiceActor, modifier: Modifier = Modifier) {
    val highlight = rememberDesktopHighlight()

    Column(
        modifier = modifier
            .width(VoiceActorCardWidth)
            .then(highlight.modifier)
            .focusable(interactionSource = highlight.interactionSource)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (highlight.highlighted) 2.dp else 0.dp,
                color = if (highlight.highlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(10.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = actor.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = actor.roles.joinToString("، "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview(showBackground = true, widthDp = 960, heightDp = 540)
@Composable
private fun MovieDetailPreview() {
    F2MBoxTheme {
        MovieDetail(
            data = MovieData(
                title = "Sample Movie",
                intro = MovieIntro(
                    title = "Sample Movie",
                    notes = listOf("1080p", "SoftSub"),
                    formats = listOf("MKV", "MP4"),
                    genres = listOf(GenreRef("Action", "/action")),
                    info = MovieInfo(
                        release = "2024",
                        durationMinutes = 120,
                        countries = "USA",
                        language = "English"
                    )
                ),
                trailer = Trailer(url = "http://example.com/trailer.mp4")
            ),
            fetchPage = { Result.success(ScrapeResult.Listing(com.mab.f2mbox.data.remote.ListData())) },
            onOpenUrl = {},
            onNavigateBack = {}
        )
    }
}

