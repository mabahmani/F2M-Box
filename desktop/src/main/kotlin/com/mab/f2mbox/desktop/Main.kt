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
import com.mab.f2mbox.desktop.platform.LocalWindowFullscreen
import com.mab.f2mbox.desktop.platform.rememberAppIconPainter
import com.mab.f2mbox.desktop.platform.desktopStores
import com.mab.f2mbox.desktop.platform.rememberWindowFullscreen
import com.mab.f2mbox.desktop.platform.subtitleStyleStore
import com.mab.f2mbox.desktop.platform.VazirFonts
import com.mab.f2mbox.desktop.ui.common.LocalSubtitleStyleStore
import androidx.compose.ui.window.WindowPlacement
import com.mab.f2mbox.desktop.ui.common.LocalWatched
import com.mab.f2mbox.desktop.strings.desktopHomeStrings
import com.mab.f2mbox.storage.WatchedHistory
import com.mab.f2mbox.ui.common.LocalShowDesktopScrollbars
import com.mab.f2mbox.ui.common.LocalWatchedItems
import com.mab.f2mbox.ui.common.WatchedItemsReader
import com.mab.f2mbox.ui.home.LocalHomeStrings
import com.mab.f2mbox.ui.common.LocalWatchedWriter
import com.mab.f2mbox.ui.common.WatchedWriter
import com.mab.f2mbox.navigation.LocalPlayerRouteContent
import com.mab.f2mbox.ui.detail.LocalDetailStrings
import com.mab.f2mbox.desktop.ui.navigation.desktopPlayerRouteContent
import com.mab.f2mbox.desktop.strings.desktopDetailStrings
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
    // On macOS the interop view still wins AWT mouse dispatch when it carries
    // its own listeners, so the video surface must stay listener-free
    // (VlcPlayerEngine uses InputEvents.NONE and the player installs no AWT
    // mouse listeners). That lets Compose's interop event forwarding deliver
    // clicks over the video to the Compose overlay controls.
    System.setProperty("compose.interop.blending", "true")

    // Console mirror of the scraper log: the desktop equivalent of the
    // Android build's logcat mirroring in ScraperLog.log.
    ScraperLog.mirror = { message -> println("[F2MScraper] $message") }

    // The html cache lives for one app open — drop whatever a previous
    // open left behind (the process can survive between launches), same
    // as the Android Activity.onCreate does.
    HtmlCache.clear()

    // Register the bundled Vazirmatn family with the OS font manager before
    // any libvlc engine is built, so subtitle styling can ask for it by name
    // (libvlc's freetype renderer resolves families via the platform text
    // engine, not from a font file). Best effort: if it fails, playback uses
    // libvlc's default subtitle font.
    VazirFonts.registeredFamily()
    val (keyValueStore, watchedStorage) = desktopStores()
    val subtitleStyles = subtitleStyleStore()
    // KeyValueStorage adapts the string-map store to the resolver's
    // Storage contract, exactly like MainActivity does on Android.
    val resolver = BaseUrlResolver(BaseUrlResolver.KeyValueStorage(keyValueStore))
    val scraper = F2MScraper(resolver = resolver)

    application {
        val windowState = rememberWindowState(width = 1280.dp, height = 720.dp)
        val windowFullscreen = rememberWindowFullscreen(windowState)
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "F2M Box",
            icon = rememberAppIconPainter(),
        ) {
            if (windowState.placement != WindowPlacement.Fullscreen) {
                MenuBar {
                    Menu("File") {
                        Item("Quit", onClick = ::exitApplication)
                    }
                }
            }

            F2MBoxTheme {
                // The whole app is RTL by default: the content is Persian and
                // every layout mirrors (rows flow right-to-left, start = right).
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    CompositionLocalProvider(LocalWatched provides watchedStorage) {
                        CompositionLocalProvider(
                            LocalShowDesktopScrollbars provides true,
                            LocalHomeStrings provides desktopHomeStrings(),
                            LocalWatchedItems provides WatchedItemsReader {
                                WatchedHistory.getWatchedItems(watchedStorage)
                            },
                            LocalDetailStrings provides desktopDetailStrings(),
                            LocalWatchedWriter provides WatchedWriter { item ->
                                WatchedHistory.save(watchedStorage, item)
                            },
                            LocalPlayerRouteContent provides desktopPlayerRouteContent(),
                        ) {
                        CompositionLocalProvider(LocalSubtitleStyleStore provides subtitleStyles) {
                            CompositionLocalProvider(LocalWindowFullscreen provides windowFullscreen) {
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
        }
    }
}
