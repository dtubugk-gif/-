#!/usr/bin/env bash
# Builds dist/SuperFaceBros.apk. Needs JDK 17+, Gradle 8.x (or android/gradlew) and an Android SDK ($ANDROID_HOME).
# Real photos in .local-faces/hero.png + enemy.png (git-ignored) are used when present; otherwise placeholder faces.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:=/opt/android-sdk}"; export ANDROID_HOME
./tools/stage.sh
python3 tools/make_icons.py
# One-time local release key (kept out of git). Keep it: updates must be signed with the same key.
if [ ! -f .local-keystore/release.jks ]; then
  mkdir -p .local-keystore
  keytool -genkeypair -keystore .local-keystore/release.jks -storepass superfacebros -keypass superfacebros \
    -alias sfb -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Super Face Bros" >/dev/null 2>&1
fi
GRADLE=gradle; [ -x android/gradlew ] && GRADLE=./gradlew
(cd android && $GRADLE --no-daemon -q assembleRelease)
mkdir -p dist && cp android/app/build/outputs/apk/release/app-release.apk dist/SuperFaceBros.apk
echo "OK -> dist/SuperFaceBros.apk"
