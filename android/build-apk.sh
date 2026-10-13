#!/usr/bin/env bash
# Builds Hvylia.apk: a native Android shell (WebView player, media notification, YouTube and Spotify tabs
# with MP3 downloads) around ../musicplayer. No Gradle needed.
# Tools (Ubuntu/Debian):  sudo apt install aapt zipalign apksigner protobuf-compiler  + JDK 17+, git, curl
# Third-party libraries and D8 are prepared by ./build-libs.sh (called automatically).
# The Android 14 framework jar is fetched once from Maven Central (Robolectric's android-all).
set -euo pipefail
cd "$(dirname "$0")"
HERE=$(pwd)
OUT=$HERE/build
BT=${BUILD_TOOLS:-/usr/lib/android-sdk/build-tools/debian}
VERSION_CODE=${VERSION_CODE:-4}
VERSION_NAME=${VERSION_NAME:-1.3}
MIN_SDK=26
ANDROID_JAR=${ANDROID_JAR:-$OUT/android-34.jar}
KEYSTORE=${KEYSTORE:-$HERE/hvylia.keystore}
KS_PASS=${KS_PASS:-hvylia-sideload}
LIBS=$OUT/libs

mkdir -p "$OUT"
if [ ! -s "$ANDROID_JAR" ]; then
  echo "Downloading Android 14 framework jar…"
  curl -fL --retry 5 --retry-delay 10 -o "$ANDROID_JAR" \
    https://repo1.maven.org/maven2/org/robolectric/android-all/14-robolectric-10818077/android-all-14-robolectric-10818077.jar
fi
./build-libs.sh

rm -rf "$OUT/assets" "$OUT/gen" "$OUT/classes" "$OUT/stubs" "$OUT/dex" "$OUT/res.zip" "$OUT"/*.apk "$OUT"/app.jar "$OUT"/stubs.jar
mkdir -p "$OUT/assets/www/fonts" "$OUT/assets/fonts" "$OUT/gen" "$OUT/classes" "$OUT/stubs" "$OUT/dex"

# Web app (the same files as the PWA), its fonts for offline use, and fonts for the native screens
cp -r ../musicplayer/index.html ../musicplayer/manifest.webmanifest ../musicplayer/icons "$OUT/assets/www/"
cp web/*.html "$OUT/assets/www/"   # app-only screens (Spotify without an account)
cp fonts/*.woff2 fonts/fonts.css "$OUT/assets/www/fonts/"
cp fonts/ttf/*.ttf "$OUT/assets/fonts/"

"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -I "$ANDROID_JAR" --manifest AndroidManifest.xml -A "$OUT/assets" "$OUT/res.zip" \
  --min-sdk-version $MIN_SDK --target-sdk-version 34 --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --java "$OUT/gen" -o "$OUT/unaligned.apk"

# javax.sound stand-in for the MP3 encoder (Java 8 target: the JDK module system would refuse it otherwise)
javac -nowarn --release 8 -encoding UTF-8 -d "$OUT/stubs" $(find stubs -name '*.java')
jar cf "$OUT/stubs.jar" -C "$OUT/stubs" .
CP="$ANDROID_JAR:$OUT/stubs.jar:$LIBS/newpipe-extractor.jar:$LIBS/nanojson.jar:$LIBS/jump3r.jar:$LIBS/jsr305.jar"
javac -nowarn --release 17 -encoding UTF-8 -classpath "$CP" -d "$OUT/classes" \
  $(find src -name '*.java') $(find "$OUT/gen" -name '*.java')
jar cf "$OUT/app.jar" -C "$OUT/classes" .

java -cp "$OUT/r8lib.jar" com.android.tools.r8.D8 --release --min-api $MIN_SDK --lib "$ANDROID_JAR" --output "$OUT/dex" \
  "$OUT/app.jar" "$OUT/stubs.jar" "$LIBS/newpipe-extractor.jar" "$LIBS/nanojson.jar" "$LIBS/jsoup.jar" \
  "$LIBS/rhino.jar" "$LIBS/protobuf-javalite.jar" "$LIBS/jump3r.jar"
(cd "$OUT/dex" && zip -q -j "$OUT/unaligned.apk" classes*.dex)

zipalign -f -p 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"
apksigner sign --ks "$KEYSTORE" --ks-key-alias hvylia --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out "$OUT/Hvylia.apk" "$OUT/aligned.apk"
apksigner verify --print-certs "$OUT/Hvylia.apk" | head -3
echo "Built $OUT/Hvylia.apk ($(du -h "$OUT/Hvylia.apk" | cut -f1))"
