package com.mab.f2mbox.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import com.mab.f2mbox.desktop.platform.desktopStores
import com.mab.f2mbox.desktop.ui.common.LocalWatched
import com.mab.f2mbox.desktop.ui.theme.F2MBoxTheme
import com.mab.f2mbox.scraper.BaseUrlResolver
import com.mab.f2mbox.scraper.F2MScraper
import com.mab.f2mbox.scraper.HtmlCache
import com.mab.f2mbox.scraper.ScraperLog

/**
 * F2M Box desktop entry point. One window hosts the whole app; the UI
 * is RTL end to end, exactly like the TV build.
 */
fun main() {
    // Compose elements must draw ABOVE Swing interop content (the video
    // surface's SwingPanel) — without this the video sits on top of the
    // entire Compose layer and the player controls are invisible under it.
    // macOS caveat (per CMP docs): the interop view may still catch mouse
    // events even when visually below Compose content; the player screen
    // handles that by keeping key/wheel interaction paths authoritative.
    System.setProperty("compose.interop.blending", "true")

    // Console mirror of the scraper log: the desktop equivalent of the
    // Android build's logcat mirroring in ScraperLog.log.
    ScraperLog.mirror = { message -> println("[F2MScraper] $message") }

    // The html cache lives for one app open — drop whatever a previous
    // open left behind (the process can survive between launches), same
    // as the Android Activity.onCreate does.
    HtmlCache.clear()
    val (keyValueStore, watchedStorage) = desktopStores()
    // KeyValueStorage adapts the string-map store to the resolver's
    // Storage contract, exactly like MainActivity does on Android.
    val resolver = BaseUrlResolver(BaseUrlResolver.KeyValueStorage(keyValueStore))
    val scraper = F2MScraper(resolver = resolver)

    application {
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
                // The whole app is RTL by default: the content is Persian and
                // every layout mirrors (rows flow right-to-left, start = right).
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    CompositionLocalProvider(LocalWatched provides watchedStorage) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            DesktopApp(scraper = scraper)
                        }
                    }
                }
            }
        }
    }
}
