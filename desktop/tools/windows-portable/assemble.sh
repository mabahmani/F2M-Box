#!/bin/bash
# Assembles a Windows x64 app image on macOS from:
#   * the Windows-Skiko uber jar produced by
#     `./gradlew :desktop:packageUberJarForCurrentOS -Pf2mDesktopTargetOs=windows`
#   * the Windows libVLC staged by
#     `./gradlew :desktop:prepareVlc -Pf2mDesktopTargetOs=windows`
#   * a Windows runtime cross-linked from Temurin's Windows jmods
# jpackage cannot cross-build, so this reproduces its app-image layout by hand.
#
# Emits two flavours of the same app:
#   F2MBox/        jpackage-faithful: 64-bit javaw.exe renamed to F2MBox.exe,
#                  configured by app/F2MBox.cfg ($APPDIR expansion).
#   F2MBox-l4j/    Launch4j wrapper: branded exe (app icon + version info),
#                  kept as a fallback launcher.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
OUT="$ROOT/desktop/build/windows-portable"
WORK="$OUT/work"
WIN_JDK="${WIN_JDK:?set WIN_JDK to a Windows x64 JDK 21 directory}"
HOST_JDK="${HOST_JDK:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
L4J="${L4J:?set L4J to the extracted Launch4j directory}"

VERSION_NAME=$(sed -n 's/^f2mVersionName=//p' "$ROOT/gradle.properties")
VERSION_CODE=$(sed -n 's/^f2mVersionCode=//p' "$ROOT/gradle.properties")
VERSION="$VERSION_NAME.$VERSION_CODE"

UBER_JAR=$(ls -t "$ROOT"/desktop/build/compose/jars/*.jar | head -1)
VLC_SRC="$ROOT/desktop/build/appResources/windows/vlc"
[ -f "$UBER_JAR" ] || { echo "uber jar missing"; exit 1; }
[ -d "$VLC_SRC" ] || { echo "windows libVLC missing"; exit 1; }
mkdir -p "$WORK"
unzip -Z1 "$UBER_JAR" > "$WORK/jar-entries.txt"
grep -qx 'skiko-windows-x64.dll' "$WORK/jar-entries.txt" \
  || { echo "uber jar has no Windows Skiko; rebuild with -Pf2mDesktopTargetOs=windows"; exit 1; }

echo ">> Windows runtime (cross-jlink)"
RT="$WORK/runtime"
if [ ! -x "$RT/bin/javaw.exe" ]; then
  rm -rf "$RT"
  "$HOST_JDK/bin/jlink" \
    --module-path "$WIN_JDK/jmods" \
    --add-modules ALL-MODULE-PATH \
    --output "$RT" \
    --strip-debug --no-header-files --no-man-pages --compress=zip-6
fi

echo ">> icon"
python3 - "$ROOT/desktop/src/main/resources/f2mbox-logo.png" "$WORK/f2mbox.ico" <<'PY'
import sys
from PIL import Image
src, dst = sys.argv[1], sys.argv[2]
Image.open(src).convert("RGBA").save(
    dst, format="ICO", sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])
PY

stage_common() { # $1 = image dir
  local img="$1"
  rm -rf "$img"
  mkdir -p "$img/app/resources" "$img/runtime"
  cp "$UBER_JAR" "$img/app/F2MBox.jar"
  # Compose's jpackage image extracts the Skiko native next to the cfg and
  # points -Dskiko.library.path at it; mirror that instead of relying on the
  # jar's temp extraction.
  unzip -o -j "$UBER_JAR" 'skiko-windows-x64.dll*' -d "$img/app" > /dev/null
  cp -R "$VLC_SRC" "$img/app/resources/vlc"
  cp -R "$RT/." "$img/runtime/"
}

echo ">> flavour 1: jpackage layout (javaw.exe + cfg)"
IMG="$OUT/F2MBox"
stage_common "$IMG"
cp "$RT/bin/javaw.exe" "$IMG/F2MBox.exe"
# Mirrors the .cfg Compose/jpackage produce on the host build; CRLF for Windows.
printf '%s\r\n' \
  '[Application]' \
  'app.classpath=$APPDIR\F2MBox.jar' \
  'app.mainclass=com.mab.f2mbox.desktop.MainKt' \
  '' \
  '[JavaOptions]' \
  "java-options=-Djpackage.app-version=$VERSION" \
  'java-options=-Dcompose.application.resources.dir=$APPDIR\resources' \
  'java-options=-Dcompose.application.configure.swing.globals=true' \
  'java-options=-Dskiko.library.path=$APPDIR' \
  > "$IMG/app/F2MBox.cfg"

echo ">> flavour 2: launch4j (branded exe)"
IMG4J="$OUT/F2MBox-l4j"
stage_common "$IMG4J"
SHIM="$WORK/shim"
rm -rf "$SHIM"
mkdir -p "$SHIM/src" "$SHIM/classes"
cat > "$SHIM/src/F2MLauncher.java" <<'JAVA'
import java.nio.file.Path;
import java.nio.file.Paths;

/** Launch4j entry point: injects the app-resources dir relative to this jar. */
public final class F2MLauncher {
    public static void main(String[] args) {
        try {
            Path jar = Paths.get(
                F2MLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            System.setProperty(
                "compose.application.resources.dir",
                jar.getParent().resolve("resources").toString());
        } catch (Exception ignored) {
            // Fall back to libVLC discovery via a system VLC install.
        }
        com.mab.f2mbox.desktop.MainKt.main();
    }
}
JAVA
"$HOST_JDK/bin/javac" -cp "$IMG4J/app/F2MBox.jar" -d "$SHIM/classes" "$SHIM/src/F2MLauncher.java"
printf 'Main-Class: F2MLauncher\nClass-Path: F2MBox.jar\n' > "$SHIM/manifest.mf"
"$HOST_JDK/bin/jar" --create --file "$IMG4J/app/launcher.jar" \
  --manifest "$SHIM/manifest.mf" -C "$SHIM/classes" .

cat > "$WORK/launch4j.xml" <<XML
<?xml version="1.0" encoding="UTF-8"?>
<launch4jConfig>
  <dontWrapJar>true</dontWrapJar>
  <headerType>gui</headerType>
  <jar>app\\launcher.jar</jar>
  <outfile>$IMG4J/F2MBox.exe</outfile>
  <errTitle>F2MBox</errTitle>
  <cmdLine></cmdLine>
  <chdir>.</chdir>
  <priority>normal</priority>
  <stayAlive>false</stayAlive>
  <restartOnCrash>false</restartOnCrash>
  <icon>$WORK/f2mbox.ico</icon>
  <jre>
    <path>runtime</path>
    <bundledJre64Bit>true</bundledJre64Bit>
    <bundledJreAsFallback>false</bundledJreAsFallback>
    <minVersion>21</minVersion>
    <jdkPreference>jreOnly</jdkPreference>
    <runtimeBits>64</runtimeBits>
  </jre>
  <versionInfo>
    <fileVersion>${VERSION}.0</fileVersion>
    <txtFileVersion>${VERSION}</txtFileVersion>
    <fileDescription>F2M Box - your world of entertainment, on the desktop</fileDescription>
    <copyright>F2M Box</copyright>
    <productVersion>${VERSION}.0</productVersion>
    <txtProductVersion>${VERSION}</txtProductVersion>
    <productName>F2M Box</productName>
    <companyName>F2M Box</companyName>
    <internalName>F2MBox</internalName>
    <originalFilename>F2MBox.exe</originalFilename>
  </versionInfo>
</launch4jConfig>
XML
"$HOST_JDK/bin/java" -Djava.awt.headless=true \
  -jar "$L4J/launch4j.jar" "$WORK/launch4j.xml"

echo
for img in "$IMG" "$IMG4J"; do
  zip="$OUT/$(basename "$img")-$VERSION-windows-x64-portable.zip"
  rm -f "$zip"
  (cd "$OUT" && zip -qr -y "$zip" "$(basename "$img")")
  echo "$zip"
  du -sh "$zip" | cut -f1
  shasum -a 256 "$zip"
  echo
done
