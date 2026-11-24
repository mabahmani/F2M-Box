package com.mab.f2mbox.desktop.player

/**
 * Utility for parsing SubRip (.srt) text content into [SubtitleCue] objects,
 * enabling pure Compose text subtitle rendering on Desktop.
 */
object SrtParser {

    private val htmlTagRegex = Regex("<[^>]*>")
    private val timestampRegex = Regex("""(?:(\d{2}):)?(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(?:(\d{2}):)?(\d{2}):(\d{2})[,.](\d{3})""")

    /**
     * Parses raw SRT/WebVTT content into a sorted list of [SubtitleCue]s.
     */
    fun parse(rawSrt: String): List<SubtitleCue> {
        if (rawSrt.isBlank()) return emptyList()

        val blocks = rawSrt.split(Regex("""\r?\n\r?\n"""))
        val cues = mutableListOf<SubtitleCue>()

        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue

            var timestampLineIndex = -1
            var matchResult: MatchResult? = null

            for (i in lines.indices) {
                val match = timestampRegex.find(lines[i])
                if (match != null) {
                    timestampLineIndex = i
                    matchResult = match
                    break
                }
            }

            if (timestampLineIndex == -1 || matchResult == null) continue

            val groups = matchResult.groupValues
            val startMs = parseTimeToMs(
                hStr = groups[1],
                mStr = groups[2],
                sStr = groups[3],
                msStr = groups[4]
            )
            val endMs = parseTimeToMs(
                hStr = groups[5],
                mStr = groups[6],
                sStr = groups[7],
                msStr = groups[8]
            )

            val textLines = lines.subList(timestampLineIndex + 1, lines.size)
            val cleanText = textLines
                .joinToString("\n")
                .replace(htmlTagRegex, "")
                .trim()

            if (cleanText.isNotEmpty()) {
                cues.add(SubtitleCue(startMs, endMs, cleanText))
            }
        }

        return cues.sortedBy { it.startMs }
    }

    /**
     * Finds the active [SubtitleCue] for the current video position [timeMs].
     */
    fun findActiveCue(cues: List<SubtitleCue>, timeMs: Long): SubtitleCue? {
        if (cues.isEmpty()) return null
        return cues.firstOrNull { timeMs in it.startMs..it.endMs }
    }

    private fun parseTimeToMs(hStr: String, mStr: String, sStr: String, msStr: String): Long {
        val h = hStr.takeIf { it.isNotEmpty() }?.toLong() ?: 0L
        val m = mStr.toLong()
        val s = sStr.toLong()
        val ms = msStr.toLong()
        return (h * 3600_000L) + (m * 60_000L) + (s * 1000L) + ms
    }
}
