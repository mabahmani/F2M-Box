@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mab.f2mbox.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.mab.f2mbox.R
import com.mab.f2mbox.data.remote.HomeSection
import com.mab.f2mbox.data.remote.SectionItem

private val ContentMargin = 48.dp
private val PosterCardWidth = 140.dp
private val PosterCardHeight = 210.dp
private const val MAX_GENRE_CHIPS = 3

/**
 * One home carousel: a section title followed by a horizontally scrollable
 * row of poster cards. Used for every entry of the home payload's
 * `sections` list.
 */
@Composable
fun SectionRow(
    section: HomeSection,
    onOpenUrl: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (section.items.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth().padding(top = 28.dp)) {
        Text(
            text = section.title.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = ContentMargin, vertical = 12.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = ContentMargin),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(section.items.size) { index ->
                PosterCard(
                    item = section.items[index],
                    onClick = { onOpenUrl(section.items[index].url) },
                    modifier = Modifier.width(PosterCardWidth)
                )
            }
            section.url?.let {
                item(key = "see-more-${section.id}") {
                    SeeMoreCard(onClick = { onOpenUrl(section.url) })
                }
            }
        }
    }
}

/**
 * A chevron drawn as a vector stroke pointing right in LTR and mirrored to
 * point left in RTL. Drawn manually because text glyphs like "›" are
 * bidi-mirrored by the text renderer, which double-flips any manual
 * transform applied on top.
 */
@Composable
private fun Chevron(color: Color, modifier: Modifier = Modifier) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier = modifier.graphicsLayer { scaleX = if (isRtl) -1f else 1f }) {
        val path = Path().apply {
            moveTo(size.width * 0.2f, size.height * 0.15f)
            lineTo(size.width * 0.8f, size.height * 0.5f)
            lineTo(size.width * 0.2f, size.height * 0.85f)
        }
        drawPath(
            path,
            color = color,
            style = Stroke(
                width = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/**
 * Terminal card of a section row: same size as a poster card, shows
 * "See more" with a chevron and leads to the section's listing page.
 */
@Composable
private fun SeeMoreCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "see-more-scale"
    )

    Box(
        modifier = modifier
            .width(PosterCardWidth)
            .height(PosterCardHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
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
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.see_more),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
            Chevron(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .offset(y = (-2).dp)
                    .size(width = 8.dp, height = 12.dp)
            )
        }
    }
}

/**
 * A focusable poster card: vertical poster image, rating badge on top,
 * title and status/year below. Scales up and gets a border when focused.
 * Width comes from [modifier]; the poster keeps a 2:3 aspect ratio, so
 * callers pass e.g. `Modifier.width(PosterCardWidth)` in rows or
 * `Modifier.fillMaxWidth()` in grid cells. Pressing OK fires [onClick].
 */
@Composable
internal fun PosterCard(
    item: SectionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "poster-scale"
    )

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            AsyncImage(
                model = item.poster?.url,
                contentDescription = item.poster?.alt ?: item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
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
            )
            item.rating?.let { rating ->
                Text(
                    text = stringResource(R.string.rating_format, rating.toString()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Genre chips slide in over the poster while the card is focused.
            val genreNames = item.genres.mapNotNull { it.name }
            if (genreNames.isNotEmpty()) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isFocused,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        genreNames.take(MAX_GENRE_CHIPS).forEach { name ->
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Black.copy(alpha = 0.75f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )

        val subtitle = item.statusText ?: item.year?.toString()
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (item.formats.isNotEmpty()) {
            Text(
                text = item.formats.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
