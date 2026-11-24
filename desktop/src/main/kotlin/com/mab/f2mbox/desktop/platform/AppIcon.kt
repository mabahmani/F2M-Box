package com.mab.f2mbox.desktop.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Taskbar
import javax.imageio.ImageIO

private const val RESOURCE = "f2mbox-logo.png"

private val isMacOs: Boolean
    get() = System.getProperty("os.name")?.contains("Mac", ignoreCase = true) == true

@Composable
fun rememberAppIconPainter(): Painter? = remember {
    // Compose: on macOS the Dock icon comes from the packaged .icns (iconFile in
    // Gradle). Setting Taskbar/AWT icons overrides it with a flat PNG — larger and
    // without the system rounded-square mask.
    if (isMacOs) null else loadAppIconPainter()
}

fun loadAppIconPainter(): Painter? = try {
    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(RESOURCE) ?: return null
    stream.use {
        val image = ImageIO.read(it) ?: return null
        installTaskbarIcon(image)
        BitmapPainter(image.toComposeImageBitmap())
    }
} catch (_: Throwable) {
    null
}

/** Windows / Linux taskbar; not used on macOS (see [rememberAppIconPainter]). */
private fun installTaskbarIcon(image: java.awt.Image) {
    if (isMacOs) return
    try {
        if (Taskbar.isTaskbarSupported()) {
            val taskbar = Taskbar.getTaskbar()
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                taskbar.iconImage = image
            }
        }
    } catch (_: Throwable) {
    }
}