# Release: keystore, signing, bundle

Everything below runs from the repository root. Replace the placeholder passwords.

## 1. Create the upload keystore (once)

```bash
mkdir -p keystore
keytool -genkeypair -v \
  -keystore keystore/rikavon-upload.jks \
  -alias rikavon-upload \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Rikavon, OU=Mobile, O=Rikavon, L=Tel Aviv, C=IL" \
  -storepass 'CHANGE_ME_STORE' -keypass 'CHANGE_ME_KEY'
```

Keep `keystore/rikavon-upload.jks` outside version control (the `keystore/` folder is git-ignored).
Back it up in two places. Losing it with Play App Signing enabled is recoverable; without it, it is not.

## 2. Tell Gradle where it is

Create `keystore/release.properties` (git-ignored):

```properties
storeFile=keystore/rikavon-upload.jks
storePassword=CHANGE_ME_STORE
keyAlias=rikavon-upload
keyPassword=CHANGE_ME_KEY
```

`app/build.gradle.kts` reads this file if it exists and wires the `release` signing config automatically.
Without the file, `assembleRelease` produces an unsigned APK (fine for local verification only).

## 3. Build

```bash
# Signed release APK (sideload / internal testing)
./gradlew :app:assembleRelease
# -> app/build/outputs/apk/release/app-release.apk

# Android App Bundle for Google Play
./gradlew :app:bundleRelease
# -> app/build/outputs/bundle/release/app-release.aab
```

Verify the signature:

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## 4. Version bump

Edit `versionCode` / `versionName` in `app/build.gradle.kts`. `versionCode` must increase on every Play upload.

## 5. Quality gate before tagging

```bash
./gradlew ktlintCheck detekt testDebugUnitTest :app:lintRelease
```

All four must be green. The build treats Kotlin warnings as errors, so a warning is a failed build.

## 6. Debug build

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug build uses the application id `il.rikavon.debug`, so it installs next to a release build.
