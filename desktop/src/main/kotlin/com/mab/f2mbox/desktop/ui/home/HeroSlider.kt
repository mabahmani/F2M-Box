
package com.mab.f2mbox.desktop.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import coil3.compose.AsyncImage
import com.mab.f2mbox.data.remote.HeroSlide
import com.mab.f2mbox.desktop.ui.common.IntStateSaver
import com.mab.f2mbox.desktop.ui.common.rememberDesktopHighlight
import kotlinx.coroutines.delay
import com.mab.f2mbox.desktop.platform.Strings

private const val AUTO_ADVANCE_MS = 6_000L
private const val HERO_EDGE_FRACTION = 0.18f
private const val HERO_DRAG_FRACTION = 0.12f

/**
 * Full-bleed hero slider. Each slide shows the landscape backdrop covering
 * the whole screen with a small vertical poster, the title, rating and year
 * overlaid at the bottom. Auto-advances every few seconds; D-pad or mouse
 * left/right (edge click or horizontal drag) changes slides; center click or
 * OK opens the slide's url via [onSlideClick]. Indicator pills jump to a slide.
 *
 * The slide the user is on is saved, so it is still showing when the user
 * comes back to the home screen. [onFocused] reports that the slider took
 * the focus, so it can be restored later.
 */
@Composable
fun HeroSlider(
    items: List<HeroSlide>,
    focusRequester: FocusRequester,
    onSlideClick: (String) -> Unit = {},
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    var page by rememberSaveable(items, saver = IntStateSaver) { mutableIntStateOf(0) }
    // In RTL the slider reads right-to-left, so "next" lies to the left.
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // Auto-advance. Keyed on [page] so any change (auto or manual) restarts
    // the countdown.
    LaunchedEffect(items, page) {
        delay(AUTO_ADVANCE_MS)
        page = (page + 1) % items.size
    }

    val highlight = rememberDesktopHighlight(onFocus = onFocused)

    val goToPrevious: () -> Unit = {
        page = if (isRtl) {
            (page + 1) % items.size
        } else {
            (page - 1 + items.size) % items.size
        }
    }
    val goToNext: () -> Unit = {
        page = if (isRtl) {
            (page - 1 + items.size) % items.size
        } else {
            (page + 1) % items.size
        }
    }
    val openCurrent: () -> Unit = { onSlideClick(items[page].url) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            // Must sit OUTSIDE focusable/clickable: key events route through
            // a node's ancestors only, so an interceptor nested deeper than
            // the focused node never sees them.
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            goToPrevious()
                            true
                        }
                        Key.DirectionRight -> {
                            goToNext()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .then(highlight.modifier)
            .focusable(interactionSource = highlight.interactionSource)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                        openCurrent()
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(items.size, isRtl) {
                var dragX = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, delta -> dragX += delta },
                    onDragEnd = {
                        val threshold = size.width * HERO_DRAG_FRACTION
                        when {
                            // Finger left = next slide in LTR (content follows
                            // finger); RTL flips inside the helpers.
                            dragX < -threshold -> if (isRtl) goToPrevious() else goToNext()
                            dragX > threshold -> if (isRtl) goToNext() else goToPrevious()
                        }
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f },
                )
            }
    ) {
        Crossfade(
            targetState = page,
            animationSpec = tween(durationMillis = 700),
            label = "hero-slide"
        ) { current ->
            SlideContent(items[current])
        }

        // Hit targets stay LTR so "start" is always the physical left edge and
        // matches the Left arrow key (RTL page math stays in goToPrevious/Next).
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val edgeWidth = with(density) {
                    (maxWidth * HERO_EDGE_FRACTION).coerceIn(64.dp, 200.dp)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(edgeWidth)
                        .fillMaxHeight()
                        .semantics { contentDescription = Strings.heroPreviousSlide }
                        .clickable(onClick = goToPrevious)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(edgeWidth)
                        .fillMaxHeight()
                        .semantics { contentDescription = Strings.heroNextSlide }
                        .clickable(onClick = goToNext)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = edgeWidth)
                        .semantics { contentDescription = Strings.heroOpenSlide }
                        .clickable(
                            interactionSource = highlight.interactionSource,
                            onClick = openCurrent,
                        )
                )
            }
        }

        HeroIndicators(
            count = items.size,
            active = page,
            onSelect = { page = it },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 96.dp, bottom = 48.dp)
        )
    }
}

/** Backdrop, scrims and the poster + title block of a single slide. */
@Composable
private fun SlideContent(slide: HeroSlide) {
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = slide.backdrop?.url,
            contentDescription = slide.backdrop?.alt ?: slide.title,
            contentScale = ContentScale.Crop,
            // Crop centers by default, which cuts the top off wide backdrops;
            // aligning to the top keeps the artwork's upper area visible.
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize()
        )

        // Darken the content side (start = right in RTL) and the bottom so
        // overlay content stays legible. Gradient stops mirror with the
        // layout direction because brushes are not direction-aware.
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isRtl) {
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.45f to Color.Black.copy(alpha = 0.35f),
                            1f to Color.Black.copy(alpha = 0.85f)
                        )
                    } else {
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.85f),
                            0.55f to Color.Black.copy(alpha = 0.35f),
                            1f to Color.Transparent
                        )
                    }
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.7f)
                    )
                )
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 96.dp, end = 96.dp, bottom = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            AsyncImage(
                model = slide.poster,
                contentDescription = slide.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(132.dp)
                    .height(198.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = slide.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    slide.rating?.let { RatingBadge(rating = it) }
                    slide.year?.let { year ->
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Small pill with a star and the IMDb-style rating. */
@Composable
private fun RatingBadge(rating: Double, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = Strings.ratingFormat.format(rating.toString()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Row of pill-shaped page indicators; the active one is wider and branded. */
@Composable
private fun HeroIndicators(
    count: Int,
    active: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val isActive = index == active
            val dotHighlight = rememberDesktopHighlight()
            val width by animateDpAsState(
                targetValue = if (isActive) 28.dp else 10.dp,
                label = "indicator-width"
            )
            Box(
                modifier = Modifier
                    .then(dotHighlight.modifier)
                    .clickable(
                        interactionSource = dotHighlight.interactionSource,
                        onClick = { onSelect(index) },
                    )
                    .height(24.dp)
                    .widthIn(min = 24.dp)
                    .padding(horizontal = 4.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(width)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            when {
                                isActive -> MaterialTheme.colorScheme.primary
                                dotHighlight.highlighted -> {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                }
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            }
                        )
                )
            }
        }
    }
}
