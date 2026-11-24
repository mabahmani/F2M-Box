package com.mab.f2mbox.storage

import com.mab.f2mbox.data.WatchedItem

/**
 * Persistence seam for the watch history: the logic lives in
 * [WatchedHistory] (platform-free), while each app supplies where the
 * serialized list comes from — Android SharedPreferences, a desktop
 * JSON file, and anything else later.
 */
interface WatchedStorage {
    fun load(): List<WatchedItem>
    fun save(items: List<WatchedItem>)
}
