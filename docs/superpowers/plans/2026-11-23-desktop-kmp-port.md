# Desktop (macOS/Windows/Linux) Compose Multiplatform Port — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port F2M Box to a desktop app (macOS Apple Silicon primary; Windows/Linux same codebase) on a new branch, by extracting the pure-Kotlin scraper/data layer into a shared module and building a Compose Multiplatform desktop app alongside the untouched Android app.

**Architecture:** Three Gradle modules. `shared` holds scraper + data models + storage interfaces (pure Kotlin, zero Android imports). `app` is the existing Android app (UI unchanged, storage impls stay there). `desktop` is a new Compose Multiplatform desktop app with UI adapted from the Android files (TV Material → Material 3), a vlcj/libVLC player replacing Media3, and file-based storage under the OS app-data dir. Desktop ships unsigned via GitHub releases.

**Tech Stack:** Kotlin 2.2.10, Compose Multiplatform (latest stable 1.12.x, see Task 2 version spike), OkHttp, Jsoup, kotlinx-serialization, Coil 3 (multiplatform), vlcj 4.8.x + libVLC, Gradle 9.6.0, AGP 9.5.1 (android only).

**Spec:** Conversation design decisions (2026-11-23): vlcj player, full parity scope, unsigned GitHub distribution, macOS+Windows+Linux installers; TV Material API surface is only 11 symbols (verified); `Glow` and custom subtitle *styling* are the known parity cuts.

## Global Constraints

- Branch: all work on `desktop-kmp`, created from current HEAD. Frequent commits.
- Android app must keep building and behaving identically (`./gradlew :app:assembleDebug` after every task). Existing behavior is the regression oracle.
- `shared` module must stay 100% Android-import-free (`grep -r "import android" shared/` = empty).
- Desktop strings come from a `Strings` object mirroring `app/src/main/res/values/strings.xml` values verbatim (Persian text, `&#x200E;` LRM marks preserved).
- Commits follow repo style (`feat: …`, `fix: …` prefixed subjects) and append the AI metadata block:
  `AI-Assisted: true` + `AI-Model: zai/glm-5.3-flash` (blank line before block).
- Desktop is unsigned: no signing config; packaging produces plain installers/zip.
- Version pinning ladder for the CMP version spike (Task 2): try 1.12.2 → on Kotlin/compiler-plugin friction try 1.10.3 → 1.9.3; only bump Kotlin if CMP refuses 2.2.x, and re-verify AGP 9.5.1 immediately after a Kotlin bump.
- Do not add Settings persistence, fullscreen-toggle, or crash reporter on desktop in this branch (documented follow-ups; Logs screen shows scraper log only).

## Review Focus

- **Persian text integrity:** every copied string must keep exact characters and the trailing `\u200E` (LRM); a dropped LRM silently flips punctuation to the wrong side in RTL. Test: Task 3 step asserting `Strings.searchHint` ends with `\u200E`.
- **VLC natives absent:** first run on a machine without VLC must show a friendly setup message, not a crash. Test: `VlcPlayerEngine` factory throws `VlcMissingException`; player screen renders its error card with `brew install --cask vlc` hint.
- **Watch history parity:** Android dedupe matches on url OR title with cap 24, newest first. Test: `WatchedHistoryTest` in shared pins dedupe/cap/order.
- **Back-stack state loss:** popping a route must drop its saved state after the new route composes (existing `poppedStateKey` behavior); naive desktop ports often keep stale focus. Covered by Task 5 review step (manual: navigate home→list→detail→back×3, focus returns correctly).
- **Server-quality home page:** ConnectGate treats missing hero slider as unhealthy — desktop port must keep this exact health check, otherwise it opens on a parked/error page. Test: unit test `ConnectGate` uses `scraper.home()` result with empty `heroSlider` → stays `Connecting/Failed` (logic extracted into a testable `ConnectFlow`).

---

### Task 1: Branch + `shared` module extraction

**Files:**
- Create: `shared/build.gradle.kts`, `shared/src/commonMain/kotlin/…` (moved packages)
- Move (git mv): `app/src/main/java/com/mab/f2mbox/scraper/**` and `data/**` (except `data/CrashReporter.kt`, `data/WatchedManager.kt` rewrite) → `shared/src/commonMain/kotlin/com/mab/f2mbox/{scraper,data}`
- Modify: `settings.gradle.kts` (include `:shared`), root `build.gradle.kts` if plugin aliasing needed, `app/build.gradle.kts` (add `implementation(project(":shared"))`, remove now-shared deps if harmless to keep)
- Create: `shared/src/commonMain/kotlin/com/mab/f2mbox/storage/WatchedStorage.kt`, `.../WatchedHistory.kt`
- Rewrite in app: `data/WatchedManager.kt` → thin Android `WatchedStorage` impl + `LocalWatched`-compatible wiring kept for the two call sites (`HomeScreen.kt:94`, `MovieDetail.kt:137` keep calling `WatchedManager`, which becomes a thin delegating object to `WatchedHistory` + the app storage — zero UI behavior change)
- Fix in shared: `scraper/ScraperLog.kt` replace `android.text.format.DateFormat` with `java.text.SimpleDateFormat("HH:mm:ss")` wrapped in `@OptIn(DelicateApi)`-free plain code (valid on both JVM targets)
- Keep interface: `BaseUrlResolver.KeyValueStore` stays in shared (suspend getString/putString/remove)

**Interfaces:**
- Consumes: existing scraper/data sources unmodified except ScraperLog time formatting.
- Produces: `WatchedStorage { fun load(): List<WatchedItem>; fun save(items: List<WatchedItem>) }`; `WatchedHistory.getWatchedItems(storage: WatchedStorage): List<WatchedItem>`, `WatchedHistory.save(storage, item: WatchedItem)` — logic identical to today (dedupe by url-or-title, newest first, cap 24).

- [ ] Step 1: `git checkout -b desktop-kmp`
- [ ] Step 2: Create `shared` module (KMP with `androidTarget()` + `jvm()` preferred; if AGP 9.5.1 + KGP 2.2.10 conflict appears in this task, fall back to plain `kotlin("jvm")` module — file layout is identical either way; record which variant was chosen in the commit body)
- [ ] Step 3: git mv scraper/data sources into `shared/commonMain`; move `SharedPreferencesStore.kt` back into `app` module (it implements the shared interface with Android APIs)
- [ ] Step 4: Write failing JUnit `WatchedHistoryTest` in `shared` (dedupe by url OR title, cap 24, newest first)
- [ ] Step 5: Run `./gradlew :shared:test` → test fails (class missing), implement `WatchedHistory`, re-run → PASS
- [ ] Step 6: `grep -r "import android" shared/` returns nothing
- [ ] Step 7: `./gradlew :app:assembleDebug :shared:build` both green
- [ ] Step 8: Commit `feat: extract shared scraper/data module`

### Task 2: `desktop` module scaffold (CMP version spike)

**Files:**
- Create: `desktop/build.gradle.kts`, `desktop/src/main/kotlin/com/mab/f2mbox/desktop/Main.kt`
- Modify: `settings.gradle.kts` (include `:desktop`), root `gradle/libs.versions.toml` (add cmp version entry chosen by spike)

**Interfaces:**
- Produces: `Main.kt` opens a 1280×720 `Window { F2MBoxDesktopTheme { AppShell() } }` with RTL `LocalLayoutDirection`; `AppShell` renders a placeholder `Text`. Menu bar composable present (File → Quit) but routes added later.

- [ ] Step 1: Add CMP dependency at version per ladder (1.12.2 first), `org.jetbrains.kotlin.plugin.compose`, `org.jetbrains.compose`, `compose.desktop.application` plugin blocks; kotlin jvm target
- [ ] Step 2: `./gradlew :desktop:build` — if version friction (compile/Kotlin metadata errors), step down the ladder / bump Kotlin per Global Constraints; record final pins in commit message
- [ ] Step 3: Icons: prefer `org.jetbrains.compose.material:material-icons-extended:<cmp>`; fallback `androidx.compose.material:material-icons-extended:1.7.8` (last KMP desktop artifact) — verify whichever resolves
- [ ] Step 4: Copy 9 `vazirmatn_rd_*.ttf` into `desktop/src/main/composeResources/fonts/`, build `VazirmatnRD` FontFamily via `compose.resources` `Font(Res.font.…)` at 9 weights; port `Theme.kt` (darkColorScheme values from current TV theme) and full Typography from `Type.kt` to M3
- [ ] Step 5: `./gradlew :desktop:run` opens window with correct dark theme + Persian font on the placeholder → commit `feat: desktop module scaffold with CMP`

### Task 3: Strings + platform stores for desktop

**Files:**
- Create: `desktop/src/main/kotlin/com/mab/f2mbox/desktop/platform/Strings.kt` (all ~90 strings from values/strings.xml, names camelCased from resource names: `retry`, `searchHint`, `playerSpeed`, …)
- Create: `desktop/src/main/kotlin/com/mab/f2mbox/desktop/platform/DesktopStores.kt` — `FileKeyValueStore : BaseUrlResolver.KeyValueStore` (JSON map file), `DesktopWatchedStorage : WatchedStorage` (JSON list file), both rooted at `<os-app-data>/F2MBox/` (`~/Library/Application Support` / `%APPDATA%` / `~/.local/share` via `user.home` + os.name)

**Interfaces:**
- Consumes: `BaseUrlResolver.KeyValueStore`, `WatchedStorage` from shared.
- Produces: `object Strings { … }` with one `const val` per resource; `fun desktopStores(): Pair<KeyValueStore, WatchedStorage>` wiring used by Main.kt.

- [ ] Step 1: Write `StringsTest asserting Strings.searchHint == "Movie or series name…\u200E"` (LRM preserved) and error strings match; run → fails; fill Strings → passes
- [ ] Step 2: FileKeyValueStore round-trip JUnit (put/get/remove/persist-across-instances) → PASS
- [ ] Step 3: Commit `feat: desktop strings and file-based storage`

### Task 4: Desktop UI shell + Connect/screens core (ConnectGate, SplashScreen, LogsScreen)

**Files:**
- Create: `desktop/…/DesktopApp.kt` — port of `Route` sealed interface, `stateKey()`, `ConnectGate`, back stack, `PageCache` wiring (copy from MainActivity.kt:134-352, android Log → SLF4J-style println/`ScraperLog` only)
- Create: `desktop/…/ui/connect/ConnectScreens.kt` (port, `NonInteractiveSurfaceDefaults` → M3 `Surface(color=…)`, R.string → Strings)
- Create: `desktop/…/ui/logs/LogsScreen.kt` (port; no crash section — logs only)
- Create: `desktop/…/ui/common/{Savers,FocusRestore,ScraperLogFeed}.kt` (copies — plain Compose, imports verified portable)
- Extract (shared logic, testable): `desktop/…/connect/ConnectFlow.kt` containing the retry/health loop; `ConnectGate` delegates

**Interfaces:**
- Consumes: `F2MScraper`, `PageCache`, `ConnectState`, `ScraperLog` (shared), `Strings`.
- Produces: `ConnectGate(scraper, stores)` suspend-driven route host: Route.Home route renders placeholder in this task; ESC key equivalents map Back keys → `goBack`.

- [ ] Step 1: `ConnectFlowTest`: empty `heroSlider` → Failed-with-retry path; network error → 3 attempts then `Failed`; manual domain accepted → Ready (mock `F2MScraper? no — construct with fake` if `F2MScraper` is open/sealed for tests; if not testable, drive via NaN and mark manual verification) 
- [ ] Step 2: Port screens; `./gradlew :desktop:run` shows splash → retry/live connect behavior identical to Android
- [ ] Step 3: Commit `feat: desktop connect gate and logs screens`

### Task 5: Browse UI port (Home, List, Detail, Person)

**Files (copies into `desktop/…/ui/`, TV→M3 mapped):**
- `home/HomeScreen.kt`, `home/HomeNavigation.kt`, `home/SectionRow.kt`, `home/HeroSlider.kt`, `home/SearchField.kt`, `home/HomeFocus.kt`, `home/HomeState.kt`
- `list/ListScreen.kt`, `detail/MovieDetail.kt`, `person/PersonScreen.kt`
- `data/WatchedManager` call sites → `WatchedHistory(desktopWatchedStorage)`

**TV→M3 mapping (mechanical):** `androidx.tv.material3.{MaterialTheme,Text,Button,Icon,IconButton,ButtonDefaults,darkColorScheme}` → `androidx.compose.material3.*` same-name; `NonInteractiveSurfaceDefaults.colors(x)` → `Surface(color = x)`; `Glow` usages → drop (M3 elevation/shadow). Focus code (`FocusRequester`, `onPreviewKeyEvent`, D-pad keys) ports as-is — same Key enums on desktop; add mouse `clickable` (already present) so both input modes work.

**Interfaces:**
- Consumes: everything from Tasks 2-4; Coil 3 (`coil3.compose.AsyncImage` — same API cross-platform).
- Produces: Route.Home / Scrape / Results fully interactive; onPlay callback routed (task 6 wires player).

- [ ] Step 1: Port Home stack; run: home renders live site data, RTL correct, focus+mouse both navigate, watched-history row appears
- [ ] Step 2: Port list/detail/person; run: back-stack state restore works (Review Focus item 4 manual check)
- [ ] Step 3: Commit `feat: desktop browse screens`

### Task 6: VLC player engine

**Files:**
- Create: `desktop/…/player/VlcPlayerEngine.kt`, `player/VlcDiscovery.kt`

**Interfaces:**
- Produces:
  - `class VlcPlayerEngine(url: String, listener: (EngineEvent) -> Unit)`; `interface EngineEvent` sealed: `Playing`, `Paused`, `Position(ms: Long, durationMs: Long)`, `Rate(rate: Float)`, `TracksReady(audio: List<TrackInfo>, subtitles: List<TrackInfo>, currentAudio: Int, currentSubtitle: Int)`, `Error(kind: ErrorKind)` (`ErrorKind`= Network/Decode/Source); methods `play() pause() seekTo(ms) seekBy(delta) setRate(f) selectAudio(id) selectSubtitle(id) release()`; `TrackInfo(id: Int, label: String)`
  - `VlcDiscovery.findLibVlc(): Result<Unit>` — probes standard paths + `/opt/homebrew/opt/libvlc/lib`, `/Applications/VLC.app/Contents/MacOS/lib`; failure message includes `brew install --cask vlc`
- Implementation: vlcj 4.8.3 `MediaPlayerFactory` + `EmbeddedMediaPlayerComponent` (AWT), SyntaxOptions for RTSP-less direct URLs (`startPaused=false`, "avcodec-hw=any" default), event listeners → EngineEvent; tracks read via `mediaPlayer.audio().track()` + `mediaPlayer.subpictures().track()`

- [ ] Step 1: `VlcDiscoveryTest`: missing-dir path returns failure with brew hint (no native load)
- [ ] Step 2: Engine state machine unit checks where runnable without natives (event fan-out); real playback verified manually in Task 7 run
- [ ] Step 3: Commit `feat: vlc player engine`

### Task 7: Player screen port

**Files:**
- Create: `desktop/…/ui/player/PlayerScreen.kt` — port control UI from `app/.../PlayerScreen.kt` (functions PlayerControls/SeekBar/PlayerButton/ChoiceMenu/formatTime/PlayerError ports) onto `VlcPlayerEngine`; video surface via `androidx.compose.ui.awt.SwingPanel(factory = { EmbeddedMediaPlayerComponent })`, Compose overlay for controls
- Cut: `SubtitleSettingsPanel` + custom sub styling + `AndroidView` SubtitleView — desktop v1 lets libvlc render selected subtitle track; keep track menu, speed, seek ±10s, play/pause, error gray cards (`player_error*` strings)

**Interfaces:**
- Consumes: `VlcPlayerEngine` events/methods; Strings player_*.
- Produces: `PlayerScreen(url, title, onNavigateBack)` wired from Route.Player; ESC + Backspace → `onNavigateBack`.

- [ ] Step 1: Port controls; `:desktop:run` → play a real stream: video in SwingPanel, overlay controls over it, seek/speed/audio/subtitle menus work
- [ ] Step 2: Verify no-VLC machine path shows setup card (or document skip if only VLC-present dev box available)
- [ ] Step 3: Commit `feat: desktop player with vlcj` (note parity cut in body)

### Task 8: Packaging + distribution docs

**Files:**
- Modify: `desktop/build.gradle.kts` — `nativeDistributions { targetFormats(Dmg, Msi, Exe, Deb); macOS { icon } … }` unsigned; app name "F2MBox"
- Create: `desktop/src/main/resources/f2mbox-logo.png` placeholder if icon missing (or reuse existing launcher art converted)
- Modify: `README.md` — Desktop section: build/run/install matrix, macOS unsigned first-launch instructions (`xattr -cr` / Open Anyway), note player requires VLC for dev-run (bundled in installers).

**Interfaces:** none new. Produces release artifacts.

- [ ] Step 1: `./gradlew :desktop:packageDistributionForCurrentOS` → DMG opens and runs on this Mac (first-launch friction reproduced and documented)
- [ ] Step 2: `./gradlew :app:assembleDebug :shared:test :desktop:build` full green
- [ ] Step 3: Commit `feat: desktop packaging and release docs`

### Task 9: Final verification + branch summary

- [ ] Step 1: On Apple Silicon: run app end-to-end (connect → home → detail → trailer/player → history persists across restart)
- [ ] Step 2: Assemble summary of parity cuts + follow-ups (subtitle styling panel, fullscreen, window-size persistence, CI matrix, native-instance bundling for player), post to user
- [ ] Step 3: Squash-fix stray TODOs; final commit; push branch `git push -u origin desktop-kmp`
