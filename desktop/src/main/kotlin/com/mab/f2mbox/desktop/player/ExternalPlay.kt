package com.mab.f2mbox.desktop.player

import java.io.IOException

/**
 * Hands a media url to the OS's default handler — the instant playback
 * path for machines without VLC, offered from the player screen's setup
 * card.
 *
 * Nuance worth stating: for an https url every supported OS routes the
 * open request to the default **browser**, which plays the site's direct
 * MP4 links natively. The UI copy says "system player/browser", not
 * "VLC" — this is a hand-off, not in-app playback.
 *
 * The command is an argument **list** for [ProcessBuilder], never a shell
 * string, so urls with spaces or shell-special characters reach the
 * player intact. Any process failure is swallowed: the fallback is
 * best-effort and must never crash the app.
 */
object ExternalPlay {

    /**
     * The per-OS open command for [url], or `null` when [osName] is not
     * recognized (open then does nothing).
     */
    fun commandFor(osName: String, url: String): List<String>? {
        val os = osName.lowercase()
        return when {
            os.contains("mac") -> listOf("open", url)
            os.contains("win") ->
                // `start` treats its first quoted argument as a window
                // title, hence the empty title before the url.
                listOf("cmd", "/c", "start", "", url)
            os.contains("linux") || os.contains("nix") || os.contains("nux") ->
                listOf("xdg-open", url)
            else -> null
        }
    }

    /**
     * Opens [url] with the OS default handler. Never throws: an unknown
     * OS is a no-op and a failed process start is swallowed.
     */
    fun open(
        url: String,
        osName: String = System.getProperty("os.name", ""),
        run: (List<String>) -> Unit = ::defaultRun
    ) {
        commandFor(osName, url)?.let(run)
    }

    /** Starts the command, swallowing [IOException] (missing command, …). */
    fun defaultRun(command: List<String>) {
        try {
            ProcessBuilder(command).start()
        } catch (_: IOException) {
            // Best-effort hand-off; the setup card stays on screen.
        }
    }
}
