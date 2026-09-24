# Handoff for a second Claude session: find bugs and improve the app

מסמך זה נכתב על ידי הסשן שבנה את האפליקציה, עבור סשן נוסף שהמשתמש חיבר לאותו פרויקט.
המשימה שלך: **למצוא באגים ובעיות בקוד, ולשפר את האפליקציה.** דבר עם המשתמש בעברית.

Branch: `claude/dynamic-island-app-ms7wyf` (all work, mine and yours, goes there).

## What the app is

"אי דינמי": an iPhone-style Dynamic Island overlay for Samsung Galaxy (One UI 7/8, Android 15/16;
minSdk 28, targetSdk 35). Kotlin, Compose for the settings screen, a custom View for the island.
Everything runs in one process on the main thread. The user sideloads the APK and cannot debug.
**Nothing has been tested on a real device yet.** All verification so far: Paparazzi snapshot
rendering, JVM unit tests, and adversarial code reviews. Treat every runtime assumption as unverified.

## Key files (`app/src/main/java/io/github/dtubugk/island/`)

- `island/IslandService.kt` — AccessibilityService. Hosts the overlay window
  (`TYPE_ACCESSIBILITY_OVERLAY`, centered on the camera cutout from `DisplayCutout`; the window is
  resized between resting and animating sizes). Receivers: screen on/off, battery, power, ringer,
  DND, speakerphone/mic mute, power save; `AudioDeviceCallback` for headphones; `TorchCallback`;
  `ConnectivityManager` callback for VPN. Reads ONLY the SystemUI status bar (service config
  `packageNames=com.android.systemui`) to find Samsung's status-bar chip for the current song or
  caller and stretch the island over it. Quick actions in `act()`: flashlight, DND (through the
  notification listener's `requestInterruptionFilter`), screenshot/lock (global actions),
  speaker/mute (`setCommunicationDevice` / `isMicrophoneMute`, only during a call), airplane mode
  (opens the quick panel with `GLOBAL_ACTION_QUICK_SETTINGS`, finds the tile by label, touches it
  with `dispatchGesture`, confirms One UI's "Turn on?" dialog, closes the panel; writes a report to
  `filesDir/airplane-report.txt`).
- `island/IslandNotificationListener.kt` — NotificationListenerService: media sessions (every
  active controller is watched; artwork + Palette accent), live activities from notifications
  (CALL; ALARM only when ongoing or full-screen; NAVIGATION by category or Maps/Waze packages;
  RECORDING by recorder package + chronometer; TIMER by chronometer; PROGRESS by determinate
  progress or Android 16 `android.requestPromotedOngoing`), message peeks (dedupe by key + `when` +
  title/text hash; `FLAG_ONLY_ALERT_ONCE` special-cased for chats), hotspot from system ongoing
  notifications.
- `island/IslandDirector.kt` — the state machine. `candidates()` lists concurrent activities in
  priority order (call, alarm, standing API islands, music, navigation, recording, timer,
  progress); `selectedKey` = the user's tab/swipe choice; expanded card vs peek (queued notices,
  max 3) vs compact vs idle; auto-close timers armed per state (`armedFor`, `rearm()` on deliberate
  changes); "try it" demos; snooze (swipe-up hide, 60 s); taps and gestures; Custom API islands.
- `island/IslandView.kt` — springs (`Spring.kt`) for width/height/radius/offset/shadow/press/shown;
  layered scene crossfades; `GestureDetector`: tap, double tap (opens the app of what was shown at
  the FIRST tap), long press, fling/scroll up/down/sideways; opacity applies to card bodies only.
- `island/Scenes.kt` — every scene: `IdleScene`, `InfoScene` (quick-actions row), `NoticeScene`,
  `MediaCompactScene`/`MediaCardScene`, `LiveCompactScene`/`LiveCardScene` (tabs for concurrent
  activities, ETA, progress ring/bar, call tools row), `MessageScene`, `CustomCardScene`.
  `IslandPainter.kt` draws (Rubik font, marquee, waveform, battery). `IslandGeometry.kt` computes
  shapes (always covers the camera; `covering()` stretches over the chip).
- `island/LiveState.kt` — data classes + `LiveBus` flows between the listener and the service.
  `island/IslandApiReceiver.kt` — exported broadcast API (`io.github.dtubugk.island.SHOW/HIDE`,
  opt-in in the settings).
- `data/IslandSettings.kt` — SharedPreferences + StateFlow config. `ui/IslandScreen.kt`,
  `ui/Theme.kt` — Hebrew RTL Compose settings with a live preview (an `IslandView` +
  `IslandDirector` inside `AndroidView`), Material You on Android 12+. `MainActivity.kt`.
- `README.md` documents every feature in Hebrew, including the API with a Tasker example.

## Build and test

```bash
./gradlew :app:testDebugUnitTest      # geometry/spring unit tests + Paparazzi snapshots
./gradlew :app:recordPaparazziDebug   # re-record snapshots (committed under app/src/test/snapshots/images)
./gradlew :app:assembleRelease        # signed APK; signing/island.keystore is committed on purpose
```

GitHub Actions builds every push and publishes the APK to the release tag `dynamic-island`.
There is no INTERNET permission: keep it that way. If Maven Central answers 429 in your sandbox, a
`~/.gradle/init.d` init script that rewrites `repo.maven.apache.org` / `repo1.maven.org` to
`https://maven-central.storage-download.googleapis.com/maven2/` fixes it.

## Rules to keep

- Hebrew UI, RTL, body text ≥ 16 sp, Rubik font; the island is pure black (`#000000`) with white or
  accent content.
- Don't change the package name, the signing key, the CI workflow, or add permissions the user
  hasn't been told about.
- Small atomic commits with clear messages. Run the tests and `assembleRelease` before every push.
  `git pull --rebase` before pushing: I keep pushing to the same branch.

## What I (the first session) am doing right now — avoid overlapping edits

Competitor-parity features (dynamicSpot / Notch Screen View): per-app notification blacklist,
notification action buttons + quick reply (`RemoteInput`) in the message card, auto-hide in
fullscreen apps, show-on-lock-screen toggle, notification stacking with a count, pop-up duration
setting, corner radius, border style, animation speed, landscape toggle, screenshot notice. These
touch `IslandDirector.kt`, `Scenes.kt`, `IslandNotificationListener.kt`, `IslandSettings.kt`,
`IslandScreen.kt` heavily. If you must edit those files, keep the changes surgical and say so in
the commit message.

## Known open problems (reported by the user on a real Galaxy)

1. The island "disappeared" once: either the update turned the accessibility service off, or the
   swipe-up hide fired. I made the hide harder to trigger and the app now wakes the island when
   opened. Unverified.
2. The airplane-mode quick action opened a settings menu instead of toggling: the tile wasn't found
   in the quick panel. Rewritten (real touches, pulling the panel open, scrolling, confirming the
   dialog, a diagnostic report). Unverified. If you can reason about what One UI 7/8's quick panel
   exposes to accessibility services, that is the highest-value thing to check.
3. Speaker/mute during calls rely on `setCommunicationDevice` from a non-dialer app; Samsung's
   dialer may refuse it.

## Where I'd start a bug hunt

- `IslandService`: window sizing/offset math when the island stretches off-center (`offsetX`) and on
  rotation; `FLAG_NOT_TOUCHABLE` toggling; chip scans (throttled to 400 ms) on the main thread;
  `getWindows()` assumptions on One UI.
- `IslandNotificationListener`: message dedupe against real WhatsApp/Telegram update patterns; media
  session switching; artwork memory.
- `IslandDirector`: timer arming (`armedFor`): any path that leaves a card open forever or closes it
  wrongly; queue starvation; demos vs real content; snooze.
- `IslandView` gestures: double-tap flag lifetimes, swipe thresholds near the top edge, long-press vs
  double-tap-and-hold.
- API-level guards (minSdk 28): anything that only exists on 30/31/33.
- Performance: per-frame allocations while music plays (30 fps), invalidate loops, leaks in the
  Compose preview.

Skip style nits; runtime correctness and battery matter most. When you've pushed fixes, add a short
"## Log" entry at the bottom of this file (date, one paragraph of what changed) so the other session
can rebase cleanly.

## Log

- 2026-09-24 — first session: app built, reviewed and pushed; this handoff written.
