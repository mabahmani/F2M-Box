package com.mab.f2mbox.desktop.platform

import com.mab.f2mbox.desktop.res.Res
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.runBlocking

/**
 * Makes the app's bundled Vazirmatn font usable by libVLC's subtitle
 * renderer.
 *
 * libvlc's freetype module does not take a font *file*: `--freetype-font`
 * wants a font *family* that the platform text engine can resolve (CoreText
 * on macOS, fontconfig on Linux/Windows). A family that is merely shipped
 * inside the jar is invisible to it, so we extract the bundled TTFs to disk
 * and register them with the OS font manager once at startup, in a scope
 * that does not linger beyond this process. Then
 * `--freetype-font=[FAMILY]` resolves.
 *
 * Verified on macOS: AWT's `registerFont` is NOT enough (CoreText's font
 * collection ignores it — the subtitle stayed byte-identical), whereas
 * `CTFontManagerRegisterFontsForURL` with process scope makes libvlc pick
 * up Vazirmatn (subtitle pixels changed ~5%). If registration fails for any
 * reason the caller passes no font arg and libvlc keeps its default font —
 * subtitles still render, just not in Vazirmatn.
 */
object VazirFonts {

    /** The family CoreText/fontconfig resolves for the bundled files. */
    const val FAMILY = "Vazirmatn RD"

    /** Every bundled weight, so bold/regular map to real faces. */
    private val WEIGHTS = listOf(
        "vazirmatn_rd_regular",
        "vazirmatn_rd_bold",
        "vazirmatn_rd_medium",
        "vazirmatn_rd_semibold",
        "vazirmatn_rd_extrabold",
        "vazirmatn_rd_black",
        "vazirmatn_rd_light",
        "vazirmatn_rd_extralight",
        "vazirmatn_rd_thin",
    )

    // Generated Compose resource path prefix (see packageOfResClass in
    // build.gradle.kts); used only as a classpath fallback if Res.readBytes
    // is unavailable.
    private const val CLASSPATH_PREFIX =
        "composeResources/com.mab.f2mbox.desktop.res/font/"

    @Volatile private var attempted = false

    @Volatile private var family: String? = null

    /**
     * The family to hand to libvlc as `--freetype-font`, or null when the
     * font could not be registered (libvlc then uses its default). Safe to
     * call repeatedly and from any thread; does the work once.
     */
    fun registeredFamily(): String? {
        if (!attempted) {
            synchronized(this) {
                if (!attempted) {
                    attempted = true
                    family = try {
                        register()
                    } catch (_: Throwable) {
                        null
                    }
                }
            }
        }
        return family
    }

    private fun register(): String? {
        val dir = defaultAppSupportDir().resolve("fonts")
        Files.createDirectories(dir)
        val paths = WEIGHTS.mapNotNull { extract(dir, it) }
        if (paths.isEmpty()) return null
        when {
            isMac -> registerCoreText(paths)
            isWindows -> registerGdi(paths)
            else -> registerFontconfig(paths)
        }
        // Report the family whenever we have the files: registration can
        // legitimately report "already registered" (e.g. a previous process
        // left it in an outer scope), in which case the family still
        // resolves. If it genuinely isn't available, libvlc logs a warning
        // and falls back to its default font — subtitles still render.
        return FAMILY
    }

    /** Writes [name].ttf under [dir] (skipping unchanged copies); returns its path. */
    private fun extract(dir: Path, name: String): Path? = try {
        val target = dir.resolve("$name.ttf")
        val bytes = readBundled("$name.ttf")
        if (bytes != null && (!Files.exists(target) || Files.size(target) != bytes.size.toLong())) {
            Files.write(target, bytes)
        }
        if (Files.exists(target)) target else null
    } catch (_: Throwable) {
        null
    }

    /** Reads a bundled font's bytes, preferring the Compose resource API. */
    private fun readBundled(fileName: String): ByteArray? {
        val viaRes = try {
            runBlocking { Res.readBytes("font/$fileName") }
        } catch (_: Throwable) {
            null
        }
        if (viaRes != null) return viaRes
        return try {
            VazirFonts::class.java.getResourceAsStream("/$CLASSPATH_PREFIX$fileName")
                ?.use { it.readBytes() }
        } catch (_: Throwable) {
            null
        }
    }

    private val isMac: Boolean
        get() = System.getProperty("os.name").lowercase().let {
            it.contains("mac") || it.contains("darwin")
        }

    private val isWindows: Boolean
        get() = System.getProperty("os.name").lowercase().contains("win")

    // ---- macOS: CoreText, process scope ----

    private interface CoreFoundation : Library {
        fun CFURLCreateFromFileSystemRepresentation(
            allocator: Pointer?,
            buffer: ByteArray,
            len: Long,
            isDirectory: Boolean
        ): Pointer?

        fun CFRelease(cf: Pointer?)
    }

    private interface CoreText : Library {
        fun CTFontManagerRegisterFontsForURL(fontURL: Pointer?, scope: Int, error: Pointer?): Byte
    }

    private fun registerCoreText(paths: List<Path>): Boolean = try {
        val cf = Native.load("CoreFoundation", CoreFoundation::class.java)
        val ct = Native.load("CoreText", CoreText::class.java)
        var any = false
        for (path in paths) {
            val bytes = path.toAbsolutePath().toString().toByteArray(Charsets.UTF_8)
            val url = cf.CFURLCreateFromFileSystemRepresentation(
                null, bytes, bytes.size.toLong(), false
            ) ?: continue
            try {
                // kCTFontManagerScopeProcess == 1: visible to this process
                // only and dropped automatically when it exits. (Do NOT use
                // 3 — that is kCTFontManagerScopeSession, which persists for
                // the whole login session.)
                if (ct.CTFontManagerRegisterFontsForURL(url, 1, null) != 0.toByte()) any = true
            } finally {
                cf.CFRelease(url)
            }
        }
        any
    } catch (_: Throwable) {
        false
    }

    // ---- Windows: GDI, private to this process ----

    private interface Gdi32 : Library {
        fun AddFontResourceExW(name: WString, fl: Int, reserved: Pointer?): Int
    }

    private fun registerGdi(paths: List<Path>): Boolean = try {
        val gdi = Native.load("gdi32", Gdi32::class.java)
        val frPrivate = 0x10 // FR_PRIVATE
        var any = false
        for (path in paths) {
            val added = gdi.AddFontResourceExW(
                WString(path.toAbsolutePath().toString()), frPrivate, null
            )
            if (added > 0) any = true
        }
        any
    } catch (_: Throwable) {
        false
    }

    // ---- Linux/BSD: install into the user font dir + refresh fontconfig ----

    private fun registerFontconfig(paths: List<Path>): Boolean = try {
        val home = System.getProperty("user.home")
        val xdg = System.getenv("XDG_DATA_HOME") ?: "$home/.local/share"
        val fontDir = Path.of(xdg, "fonts")
        Files.createDirectories(fontDir)
        for (path in paths) {
            Files.copy(
                path, fontDir.resolve(path.fileName),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
        // No process-scoped API on Linux; ask fontconfig to rescan. Missing
        // fc-cache just means the fonts may only be picked up next launch.
        try {
            ProcessBuilder("fc-cache", "-f").redirectErrorStream(true).start().waitFor()
        } catch (_: Throwable) {
            // Best effort.
        }
        true
    } catch (_: Throwable) {
        false
    }
}
