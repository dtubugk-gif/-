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
| Project to computer | Streams the phone screen **and sound** to a computer running **ShakeLight Receiver**, directly over Wi-Fi (no Google Cast, no internet). The computer finds itself automatically; a projector/TV plugged into the computer shows it. |
| Control & desktop mode | Use the computer's **mouse and keyboard** on the phone (click, drag, wheel scroll, typing in any language, Ctrl+V paste), a **taskbar** with Start menu of all phone apps (search, pin), Back/Home/Recents/Split screen/Notifications/Screenshot/Volume/Lock, and **Desktop mode** that turns the phone sideways for a wide screen. |
| Projector | Photos/videos to a projector over a USB-C→HDMI cable (the phone becomes the remote), or a **DIY lens-box** mode (rotate 180° / mirror). |

> A phone has no projector lens — no app can make it project onto a wall by itself.
> These are the ways that really work: a computer + projector, a cable to a projector, or a lens box.

## Project to computer

1. On the computer, download and open the receiver from `dist/`:
   - Windows: `ShakeLight-Receiver-Windows.exe` — if SmartScreen warns, click **More info → Run anyway**;
     when the firewall asks, click **Allow** (private networks).
   - Mac: `ShakeLight-Receiver-Mac-AppleSilicon` (M1/M2/M3…) or `-Mac-Intel`. First time: right-click → **Open**.
   - Linux: `ShakeLight-Receiver-Linux` or `-Linux-ARM64` (Raspberry Pi etc.), `chmod +x` first.
2. A full-screen window opens (Edge/Chrome app window) and waits for the phone.
3. On the phone: **ShakeLight → Project to computer → tap the computer** and allow screen sharing.

### Control & desktop mode

On the phone, in **Project to computer → Control & desktop mode**, switch on once:
- **Mouse & keyboard** → *Turn on* → pick *ShakeLight remote control* in Accessibility. On Android 13+, if the
  switch is greyed out: Settings → Apps → ShakeLight → ⋮ → *Allow restricted settings*, then try again.
- **Desktop mode (landscape)** → *Allow* ("Modify system settings").

On the computer: left-click = tap, drag = swipe, hold = long-press, wheel = scroll, right-click / `Esc` = Back,
middle-click = Home, `Windows` key = Start menu, Ctrl+V = paste text on the phone. The taskbar has the rest.
Without control (view only) the keys are: `F` full screen · `R` rotate 180° · `M` mirror · `S` sound.

> Real Samsung DeX (several resizable app windows) is part of Samsung's system and can't be recreated by an
> app. ShakeLight gives the DeX-style experience: big screen, mouse, keyboard, app launcher, split screen.
> On Samsung phones you can also use real DeX with a USB-C→HDMI cable to a monitor or projector.

How it works: the receiver announces itself on UDP 47800; the phone connects on TCP 47801 and sends
H.264 video (hardware encoder) plus 48 kHz PCM sound; the receiver serves a local page on
`127.0.0.1:47802` that decodes it with the browser's hardware decoder (WebCodecs). Source: `receiver/`.
Sound needs Android 10+. Apps that block screenshots (Netflix, banking) appear black.

## Adding a feature

1. Create an `Activity` (see `ui/ScreenLightActivity.kt` for a minimal one).
2. Register it in `app/src/main/AndroidManifest.xml`.
3. Add one line to `ui/Features.kt` — it appears on the home screen automatically.

Shared building blocks: `core/TorchController` (all flash access), `core/Prefs` (settings keys),
`core/Screen` (full-screen + brightness), `core/Haptics`.

## Build

```bash
./gradlew testReleaseUnitTest assembleRelease   # APK: app/build/outputs/apk/release/app-release.apk
cd receiver && GOOS=windows GOARCH=amd64 go build -o ShakeLight-Receiver-Windows.exe .
```

Every push builds the APK and the receivers on GitHub Actions; pushes to `main` also publish them as a Release.

The APK is signed with `keystore/shakelight.jks` (committed on purpose, so every build installs as an
update over the last). **Replace it with a private key before publishing on Google Play.**
