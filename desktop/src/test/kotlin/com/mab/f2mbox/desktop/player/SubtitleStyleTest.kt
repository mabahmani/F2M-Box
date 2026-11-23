package com.mab.f2mbox.desktop.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The mapping is the contract with libvlc: option names/values verified
 * against the installed VLC 3 (`--full-help`): sub-text-scale 10..500,
 * freetype-bold, freetype-color (RGB int), freetype-background-opacity
 * and outline/shadow opacity 0..255, freetype-outline-thickness
 * {0,2,4,6}, sub-margin in pixels. They are emitted as *global* libvlc
 * arguments ("--opt=value"), the only form the freetype renderer honours
 * (per-media ":" options are ignored — verified with a frame capture).
 */
class SubtitleStyleTest {

    @Test
    fun `default style maps to libvlc defaults`() {
        val args = SubtitleStyle.Default.toVlcArgs(videoHeightPx = 1080)
        assertEquals(
            listOf(
                "--sub-text-scale=100",
                "--no-freetype-bold",
                "--freetype-color=16777215",
                "--freetype-background-opacity=0",
                "--freetype-outline-thickness=0",
                "--freetype-shadow-opacity=0",
                "--sub-margin=22"
            ),
            args
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
                "--sub-text-scale=160",
                "--freetype-bold",
                "--freetype-color=58879",
                "--freetype-background-opacity=102",
                "--freetype-outline-thickness=4",
                "--freetype-outline-opacity=153",
                "--sub-margin=108"
            ),
            style.toVlcArgs(videoHeightPx = 720)
        )
    }

    @Test
    fun `shadow edge maps to shadow opacity`() {
        val style = SubtitleStyle.Default.copy(
            edge = SubtitleStyle.Edge.Shadow,
            edgeStrengthPercent = 50
        )
        val args = style.toVlcArgs(videoHeightPx = 1080)
        assertEquals("--freetype-outline-thickness=0", args[4])
        assertEquals("--freetype-shadow-opacity=127", args[5])
    }

    @Test
    fun `bold false emits the no-freetype-bold flag`() {
        val args = SubtitleStyle.Default.copy(bold = false).toVlcArgs()
        assertEquals("--no-freetype-bold", args[1])
    }

    @Test
    fun `every argument is a global libvlc flag`() {
        // A stray ":" (per-media option) would be silently ignored by libvlc.
        SubtitleStyle.Default.toVlcArgs().forEach { arg ->
            assert(arg.startsWith("--")) { "expected a global arg, got: $arg" }
        }
    }

    @Test
    fun `font family is emitted as a freetype-font arg`() {
        val args = SubtitleStyle.Default.toVlcArgs(fontFamily = "Vazirmatn RD")
        assertEquals("--freetype-font=Vazirmatn RD", args.first())
    }

    @Test
    fun `null or blank font family adds no font arg`() {
        assert(SubtitleStyle.Default.toVlcArgs(fontFamily = null).none { it.startsWith("--freetype-font") })
        assert(SubtitleStyle.Default.toVlcArgs(fontFamily = "  ").none { it.startsWith("--freetype-font") })
    }

    @Test
    fun `non-positive video height falls back to 1080`() {
        val args = SubtitleStyle.Default.toVlcArgs(videoHeightPx = 0)
        assertEquals("--sub-margin=22", args.last())
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
