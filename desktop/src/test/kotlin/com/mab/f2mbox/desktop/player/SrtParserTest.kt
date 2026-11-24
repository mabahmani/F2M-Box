package com.mab.f2mbox.desktop.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrtParserTest {

    @Test
    fun `parses single cue with timestamp and text`() {
        val srtContent = """
            1
            00:01:20,500 --> 00:01:25,000
            Hello, world!
        """.trimIndent()

        val cues = SrtParser.parse(srtContent)
        assertEquals(1, cues.size)
        assertEquals(80500L, cues[0].startMs)
        assertEquals(85000L, cues[0].endMs)
        assertEquals("Hello, world!", cues[0].text)
    }

    @Test
    fun `parses multiple cues and multi-line text`() {
        val srtContent = """
            1
            00:00:01,000 --> 00:00:04,000
            First line
            Second line

            2
            00:00:05,000 --> 00:00:08,250
            Another cue
        """.trimIndent()

        val cues = SrtParser.parse(srtContent)
        assertEquals(2, cues.size)
        assertEquals("First line\nSecond line", cues[0].text)
        assertEquals(1000L, cues[0].startMs)
        assertEquals(4000L, cues[0].endMs)

        assertEquals("Another cue", cues[1].text)
        assertEquals(5000L, cues[1].startMs)
        assertEquals(8250L, cues[1].endMs)
    }

    @Test
    fun `strips basic HTML formatting tags`() {
        val srtContent = """
            1
            00:00:02,000 --> 00:00:06,000
            <i>Italic text</i> and <b>bold text</b>
        """.trimIndent()

        val cues = SrtParser.parse(srtContent)
        assertEquals(1, cues.size)
        assertEquals("Italic text and bold text", cues[0].text)
    }

    @Test
    fun `parses webvtt style timestamps with dots`() {
        val vttContent = """
            WEBVTT

            1
            01:20.500 --> 01:25.000
            WebVTT subtitle
        """.trimIndent()

        val cues = SrtParser.parse(vttContent)
        assertEquals(1, cues.size)
        assertEquals(80500L, cues[0].startMs)
        assertEquals(85000L, cues[0].endMs)
        assertEquals("WebVTT subtitle", cues[0].text)
    }

    @Test
    fun `handles empty or blank content safely`() {
        assertTrue(SrtParser.parse("").isEmpty())
        assertTrue(SrtParser.parse("   \n\n  ").isEmpty())
    }

    @Test
    fun `finds active cues for a given timestamp`() {
        val cues = listOf(
            SubtitleCue(startMs = 1000L, endMs = 3000L, text = "First"),
            SubtitleCue(startMs = 4000L, endMs = 7000L, text = "Second")
        )

        assertEquals("First", SrtParser.findActiveCue(cues, 2000L)?.text)
        assertEquals("Second", SrtParser.findActiveCue(cues, 5000L)?.text)
        assertEquals(null, SrtParser.findActiveCue(cues, 3500L))
        assertEquals(null, SrtParser.findActiveCue(cues, 8000L))
    }
}
