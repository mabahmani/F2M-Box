@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mab.f2mbox.ui.detail

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.mab.f2mbox.R
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
import com.mab.f2mbox.ui.home.PosterCard
import com.mab.f2mbox.ui.theme.F2MBoxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
 * Detail page of a `type: "movie"` scrape. A blurred full-screen poster
 * backdrop (with a darkening scrim) sits behind everything; on top, a
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

    // Grabs focus for the back button once the header attaches, the same
    // way the hero does on home.
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(data) {
        while (!backFocus.requestFocus()) delay(50)
    }

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
    // from the season header row itself.
    val seasons = data.downloads?.seasons.orEmpty()
    var expandedSeasons by remember(data) {
        mutableStateOf(if (seasons.isEmpty()) emptySet() else setOf(seasons.lastIndex))
    }
    // Collapsible dubs: which dub indexes are expanded (all collapsed by
    // default). Toggled from the dub header row itself.
    var expandedDubs by remember(data) {
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
        // Blurred poster as the full-screen backdrop, scaled up so the
        // blur's faded edges stay outside the visible bounds, under a scrim
        // to keep the foreground legible.
        AsyncImage(
            model = intro.poster?.url,
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
            Button(onClick = onNavigateBack, modifier = Modifier.focusRequester(backFocus)) {
                Text(stringResource(R.string.back))
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

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(
                start = ContentMargin,
                end = ContentMargin,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "intro") {
                val trailer = data.trailer?.takeIf { it.url != null }
                if (trailer != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        IntroBlock(
                            intro = intro,
                            reactions = data.reactions,
                            onOpenUrl = onOpenUrl,
                            modifier = Modifier.weight(1f)
                        )
                        TrailerCard(
                            trailer = trailer,
                            title = intro.title.orEmpty(),
                            onPlay = onPlay
                        )
                    }
                } else {
                    IntroBlock(
                        intro = intro,
                        reactions = data.reactions,
                        onOpenUrl = onOpenUrl
                    )
                }
            }
            intro.excerpt?.takeIf { it.isNotBlank() }?.let { excerpt ->
                item(key = "excerpt") {
                    Text(
                        text = excerpt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val people = buildList {
                addAll(data.directors)
                addAll(data.actors)
            }
            if (people.isNotEmpty()) {
                item(key = "people-title") {
                    Text(
                        text = stringResource(R.string.cast_and_crew),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                item(key = "people-row") {
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
            }
            data.dubbing?.takeIf { it.dubs.isNotEmpty() }?.let { dubbing ->
                item(key = "dubbing-title") {
                    Text(
                        text = stringResource(R.string.dubbing),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                dubbing.dubs.forEachIndexed { dubIndex, dub ->
                    val isExpanded = dubIndex in expandedDubs
                    item(key = "dub-$dubIndex-title") {
                        var isFocused by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isFocused = it.isFocused }
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isFocused) {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable {
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
                        item(key = "dub-$dubIndex-info") {
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
                        }
                        if (dub.voiceActors.isNotEmpty()) {
                            item(key = "dub-$dubIndex-voice-title") {
                                Text(
                                    text = stringResource(R.string.voice_actors),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            item(key = "dub-$dubIndex-voice-row") {
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
            }
            data.downloads?.takeIf { it.variants.isNotEmpty() || it.seasons.isNotEmpty() }?.let {
                item(key = "downloads-title") {
                    Text(
                        text = stringResource(R.string.downloads),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            data.downloads?.variants?.forEach { variant ->
                item(key = "variant-title-${variant.variant}") {
                    Text(
                        text = variant.title.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                item(key = "variant-row-${variant.variant}") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(variant.qualities.size) { index ->
                            QualityCard(
                                quality = variant.qualities[index],
                                title = intro.title.orEmpty(),
                                onPlay = onPlay
                            )
                        }
                    }
                }
            }
            data.downloads?.seasons?.forEachIndexed { seasonIndex, season ->
                val isExpanded = seasonIndex in expandedSeasons
                item(key = "season-$seasonIndex-title") {
                    var isFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .onFocusChanged { isFocused = it.isFocused }
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isFocused) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    Color.Transparent
                                }
                            )
                            .clickable {
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
                        item(key = "season-$seasonIndex-variant-$variantIndex-title") {
                            Text(
                                text = variant.title.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        variant.qualities.forEachIndexed { qualityIndex, quality ->
                            item(key = "season-$seasonIndex-variant-$variantIndex-quality-$qualityIndex-title") {
                                Text(
                                    text = quality.quality.orEmpty(),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            item(key = "season-$seasonIndex-variant-$variantIndex-quality-$qualityIndex-row") {
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
                                            onPlay = onPlay
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            data.suggested?.takeIf { it.items.isNotEmpty() }?.let { suggested ->
                item(key = "suggested-title") {
                    Text(
                        text = suggested.title ?: stringResource(R.string.suggested),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                item(key = "suggested-row") {
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
            }
            data.comments?.takeIf { it.items.isNotEmpty() }?.let { comments ->
                item(key = "comments-title") {
                    Text(
                        text = stringResource(
                            R.string.comments,
                            comments.total ?: comments.count ?: comments.items.size
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                items(comments.items.size, key = { "comment-${comments.items[it].id ?: it}" }) { index ->
                    CommentCard(comment = comments.items[index])
                }
                item(key = "comments-pager") {
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
            }
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        AsyncImage(
            model = intro.poster?.url,
            contentDescription = intro.poster?.alt ?: intro.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(PosterWidth)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val satisfaction = intro.satisfactionPercent
            if (satisfaction != null || intro.notes.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    satisfaction?.let {
                        AccentChip(text = it, color = MaterialTheme.colorScheme.secondary)
                    }
                    intro.notes.forEach { note ->
                        AccentChip(text = note, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            reactions?.takeIf { it.likeCount > 0 || it.dislikeCount > 0 }?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.like_count, it.likeCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.dislike_count, it.dislikeCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val formats = intro.formats.distinct()
            if (formats.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    formats.forEach { format ->
                        AccentChip(
                            text = format,
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
                    InfoRow(stringResource(R.string.info_release), info.release)
                    InfoRow(stringResource(R.string.info_published), info.published)
                    // `duration` is often just the Persian word for minutes;
                    // prefer the numeric minute count when present.
                    val durationText = info.durationMinutes?.let {
                        stringResource(R.string.info_duration_value, it)
                    } ?: info.duration?.takeIf { value -> value.any { it.isDigit() } }
                    InfoRow(stringResource(R.string.info_duration), durationText)
                    InfoRow(stringResource(R.string.info_countries), info.countries)
                    InfoRow(stringResource(R.string.info_language), info.language)
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
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) PersonCardScale else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "person-scale"
    )

    Column(
        modifier = modifier
            .width(PersonCardWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(enabled = person.url != null) { person.url?.let(onOpenUrl) },
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
                    width = if (isFocused) 2.dp else 0.dp,
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
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) {
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
                text = stringResource(R.string.like_count, comment.likeCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.dislike_count, comment.dislikeCount),
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
                text = stringResource(R.string.comments_error),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            Button(onClick = onLoadPrevious, enabled = canGoBack && !isLoading) {
                Text(stringResource(R.string.previous))
            }
            Button(onClick = onLoadNext, enabled = !isLoading && comments.pagination.next != null) {
                Text(
                    text = if (isLoading) {
                        stringResource(R.string.loading)
                    } else {
                        stringResource(R.string.more_comments)
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
 * with the brand color, lifts slightly and casts a glow.
 */
@Composable
private fun PlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        scale = ButtonDefaults.scale(focusedScale = 1.04f),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ButtonDefaults.colors(
            containerColor = scheme.primary.copy(alpha = 0.22f),
            contentColor = scheme.primary,
            focusedContainerColor = scheme.primary,
            focusedContentColor = scheme.onPrimary,
            pressedContainerColor = scheme.primary,
            pressedContentColor = scheme.onPrimary
        ),
        glow = ButtonDefaults.glow(
            focusedGlow = Glow(elevationColor = scheme.primary, elevation = 8.dp)
        )
    ) {
        // The label fills the button and centers itself in it.
        Text(
            text = stringResource(R.string.player_play),
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
                modifier = Modifier.fillMaxWidth()
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
                modifier = Modifier.fillMaxWidth()
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
            contentDescription = stringResource(R.string.trailer),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(200.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.trailer),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Button(onClick = { trailer.url?.let { onPlay(it, title) } }) {
                Text(stringResource(R.string.watch_trailer))
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
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .width(VoiceActorCardWidth)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) {
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

