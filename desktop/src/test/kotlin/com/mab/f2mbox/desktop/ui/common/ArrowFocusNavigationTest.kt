package com.mab.f2mbox.desktop.ui.common

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.junit.Test

/**
 * Proves the desktop arrow-key focus bridge ([arrowKeyFocusNavigation]):
 * on desktop Compose does not turn arrow presses into focus moves by
 * itself, so the modifier must do it — including into lazy items that sit
 * below the fold (the home screen's hero covers the whole viewport, and
 * the next content starts beyond it).
 */
@OptIn(ExperimentalTestApi::class)
class ArrowFocusNavigationTest {

    @Test
    fun `arrow key moves focus between siblings`() = runComposeUiTest {
        setContent {
            Box(Modifier.fillMaxSize().arrowKeyFocusNavigation()) {
                Row {
                    Box(Modifier.size(100.dp).testTag("a").focusable())
                    Box(Modifier.size(100.dp).testTag("b").focusable())
                }
            }
        }

        onNodeWithTag("a").requestFocus()
        onNodeWithTag("a").assertIsFocused()

        onNodeWithTag("a").performKeyInput { pressKey(Key.DirectionRight) }

        onNodeWithTag("b").assertIsFocused()
    }

    @Test
    fun `down reaches the lazy item below the fold`() = runComposeUiTest {
        setContent {
            Box(Modifier.fillMaxSize().arrowKeyFocusNavigation()) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(4) { index ->
                        Box(
                            Modifier
                                .fillParentMaxSize()
                                .testTag("item$index")
                                .focusable()
                        ) {
                            Text("item $index")
                        }
                    }
                }
            }
        }

        onNodeWithTag("item0").requestFocus()
        onNodeWithTag("item0").assertIsFocused()

        onNodeWithTag("item0").performKeyInput { pressKey(Key.DirectionDown) }

        onNodeWithTag("item1").assertIsFocused()
    }

    @Test
    fun `unconsumed non-arrow keys are left alone`() = runComposeUiTest {
        setContent {
            Box(Modifier.fillMaxSize().arrowKeyFocusNavigation()) {
                Row {
                    Box(Modifier.size(100.dp).testTag("a").focusable())
                    Box(Modifier.size(100.dp).testTag("b").focusable())
                }
            }
        }

        onNodeWithTag("a").requestFocus()
        onNodeWithTag("a").assertIsFocused()

        onNodeWithTag("a").performKeyInput { pressKey(Key.X) }

        onNodeWithTag("a").assertIsFocused()
    }
}
