# Clash Party: 1–4 Player Mini Games

A local multiplayer party game for Android, in the style of "2 3 4 Player Mini Games".
Up to 4 players share one phone. Each player has their own button in a corner of the screen,
and any empty seat can be filled by a CPU bot.

**Download:** [`ClashParty.apk`](ClashParty.apk). Install it on Android 8.0 or newer
(you will need to allow installing apps from unknown sources).

## Games (16)

| Game | Control | Goal |
|---|---|---|
| Rocket Race | Tap fast | Mash your button to fly your rocket to the finish first |
| Quick Draw | Tap | Tap first when the orb turns white (+1). Tap early or on purple: -1. First to 3 |
| Soccer Clash | Hold + release | Bottom team vs top team (2v2 with 4 players). Hold to run, release to kick. A goal wins the round |
| Turbo Racers | Hold = gas | Slot-car racing: brake before corners or you crash. 3 laps |
| Sumo Bump | Hold | You spin; hold to dash. Knock everyone off the shrinking ring |
| Tank Battle | Hold + release | Hold to drive, release to fire bouncing shots. 3 hits and you're out |
| Penalty Kicks | Tap to shoot | Time your shot past the goalkeeper; bank shots off walls count |
| Hurdle Dash | Tap to jump | Auto-run down your lane and jump the hurdles (watch the doubles) |
| Neon Snake | Hold to turn | Don't crash into the glowing trails. Last snake alive wins |
| Snake Arena | Hold to turn | Eat stars to grow; heads that hit a body are out |
| Hot Bomb | Tap to pass | Throw the bomb away before it explodes in your hands |
| Paint Fight | Hold to move | Paint the most floor in 20 seconds; paint bombs splash big |
| Ping Pong | Tap to turn | Your paddle slides by itself; tap to reverse. Defend your edge |
| Tower Stack | Tap to drop | Stack blocks; overhang gets sliced off. First to 10 |
| Brain Blitz | Tap if true | Tap only when the equation is correct |
| Laser Jump | Tap to jump | Jump over the spinning laser. It speeds up and reverses |

**How matches work:** every mini game is a match of quick rounds. The winner of each round gets a star,
and a scoreboard (readable from both ends of the phone) shows everyone's stars between rounds. The first player to
win 2, 3 or 5 rounds (set in Settings, default 3) wins the match. Draw rounds give no star.

**Modes:** *Party Cup* (random mini games; each match winner earns a cup; first to 3/5/7 cups is champion) or
*Mini Games* (pick any game and play one match).
Tap a player tile on the home screen to switch it between Player, CPU and Off.

## Project layout

```
web/       The game itself: HTML5 canvas + vanilla JS, no dependencies
  js/engine.js     Layout, multi-touch input, game loop, effects, phases
  js/games/*.js    One file per mini game (each one includes its own CPU AI)
  js/app.js        Menus, tournament flow, results
android/   A small native Android app that runs /web in a fullscreen WebView
```

To add a game, create a file in `web/js/games/` that calls `Games.push({...})`,
then add it to `web/index.html`.

## Build

You can try the game in a desktop browser: open `web/index.html` and use phone emulation in DevTools.

To build the APK, you need JDK 17+ and the Android SDK (platform 35):

```bash
cd android
ANDROID_HOME=/path/to/android-sdk ./gradlew assembleRelease
# -> android/app/build/outputs/apk/release/app-release.apk
```

GitHub Actions (`.github/workflows/build-apk.yml`) builds the APK on every push
and uploads it as a workflow artifact.

> **Signing key:** `android/keystore/clashparty.jks` (password `clashparty`) is committed so every
> build has the same signature and updates install over older versions. That's fine for sideloading.
> **Before publishing to Google Play, generate your own private key and keep it out of the repo.**

Font: [Fredoka](https://fonts.google.com/specimen/Fredoka) (SIL Open Font License).
