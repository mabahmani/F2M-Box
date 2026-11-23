package com.mab.f2mbox.desktop.player

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
     *
     * No branch routes through a shell: `ProcessBuilder` quotes arguments
     * but does not escape shell metacharacters, and a target that re-parses
     * the joined command line (`cmd /c start …`) would split a url bearing
     * `&` — ordinary in query strings — and let a hostile url execute
     * commands. Windows therefore goes through `rundll32`, the standard
     * non-shell way to invoke the ShellExecute handler.
     */
    fun commandFor(osName: String, url: String): List<String>? {
        val os = osName.lowercase()
        return when {
            os.contains("mac") -> listOf("open", url)
            os.contains("win") -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
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

    /** Starts the command, swallowing any failure (missing command, security manager, …). */
    fun defaultRun(command: List<String>) {
        try {
            ProcessBuilder(command).start()
        } catch (_: Exception) {
            // Best-effort hand-off; the setup card stays on screen.
        }
    }
}
