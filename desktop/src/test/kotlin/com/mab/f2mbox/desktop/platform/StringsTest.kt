package com.mab.f2mbox.desktop.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the desktop string table against the Android resources they
 * pair with: values verbatim, and every LTR-leaning string keeps its
 * trailing LRM (\u200E) so RTL layout does not flip trailing
 * punctuation to the wrong side.
 */
class StringsTest {

    @Test
    fun `ltr-leaning strings keep their trailing LRM`() {
        for (s in listOf(
            Strings.loading,
            Strings.searchHint,
            Strings.errorDomainInvalid,
            Strings.playerError,
            Strings.pageFormat,
            Strings.clearCacheRefresh,
        )) {
            assertTrue("missing LRM: '$s'", s.endsWith('\u200E'))
        }
    }

    @Test
    fun `formats mirror the android %1$ placeholders`() {
        assertEquals("★ 8.4", Strings.ratingFormat.format("8.4"))
        assertEquals("Page 7\u200E", Strings.pageFormat.format(7))
        assertEquals("24 min\u200E", Strings.infoDurationValue.format(24))
        assertEquals("Comments (3)\u200E", Strings.comments.format(3))
        assertEquals("👍 12", Strings.likeCount.format(12))
    }

    @Test
    fun `values are verbatim`() {
        assertEquals("Search", Strings.search)
        assertEquals("Movie or series name…\u200E", Strings.searchHint)
        assertEquals("Playback speed", Strings.playerSpeed)
        assertEquals("Know the new site address? Enter it below (without https://).\u200E", Strings.errorDomainHint)
        assertEquals("The server may take up to 2 minutes to wake up", Strings.serverWakeupHint)
    }
}
