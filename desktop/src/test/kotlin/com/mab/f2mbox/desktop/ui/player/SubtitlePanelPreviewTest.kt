package com.mab.f2mbox.desktop.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import com.mab.f2mbox.desktop.player.SubtitleStyle
import com.mab.f2mbox.desktop.ui.theme.F2MBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The customize panel now previews the draft subtitle style live. These tests
 * pin the two user-visible promises: the panel actually shows a sample line,
 * and the line follows the draft's text size before Save.
 */
@OptIn(ExperimentalTestApi::class)
class SubtitlePanelPreviewTest {

    @Test
    fun `preview follows the draft text size`() = runComposeUiTest {
        var style by mutableStateOf(SubtitleStyle(textSizeSp = 12f))
        setContent {
            F2MBoxTheme {
                SubtitlePreview(style = style)
            }
        }

        val small = onNodeWithText(SubtitlePreviewSample).fetchSemanticsNode().size
        style = style.copy(textSizeSp = 40f)
        waitForIdle()
        val large = onNodeWithText(SubtitlePreviewSample).fetchSemanticsNode().size

        assertTrue(
            "40sp sample should be wider than 12sp (small=$small, large=$large)",
            large.width > small.width
        )
    }

    @Test
    fun `panel shows the live preview sample`() = runComposeUiTest {
        setContent {
            F2MBoxTheme {
                SubtitleSettingsPanel(
                    style = SubtitleStyle(),
                    onChange = {},
                    onSave = {},
                    onCancel = {}
                )
            }
        }

        onNodeWithText(SubtitlePreviewSample).assertIsDisplayed()
    }

    @Test
    fun `size row adjusts the draft size with the arrow keys`() = runComposeUiTest {
        var style by mutableStateOf(SubtitleStyle(textSizeSp = 20f))
        setContent {
            F2MBoxTheme {
                SubtitleSettingsPanel(
                    style = style,
                    onChange = { style = it },
                    onSave = {},
                    onCancel = {},
                    modifier = Modifier.testTag("subtitle-panel")
                )
            }
        }
        waitForIdle()

        onNodeWithTag("subtitle-panel").performKeyInput { pressKey(Key.DirectionRight) }

        assertEquals(21f, style.textSizeSp)
    }
}
