
package com.mab.f2mbox.desktop.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.mab.f2mbox.desktop.platform.Strings

private val SearchButtonWidth = 160.dp

// Rectangle with a small radius, matching the outlined text field's
// corners (tv buttons default to a full pill).
private val SearchControlShape = RoundedCornerShape(4.dp)

/**
 * Always-visible search input for the home navigation block. tv-material
 * has no text field yet, so this uses compose material3's outlined field
 * restyled with the TV theme's colors.
 *
 * TV behavior: the field is focus-activated — the first OK press on the
 * focused field starts editing and opens the IME, so merely transiting
 * focus never pops the keyboard. Submitting via the IME's search action or
 * the button hands the trimmed query to [onSubmit].
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    // Locale of the context (system language), not the forced RTL layout:
    // the typed text follows it — right-to-left in a Persian locale, with
    // the actual content still winning per first-strong bidi rule, so
    // English titles lay out left-to-right inside the same field.
    val localeIsRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val submit = {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) onSubmit(trimmed)
    }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            // Buttons stretch to the text field's height.
            modifier = Modifier.height(IntrinsicSize.Min)
        ) {
            Button(
                onClick = { submit() },
                modifier = Modifier
                    .width(SearchButtonWidth)
                    .fillMaxHeight(),
                shape = SearchControlShape
            ) {
                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = Strings.search,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(
                    textDirection = if (localeIsRtl) {
                        TextDirection.ContentOrRtl
                    } else {
                        TextDirection.ContentOrLtr
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { event ->
                        // TV: the first OK press on the focused field starts
                        // editing and opens the IME instead of doing nothing.
                        if (!isEditing && event.type == KeyEventType.KeyUp &&
                            (
                                event.key == Key.Enter ||
                                    event.key == Key.NumPadEnter ||
                                    event.key == Key.DirectionCenter
                                )
                        ) {
                            isEditing = true
                            keyboard?.show()
                            true
                        } else {
                            false
                        }
                    },
                singleLine = true,
                readOnly = !isEditing,
                placeholder = {
                    Text(
                        text = Strings.searchHint,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Left,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }

        Text(
            text = Strings.searchHintDetail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Left,
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
        )
    }
}
