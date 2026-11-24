package com.mab.f2mbox.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import com.mab.f2mbox.data.WatchedItem

fun interface WatchedWriter {
    fun save(item: WatchedItem)
}

val LocalWatchedWriter = staticCompositionLocalOf<WatchedWriter> {
    error("LocalWatchedWriter not provided")
}