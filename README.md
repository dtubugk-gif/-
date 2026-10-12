# ShakeLight

Android app: **shake your phone to turn the flashlight on/off** — plus a full flashlight toolkit,
a screen lamp and a projector mode. English + Hebrew UI.

## Install

1. Download `dist/ShakeLight.apk` (or the latest file under **Releases** / the **Actions** run).
2. Open it on the phone and allow "Install unknown apps" for your browser/file manager when asked.
3. Open ShakeLight and flip **Shake to light** on. Tap **Allow** on the battery prompt so it keeps
   working when the screen has been off for a long time.

Requires Android 8.0 or newer.

## Features

| Feature | What it does |
|---|---|
| Shake to light | Background service; works with the screen locked. Tunable sensitivity and number of shakes, vibration feedback (1 buzz = on, 2 = off), **pocket guard** (proximity sensor ignores shakes in a pocket/bag), auto turn-off timer, restarts after reboot. Quick Settings tile + notification buttons. |
| Flashlight | Big on/off button, brightness control (Android 13+ on supported phones), strobe 1–10 Hz, SOS, Morse code transmitter. |
| Screen light | Full-screen colored lamp at max brightness; long-press for color-cycling party mode. |
| Projector | Photos/videos to a projector or TV: wireless cast, or USB-C→HDMI. A connected projector is used as a second screen and the phone becomes the remote. Also a **DIY lens-box** mode (rotate 180° / mirror). |

> A phone has no projector lens — no app can make it project onto a wall by itself.
> The projector mode does the two things that really work: drive a real projector/TV, or a lens box.

## Adding a feature

1. Create an `Activity` (see `ui/ScreenLightActivity.kt` for a minimal one).
2. Register it in `app/src/main/AndroidManifest.xml`.
3. Add one line to `ui/Features.kt` — it appears on the home screen automatically.

Shared building blocks: `core/TorchController` (all flash access), `core/Prefs` (settings keys),
`core/Screen` (full-screen + brightness), `core/Haptics`.

## Build

```bash
./gradlew testReleaseUnitTest assembleRelease   # APK: app/build/outputs/apk/release/app-release.apk
```

Every push builds the APK on GitHub Actions; pushes to `main` also publish it as a Release.

The APK is signed with `keystore/shakelight.jks` (committed on purpose, so every build installs as an
update over the last). **Replace it with a private key before publishing on Google Play.**
