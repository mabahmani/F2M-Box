package com.mab.f2mbox.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.mab.f2mbox.desktop.ui.theme.F2MBoxTheme

/**
 * F2M Box desktop entry point. One window hosts the whole app; the UI
 * is RTL end to end, exactly like the TV build.
 */
fun main() = application {
    val windowState = rememberWindowState(width = 1280.dp, height = 720.dp)

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "F2M Box"
    ) {
        MenuBar {
            Menu("File") {
                Item("Quit", onClick = ::exitApplication)
            }
        }

        F2MBoxTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ScaffoldPlaceholder()
                }
            }
        }
    }
}

/** Temporary placeholder until the connect gate lands (Task 4). */
@Composable
private fun ScaffoldPlaceholder() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "F2M Box — desktop scaffold",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
