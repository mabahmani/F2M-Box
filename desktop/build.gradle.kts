plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

import java.io.File

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
