package com.mab.f2mbox.ui.common

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val HorizontalScrollbarTopMargin = 16.dp
/** Clears the row thumb from overlapping poster/card focus rings. */
private val HorizontalRowScrollbarClearance = 8.dp

/** Physical right edge without forcing LTR on scrollable content. */
@Composable
private fun physicalRightVerticalAlignment(): Alignment =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
        Alignment.CenterStart
    } else {
        Alignment.CenterEnd
    }

@Composable
actual fun PlatformF2mLazyColumn(
    state: LazyListState,
    modifier: Modifier,
    contentPadding: PaddingValues,
    reverseLayout: Boolean,
    verticalArrangement: Arrangement.Vertical,
    horizontalAlignment: Alignment.Horizontal,
    flingBehavior: FlingBehavior,
    userScrollEnabled: Boolean,
    content: LazyListScope.() -> Unit,
) {
    if (!LocalShowDesktopScrollbars.current) {
        LazyColumn(
            state = state,
            modifier = modifier,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            flingBehavior = flingBehavior,
            userScrollEnabled = userScrollEnabled,
            content = content,
        )
        return
    }
    val adapter = rememberScrollbarAdapter(state)
    Row(modifier) {
        VerticalScrollbar(
            adapter = adapter,
            modifier = Modifier.fillMaxHeight(),
            reverseLayout = reverseLayout,
        )
        LazyColumn(
            state = state,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            flingBehavior = flingBehavior,
            userScrollEnabled = userScrollEnabled,
            content = content,
        )
    }
}

@Composable
actual fun PlatformF2mLazyRow(
    state: LazyListState,
    modifier: Modifier,
    contentPadding: PaddingValues,
    reverseLayout: Boolean,
    horizontalArrangement: Arrangement.Horizontal,
    verticalAlignment: Alignment.Vertical,
    flingBehavior: FlingBehavior,
    userScrollEnabled: Boolean,
    content: LazyListScope.() -> Unit,
) {
    if (!LocalShowDesktopScrollbars.current) {
        LazyRow(
            state = state,
            modifier = modifier,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
            flingBehavior = flingBehavior,
            userScrollEnabled = userScrollEnabled,
            content = content,
        )
        return
    }
    val adapter = rememberScrollbarAdapter(state)
    Box(modifier) {
        LazyRow(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = HorizontalRowScrollbarClearance + HorizontalScrollbarTopMargin),
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
            flingBehavior = flingBehavior,
            userScrollEnabled = userScrollEnabled,
            content = content,
        )
        HorizontalScrollbar(
            adapter = adapter,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(top = HorizontalScrollbarTopMargin),
            reverseLayout = reverseLayout,
        )
    }
}

@Composable
actual fun PlatformF2mLazyVerticalGrid(
    columns: GridCells,
    state: LazyGridState,
    modifier: Modifier,
    contentPadding: PaddingValues,
    reverseLayout: Boolean,
    verticalArrangement: Arrangement.Vertical,
    horizontalArrangement: Arrangement.Horizontal,
    flingBehavior: FlingBehavior,
    userScrollEnabled: Boolean,
    content: LazyGridScope.() -> Unit,
) {
    if (!LocalShowDesktopScrollbars.current) {
        LazyVerticalGrid(
            columns = columns,
            state = state,
            modifier = modifier,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            verticalArrangement = verticalArrangement,
            horizontalArrangement = horizontalArrangement,
            flingBehavior = flingBehavior,
            userScrollEnabled = userScrollEnabled,
            content = content,
        )
        return
    }
    val adapter = rememberScrollbarAdapter(state)
    Row(modifier) {
        VerticalScrollbar(
            adapter = adapter,
            modifier = Modifier.fillMaxHeight(),
            reverseLayout = reverseLayout,
        )
        LazyVerticalGrid(
            columns = columns,
            state = state,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            verticalArrangement = verticalArrangement,
            horizontalArrangement = horizontalArrangement,
            flingBehavior = flingBehavior,
            userScrollEnabled = userScrollEnabled,
            content = content,
        )
    }
}

@Composable
actual fun PlatformF2mVerticalScroll(
    modifier: Modifier,
    state: ScrollState,
    content: @Composable () -> Unit,
) {
    if (!LocalShowDesktopScrollbars.current) {
        Column(modifier = modifier.verticalScroll(state)) {
            content()
        }
        return
    }
    val adapter = rememberScrollbarAdapter(state)
    Box {
        Column(modifier = modifier.verticalScroll(state)) {
            content()
        }
        VerticalScrollbar(
            adapter = adapter,
            modifier = Modifier
                .align(physicalRightVerticalAlignment())
                .fillMaxHeight(),
        )
    }
}

@Composable
actual fun PlatformF2mVerticalScrollColumn(
    modifier: Modifier,
    state: ScrollState,
    verticalArrangement: Arrangement.Vertical,
    horizontalAlignment: Alignment.Horizontal,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!LocalShowDesktopScrollbars.current) {
        Column(
            modifier = modifier.verticalScroll(state),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
        return
    }
    val adapter = rememberScrollbarAdapter(state)
    Row(modifier) {
        VerticalScrollbar(
            adapter = adapter,
            modifier = Modifier.fillMaxHeight(),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(state),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}