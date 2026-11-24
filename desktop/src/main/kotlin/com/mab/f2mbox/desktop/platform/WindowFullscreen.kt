package com.mab.f2mbox.desktop.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState

private data class SavedWindowBounds(
    val size: DpSize,
    val position: WindowPosition,
    val placement: WindowPlacement,
)

/**
 * Toggles [WindowPlacement.Fullscreen] on the main app window (not libVLC
 * embed fullscreen — Compose overlays must stay above the video surface).
 * Saves size/position/placement before entering and restores on [exit].
 */
@Stable
class WindowFullscreenState internal constructor(
    private val windowState: WindowState,
) {
    private var saved: SavedWindowBounds? = null

    val isFullscreen: Boolean
        get() = windowState.placement == WindowPlacement.Fullscreen

    fun enter() {
        if (isFullscreen) return
        saved = SavedWindowBounds(
            size = windowState.size,
            position = windowState.position,
            placement = windowState.placement,
        )
        windowState.placement = WindowPlacement.Fullscreen
    }

    fun exit() {
        if (!isFullscreen) return
        val prev = saved
        windowState.placement = WindowPlacement.Floating
        if (prev != null) {
            windowState.size = prev.size
            windowState.position = prev.position
            if (prev.placement != WindowPlacement.Fullscreen) {
                windowState.placement = prev.placement
            }
        }
        saved = null
    }

    fun toggle() {
        if (isFullscreen) exit() else enter()
    }
}

@Composable
fun rememberWindowFullscreen(windowState: WindowState): WindowFullscreenState =
    remember(windowState) { WindowFullscreenState(windowState) }

/** The main window's cinema fullscreen controller; null outside [Main]. */
val LocalWindowFullscreen = compositionLocalOf<WindowFullscreenState?> { null }