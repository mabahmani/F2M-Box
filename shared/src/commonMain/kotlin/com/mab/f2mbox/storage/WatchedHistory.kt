package com.mab.f2mbox.storage

import com.mab.f2mbox.data.WatchedItem

/**
 * Watch-history logic, split out of the old Android-only
 * `WatchedManager`: a saved item replaces any earlier entry with the
 * same url OR title, lands at the front (newest first), and the list is
 * capped at 24 entries.
 */
object WatchedHistory {

    private const val MAX_ITEMS = 24

    fun getWatchedItems(storage: WatchedStorage): List<WatchedItem> = storage.load()

    fun save(storage: WatchedStorage, item: WatchedItem) {
        val current = storage.load().toMutableList()
        current.removeAll { it.url == item.url || it.title == item.title }
        current.add(0, item)
        storage.save(current.take(MAX_ITEMS))
    }
}
