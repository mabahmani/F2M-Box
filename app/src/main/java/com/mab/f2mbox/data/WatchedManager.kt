package com.mab.f2mbox.data

import android.content.Context
import androidx.core.content.edit
import com.mab.f2mbox.data.WatchedItem
import com.mab.f2mbox.storage.WatchedCodec
import com.mab.f2mbox.storage.WatchedHistory
import com.mab.f2mbox.storage.WatchedStorage

/**
 * Android persistence of the watch history: a JSON list inside
 * SharedPreferences. Pref name and key are the originals, so histories
 * written by app versions before the storage split stay readable.
 *
 * All dedupe/order/cap logic lives in [WatchedHistory] (shared), so the
 * desktop app gets the exact same behavior through its own storage.
 */
object WatchedManager {

    fun getWatchedItems(context: Context) =
        WatchedHistory.getWatchedItems(AndroidWatchedStorage(context))

    fun save(context: Context, item: WatchedItem) =
        WatchedHistory.save(AndroidWatchedStorage(context), item)
}

/** [WatchedStorage] backed by SharedPreferences. */
class AndroidWatchedStorage(context: Context) : WatchedStorage {

    private val prefs =
        context.getSharedPreferences("f2mbox_watched_prefs", Context.MODE_PRIVATE)

    override fun load() = WatchedCodec.decode(prefs.getString("watched_items", null))

    override fun save(items: List<WatchedItem>) {
        prefs.edit { putString("watched_items", WatchedCodec.encode(items)) }
    }
}
