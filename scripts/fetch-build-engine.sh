#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="${RUNNER_TEMP:-$ROOT/.engine-build}"
JS_ZIP="$WORK/SweetHome3DJS-7.5.2-src.zip"
JAVA_ZIP="$WORK/SweetHome3D-7.5-src.zip"
JS_DIR="$WORK/SweetHome3DJS"
JAVA_DIR="$WORK/SweetHome3D"

mkdir -p "$WORK"
cd "$WORK"

# Official SourceForge release archives. The JS archive SHA-256 is published
# by SourceForge; verify it before extraction.
curl -fL --retry 3 --retry-delay 2 \
  "https://sourceforge.net/projects/sweethome3d/files/SweetHome3DJS-source/SweetHome3DJS-7.5.2-src.zip/download" \
  -o "$JS_ZIP"
echo "92601c109399bb90bc34e0138e46ac983e71f079eb140a128c2a0178628f46e8  $JS_ZIP" | sha256sum -c -

curl -fL --retry 3 --retry-delay 2 \
  "https://sourceforge.net/projects/sweethome3d/files/SweetHome3D-source/SweetHome3D-7.5-src.zip/download" \
  -o "$JAVA_ZIP"

mkdir -p "$JS_DIR" "$JAVA_DIR"
unzip -q "$JS_ZIP" -d "$JS_DIR"
unzip -q "$JAVA_ZIP" -d "$JAVA_DIR"

# Normalize archive roots without assuming SourceForge's internal folder names.
JS_BUILD_XML="$(find "$JS_DIR" -name build.xml -print -quit)"
JAVA_SRC="$(find "$JAVA_DIR" -type d -path '*/src/com/eteks/sweethome3d' -print -quit)"
if [[ -z "$JS_BUILD_XML" || -z "$JAVA_SRC" ]]; then
  echo "Sweet Home 3D source archive layout was not recognized" >&2
  exit 20
fi
JS_ROOT="$(dirname "$JS_BUILD_XML")"
JAVA_ROOT="$(dirname "$(dirname "$(dirname "$(dirname "$JAVA_SRC")")")")"

cd "$JS_ROOT"
ant -noinput applicationLibraries
ant -noinput applicationDistribution

# Stage the complete upstream JS editor, generated JSweet output, resources,
# and its own testHome entry page as a local Android WebView payload.
DEST="$ROOT/app/src/main/assets/engine"
rm -rf "$DEST"
mkdir -p "$DEST"
cp -a "$JS_ROOT/." "$DEST/"
cp -a "$JAVA_ROOT" "$WORK/SweetHome3D-source"

test -s "$DEST/lib/generated/SweetHome3D.js"
test -s "$DEST/lib/generated/geom.js"
test -s "$DEST/test/testHome.html"
echo "Sweet Home 3D JS editor libraries generated and staged successfully."
