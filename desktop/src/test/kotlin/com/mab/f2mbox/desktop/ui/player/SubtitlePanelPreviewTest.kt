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

    @Test
    fun `edge row selects shadow and outline with the arrow keys`() = runComposeUiTest {
        var style by mutableStateOf(SubtitleStyle(edge = SubtitleStyle.Edge.None))
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
        val panel = onNodeWithTag("subtitle-panel")

        // Row 0 (size) down to row 4 (edge).
        repeat(4) { panel.performKeyInput { pressKey(Key.DirectionDown) } }

        // Right moves None -> Shadow, then Enter selects it.
        panel.performKeyInput { pressKey(Key.DirectionRight) }
        panel.performKeyInput { pressKey(Key.Enter) }
        assertEquals(SubtitleStyle.Edge.Shadow, style.edge)

        // Right again moves Shadow -> Outline, then Enter selects it.
        panel.performKeyInput { pressKey(Key.DirectionRight) }
        panel.performKeyInput { pressKey(Key.Enter) }
        assertEquals(SubtitleStyle.Edge.Outline, style.edge)
    }

    @Test
    fun `color row scrolls the last color into view with the arrow keys`() = runComposeUiTest {
        var style by mutableStateOf(SubtitleStyle(colorRgb = SubtitleStyle.ColorOptions.first()))
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
        val panel = onNodeWithTag("subtitle-panel")

        // Row 0 (size) -> row 2 (color).
        repeat(2) { panel.performKeyInput { pressKey(Key.DirectionDown) } }
        // Move the highlight across every color to the last one, then select it.
        repeat(SubtitleStyle.ColorOptions.lastIndex) {
            panel.performKeyInput { pressKey(Key.DirectionRight) }
        }
        panel.performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertEquals(SubtitleStyle.ColorOptions.last(), style.colorRgb)

        // The last chip must have been scrolled into view: a clipped-off chip
        // reports zero-width bounds, so require a real width inside the panel.
        val panelBounds = onNodeWithTag("subtitle-panel").fetchSemanticsNode().boundsInRoot
        val chipBounds = onNodeWithText("Light blue").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "last color chip (${chipBounds.left}..${chipBounds.right}, w=${chipBounds.width}) " +
                "should be visible within the panel (${panelBounds.left}..${panelBounds.right})",
            chipBounds.width > 0f &&
                chipBounds.left >= panelBounds.left - 1f &&
                chipBounds.right <= panelBounds.right + 1f
        )
    }
}
