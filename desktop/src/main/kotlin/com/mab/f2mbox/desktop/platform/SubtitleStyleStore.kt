package com.mab.f2mbox.desktop.platform

import com.mab.f2mbox.desktop.player.SubtitleStyle
import java.nio.file.Files
import java.nio.file.Path

/**
 * Persists the player's subtitle look as one encoded string (see
 * [SubtitleStyle.encode]) in the app-support dir — the desktop counterpart
 * of the Android player's `player_subtitle_settings` SharedPreferences file.
 *
 * Reads/writes are small and infrequent (only when the user saves the
 * panel), so they run inline; a corrupt or unreadable file falls back to
 * the default look rather than breaking playback.
 */
class SubtitleStyleStore(private val file: Path) {

    fun load(): SubtitleStyle = try {
        if (Files.exists(file)) SubtitleStyle.load(Files.readString(file).trim())
        else SubtitleStyle.Default
    } catch (_: Exception) {
        SubtitleStyle.Default
    }

    fun save(style: SubtitleStyle) {
        try {
            file.parent?.let { Files.createDirectories(it) }
            Files.writeString(file, SubtitleStyle.encode(style))
        } catch (_: Exception) {
            // Styling is a preference; failing to persist it must not
            // interrupt playback.
        }
    }
}

/** The subtitle-style store under the app-support dir. */
fun subtitleStyleStore(baseDir: Path = defaultAppSupportDir()): SubtitleStyleStore =
    SubtitleStyleStore(baseDir.resolve("subtitle-style.txt"))
