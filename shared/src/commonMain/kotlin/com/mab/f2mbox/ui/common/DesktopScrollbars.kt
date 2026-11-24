package com.mab.f2mbox.ui.common

import androidx.compose.runtime.staticCompositionLocalOf

/** When true, JVM/desktop builds show Compose scrollbars on lazy and scroll containers. */
val LocalShowDesktopScrollbars = staticCompositionLocalOf { false }