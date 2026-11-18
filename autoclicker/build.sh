#!/usr/bin/env bash
# Builds app-release.apk using only the Android SDK build tools (no Gradle).
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-/opt/android-sdk}"
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-35/android.jar"
rm -rf build && mkdir -p build/res build/gen build/classes build/dex

"$BT/aapt2" compile --dir res -o build/res/res.zip
"$BT/aapt2" link -o build/unsigned.apk -I "$JAR" --manifest AndroidManifest.xml \
    --java build/gen build/res/res.zip
javac -source 11 -target 11 -nowarn -encoding UTF-8 -classpath "$JAR" -d build/classes \
    $(find src build/gen -name '*.java')
"$BT/d8" --release --min-api 24 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
(cd build/dex && zip -q -j ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk

KS="${KEYSTORE:-release.keystore}"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias app \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Fast Clicker" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
    --out FastClicker.apk build/aligned.apk
"$BT/apksigner" verify FastClicker.apk && echo "Built $(pwd)/FastClicker.apk"
