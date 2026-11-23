package com.mab.f2mbox.desktop.player

import java.nio.file.Files
import java.nio.file.Path

/**
 * Finds the native libvlc the player engine needs. Only probes paths and
 * records JNA search paths — it never loads the library itself, so it is
 * safe (and unit-tested) on machines without VLC.
 *
 * On macOS the candidates are the Homebrew formula and the official
 * VLC.app bundle; on Linux/Windows the usual system locations.
 */
object VlcDiscovery {

    /** Directories that may hold the native libvlc binary. */
    fun defaultCandidates(): List<String> = listOf(
        // macOS: homebrew + official app bundle
        "/opt/homebrew/opt/libvlc/lib",
        "/usr/local/opt/libvlc/lib",
        "/Applications/VLC.app/Contents/MacOS/lib",
        // Linux
        "/usr/lib/x86_64-linux-gnu",
        "/usr/lib",
        // Windows
        "C:\\Program Files\\VideoLAN\\VLC",
        "C:\\Program Files (x86)\\VideoLAN\\VLC",
    )

    /**
     * Probes [candidates] in order; the first directory that actually
     * contains a libvlc binary wins and is registered as a JNA search
     * path. The failure message tells the user how to install VLC — the
     * player is unusable without it.
     */
    fun findLibVlc(
        candidates: List<String> = defaultCandidates(),
    ): Result<Unit> {
        for (candidate in candidates) {
            val dir = runCatching { Path.of(candidate) }.getOrNull() ?: continue
            if (Files.isDirectory(dir) && containsLibVlc(dir)) {
                // Registers the search path for JNA; no native load here.
                com.sun.jna.NativeLibrary.addSearchPath("libvlc", candidate)
                return Result.success(Unit)
            }
        }
        return Result.failure(
            IllegalStateException(
                "libvlc not found. The player needs the native libvlc — " +
                    "install VLC (macOS: brew install --cask vlc) and retry, " +
                    "or use the \"Play in system player\" button on this screen. " +
                    "Pass -Dvlcj.log=INFO to see the probed paths."
            )
        )
    }

    /** Any file whose name starts with "libvlc" (libvlc.dylib / .so / .dll). */
    private fun containsLibVlc(dir: Path): Boolean =
        Files.list(dir).use { stream ->
            stream.anyMatch { it.fileName.toString().startsWith("libvlc") }
        }
}
