# Neon Swarm · נחיל הנאון

A native Android arcade shooter written in Kotlin. Custom rendering engine on a hardware-accelerated `SurfaceView`, no game framework and no audio/image assets.

## What's in it
- **Rendering:** a dedicated render thread, additive neon glow, up to 1,500 particles, shockwaves, screen shake, hit-stop and a scrolling synthwave grid.
- **Audio:** every sound effect and the music loop are synthesized in code at launch and played through `SoundPool`. Pitch rises as you chain crystal pickups.
- **Haptics:** graded vibration for kills, hits, boss deaths and Overdrive.
- **Gameplay:** endless waves, 6 enemy types, a boss every 5 waves with 3 attack patterns and an enraged mode, 6 power-ups, combo multiplier up to x10, perfect-wave bonus, and Overdrive charged by collecting crystals.
- High score and settings are saved on the device.

## Controls
Drag anywhere on the screen to move the ship (relative movement, so your finger never covers it). Firing is automatic.

## Building
```bash
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
./gradlew testReleaseUnitTest    # headless simulation + screenshots in app/build/screens
```
GitHub Actions builds the APK on every push (artifact `NeonSwarm-apk`).

The release APK is signed with the debug key so it can be installed directly. Publishing to Google Play requires a dedicated keystore and an AAB.
