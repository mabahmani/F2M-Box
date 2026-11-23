package com.mab.f2mbox.desktop.player

import java.nio.file.Files
import java.nio.file.Path

/**
 * Where a usable native libVLC lives. [libDir] is the directory that holds
 * the libvlc binary (and, on macOS/Linux, sits next to `plugins/`).
 */
data class VlcLocation(val libDir: Path)

/**
 * Finds the native libvlc the player engine needs. Only probes paths and
 * records JNA search paths — it never loads the library itself, so it is
 * safe (and unit-tested) on machines without VLC.
 *
 * Search order: the libVLC bundled into the app image first, then the
 * Homebrew formula, the official VLC.app bundle, and the usual Linux/Windows
 * system locations.
 */
object VlcDiscovery {

    /**
     * Directories that may hold the native libvlc binary. The bundled copy
     * (when present) is tried first, so a packaged app never depends on a
     * system VLC; source runs fall back to an installed VLC as before.
     */
    fun defaultCandidates(): List<String> {
        val candidates = mutableListOf<String>()
        bundledRootDir()?.let { candidates += bundledLibDir(it).toString() }
        candidates += listOf(
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
        return candidates
    }

    /**
     * The libVLC shipped inside the packaged app, or `null` in a source run.
     *
     * Compose Desktop exposes the app's resources directory through the
     * `compose.application.resources.dir` system property; `prepareVlc`
     * stages the natives under `vlc/` there. Windows keeps the DLLs at
     * `vlc/` (vlcj's plugin format is `<libdir>\plugins`), while macOS and
     * Linux keep them under `vlc/lib` (plugin format `<libdir>/../plugins`).
     * `-Df2mbox.vlc.dir=<dir>` overrides this, e.g. for diagnostics.
     */
    fun bundledRootDir(): Path? {
        val root = System.getProperty("f2mbox.vlc.dir")?.let { Path.of(it) }
            ?: System.getProperty("compose.application.resources.dir")
                ?.let { Path.of(it, "vlc") }
            ?: return null
        val lib = bundledLibDir(root)
        return root.takeIf { Files.isDirectory(it) && Files.isDirectory(lib) && containsLibVlc(lib) }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase().contains("win")

    private fun bundledLibDir(root: Path): Path =
        if (isWindows()) root else root.resolve("lib")

    /**
     * Probes [candidates] in order; the first directory that actually
     * contains a libvlc binary wins and is registered as a JNA search
     * path. The failure message tells the user how to install VLC — the
     * player is unusable without it.
     */
    fun findLibVlc(
        candidates: List<String> = defaultCandidates(),
    ): Result<VlcLocation> {
        for (candidate in candidates) {
            val dir = runCatching { Path.of(candidate) }.getOrNull() ?: continue
            if (Files.isDirectory(dir) && containsLibVlc(dir)) {
                // Registers search paths for JNA; no native load here.
                com.sun.jna.NativeLibrary.addSearchPath("libvlc", candidate)
                com.sun.jna.NativeLibrary.addSearchPath("libvlccore", candidate)
                return Result.success(VlcLocation(dir))
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
