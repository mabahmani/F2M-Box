package com.mab.f2mbox.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import com.mab.f2mbox.data.WatchedItem
import com.mab.f2mbox.data.remote.HomeSection

fun interface WatchedItemsReader {
    fun load(): List<WatchedItem>
}

val LocalWatchedItems = staticCompositionLocalOf<WatchedItemsReader> {
    error("LocalWatchedItems not provided")
}

fun continueWatchingSection(
    items: List<WatchedItem>,
    title: String,
): HomeSection? {
    if (items.isEmpty()) return null
    return HomeSection(
        id = "continue-watching",
        title = title,
        items = items.map { it.toSectionItem() },
    )
}