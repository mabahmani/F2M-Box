package com.mab.f2mbox.desktop.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The mapping is the contract with libvlc: option names/values verified
 * against the installed VLC 3 (`--full-help`): sub-text-scale 10..500,
 * freetype-bold, freetype-color (RGB int), freetype-background-opacity
 * and outline/shadow opacity 0..255, freetype-outline-thickness
 * {0,2,4,6}, sub-margin in pixels. Options carry the ":" prefix libvlc
 * media options require.
 */
class SubtitleStyleTest {

    @Test
    fun `default style maps to libvlc defaults`() {
        val options = SubtitleStyle.Default.toMediaOptions(videoHeightPx = 1080)
        assertEquals(
            listOf(
                ":sub-text-scale=100",
                ":freetype-bold=0",
                ":freetype-color=16777215",
                ":freetype-background-opacity=0",
                ":freetype-outline-thickness=0",
                ":freetype-shadow-opacity=0",
                ":sub-margin=22"
            ),
            options
        )
    }

    @Test
    fun `custom style maps every setting`() {
        val style = SubtitleStyle(
            textSizeSp = 32f,
            bold = true,
            colorRgb = 0x00E5FF,
            backgroundOpacityPercent = 40,
            edge = SubtitleStyle.Edge.Outline,
            edgeStrengthPercent = 60,
            bottomOffsetPercent = 15
        )
        assertEquals(
            listOf(
                ":sub-text-scale=160",
                ":freetype-bold=1",
                ":freetype-color=58879",
                ":freetype-background-opacity=102",
                ":freetype-outline-thickness=4",
                ":freetype-outline-opacity=153",
                ":sub-margin=108"
            ),
            style.toMediaOptions(videoHeightPx = 720)
        )
    }

    @Test
    fun `shadow edge maps to shadow opacity`() {
        val style = SubtitleStyle.Default.copy(
            edge = SubtitleStyle.Edge.Shadow,
            edgeStrengthPercent = 50
        )
        val options = style.toMediaOptions(videoHeightPx = 1080)
        assertEquals(":freetype-outline-thickness=0", options[4])
        assertEquals(":freetype-shadow-opacity=127", options[5])
    }

    @Test
    fun `non-positive video height falls back to 1080`() {
        val options = SubtitleStyle.Default.toMediaOptions(videoHeightPx = 0)
        assertEquals(":sub-margin=22", options.last())
    }

    @Test
    fun `encode and load round-trip`() {
        val style = SubtitleStyle(
            textSizeSp = 25f,
            bold = true,
            colorRgb = 0xFF9800,
            backgroundOpacityPercent = 30,
            edge = SubtitleStyle.Edge.Shadow,
            edgeStrengthPercent = 80,
            bottomOffsetPercent = 12
        )
        assertEquals(style, SubtitleStyle.load(SubtitleStyle.encode(style)))
    }

    @Test
    fun `loading null or empty yields the default`() {
        assertEquals(SubtitleStyle.Default, SubtitleStyle.load(null))
        assertEquals(SubtitleStyle.Default, SubtitleStyle.load(""))
    }

    @Test
    fun `garbage fields fall back per field without throwing`() {
        val loaded = SubtitleStyle.load("size=abc;bold=yes;color=;bg=999;edge=7;strength=-5;offset=nan")
        assertEquals(SubtitleStyle.Default, loaded)
    }

    @Test
    fun `unknown keys and missing fields keep defaults`() {
        val loaded = SubtitleStyle.load("size=25;future=1")
        assertEquals(25f, loaded.textSizeSp)
        assertEquals(SubtitleStyle.Default.bold, loaded.bold)
        assertEquals(SubtitleStyle.Default.edge, loaded.edge)
    }
}
