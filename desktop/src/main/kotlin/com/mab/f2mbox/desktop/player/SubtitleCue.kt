package com.mab.f2mbox.desktop.player

/**
 * Represents a single subtitle cue segment for desktop video playback.
 *
 * @property startMs The timestamp in milliseconds when this cue becomes visible.
 * @property endMs The timestamp in milliseconds when this cue hides.
 * @property text The plain text content of the subtitle cue.
 */
data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)
