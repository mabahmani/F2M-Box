package com.mab.f2mbox.ui.logs

import androidx.compose.runtime.staticCompositionLocalOf

/** Optional crash text from the previous session (Android CrashReporter). */
interface SessionCrashStore {
    fun pending(): String?
    fun clear()
}

val LocalSessionCrashStore = staticCompositionLocalOf<SessionCrashStore?> { null }