# F2M Box 📺

[![Platform](https://img.shields.io/badge/Platform-Android%20TV%20%7C%20Tablet%20%7C%20Desktop-green.svg)](https://developer.android.com/tv)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![UI Toolkit](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2F%20CMP-brightgreen.svg)](https://developer.android.com/jetpack/compose)
[![Version](https://img.shields.io/badge/Release-v1.4-orange.svg)](https://github.com/mabahmani/F2MBox/releases)

**F2M Box** streams movies, series, trailers, and cast information through a **Kotlin Multiplatform** client: one shared scraper and UI core (`:shared`) powers the **Android TV / tablet** app (`:app`) and the **desktop** app (`:desktop`). The interface is **RTL** with **Vazirmatn** typography. **TV** builds use D-pad focus and Leanback-friendly controls; **touchscreen tablets** get tap, scroll, and swipe; **desktop** adds mouse, keyboard, and native scrollbars while keeping the same catalog flows.

---

## ✨ Features

- 📺 **Android TV & Leanback**: D-pad focus navigation, TV keyboard gate for search fields, and remote-friendly player controls.
- 📱 **Tablet touch**: Touch is detected separately from TV (including touchscreen TVs in TV UI mode). Hero slider supports finger swipe; home uses a collapsible top menu; category chips preview genres on tap/hover.
- 🖥️ **Desktop client**: Shared home, search, listings, detail, person, logs, and in-app playback with **libVLC**; **vertical and horizontal scrollbars** on lazy lists; ESC / Backspace / Delete for back; window fullscreen in the player.
- 🎬 **Advanced media player**: **ExoPlayer (Media3)** on Android; **libVLC (vlcj)** on desktop—with subtitle styling, playback speed, audio/subtitle tracks (desktop subtitles via software decode; see below).
- 🔍 **Search & catalog**: Hero slider, section carousels, navigation genres, and detail pages with cast, downloads, and comments.
- 🌐 **RTL & Persian support**: End-to-end RTL layout; LTR enclaves where needed (URLs, logs, hero slide controls).
- 🔄 **Smart base URL resolver**: Dynamic domain resolution, HTML cache, and live scraper log feeds on connect/loading screens.
- 🕒 **Watched history**: Local continue-watching and playback progression (platform-specific storage).

---

## 🧱 Project layout

| Module | Role |
| --- | --- |
| `:shared` | KMP `commonMain` + `androidMain` / `jvmMain`: scraping, navigation shell, home, detail, list, person, logs, connect splash, shared components. |
| `:app` | Android application (TV + tablet): Material 3 for TV, ExoPlayer player, Android strings/resources. |
| `:desktop` | Compose Desktop entry, libVLC player UI, desktop strings, packaging (DMG / MSI / DEB). |

---

## 🛠 Tech stack

- **Language**: Kotlin (Multiplatform)
- **UI**: Jetpack Compose on Android; Compose Multiplatform on desktop; Material 3 (nested TV M3 on Android TV; CMP Material3 on desktop)
- **Android media**: Jetpack Media3 ExoPlayer (`androidx.media3`)
- **Desktop media**: libVLC via vlcj (bundled on macOS/Windows packages)
- **Networking & scraping**: OkHttp & Jsoup (shared)
- **Images**: Coil 3
- **Serialization**: Kotlinx Serialization JSON
- **Build**: Gradle Kotlin DSL, version catalogs

---

## 🚀 Building & running (Android)

### Prerequisites

- Android Studio Ladybug or newer
- JDK 17+
- Android SDK (compile SDK 37, min SDK 26)

### Commands

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:assembleRelease        # release APK
./gradlew :shared:compileAndroidMain  # compile shared Android sources only
```

Release APK (default TV artifact name):

```text
app/build/outputs/apk/release/F2MBox-TV-release.apk
```

---

## 🖥️ Desktop (macOS / Windows / Linux)

Same catalog and most screens as Android; playback and window chrome are desktop-specific. UI stays **RTL**; **vertical scrollbars** sit on the physical **right**; **horizontal scrollbars** sit under carousels (with extra spacing); genre rows use a taller slot so the bar is not clipped.

### Build & run (dev)

| Task | Command |
| --- | --- |
| Run from source | `./gradlew :desktop:run` |
| Tests | `./gradlew :desktop:test` |
| Package for the current OS (DMG/MSI/EXE/DEB) | `./gradlew :desktop:packageReleaseDistributionForCurrentOS` |

**Prerequisites for dev-run:**

- JDK 17+ (Android Studio's bundled JBR works for `run`; **packaging needs a full JDK with `jpackage`**, e.g. `brew install openjdk@21` — auto-detected).
- Packaging downloads the official libVLC build for the host OS/arch (~50–80 MB; cached under `desktop/build/vlc-cache`) and needs network access.
- Packaged installers **bundle libVLC**; `:desktop:run` can use system VLC—run `./gradlew :desktop:prepareVlc` to exercise the bundled copy. With no libVLC, **Play in system player/browser** still opens the URL in the OS default handler.

### Bundled libVLC

`prepareVlc` downloads VideoLAN **VLC 3.0.23** for the host OS/arch (macOS arm64 / intel64, Windows x64), verifies SHA-256, and stages `lib` + `plugins` plus LGPL license text into the app image. Runtime sets `jna.library.path` to that copy. First launch after packaging may rebuild the plugin cache (~5–15 s). Linux packages use **system libVLC** (no relocatable bundle). LGPL-2.1-or-later — see `resources/vlc/` (`NOTICE.txt`, `COPYING.LGPL-2.1`).

### Subtitles (desktop)

Embedded text subtitles use **software decoding** (`--codec=avcodec --avcodec-hw=none`) so libVLC can blend subtitles on the memory video surface under Compose controls. GPU decode paths drop blended subtitles (chroma mismatch). Image-based PGS/VobSub are not rendered on this path.

### Installing the DMG (macOS, unsigned)

```bash
xattr -cr /Applications/F2MBox.app
```

Or **System Settings → Privacy & Security → Open Anyway**. On Windows: SmartScreen → *More info → Run anyway*.

### Desktop vs Android

| Area | Android (`:app`) | Desktop (`:desktop`) |
| --- | --- | --- |
| Player | ExoPlayer, touch + D-pad | libVLC, mouse + keyboard |
| Input profile | TV vs tablet via `LocalUsesTouchInput` | Mouse, wheel, scrollbars |
| Crash / session logs | `SessionCrashStore` on Android | Desktop session store + shared **Logs** screen |
| App data | Android storage | `~/Library/Application Support/F2MBox` (macOS), `%APPDATA%\F2MBox` (Windows), `~/.local/share/F2MBox` (Linux) |

---

## 📦 Release & tagging guide

### 1. Update version in Gradle

Bump **`f2mVersionName`** and **`f2mVersionCode`** in [`gradle.properties`](gradle.properties). Android uses both fields; desktop installers use **`MAJOR.MINOR.BUILD`** with **BUILD = `f2mVersionCode`** (e.g. `1.4` + code `5` → package version **`1.4.5`**).

### 2. Commit and tag

```bash
git add .
git commit -m "release: v1.4"
git tag -a v1.4 -m "Release version 1.4"
```

### 3. Push

```bash
git push origin main
git push origin v1.4
```

### 4. GitHub release

```bash
gh release create v1.4 ./app/build/outputs/apk/release/F2MBox-TV-release.apk \
  --title "F2M Box v1.4" --notes "Release notes"
```

Attach desktop packages from `desktop/build/compose/binaries/main-release/` when publishing a desktop build.

---

## 📄 License

This project is for educational and personal research purposes.