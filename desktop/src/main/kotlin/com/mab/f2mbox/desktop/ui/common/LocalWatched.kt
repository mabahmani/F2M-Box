package com.mab.f2mbox.desktop.ui.common

import com.mab.f2mbox.storage.WatchedStorage
import androidx.compose.runtime.compositionLocalOf

/**
 * The watch-history storage of the current app instance, provided at the
 * desktop window root. Screens read it to load and record what the user
 * watched, mirroring the Android build's `WatchedManager(context, …)`.
 */
val LocalWatched = compositionLocalOf<WatchedStorage> {
    error("LocalWatched not provided: the window root must supply it")
}
