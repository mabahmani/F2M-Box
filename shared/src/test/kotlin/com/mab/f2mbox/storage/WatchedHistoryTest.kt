package com.mab.f2mbox.storage

import com.mab.f2mbox.data.WatchedItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the watch-history behavior the Android app has today:
 * dedupe by url OR title, newest first, hard cap of 24 entries.
 */
class WatchedHistoryTest {

    private class FakeStorage : WatchedStorage {
        var items: List<WatchedItem> = emptyList()
        override fun load(): List<WatchedItem> = items
        override fun save(items: List<WatchedItem>) {
            this.items = items
        }
    }

    private fun item(title: String, url: String, ts: Long) =
        WatchedItem(title = title, url = url, timestamp = ts)

    @Test
    fun `newest first with dedupe by url or title`() {
        val storage = FakeStorage()
        WatchedHistory.save(storage, item("A", "u1", 1))
        WatchedHistory.save(storage, item("B", "u2", 2))
        // same url as "A" → replaces it, keeps newest timestamp
        WatchedHistory.save(storage, item("A two", "u1", 3))
        // same title as "B", different url → still a duplicate
        WatchedHistory.save(storage, item("B", "u3", 4))

        val loaded = WatchedHistory.getWatchedItems(storage)
        assertEquals(listOf("B", "A two"), loaded.map { it.title })
        assertEquals(listOf(4L, 3L), loaded.map { it.timestamp })
    }

    @Test
    fun `caps the history at 24 entries keeping the newest`() {
        val storage = FakeStorage()
        for (i in 1..25) {
            WatchedHistory.save(storage, item("t$i", "u$i", i.toLong()))
        }
        val loaded = WatchedHistory.getWatchedItems(storage)
        assertEquals(24, loaded.size)
        assertEquals("t25", loaded.first().title)
        assertEquals("t2", loaded.last().title)
    }

    @Test
    fun `round-trips through the codec`() {
        val storage = FakeStorage()
        WatchedHistory.save(storage, item("قاتل", "https://x/1", 9))
        WatchedHistory.save(storage, WatchedItem(title = "p", url = "https://x/2", posterUrl = "https://x/p.jpg", timestamp = 10))

        val loaded = WatchedHistory.getWatchedItems(storage)
        assertEquals(2, loaded.size)
        assertEquals("قاتل", loaded[1].title)
        assertEquals("https://x/p.jpg", loaded[0].posterUrl)
    }
}
