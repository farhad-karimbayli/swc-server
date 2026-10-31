#!/usr/bin/env bash
#
# build_client.sh - Patch and sign Star Wars: Commander APK for a custom server address
#
# Usage:
#   ./tools/build_client.sh <HOST:PORT> [SOURCE_APK]
#
# Examples:
#   ./tools/build_client.sh 192.168.1.100:8080
#   ./tools/build_client.sh 10.0.0.5:8080 path/to/star-wars-commander.apk
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TARGET_HOST="${1:-}"
SOURCE_APK="${2:-}"

if [ -z "$TARGET_HOST" ]; then
  echo "Usage: $0 <HOST:PORT> [SOURCE_APK]" >&2
  echo "Example: $0 192.168.1.100:8080" >&2
  exit 1
fi

# Locate build tools (zipalign and apksigner)
ZIPALIGN="$(command -v zipalign 2>/dev/null || true)"
APKSIGNER="$(command -v apksigner 2>/dev/null || true)"

if [ -z "$ZIPALIGN" ] || [ -z "$APKSIGNER" ]; then
  # Check common Android SDK build-tools paths
  for BT_DIR in \
    /opt/homebrew/share/android-commandlinetools/build-tools/* \
    "$HOME/Library/Android/sdk/build-tools"/* \
    "${ANDROID_HOME:-/nonexistent}/build-tools"/*; do
    if [ -d "$BT_DIR" ] && [ -x "$BT_DIR/zipalign" ] && [ -x "$BT_DIR/apksigner" ]; then
      ZIPALIGN="$BT_DIR/zipalign"
      APKSIGNER="$BT_DIR/apksigner"
      break
    fi
  done
fi

if [ -z "$ZIPALIGN" ] || [ -z "$APKSIGNER" ]; then
  echo "Error: 'zipalign' or 'apksigner' not found in PATH or Android SDK build-tools." >&2
  echo "Please install Android command-line tools or set PATH to your build-tools directory." >&2
  exit 1
fi

KEYSTORE="$ROOT/client/swc-dev.keystore"
KEYSTORE_PASS="swcserver"
KEY_ALIAS="swc"

if [ ! -f "$KEYSTORE" ]; then
  echo "Error: Developer keystore not found at $KEYSTORE" >&2
  exit 1
fi

# Find source/template APK and clean APK
PRISTINE_APK=""
TEMPLATE_APK=""

# Check if explicitly provided
if [ -n "$SOURCE_APK" ] && [ -f "$SOURCE_APK" ]; then
  PRISTINE_APK="$SOURCE_APK"
fi

# Locate pristine APK with original metadata
if [ -z "$PRISTINE_APK" ]; then
  for CANDIDATE in \
    "$ROOT/client/star-wars-commander-7-8-1-253.apk" \
    "$ROOT/../star-wars-commander-7-8-1-253.apk" \
    "$ROOT/client/swc-base.apk" \
    "$ROOT/../swc-base.apk"; do
    if [ -f "$CANDIDATE" ]; then
      PRISTINE_APK="$CANDIDATE"
      break
    fi
  done
fi

# Locate template APK (one that already has android:usesCleartextTraffic="true")
for CANDIDATE in \
  "$ROOT/client"/swc-patched-*.apk \
  "$PRISTINE_APK"; do
  if [ -f "$CANDIDATE" ]; then
    TEMPLATE_APK="$CANDIDATE"
    break
  fi
done

if [ -z "$PRISTINE_APK" ] || [ ! -f "$PRISTINE_APK" ]; then
  echo "Error: Pristine base APK (with original metadata) not found." >&2
  echo "Please provide the path to star-wars-commander-7-8-1-253.apk:" >&2
  echo "  $0 $TARGET_HOST /path/to/star-wars-commander-7-8-1-253.apk" >&2
  exit 1
fi

if [ -z "$TEMPLATE_APK" ] || [ ! -f "$TEMPLATE_APK" ]; then
  TEMPLATE_APK="$PRISTINE_APK"
fi

echo "==> Pristine APK:     $PRISTINE_APK"
echo "==> Template APK:     $TEMPLATE_APK"
echo "==> Target server:    $TARGET_HOST"

TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT

WORK_APK="$TMPDIR/working.apk"
cp "$TEMPLATE_APK" "$WORK_APK"

METADATA_REL_PATH="assets/bin/Data/Managed/Metadata/global-metadata.dat"
mkdir -p "$TMPDIR/extracted"
echo "==> Extracting $METADATA_REL_PATH from pristine APK..."
unzip -q -j "$PRISTINE_APK" "$METADATA_REL_PATH" -d "$TMPDIR/extracted"

echo "==> Patching metadata with tools/patch_metadata.py..."
python3 "$ROOT/tools/patch_metadata.py" "$TMPDIR/extracted/global-metadata.dat" "$TARGET_HOST"

echo "==> Updating APK with patched metadata..."
(
  cd "$TMPDIR/extracted"
  mkdir -p "assets/bin/Data/Managed/Metadata"
  mv global-metadata.dat "assets/bin/Data/Managed/Metadata/"
  zip -q -u "$WORK_APK" "assets/bin/Data/Managed/Metadata/global-metadata.dat"
)

echo "==> Removing existing signatures..."
zip -q -d "$WORK_APK" "META-INF/*.SF" "META-INF/*.RSA" "META-INF/*.DSA" "META-INF/SWC.*" "META-INF/MANIFEST.MF" 2>/dev/null || true

HOST_IP="$(echo "$TARGET_HOST" | cut -d: -f1)"
OUTPUT_APK="$ROOT/client/swc-patched-${HOST_IP}.apk"
ALIGNED_APK="$TMPDIR/aligned.apk"

echo "==> Running zipalign..."
"$ZIPALIGN" -f -p 4 "$WORK_APK" "$ALIGNED_APK"

echo "==> Signing APK with swc-dev.keystore..."
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass "pass:$KEYSTORE_PASS" --ks-key-alias "$KEY_ALIAS" --out "$OUTPUT_APK" "$ALIGNED_APK"

echo ""
echo "SUCCESS! Patched APK created at:"
echo "  $OUTPUT_APK"
echo ""
echo "To install on a connected Android device:"
echo "  adb install -r \"$OUTPUT_APK\""
