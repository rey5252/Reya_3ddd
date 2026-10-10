#!/usr/bin/env bash
# Builds Hvylia.apk: a native Android shell (WebView + media notification) around ../musicplayer.
# No Gradle needed. Tools (Ubuntu/Debian):  sudo apt install aapt zipalign apksigner dalvik-exchange
# The Android 14 framework jar is fetched once from Maven Central (Robolectric's android-all).
set -euo pipefail
cd "$(dirname "$0")"
HERE=$(pwd)
OUT=$HERE/build
BT=${BUILD_TOOLS:-/usr/lib/android-sdk/build-tools/debian}
VERSION_CODE=${VERSION_CODE:-1}
VERSION_NAME=${VERSION_NAME:-1.0}
ANDROID_JAR=${ANDROID_JAR:-$OUT/android-34.jar}
KEYSTORE=${KEYSTORE:-$HERE/hvylia.keystore}
KS_PASS=${KS_PASS:-hvylia-sideload}

mkdir -p "$OUT"
if [ ! -s "$ANDROID_JAR" ]; then
  echo "Downloading Android 14 framework jar…"
  curl -fL --retry 5 --retry-delay 10 -o "$ANDROID_JAR" \
    https://repo1.maven.org/maven2/org/robolectric/android-all/14-robolectric-10818077/android-all-14-robolectric-10818077.jar
fi
rm -rf "$OUT/assets" "$OUT/gen" "$OUT/classes" "$OUT/res.zip" "$OUT"/*.apk "$OUT/classes.dex"
mkdir -p "$OUT/assets/www/fonts" "$OUT/gen" "$OUT/classes"

# Web app (the same files as the PWA) + bundled fonts for offline use
cp -r ../musicplayer/index.html ../musicplayer/manifest.webmanifest ../musicplayer/icons "$OUT/assets/www/"
cp fonts/*.woff2 fonts/fonts.css "$OUT/assets/www/fonts/"

"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -I "$ANDROID_JAR" --manifest AndroidManifest.xml -A "$OUT/assets" "$OUT/res.zip" \
  --min-sdk-version 24 --target-sdk-version 34 --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --java "$OUT/gen" -o "$OUT/unaligned.apk"

javac -nowarn --release 8 -encoding UTF-8 -classpath "$ANDROID_JAR" -d "$OUT/classes" \
  $(find src -name '*.java') $(find "$OUT/gen" -name '*.java')
"$BT/dx" --dex --min-sdk-version=24 --output="$OUT/classes.dex" "$OUT/classes"
(cd "$OUT" && zip -q -j unaligned.apk classes.dex)

zipalign -f -p 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"
apksigner sign --ks "$KEYSTORE" --ks-key-alias hvylia --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out "$OUT/Hvylia.apk" "$OUT/aligned.apk"
apksigner verify --print-certs "$OUT/Hvylia.apk" | head -3
echo "Built $OUT/Hvylia.apk ($(du -h "$OUT/Hvylia.apk" | cut -f1))"
