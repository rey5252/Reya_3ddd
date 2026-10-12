#!/usr/bin/env bash
# Builds the third-party Java libraries used by the YouTube tab into build/libs, from pinned versions:
#  - NewPipeExtractor (GPL-3.0) and its nanojson fork, from source on GitHub (they are only published on JitPack),
#    with third_party/newpipe-android.patch applied (Android 8+ compatible URL encoding);
#  - jsoup, Rhino, protobuf-javalite, jump3r (pure-Java LAME) from Maven Central;
#  - D8 (Google's dex compiler) from the official R8 release bucket.
# Every download is checked against a pinned SHA-256.
# Needs: git, curl, JDK 17+, protoc 3.21 (sudo apt install protobuf-compiler).
set -euo pipefail
cd "$(dirname "$0")"
HERE=$(pwd)
OUT=$HERE/build/libs
SRC=$HERE/build/src
TMP=$HERE/build/tmp
NPE_COMMIT=3313901108f0f52df68c28a04d88c2891f9c400b      # NewPipeExtractor v0.26.5 (2026-10-07)
NANOJSON_COMMIT=e9d656ddb49a412a5a0a5d5ef20ca7ef09549996 # pinned by NewPipeExtractor
M=https://repo1.maven.org/maven2
mkdir -p "$OUT" "$SRC"

fetch() { # url, file, sha256
  if [ ! -s "$2" ] || ! echo "$3  $2" | sha256sum -c --status; then
    curl -fsSL --retry 5 --retry-delay 10 -o "$2" "$1"
  fi
  echo "$3  $2" | sha256sum -c --status || { echo "Checksum mismatch for $2" >&2; rm -f "$2"; exit 1; }
}
fetch $M/org/jsoup/jsoup/1.23.2/jsoup-1.23.2.jar "$OUT/jsoup.jar" e9d8c856856680427f096d156f02a414289985c930998fcb519ce5be39b42003
fetch $M/org/mozilla/rhino/1.8.1/rhino-1.8.1.jar "$OUT/rhino.jar" 98a06d42bdd69da15bf9c2a7812b4cfe648ca9e64fe8baaeb502a32b0ed2c8ac
fetch $M/com/google/protobuf/protobuf-javalite/3.21.12/protobuf-javalite-3.21.12.jar "$OUT/protobuf-javalite.jar" cf678ab3fbc992f4b4654a86c03557c21904d635202b67688fcb2eca442c2d40
fetch $M/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar "$OUT/jsr305.jar" 766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7
fetch $M/de/sciss/jump3r/1.0.5/jump3r-1.0.5.jar "$OUT/jump3r.jar" 89a5894eee514f9e86a61f01d59b2665af6eddcc25b01accc86514bae16f0054
fetch https://storage.googleapis.com/r8-releases/raw/8.5.35/r8lib.jar "$HERE/build/r8lib.jar" 4733945987ee0a840fafc34080b135259e01678412e07212b23f706334290294

checkout() { # dir, repo, commit
  [ -d "$SRC/$1/.git" ] || git clone -q "$2" "$SRC/$1"
  git -C "$SRC/$1" fetch -q origin "$3" 2>/dev/null || true
  git -C "$SRC/$1" checkout -q -f "$3"
}

if [ ! -s "$OUT/nanojson.jar" ]; then
  checkout nanojson https://github.com/TeamNewPipe/nanojson.git $NANOJSON_COMMIT
  rm -rf "$TMP/nanojson" && mkdir -p "$TMP/nanojson"
  javac -nowarn --release 8 -encoding UTF-8 -d "$TMP/nanojson" $(find "$SRC/nanojson/src/main/java" -name '*.java')
  jar cf "$OUT/nanojson.jar" -C "$TMP/nanojson" .
fi

if [ ! -s "$OUT/newpipe-extractor.jar" ]; then
  checkout NewPipeExtractor https://github.com/TeamNewPipe/NewPipeExtractor.git $NPE_COMMIT
  git -C "$SRC/NewPipeExtractor" apply "$HERE/third_party/newpipe-android.patch"
  rm -rf "$TMP/npe" "$TMP/proto" && mkdir -p "$TMP/npe" "$TMP/proto"
  protoc --java_out=lite:"$TMP/proto" -I "$SRC/NewPipeExtractor/extractor/src/main/proto" \
    $(find "$SRC/NewPipeExtractor/extractor/src/main/proto" -name '*.proto')
  javac -nowarn --release 17 -encoding UTF-8 -proc:none \
    -cp "$OUT/jsoup.jar:$OUT/rhino.jar:$OUT/jsr305.jar:$OUT/protobuf-javalite.jar:$OUT/nanojson.jar" -d "$TMP/npe" \
    $(find "$SRC/NewPipeExtractor/extractor/src/main/java" "$SRC/NewPipeExtractor/timeago-parser/src/main/java" "$TMP/proto" -name '*.java')
  jar cf "$OUT/newpipe-extractor.jar" -C "$TMP/npe" .
fi
echo "Libraries ready in $OUT"
