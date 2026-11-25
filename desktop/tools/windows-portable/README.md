# Windows x64 portable build (local, cross-assembled on macOS)

`jpackage`/Compose only package for the OS they run on, and Gradle **silently
SKIPs** `:desktop:packageReleaseExe` on macOS. These images are therefore
assembled by hand from Windows artifacts on the Mac. They are **portable
folders**, not WiX `.exe`/`.msi` installers (that still needs Windows + WiX).

## Artifacts

Everything lands in `desktop/build/windows-portable/`:

| File | What it is |
| --- | --- |
| `F2MBox-<version>-windows-x64-portable.zip` | Primary. 64-bit `F2MBox.exe` (a renamed `javaw.exe`) configured by `app/F2MBox.cfg`, exactly like a jpackage app image. |
| `F2MBox-l4j-<version>-windows-x64-portable.zip` | Fallback. Launch4j wrapper with the app logo/version info; its 32-bit stub starts the bundled 64-bit JVM. |
| `F2MBox/`, `F2MBox-l4j/` | Unzipped images (handy for inspection). |

Zip checksums are printed by the script. They are not stable across runs
because `zip` stores file timestamps.

## Running on Windows

1. Unzip anywhere (keep the folder layout: `F2MBox.exe`, `app/`, `runtime/`).
2. Run `F2MBox.exe`. No Java/VLC installation is needed — the JDK 21 runtime
   and libVLC 3.0.23 are bundled.
3. SmartScreen: **More info → Run anyway** (unsigned, as with the CI builds).
4. App data goes to `%APPDATA%\F2MBox`.

If the primary launcher misbehaves, try the `F2MBox-l4j` flavour.

## How it is produced

```bash
# 1. Windows Skiko runtime in the desktop dependency set + Windows libVLC
./gradlew :desktop:packageUberJarForCurrentOS -Pf2mDesktopTargetOs=windows --no-configuration-cache
./gradlew :desktop:prepareVlc                -Pf2mDesktopTargetOs=windows --no-configuration-cache

# 2. Windows JDK 21 (for jmods) and Launch4j
curl -L -o win-jdk21.zip https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse
# launch4j-3.50-macosx-x86.tgz from sourceforge.net/projects/launch4j

# 3. Assemble (cross-jlink + cfg/launch4j + zip)
WIN_JDK=<windows-jdk-21> L4J=<extracted-launch4j> desktop/tools/windows-portable/assemble.sh
```

Notes:

- The uber jar is named `F2MBox-macos-arm64-*.jar` because Compose names it after
  the host, but its contents are Windows (`skiko-windows-x64.dll`, JNA win32).
  `assemble.sh` aborts if it sees a non-Windows jar.
- `-Pf2mDesktopTargetOs=windows` is an opt-in flag added to
  `desktop/build.gradle.kts`; host builds behave exactly as before.
- The jar is the unminified uber jar (no ProGuard), so the image is larger than
  the release installers built by the release pipeline.
