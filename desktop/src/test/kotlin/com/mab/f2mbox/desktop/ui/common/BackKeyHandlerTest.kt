package com.mab.f2mbox.desktop.ui.common

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mab.f2mbox.desktop.ui.home.SearchField
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Proves Backspace/Delete act as Back — but only for presses a focused
 * element did not consume: the search field consumes Backspace for text
 * deletion (even when empty), so typing never navigates back.
 */
@OptIn(ExperimentalTestApi::class)
class BackKeyHandlerTest {

    @Test
    fun `backspace and delete trigger back when unconsumed`() = runComposeUiTest {
        var backCount = 0
        setContent {
            Box(Modifier.fillMaxSize().backKeyHandler { backCount++ }) {
                Box(Modifier.size(50.dp).testTag("a").focusable())
            }
        }

        onNodeWithTag("a").requestFocus()
        onNodeWithTag("a").performKeyInput { pressKey(Key.Backspace) }
        assertEquals(1, backCount)

        onNodeWithTag("a").performKeyInput { pressKey(Key.Delete) }
        assertEquals(2, backCount)
    }

    @Test
    fun `disabled handler ignores back keys`() = runComposeUiTest {
        var backCount = 0
        setContent {
            Box(Modifier.fillMaxSize().backKeyHandler(enabled = false) { backCount++ }) {
                Box(Modifier.size(50.dp).testTag("a").focusable())
            }
        }

        onNodeWithTag("a").requestFocus()
        onNodeWithTag("a").performKeyInput { pressKey(Key.Backspace) }
        assertEquals(0, backCount)
    }

    @Test
    fun `backspace is left to the focused search field`() = runComposeUiTest {
        var backCount = 0
        var query by mutableStateOf("")
        setContent {
            Box(Modifier.fillMaxSize().backKeyHandler { backCount++ }) {
                Column(Modifier.width(400.dp)) {
                    SearchField(
                        query = query,
                        onQueryChange = { query = it },
                        onSubmit = {}
                    )
                }
            }
        }

        val field = onNode(hasSetTextAction())
        field.requestFocus()
        field.performTextInput("hi")
        field.performKeyInput { pressKey(Key.Backspace) }

        // The field consumed the press to delete a character — no Back.
        assertEquals("h", query)
        assertEquals(0, backCount)
    }
}
