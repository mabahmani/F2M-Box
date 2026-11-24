
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import coil3.compose.AsyncImage
import com.mab.f2mbox.data.remote.HomeSection
import com.mab.f2mbox.data.remote.SectionItem
import com.mab.f2mbox.ui.common.FocusTracker
import com.mab.f2mbox.ui.home.LocalHomeStrings
import com.mab.f2mbox.ui.common.rememberCardHighlight

private val ContentMargin = 48.dp
private val PosterCardWidth = 180.dp
private val PosterCardHeight = 270.dp
private const val MAX_GENRE_CHIPS = 3

/**
 * One home carousel: a section title followed by a horizontally scrollable
 * row of poster cards. Used for every entry of the home payload's
 * `sections` list.
 *
 * The posters of the row are focus-restorable: each one reports itself to
 * [focusTracker], so the focus comes back to the same poster when the user
 * returns to the home screen. [rowKey] identifies the row among the rows of
 * the home screen, so their posters do not share a focus id.
 */
@Composable
fun SectionRow(
    section: HomeSection,
    onOpenUrl: (String) -> Unit = {},
    focusTracker: FocusTracker,
    rowKey: String,
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
                val posterId = SectionPosterId(rowKey, index)
                PosterCard(
                    item = section.items[index],
                    onClick = { onOpenUrl(section.items[index].url) },
                    onFocus = { focusTracker.onFocused(posterId) },
                    modifier = Modifier
                        .focusRequester(focusTracker.requesterFor(posterId))
                        .width(PosterCardWidth)
                )
            }
            section.url?.let { url ->
                item(key = "see-more-${section.id}") {
                    SeeMoreCard(onClick = { onOpenUrl(url) })
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
    val highlight = rememberCardHighlight()
    val scale by animateFloatAsState(
        targetValue = if (highlight.highlighted) 1.08f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "see-more-scale"
    )

    Box(
        modifier = modifier
            .width(PosterCardWidth)
            .height(PosterCardHeight)
            // Same z-lift as PosterCard so the focused border isn't cut by
            // the neighboring cards' draw order.
            .zIndex(if (highlight.highlighted) 1f else 0f)
            .then(highlight.modifier)
            .clickable(
                interactionSource = highlight.interactionSource,
                onClick = onClick,
            )
            // Inside the focusable node — see PosterCard.
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
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
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = LocalHomeStrings.current.seeMore,
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
 * [onFocus] is called when the card takes the focus, so the caller can
 * restore the focus on it later.
 */
@Composable
fun PosterCard(
    item: SectionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: (() -> Unit)? = null
) {
    val highlight = rememberCardHighlight(onFocus = onFocus)
    val scale by animateFloatAsState(
        targetValue = if (highlight.highlighted) 1.08f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "poster-scale"
    )

    Column(
        modifier = modifier
            // Lift the focused card above its row/grid siblings: a scaled-up
            // card expands past its layout bounds, and later siblings would
            // otherwise paint over the expansion, cutting the focus border
            // off the poster's edges.
            .zIndex(if (highlight.highlighted) 1f else 0f)
            .then(highlight.modifier)
            .clickable(
                interactionSource = highlight.interactionSource,
                onClick = onClick,
            )
            // Sits inside the focusable node so the focus bounds stay at the
            // layout size while the card animates; a scaled focus target
            // makes the scroll containers chase its growing bounds and the
            // screen shakes.
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
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
                        width = if (highlight.highlighted) 2.dp else 0.dp,
                        color = if (highlight.highlighted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                        shape = RoundedCornerShape(10.dp)
                    )
            )
            item.rating?.let { rating ->
                Text(
                    text = LocalHomeStrings.current.formatRating(rating.toFloat()),
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
                    visible = highlight.highlighted,
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
