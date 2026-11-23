package com.mab.f2mbox.desktop.ui.player

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.mab.f2mbox.desktop.platform.Strings
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The no-VLC card must offer the instant fallback — hand the url to the
 * system player — next to the install hint, and stay usable afterwards
 * (Back still works, nothing navigates away on its own).
 */
@OptIn(ExperimentalTestApi::class)
class VlcSetupCardTest {

    @Test
    fun `play-externally button hands off and back still works`() = runComposeUiTest {
        var playedExternally = false
        var navigatedBack = false
        setContent {
            VlcSetupCard(
                message = "libvlc not found",
                url = "https://x/v.mp4",
                onPlayExternally = { playedExternally = true },
                onNavigateBack = { navigatedBack = true }
            )
        }

        onNodeWithText(Strings.playExternally).performClick()
        onNodeWithText(Strings.back).performClick()
        assertTrue(playedExternally)
        assertTrue(navigatedBack)
    }
}
