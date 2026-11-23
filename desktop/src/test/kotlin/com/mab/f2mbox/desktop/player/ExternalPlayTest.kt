package com.mab.f2mbox.desktop.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
            listOf("cmd", "/c", "start", "", "https://x/v.mp4"),
            ExternalPlay.commandFor("Windows 11", "https://x/v.mp4")
        )
        assertEquals(
            listOf("xdg-open", "https://x/v.mp4"),
            ExternalPlay.commandFor("Linux", "https://x/v.mp4")
        )
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
