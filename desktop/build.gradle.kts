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
    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.cmp.components.resources)
    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "com.mab.f2mbox.desktop.MainKt"
    }
}

// The generated Res package would otherwise be `f2m box`.desktop….
// (root project name contains a space); pin a clean one.
compose {
    resources {
        packageOfResClass = "com.mab.f2mbox.desktop.res"
    }
}
