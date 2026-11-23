package com.mab.f2mbox.desktop.player

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

/**
 * Discovery probes paths and records JNA search paths — it must never
 * load the native library itself (that happens later, in the engine), so
 * these tests run on any machine.
 */
class VlcDiscoveryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `empty candidates fail with the brew hint`() {
        val result = VlcDiscovery.findLibVlc(emptyList())
        assertFalse(result.isSuccess)
        val message = result.exceptionOrNull()!!.message
        assertTrue(
            "hint missing: $message",
            message?.contains("brew install --cask vlc") == true
        )
    }

    @Test
    fun `nonexistent candidate dirs are skipped not crashed`() {
        val result = VlcDiscovery.findLibVlc(
            listOf("/nonexistent/vlc/lib", tmp.root.resolve("nope").toString())
        )
        assertFalse(result.isSuccess)
    }

    @Test
    fun `a directory holding a libvlc binary is accepted`() {
        val lib = tmp.newFolder("lib").toPath()
        Files.writeString(lib.resolve("libvlc.dylib"), "fake")
        val result = VlcDiscovery.findLibVlc(listOf(lib.toString()))
        assertTrue("dir with libvlc file must be found", result.isSuccess)
    }

    @Test
    fun `a directory without any libvlc file is rejected`() {
        val lib = tmp.newFolder("empty-lib").toPath()
        val result = VlcDiscovery.findLibVlc(listOf(lib.toString()))
        assertFalse(result.isSuccess)
    }

    @Test
    fun `default candidates include the homebrew and app-bundle paths`() {
        val candidates = VlcDiscovery.defaultCandidates()
        assertTrue(candidates.contains("/opt/homebrew/opt/libvlc/lib"))
        assertTrue(candidates.contains("/Applications/VLC.app/Contents/MacOS/lib"))
    }
}
