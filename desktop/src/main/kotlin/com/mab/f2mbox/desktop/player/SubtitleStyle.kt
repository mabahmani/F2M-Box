package com.mab.f2mbox.desktop.player

import kotlin.math.roundToInt

/**
 * User-customizable subtitle appearance, the desktop port of the Android
 * player's `SubtitleSettings`. Persisted through the desktop key-value
 * store (the SharedPreferences counterpart) as one encoded string.
 *
 * libvlc's freetype text renderer reads its styling from the **global**
 * libvlc configuration, not from per-media input options: passing these
 * as `:opt=value` media options is silently ignored (verified with a
 * paused on/off frame capture — the subtitle footprint was byte-identical
 * with and without them). They only take effect as `--opt=value` process
 * arguments handed to `libvlc_new` (the [VlcPlayerEngine]'s factory). So a
 * style change means rebuilding the engine — see `VlcPlayerEngine` and the
 * player screen's panel. Styling reaches plain-text subtitles;
 * image-based tracks (PGS/ASS-rendered) keep their own styling, same as
 * VLC itself.
 *
 * Option names/values verified against VLC 3 (`--full-help`):
 * `sub-text-scale` 10..500, `freetype-bold`, `freetype-color` (RGB int),
 * `freetype-background-opacity` 0..255, `freetype-outline-thickness`
 * {0,2,4,6}, `freetype-outline-opacity`/`freetype-shadow-opacity`
 * 0..255, `sub-margin` in pixels.
 */
data class SubtitleStyle(
    val textSizeSp: Float = 20f,
    val bold: Boolean = false,
    val colorRgb: Int = 0xFFFFFF,
    val backgroundOpacityPercent: Int = 0,
    val edge: Edge = Edge.None,
    val edgeStrengthPercent: Int = 100,
    val bottomOffsetPercent: Int = 2
) {

    /** Edge treatment, ported from Android's CaptionStyleCompat edge types. */
    enum class Edge { None, Shadow, Outline }

    /**
     * The libvlc **global** arguments for this style — the `--opt=value`
     * form libvlc reads for the freetype renderer (per-media `:` options
     * are ignored; see the class KDoc). [videoHeightPx] scales the bottom
     * offset (a fraction of the video height, as on Android) into
     * `sub-margin` pixels; non-positive heights fall back to 1080. The
     * height is only known once media plays, so callers pass a sensible
     * constant (the player uses 1080).
     *
     * [fontFamily] is the libvlc `--freetype-font` family (the app's
     * Vazirmatn, registered by the platform). Null leaves libvlc's default
     * font — the safe fallback if the bundled font could not be registered.
     */
    fun toVlcArgs(videoHeightPx: Int = 1080, fontFamily: String? = null): List<String> {
        val height = if (videoHeightPx > 0) videoHeightPx else 1080
        val edgeArgs = when (edge) {
            Edge.None -> listOf(
                "--freetype-outline-thickness=0",
                "--freetype-shadow-opacity=0"
            )
            Edge.Shadow -> listOf(
                "--freetype-outline-thickness=0",
                "--freetype-shadow-opacity=${opacity255(edgeStrengthPercent)}"
            )
            Edge.Outline -> listOf(
                "--freetype-outline-thickness=4",
                "--freetype-outline-opacity=${opacity255(edgeStrengthPercent)}"
            )
        }
        val fontArg = fontFamily?.takeIf { it.isNotBlank() }
            ?.let { listOf("--freetype-font=$it") }
            ?: emptyList()
        return fontArg + listOf(
            "--sub-text-scale=${(textSizeSp / 20f * 100f).roundToInt().coerceIn(10, 500)}",
            // A boolean flag has no "=0" form: it is on as `--freetype-bold`
            // and off as `--no-freetype-bold`.
            if (bold) "--freetype-bold" else "--no-freetype-bold",
            "--freetype-color=${colorRgb and 0xFFFFFF}",
            "--freetype-background-opacity=${opacity255(backgroundOpacityPercent)}"
        ) + edgeArgs + listOf(
            "--sub-margin=${(bottomOffsetPercent / 100f * height).roundToInt()}"
        )
    }

    private fun opacity255(percent: Int) = (percent.coerceIn(0, 100) * 255 / 100)

    companion object {

        /** Subtitle size choices in sp, tuned for 10-foot TV viewing (Android's set). */
        val SizeOptions = listOf(16f, 20f, 25f, 32f)

        /** Text colors as RGB ints (Android's seven choices, alpha dropped). */
        val ColorOptions = listOf(
            0xFFFFFF,
            0xFFFF00,
            0x00E5FF, // cyan
            0x9CCC65, // soft green
            0xFF9800, // orange
            0xFF4081, // pink
            0x4FC3F7  // light blue
        )

        val Default = SubtitleStyle()

        private const val SEP = ";"

        /** Encodes every field; [load] round-trips this exactly. */
        fun encode(style: SubtitleStyle): String = listOf(
            "size=${style.textSizeSp}",
            "bold=${if (style.bold) 1 else 0}",
            "color=${style.colorRgb}",
            "bg=${style.backgroundOpacityPercent}",
            "edge=${style.edge.name}",
            "strength=${style.edgeStrengthPercent}",
            "offset=${style.bottomOffsetPercent}"
        ).joinToString(SEP)

        /**
         * Parses [raw] field-tolerantly: a malformed, missing, unknown or
         * out-of-range field falls back to its default — loading stored
         * data from any version never throws.
         */
        fun load(raw: String?): SubtitleStyle {
            if (raw.isNullOrBlank()) return Default
            var style = Default
            for (pair in raw.split(SEP)) {
                val eq = pair.indexOf('=')
                if (eq <= 0) continue
                val key = pair.substring(0, eq)
                val value = pair.substring(eq + 1)
                style = when (key) {
                    "size" -> value.toFloatOrNull()
                        ?.takeIf { it in SizeOptions }?.let { style.copy(textSizeSp = it) }
                    "bold" -> value.toIntOrNull()
                        ?.takeIf { it == 0 || it == 1 }?.let { style.copy(bold = it == 1) }
                    "color" -> value.toIntOrNull()
                        ?.takeIf { it in 0..0xFFFFFF }?.let { style.copy(colorRgb = it) }
                    "bg" -> value.toIntOrNull()
                        ?.takeIf { it in 0..100 }?.let { style.copy(backgroundOpacityPercent = it) }
                    "edge" -> Edge.entries.firstOrNull { it.name == value }
                        ?.let { style.copy(edge = it) }
                    "strength" -> value.toIntOrNull()
                        ?.takeIf { it in 0..100 }?.let { style.copy(edgeStrengthPercent = it) }
                    "offset" -> value.toIntOrNull()
                        ?.takeIf { it in 0..30 }?.let { style.copy(bottomOffsetPercent = it) }
                    else -> style
                } ?: style
            }
            return style
        }
    }
}
