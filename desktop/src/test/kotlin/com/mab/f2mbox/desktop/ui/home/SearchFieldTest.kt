package com.mab.f2mbox.desktop.ui.home

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import com.mab.f2mbox.desktop.ui.common.arrowKeyFocusNavigation
import com.mab.f2mbox.desktop.strings.desktopHomeStrings
import com.mab.f2mbox.desktop.ui.theme.F2MBoxTheme
import com.mab.f2mbox.ui.home.LocalHomeStrings
import com.mab.f2mbox.ui.home.SearchField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the desktop search field accepts plain typing — no TV-style
 * "press OK to start editing" gate — submits on Enter, and lets Up/Down
 * move the focus out of the field (Left/Right stay with the text cursor).
 */
@OptIn(ExperimentalTestApi::class)
class SearchFieldTest {

    // The field is reached with the arrow keys from the focusable above it:
    // the "above" box sits over the field's x-range, so Down finds the text
    // field (the submit button is far to the left, out of the way).
    @Composable
    private fun content(
        query: String,
        onQueryChange: (String) -> Unit,
        onSubmit: (String) -> Unit
    ) {
        CompositionLocalProvider(LocalHomeStrings provides desktopHomeStrings()) {
        F2MBoxTheme {
            Column(
                Modifier
                    .width(600.dp)
                    // Same bridge the app root applies in DesktopApp.
                    .arrowKeyFocusNavigation()
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(Modifier.size(50.dp).testTag("above").focusable())
                }
                SearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSubmit = onSubmit,
                    modifier = Modifier.testTag("search")
                )
                // Spans the width like a real section row under the field,
                // so Up from it finds the field geometrically.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("below")
                        .focusable()
                )
            }
        }
        }
    }

    @Test
    fun `typing works without an ok press and enter submits`() = runComposeUiTest {
        var query by mutableStateOf("")
        var submitted by mutableStateOf<String?>(null)
        setContent { content(query, { query = it }, { submitted = it }) }

        onNodeWithTag("above").requestFocus()
        onNodeWithTag("above").performKeyInput { pressKey(Key.DirectionDown) }

        // Type straight away: the field must not require a first OK press
        // (the old TV gate left it readOnly until Enter, so typing did
        // nothing) — pinned by the IsEditable semantics the readOnly gate
        // turns off.
        val field = onNode(hasSetTextAction())
        field.assertIsFocused()
        assertTrue(field.fetchSemanticsNode().config.getOrNull(SemanticsProperties.IsEditable) == true)
        field.performTextInput("hi")
        field.assertTextEquals("hi")

        field.performKeyInput { pressKey(Key.Enter) }
        assertEquals("hi", submitted)
    }

    @Test
    fun `up and down move the focus out of the field`() = runComposeUiTest {
        var query by mutableStateOf("")
        setContent { content(query, { query = it }, {}) }

        onNodeWithTag("above").requestFocus()
        onNodeWithTag("above").performKeyInput { pressKey(Key.DirectionDown) }
        onNode(hasSetTextAction()).assertIsFocused()

        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithTag("below").assertIsFocused()

        onNodeWithTag("below").performKeyInput { pressKey(Key.DirectionUp) }
        onNode(hasSetTextAction()).assertIsFocused()

        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionUp) }
        onNodeWithTag("above").assertIsFocused()
    }
}
