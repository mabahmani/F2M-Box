package com.mab.f2mbox.desktop.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The per-OS open command is pure and the runner is injectable, so these
 * tests never spawn a process. The url travels as one argument list entry
 * — spaces and shell-special characters must reach the player intact.
 */
class ExternalPlayTest {

    @Test
    fun `per-os open commands`() {
        assertEquals(
            listOf("open", "https://x/a b.mp4"),
            ExternalPlay.commandFor("Mac OS X", "https://x/a b.mp4")
        )
        assertEquals(
            listOf("rundll32", "url.dll,FileProtocolHandler", "https://x/v.mp4"),
            ExternalPlay.commandFor("Windows 11", "https://x/v.mp4")
        )
        assertEquals(
            listOf("xdg-open", "https://x/v.mp4"),
            ExternalPlay.commandFor("Linux", "https://x/v.mp4")
        )
    }

    @Test
    fun `no os routes through a shell`() {
        // cmd.exe re-parses the joined command line, so a url bearing `&`
        // (ordinary in query strings) would be split — and a hostile url
        // could execute commands. The arg list only protects the url when
        // the target is not a shell.
        for (os in listOf("Mac OS X", "Windows 11", "Windows 7", "Linux")) {
            for (url in listOf(
                "https://x/v.mp4?w=640&h=360",
                "https://x/a b.mp4",
                "https://x/v.mp4&calc"
            )) {
                val command = ExternalPlay.commandFor(os, url)!!
                assertTrue(
                    "shell in command for $os: $command",
                    command.first() != "cmd" && command.first() != "sh" && command.first() != "bash"
                )
                // The url travels as one intact argument.
                assertTrue(command.contains(url))
            }
        }
    }

    @Test
    fun `open hands the command to the runner`() {
        var ran: List<String>? = null
        ExternalPlay.open("https://x/v.mp4", osName = "Mac OS X") { ran = it }
        assertEquals(listOf("open", "https://x/v.mp4"), ran)
    }

    @Test
    fun `unknown os yields no command and open is a no-op`() {
        assertNull(ExternalPlay.commandFor("SunOS", "https://x/v.mp4"))
        var ran = false
        ExternalPlay.open("https://x/v.mp4", osName = "SunOS") { ran = true }
        assertFalse(ran)
    }

    @Test
    fun `defaultRun swallows process failures`() {
        ExternalPlay.defaultRun(listOf("definitely-not-a-real-command-xyz"))
    }
}
