package com.mab.f2mbox.desktop.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DesktopSubtitleViewTest {

    @Test
    fun `style text size and weight mappings`() {
        val style = SubtitleStyle(textSizeSp = 25f, bold = true)
        assertEquals(25f, style.textSizeSp)
        assertEquals(true, style.bold)
    }

    @Test
    fun `style background opacity alpha fraction`() {
        val style = SubtitleStyle(backgroundOpacityPercent = 50)
        val alpha = style.backgroundOpacityPercent / 100f
        assertEquals(0.5f, alpha, 0.01f)
    }

    @Test
    fun `active cue lookup returns correct cue or null`() {
        val cues = listOf(
            SubtitleCue(1000L, 3000L, "Hello"),
            SubtitleCue(4000L, 6000L, "World")
        )
        val activeAt2000 = SrtParser.findActiveCue(cues, 2000L)
        assertNotNull(activeAt2000)
        assertEquals("Hello", activeAt2000?.text)
    }
}
