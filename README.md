# F2M Box 📺

[![Platform](https://img.shields.io/badge/Platform-Android%20TV%20%7C%20Mobile-green.svg)](https://developer.android.com/tv)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![UI Toolkit](https://img.shields.io/badge/UI-Jetpack%20Compose-brightgreen.svg)](https://developer.android.com/jetpack/compose)
[![Version](https://img.shields.io/badge/Release-v1.3-orange.svg)](https://github.com/mabahmani/F2MBox/releases)

**F2M Box** is a modern Android TV and mobile application built with Jetpack Compose for streaming movies, series, trailers, and cast information. Designed with focus-friendly D-Pad navigation for Smart TVs and Leanback devices, as well as touch support for mobile screens.

---

## ✨ Features

- 📺 **Android TV & Leanback Optimized**: Full D-Pad focus navigation tailored for TV remotes.
- 🎬 **Advanced Media Player**: Powered by **Jetpack Media3 (ExoPlayer)** with subtitle customization (size, color, weight, background, offset), playback speed adjustments, and audio track selection.
- 🔍 **Search & Catalog**: Fast search, categorised recommendations, hero sliders, and detail pages with cast/crew information and trailers.
- 🌐 **RTL & Persian Support**: Native Right-To-Left UI layout with Vazirmatn typography and bidirectional text handling.
- 🔄 **Smart Base URL Resolver**: Dynamic domain resolution and fallback handling with HTML cache and live scraper log feeds.
- 🕒 **Watched History & State Persistence**: Local storage of watch history and playback progression.

---

## 🛠 Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose & Compose Material 3 for TV (`androidx.tv.material`)
- **Media Engine**: Jetpack Media3 ExoPlayer (`androidx.media3`)
- **Networking & Scraping**: OkHttp & Jsoup
- **Image Loading**: Coil Compose
- **Serialization**: Kotlinx Serialization JSON
- **Build System**: Gradle (Kotlin DSL) with Version Catalogs

---

## 🚀 Building & Running

### Prerequisites
- Android Studio Ladybug or newer
- JDK 17+
- Android SDK (API 37 compile target, min SDK 26)

### Build APK
Run the following Gradle command to assemble the release APK:

```bash
./gradlew assembleRelease
```

The output APK will be generated at:
```text
app/build/outputs/apk/release/F2MBox-TV-release.apk
```

---

## 📦 Release & Tagging Guide

### 1. Update Version in Gradle
When preparing a new release, increment `versionCode` and update `versionName` in [`app/build.gradle.kts`](app/build.gradle.kts):

```kotlin
defaultConfig {
    versionCode = 4       // Increment for every release (e.g. 1, 2, 3, 4...)
    versionName = "1.3"   // User-visible version string
}
```

### 2. Commit and Tag the Release
Commit your changes and tag the commit with the version number:

```bash
git add .
git commit -m "release: v1.2"
git tag -a v1.2 -m "Release version 1.2"
```

### 3. Push to GitHub
Push your commits and tags to GitHub:

```bash
# Push branch changes
git push origin main

# Push the release tag
git push origin v1.2
```

### 4. Create a GitHub Release
Using GitHub CLI (`gh`):
```bash
gh release create v1.2 ./app/build/outputs/apk/release/F2MBox-TV-release.apk --title "F2M Box v1.2" --notes "Release notes for v1.2"
```

Or manually on GitHub:
1. Navigate to your repository on GitHub.
2. Click **Releases** > **Draft a new release**.
3. Choose the tag `v1.2`.
4. Add release notes and attach `F2MBox-TV-release.apk`.
5. Click **Publish release**.

---

## 📄 License
This project is for educational and personal research purposes.
