package com.mab.f2mbox.data

import com.mab.f2mbox.data.remote.ImageRef
import com.mab.f2mbox.data.remote.SectionItem
import kotlinx.serialization.Serializable

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
