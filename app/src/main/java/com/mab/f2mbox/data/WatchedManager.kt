package com.mab.f2mbox.data

import android.content.Context
import com.mab.f2mbox.data.remote.ImageRef
import com.mab.f2mbox.data.remote.SectionItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.core.content.edit

@Serializable
data class WatchedItem(
    val title: String,
    val url: String,
    val posterUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toSectionItem(): SectionItem = SectionItem(
        title = title,
        url = url,
        poster = posterUrl?.let { ImageRef(url = it) }
    )
}

object WatchedManager {
    private const val PREF_NAME = "f2mbox_watched_prefs"
    private const val KEY_WATCHED_ITEMS = "watched_items"
    private const val MAX_ITEMS = 24

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun getWatchedItems(context: Context): List<WatchedItem> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_WATCHED_ITEMS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<WatchedItem>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, item: WatchedItem) {
        val current = getWatchedItems(context).toMutableList()
        current.removeAll { it.url == item.url || it.title == item.title }
        current.add(0, item)
        val trimmed = current.take(MAX_ITEMS)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_WATCHED_ITEMS, json.encodeToString(trimmed)) }
    }
}
