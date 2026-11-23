package com.mab.f2mbox.desktop.platform

import com.mab.f2mbox.data.WatchedItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class DesktopStoresTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `key value store round-trips and persists across instances`() = runBlocking {
        val file = tmp.newFile("state.json").toPath()
        val store = FileKeyValueStore(file)

        store.putString("k1", "v1")
        assertEquals("v1", store.getString("k1"))

        // A fresh instance over the same file sees the write.
        assertEquals("v1", FileKeyValueStore(file).getString("k1"))

        store.putString("k1", "v2")
        store.putString("k2", "v3")
        assertNull(store.getString("missing"))

        val reloaded = FileKeyValueStore(file)
        assertEquals("v2", reloaded.getString("k1"))
        assertEquals("v3", reloaded.getString("k2"))

        reloaded.remove("k1")
        assertNull(reloaded.getString("k1"))
        assertEquals("v3", FileKeyValueStore(file).getString("k2"))
        assertTrue(Files.exists(file))
    }

    @Test
    fun `watched storage round-trips through the shared codec`() {
        val file = tmp.newFile("watched.json").toPath()
        val storage = DesktopWatchedStorage(file)

        assertTrue(storage.load().isEmpty())
        storage.save(
            listOf(
                WatchedItem(title = "فیلم", url = "https://x/1", posterUrl = "https://x/p.jpg", timestamp = 5),
            )
        )
        val reloaded = DesktopWatchedStorage(file)
        val loaded = reloaded.load()
        assertEquals(1, loaded.size)
        assertEquals("فیلم", loaded[0].title)
        assertEquals(5L, loaded[0].timestamp)
    }

    @Test
    fun `desktopStores wiring pairs both stores over the app dir`() = runBlocking {
        val (kv, watched) = desktopStores(tmp.root.toPath().resolve("F2MBox"))
        kv.putString("probe", "ok")
        watched.save(emptyList())
        assertEquals("ok", kv.getString("probe"))
        assertTrue(tmp.root.toPath().resolve("F2MBox").resolve("scraper-state.json").toFile().exists())
        assertTrue(tmp.root.toPath().resolve("F2MBox").resolve("watched.json").toFile().exists())
    }
}
