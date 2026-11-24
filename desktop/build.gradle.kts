import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.vlcj)
    implementation(compose.desktop.currentOs)
    implementation(libs.cmp.foundation)
    implementation(libs.cmp.material3)
    implementation(libs.cmp.material.icons.extended)
    implementation(libs.cmp.components.resources)
    testImplementation(libs.junit)
    testImplementation(libs.cmp.ui.test.junit4)
}

compose.desktop {
    application {
        mainClass = "com.mab.f2mbox.desktop.MainKt"

        // jpackage (bundled with full JDKs, absent from Android Studio's
        // JBR) is only needed for packaging, not for run/test. Probe the
        // usual Homebrew locations and fall back to the Gradle JVM.
        val packagingJdks = listOf(
            "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home",
            "/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home",
            "/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home",
        )
        packagingJdks
            .firstOrNull { File(it, "bin/jpackage").exists() }
            ?.let { javaHome = it }

        // Self-contained installers per OS. Unsigned: users on macOS must
        // clear the quarantine flag once (see README's desktop section).
        nativeDistributions {
            // `prepareVlc` stages libVLC into build/appResources/vlc; this
            // makes it part of the app image, reachable at runtime through
            // the `compose.application.resources.dir` system property.
            appResourcesRootDir.set(project.layout.buildDirectory.dir("appResources"))
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            packageName = "F2MBox"
            packageVersion = "1.0.0"
            description = "F2M Box — your world of entertainment, on the desktop"
            vendor = "F2M Box"
            // Pulls every runtime module the fat jar needs (JNA, coroutines,
            // coil, vlcj's reflections) without hand-curating jlink modules.
            includeAllModules = true

            macOS {
                iconFile.set(project.file("src/main/resources/f2mbox-logo.png"))
                bundleID = "com.mab.f2mbox.desktop"
            }
        }
    }
}

// The generated Res package would otherwise be `f2m box`.desktop….
// (root project name contains a space); pin a clean one.
compose {
    resources {
        packageOfResClass = "com.mab.f2mbox.desktop.res"
    }
}

// ---------------------------------------------------------------------------
// Bundled libVLC
//
// Downloads the official VideoLAN build for the host OS/arch, verifies its
// SHA-256, and stages only the runtime pieces (libs + plugins) into
// build/appResources/vlc so the packaged app needs no system VLC. The
// download is cached under build/vlc-cache; nothing binary is committed.
//
// Layout matters: vlcj discovers the libs through `jna.library.path` and then
// derives VLC_PLUGIN_PATH from the discovery format per OS:
//   macOS : <libdir>/../plugins   -> vlc/lib/{libvlc,libvlccore}.dylib + vlc/plugins
//   Linux : <libdir>/vlc/plugins  (Linux is not bundled; system VLC is used)
//   Windows: <libdir>/plugins     -> vlc/{libvlc,libvlccore}.dll + vlc/plugins
// ---------------------------------------------------------------------------

val vlcVersion = "3.0.23"

class VlcArchive(val fileName: String, val url: String, val sha256: String)

fun hostVlcArchive(): VlcArchive? {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val base = "https://download.videolan.org/pub/videolan/vlc/$vlcVersion"
    return when {
        os.contains("mac") ->
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                VlcArchive(
                    "vlc-$vlcVersion-arm64.dmg",
                    "$base/macosx/vlc-$vlcVersion-arm64.dmg",
                    "fc6fac08d87f538517d44aca0c5e7a244b67c8c4cb589bf478363a7315fd5e0d",
                )
            } else {
                VlcArchive(
                    "vlc-$vlcVersion-intel64.dmg",
                    "$base/macosx/vlc-$vlcVersion-intel64.dmg",
                    "ec01530ce69d849dd057fba8876e68ac39bf279dc28de4e9c04e4aec11fc98db",
                )
            }
        os.contains("win") ->
            VlcArchive(
                "vlc-$vlcVersion-win64.zip",
                "$base/win64/vlc-$vlcVersion-win64.zip",
                "992d19dbd0b8a7cde9167d2f7780b1ef6f92acc8a71acfa736101a21f35181e1",
            )
        // Linux: no official relocatable build; fall back to the system libVLC.
        else -> null
    }
}

fun sha256Of(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** Runs a native command, streaming its output, and fails the build on non-zero. */
fun runCommand(command: List<String>) {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    process.inputStream.copyTo(System.out)
    val code = process.waitFor()
    check(code == 0) { "Command failed ($code): ${command.joinToString(" ")}" }
}

/** Extracts lib/ + plugins/ from a macOS VLC dmg via ditto (preserves mtimes/symlinks). */
fun stageFromDmg(dmg: File, stage: File) {
    val mount = File(stage.parentFile, "vlc-dmg-mnt").apply { deleteRecursively(); mkdirs() }
    try {
        runCommand(listOf("hdiutil", "attach", "-nobrowse", "-readonly", "-mountpoint", mount.absolutePath, dmg.absolutePath))
        val appMacOs = File(mount, "VLC.app/Contents/MacOS")
        check(File(appMacOs, "lib").isDirectory) { "VLC.app layout unexpected in ${dmg.name}" }
        // ditto keeps symlinks (libvlc.dylib -> libvlc.5.dylib) and mtimes.
        runCommand(listOf("ditto", File(appMacOs, "lib").absolutePath, File(stage, "lib").absolutePath))
        runCommand(listOf("ditto", File(appMacOs, "plugins").absolutePath, File(stage, "plugins").absolutePath))
    } finally {
        runCatching { runCommand(listOf("hdiutil", "detach", mount.absolutePath, "-quiet")) }
        mount.deleteRecursively()
    }
}

/** Extracts the two VLC DLLs + plugins/ from the Windows zip, preserving mtimes. */
fun stageFromZip(zip: File, stage: File) {
    ZipFile(zip).use { zf ->
        val entries = zf.entries().asSequence().toList()
        val libEntry = entries.firstOrNull {
            !it.isDirectory && it.name.substringAfterLast('/').equals("libvlc.dll", ignoreCase = true)
        } ?: error("libvlc.dll not found inside ${zip.name}")
        // Everything may be nested under a single top-level folder.
        val prefix = libEntry.name.substringBeforeLast('/', "")
        entries.forEach { entry ->
            if (entry.isDirectory) return@forEach
            val rel = entry.name.removePrefix(if (prefix.isEmpty()) "" else "$prefix/")
            val dest = when {
                rel.equals("libvlc.dll", ignoreCase = true) -> File(stage, "libvlc.dll")
                rel.equals("libvlccore.dll", ignoreCase = true) -> File(stage, "libvlccore.dll")
                rel.startsWith("plugins/") -> File(stage, rel)
                else -> null
            } ?: return@forEach
            dest.parentFile.mkdirs()
            zf.getInputStream(entry).use { input -> dest.outputStream().use { input.copyTo(it) } }
            dest.setLastModified(entry.time)
        }
    }
}

fun copyVlcLicenses(stage: File) {
    val licenses = project.file("vlc-licenses")
    if (licenses.isDirectory) {
        licenses.listFiles()?.forEach { it.copyTo(File(stage, it.name), overwrite = true) }
    }
}

/**
 * Drops libVLC's `plugins/plugins.dat`. The index goes stale whenever Gradle
 * re-copies or re-signs `.dylib`/`.dll` plugins but leaves the old cache
 * behind (common under `compose/tmp/prepareAppResources/vlc`). VLC rebuilds
 * the cache on the next launch when the file is absent.
 */
fun deleteVlcPluginsCache(vlcDir: File) {
    if (!vlcDir.isDirectory) return
    val cache = File(vlcDir, "plugins/plugins.dat")
    if (cache.isFile) {
        check(cache.delete()) { "Could not delete ${cache.absolutePath}" }
    }
}

fun stageFfmpeg(stageDir: File) {
    val candidates = listOf(
        File("/opt/homebrew/bin/ffmpeg"),
        File("/usr/local/bin/ffmpeg"),
        File("/usr/bin/ffmpeg"),
    )
    val sourceFfmpeg = candidates.firstOrNull { it.exists() && it.canExecute() }
    val ffmpegDir = File(stageDir.parentFile, "ffmpeg").apply { mkdirs() }
    val targetName = if (System.getProperty("os.name").lowercase().contains("win")) "ffmpeg.exe" else "ffmpeg"
    val targetFfmpeg = File(ffmpegDir, targetName)

    if (sourceFfmpeg != null) {
        sourceFfmpeg.copyTo(targetFfmpeg, overwrite = true)
        targetFfmpeg.setExecutable(true)
        project.logger.lifecycle("Embedded host ffmpeg from ${sourceFfmpeg.absolutePath} to ${targetFfmpeg.absolutePath}")
    } else if (!targetFfmpeg.exists()) {
        try {
            val os = System.getProperty("os.name").lowercase()
            val urlStr = when {
                os.contains("mac") -> "https://evermeet.cx/ffmpeg/getrelease/zip"
                os.contains("win") -> "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip"
                else -> null
            }
            if (urlStr != null) {
                project.logger.lifecycle("Host ffmpeg not found. Downloading static ffmpeg from $urlStr ...")
                val cacheDir = File(stageDir.parentFile, "ffmpeg-cache").apply { mkdirs() }
                val zipFile = File(cacheDir, "ffmpeg.zip")
                URI(urlStr).toURL().openStream().use { input ->
                    zipFile.outputStream().use { input.copyTo(it) }
                }
                ZipFile(zipFile).use { zf ->
                    val entries = zf.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val name = entry.name
                        if (!entry.isDirectory && (name.endsWith("ffmpeg") || name.endsWith("ffmpeg.exe"))) {
                            zf.getInputStream(entry).use { input ->
                                targetFfmpeg.outputStream().use { output -> input.copyTo(output) }
                            }
                            targetFfmpeg.setExecutable(true)
                            project.logger.lifecycle("Downloaded and staged static ffmpeg at ${targetFfmpeg.absolutePath}")
                            break
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            project.logger.lifecycle("Warning: Failed to download static ffmpeg: ${e.message}")
        }
    }
}

val vlcStagingDir = layout.buildDirectory.dir(
    "appResources/" + when {
        System.getProperty("os.name").lowercase().contains("mac") -> "macos"
        System.getProperty("os.name").lowercase().contains("win") -> "windows"
        else -> "linux"
    } + "/vlc",
)
val vlcCacheDir = layout.buildDirectory.dir("vlc-cache")

val prepareVlc = tasks.register("prepareVlc") {
    group = "distribution"
    description = "Stages libVLC binaries and plugins for the host OS/arch into the app resources."
    val archive = hostVlcArchive()
    outputs.dir(vlcStagingDir)
    onlyIf { archive != null }
    doLast {
        val arc = archive ?: return@doLast
        val cache = vlcCacheDir.get().asFile.apply { mkdirs() }
        val downloaded = File(cache, arc.fileName)
        if (!downloaded.exists() || sha256Of(downloaded) != arc.sha256) {
            downloaded.delete()
            logger.lifecycle("Downloading libVLC ${vlcVersion}: ${arc.url}")
            URI(arc.url).toURL().openStream().use { input ->
                downloaded.outputStream().use { input.copyTo(it) }
            }
            val actual = sha256Of(downloaded)
            check(actual == arc.sha256) {
                "SHA-256 mismatch for ${arc.fileName}: expected ${arc.sha256}, got $actual"
            }
        }
        val stage = vlcStagingDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        if (arc.fileName.endsWith(".dmg")) stageFromDmg(downloaded, stage) else stageFromZip(downloaded, stage)
        copyVlcLicenses(stage)
        stageFfmpeg(stage)
        deleteVlcPluginsCache(stage)
        logger.lifecycle("libVLC staged at ${stage.absolutePath}")
    }
}

val purgeVlcPluginsCache = tasks.register("purgeVlcPluginsCache") {
    group = "distribution"
    description = "Removes bundled libVLC plugins.dat under build/ so runtime rescans plugins."
    doLast {
        val buildDir = layout.buildDirectory.get().asFile
        val vlcDirs = linkedSetOf(vlcStagingDir.get().asFile)
        val tmpVlc = File(buildDir, "compose/tmp/prepareAppResources/vlc")
        if (tmpVlc.isDirectory) vlcDirs += tmpVlc
        val binariesRoot = File(buildDir, "compose/binaries")
        if (binariesRoot.isDirectory) {
            binariesRoot.walkTopDown().maxDepth(12).filter { dir ->
                dir.isDirectory && dir.name == "vlc" && File(dir, "plugins").isDirectory
            }.forEach { vlcDirs += it }
        }
        vlcDirs.forEach { vlc ->
            val cache = File(vlc, "plugins/plugins.dat")
            if (cache.isFile && cache.delete()) {
                logger.lifecycle("Removed libVLC plugins cache: ${cache.absolutePath}")
            }
        }
    }
}

// Bundle the staged natives into every packaged image (and its installer).
listOf(
    "prepareAppResources",
    "createDistributable",
    "packageDmg",
    "packageMsi",
    "packageExe",
    "packageDeb",
    "packageDistributionForCurrentOS",
    "run",
    "runDistributable",
).forEach { taskName ->
    tasks.matching { it.name == taskName }.configureEach {
        dependsOn(prepareVlc)
        finalizedBy(purgeVlcPluginsCache)
    }
}
