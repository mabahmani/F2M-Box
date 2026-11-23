package com.mab.f2mbox.desktop.ui.common

import androidx.compose.runtime.compositionLocalOf
import com.mab.f2mbox.desktop.platform.SubtitleStyleStore

/**
 * The subtitle-style store of the current app instance, provided at the
 * desktop window root. The player screen reads it to load the saved look
 * and writes it when the user saves the customize panel.
 */
val LocalSubtitleStyleStore = compositionLocalOf<SubtitleStyleStore> {
    error("LocalSubtitleStyleStore not provided: the window root must supply it")
}
