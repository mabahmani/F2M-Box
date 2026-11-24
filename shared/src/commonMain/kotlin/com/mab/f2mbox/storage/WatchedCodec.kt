package com.mab.f2mbox.storage

import com.mab.f2mbox.data.WatchedItem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The single JSON shape of the persisted watch list. Every platform
 * storage goes through this, so a history written by one app version
 * stays readable by the others and by future versions.
 */
object WatchedCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val serializer = ListSerializer(WatchedItem.serializer())

    fun encode(items: List<WatchedItem>): String = json.encodeToString(serializer, items)

    fun decode(text: String?): List<WatchedItem> =
        if (text.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                json.decodeFromString(serializer, text)
            } catch (_: Exception) {
                emptyList()
            }
        }
}
