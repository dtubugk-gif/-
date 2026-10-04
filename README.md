# Clash Party: 1–4 Player Mini Games

A local multiplayer party game for Android, in the style of "2 3 4 Player Mini Games".
Up to 4 players share one phone. Each player has their own button in a corner of the screen,
and any empty seat can be filled by a CPU bot.

**Download:** [`ClashParty.apk`](ClashParty.apk). Install it on Android 8.0 or newer
(you will need to allow installing apps from unknown sources).

## Games (19)

| Game | Control | Goal |
|---|---|---|
| Rocket Race | Tap fast | Mash your button to fly your rocket to the finish first |
| Quick Draw | Tap | Tap first when the orb turns white (+1). Tap early or on purple: -1. First to 3 |
| Soccer Clash | Hold + release | Bottom team vs top team (2v2 with 4 players). Hold to run, release to kick |
| Turbo Racers | Hold = gas | Slot-car racing: brake before corners or you crash. 3 laps |
| Sumo Bump | Hold | Aim and dash. Knock everyone off the shrinking ring |
| Tank Battle | Hold + release | Hold to drive, release to fire bouncing shots. 3 hits and you're out |
| Penalty Kicks | Tap to shoot | Time your shot past the goalkeeper; bank shots off walls count |
| Grab the Fish | Tap to grab | Shoot your claw to grab goldfish; pufferfish cost a point. First to 3 |
| Hurdle Dash | Tap to jump | Auto-run down your lane and jump the hurdles (watch the doubles) |
| Chicken Flip | Tap to flip | Flip gravity between the walls before a gap reaches you |
| Neon Snake | Hold to turn | Don't crash into the glowing trails. Last snake alive wins |
| Snake Arena | Hold to turn | Eat stars to grow; heads that hit a body are out |
| Hot Bomb | Tap to pass | Throw the bomb away before it explodes in your hands |
| Paint Fight | Hold to move | Paint the most floor in 20 seconds; paint bombs splash big |
| Ping Pong | Tap to turn | Your paddle slides by itself; tap to reverse. Defend your edge |
| Tower Stack | Tap to drop | Stack blocks; overhang gets sliced off. First to 10 |
| Feed the Pigeon | Hold + release | Aim the slingshot, hold for power, lob bread onto the pigeon. First to 3 |
| Brain Blitz | Tap if true | Tap only when the equation is correct |
| Laser Jump | Tap to jump | Jump over the spinning laser. It speeds up and reverses |

**Movement (Sumo, Tanks, Soccer, Paint Fight):** your aim arrow turns while you're not pressing; hold to move
where it points. Every time you let go, the arrow turns the other way, so a small miss is fixed right away.
A small arc next to your character shows which way it is turning.

**Party Cup:** pick which games to include, then every round is a different game. The round winner gets a
point, a scoreboard (readable from both ends of the phone) shows the points and the next game, and the first to
3/5/7/10 points (Settings) is champion. **Mini Games:** play any single game.

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
