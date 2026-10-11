#!/usr/bin/env bash
# Builds a signed APK without Gradle (useful offline / in CI sandboxes).
# Needs: JDK 11+, aapt2, dalvik-exchange (dx), zipalign, apksigner, python3 and an android.jar (API 34 recommended).
#   apt install aapt dalvik-exchange zipalign apksigner
# Usage: ANDROID_JAR=/path/to/android.jar ./tools/build_apk.sh [output.apk]
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$(pwd)
ANDROID_JAR=${ANDROID_JAR:?set ANDROID_JAR to an android.jar (API 34)}
APP_ID=${APP_ID:-com.toiboytoestar.kurisu}
VERSION_NAME=${VERSION_NAME:-1.2.1}
VERSION_CODE=${VERSION_CODE:-13}
OUT=${1:-$ROOT/build/amadeus-kurisu.apk}
KS=${KEYSTORE:-$ROOT/build/debug.keystore}
B=$ROOT/build/work
export JAVA_TOOL_OPTIONS=
rm -rf "$B" && mkdir -p "$B/gen" "$B/classes" "$(dirname "$OUT")"

echo "== aapt2 compile"
aapt2 compile --dir app/src/main/res -o "$B/res.zip"

echo "== aapt2 link"
aapt2 link -o "$B/base.apk" -I "$ANDROID_JAR" \
  --manifest app/src/main/AndroidManifest.xml \
  --java "$B/gen" --custom-package com.toiboytoestar.kurisu \
  --rename-manifest-package "$APP_ID" \
  --min-sdk-version 21 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --auto-add-overlay -R "$B/res.zip"

echo "== javac"
javac -Xlint:-options -encoding UTF-8 --release 8 -cp "$ANDROID_JAR" -d "$B/classes" \
  $(find app/src/main/java "$B/gen" -name '*.java')

echo "== dex"
dalvik-exchange --dex --min-sdk-version=21 --output="$B/classes.dex" "$B/classes"

echo "== package"
python3 - "$B/base.apk" "$B/classes.dex" "$B/unsigned.apk" <<'PY'
import sys, zipfile
src, dex, out = sys.argv[1:4]
with zipfile.ZipFile(src) as zi, zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zo:
    for it in zi.infolist():
        data = zi.read(it.filename)
        # resources.arsc must stay stored (uncompressed) for API 30+; also keep raw audio/webp uncompressed
        method = zipfile.ZIP_STORED if it.filename == 'resources.arsc' or it.filename.endswith(('.ogg', '.webp')) else zipfile.ZIP_DEFLATED
        zo.writestr(zipfile.ZipInfo(it.filename, it.date_time), data, compress_type=method)
    zo.write(dex, 'classes.dex', compress_type=zipfile.ZIP_DEFLATED)
PY
zipalign -f -p 4 "$B/unsigned.apk" "$B/aligned.apk"

if [ ! -f "$KS" ]; then
  echo "== creating debug keystore (use your own keystore for releases: KEYSTORE=... KS_PASS=...)"
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias amadeus \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Amadeus Fan Build,O=Unofficial" >/dev/null
fi
echo "== sign"
apksigner sign --ks "$KS" --ks-pass "pass:${KS_PASS:-android}" --key-pass "pass:${KS_PASS:-android}" --out "$OUT" "$B/aligned.apk"
apksigner verify --verbose "$OUT" | head -5
ls -la "$OUT"
