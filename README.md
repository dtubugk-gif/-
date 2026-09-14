# רקבון · Rikavon

חוסם אפליקציות, מעקב זמן מסך, וחיית מחמד דיגיטלית שנרקבת בזמן אמת.
Native Android, Kotlin + Jetpack Compose, minSdk 26, targetSdk 35. אפס רשת, אפס analytics.

The user sets daily limits for distracting apps. A pet on the home screen visibly decays as the day's usage
grows and recovers when the limits are kept. The decay *is* the feedback: no numbers, no graphs, no scolding.

---

## Build

```bash
# prerequisites: JDK 17+, Android SDK with platform 35 + build-tools 35.0.0
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest             # 60+ unit tests
./gradlew ktlintCheck detekt            # style + static analysis, both must be green
```

Kotlin warnings are errors (`allWarningsAsErrors = true`). Release signing and Play bundle: see [docs/RELEASE.md](docs/RELEASE.md).

---

## Architecture

```
:app                Application, MainActivity, navigation (shared-element host), home, onboarding,
                    settings, battery guide, privacy, premium, Glance widget, daily summary notification
:feature:blocker    enforcement engine, foreground service + overlay, adaptive polling, receivers/workers,
                    app picker, limit editor, schedules, statistics, score explainer
:feature:mascot     mascot plug-in system: manifest parser, registry, MascotView (Lottie + Compose),
                    gallery, achievements, sounds, bitmap renderer for widget/notification
:core:ui            theme (dark, flat, accent from the mascot), AnimationSpecs.kt, shared components
:core:data          Room (90-day history), DataStore settings, UsageStats source, pure domain logic,
                    repositories, backup, day rollover
```

MVVM + repositories, Hilt for DI, coroutines + Flow everywhere, no `GlobalScope`, no `SharedPreferences`.

### Design system

Material 3 structure with the design canvas's warm palette, Rubik and mascot art. One token system in
`:core:ui`; screens never hard-code a value that has a token.

| Token | Where | Value |
|---|---|---|
| Spacing | `theme/Tokens.kt` (`Spacing`) | 8dp grid: 4 / 8 / 12 / 16 / 24 / 32 / 48; screen margin 16; gap between targets 8 |
| Radius | `Radius`, `RikavonShapes` | 8 controls, 12 medium, 16 cards and rows, 20 hero cards, 28 bottom sheets, pills for buttons |
| Sizes | `Sizes` | touch 48, button 52, input 56, row 56 / 72, chip 40, icon 24, app icon 40 |
| Type | `theme/Type.kt`, `res/font/rubik_*.ttf` (OFL) | one family; 72 block headline, 56 score, 32 screen title, 22 collapsed title, 18 row title, **16 body** (Hebrew never below 16), 14 secondary, 12 caption; zero letter-spacing; body line-height 1.5 |
| Colour roles | `theme/Color.kt`, `theme/Theme.kt` | background `#131110`, surfaces `#161310 / #1d1a15 / #221e19`, text `#f4efe6` at 100 / 60 / 50 %, success `#7dc9a6`, danger `#c9564e`, over-limit `#e08b4f`; dark-only, verified ≥ 4.5:1 for body text |
| Per-mascot theme | `schemeFromAccent(accent, surfaceTint)` | accent = manifest `themeColor`; one primary colour, neutrals tinted toward `surfaceTint` at 7–17 % lightness |
| Block screen | `rotScheme()` | always rot-green `#a3b833` on `#0e120a`, whatever the pet |
| Motion | `anim/AnimationSpecs.kt` | micro 120 ms, component 220 ms, screen 320 ms, M3 emphasized easing, pressed scale 0.97, reduced-motion = fades only |

Components (`components/Common.kt`, `components/Design.kt`): `RikavonLargeTopBar` (32sp collapsing to 22sp with
`exitUntilCollapsed`), `RikavonTopBar` (back arrow mirrors in RTL), `RikavonBottomBar` (Material 3
`NavigationBar`, icon + 12sp label, pill indicator), `PrimaryButton` / `TonalButton` / `SecondaryButton` /
`LinkButton` (52 / 48dp, pill, 97 % press scale, 38 % disabled), `BottomActionBar` (editor actions pinned at the
bottom), `ListRow` (56–72dp, chevron mirrors), `GroupCard` + `SettingSwitchRow` / `SettingNavRow`, `SurfaceCard`,
`StatTile`, `ThinBar`, `SegmentPills` (M3 segmented buttons), `DotChip`, `ChoiceSheet` (`ModalBottomSheet`, 32×4
handle, 28dp radius) for single choices, `ConfirmDialog` for destructive actions only, `EmptyState` (icon + one
sentence + one action), `ErrorState` (what happened + what to do + action), `SkeletonBlock` / `SkeletonList`.

Every screen has its four states: normal, empty (an onboarding in disguise, with the one primary action),
loading (skeleton, never a blank surface) and error (permission missing → fix action). One filled action per
screen; destructive actions are outlined in the error colour and confirmed. Home / gallery / statistics /
settings sit behind the bottom bar (state saved per tab); everything else is pushed with a back arrow.
Edge-to-edge with transparent bars, platform splash (`core-splashscreen`, icon only), `LayoutDirection` from
`supportsRtl`, only `start` / `end` paddings, `Icons.AutoMirrored` for directional glyphs, haptics through
`LocalHapticFeedback`, and every icon button carries a `contentDescription`.

### Data flow

```
UsageStatsManager events ──► UsageSessionAggregator (pure, incremental) ──► UsageRepository.today (StateFlow)
                                                                                    │
LimitsRepository ───────────────────────────────────────────────────────────────────┼──► FocusScoreProvider
                                                                                    │        │
BlockerService loop (2s/5s/15s, off when screen off) ◄──────────────────────────────┘        ▼
   │  EnforcementEngine.evaluate(...) ──► BlockDecision?  ──► OverlayController (Compose in a system window)
   │                                                                                    MascotStage.fromScore
   └──► DayRolloverUseCase.runIfDue() every tick, on boot, at midnight, on app start          │
                                                                                     Home / Widget / Notification
```

### The core engine

* **Tracking** – `UsageStatsSource` reads `UsageEvents`; `UsageSessionAggregator` turns them into minutes and
  opens. Opens are counted on `ACTIVITY_RESUMED`; a resume within 2 s of the same package's previous resume
  or pause is a "return", not an open; in-app activity switches never count.
* **Enforcement** – `BlockerService` (foreground, `specialUse`) polls adaptively: 15 s normally, 5 s at 80 %
  of a limit, 2 s at 95 % / while a tracked app is on screen / while any app is blockable, nothing while the
  screen is off. The block overlay is a `ComposeView` in a `TYPE_APPLICATION_OVERLAY` window with its own
  lifecycle owner. No AccessibilityService.
* **Midnight** – correctness never depends on the alarm. Every process start, boot, service tick and worker
  run compares the stored rollover date with the local date and finalises missed days (`DailyResetPolicy`).
  An exact alarm just makes the refresh prompt.
* **Survival** – `BootReceiver`, `ServiceReviverWorker` (WorkManager, 15 min), `START_STICKY`, and a per-vendor
  battery guide (`BatteryGuideScreen` deep-links Xiaomi/Samsung/Huawei/OPPO/vivo/OnePlus settings).
* **Score** – `FocusScoreCalculator`: 50 % minutes vs limit, 30 % opens vs target, 20 % longest clean streak.
  The formula is shown to the user in the score explainer screen.

### Animations

Everything lives in `core/ui/.../anim/AnimationSpecs.kt`. `MascotView` implements:

| Requirement | Implementation |
|---|---|
| Idle loop, character per stage | One Lottie file per stage, 2–4 s loop, `IterateForever`; playback speed scales with stage |
| 800 ms morph between stages | `AnimatedContent` with fade + scale (`StageMorph`, emphasized easing); never a cut |
| Tap | squash & stretch spring + haptic + floating text that rises and fades |
| Long press | per-mascot `ReactionPreset` (pulse / wilt / flip / turn_away / glitch / roll) + sound |
| Block screen entrance | 1.2 s drop with overshoot and rotation (`BlockEntranceDrop`) |
| Score up / down | pop spring + `ParticleBurst` / dim fade + low tone |
| Shared element | `SharedTransitionLayout` in the nav host, key `MASCOT_SHARED_KEY` between home and gallery |
| Charts | `BarChart` grows each bar with a 40 ms stagger |
| Widget | `ViewFlipper` inside Glance with 400 ms fade in/out |
| Reduced motion | `LocalReducedMotion` (system animator scale or the in-app setting): no particles, no morph, fades only |
| Battery | idle stops when the lifecycle leaves RESUMED (screen off / background / overlay hidden) |

---

## Permissions

| Permission | Why | Without it |
|---|---|---|
| `PACKAGE_USAGE_STATS` | minutes and opens per app | no tracking, no blocking (limited mode) |
| `SYSTEM_ALERT_WINDOW` | the block screen | pet rots, nothing is blocked |
| `QUERY_ALL_PACKAGES` | the app picker lists launchable apps | the picker would be empty |
| `FOREGROUND_SERVICE` + `_SPECIAL_USE` | keep the enforcement loop alive | Android kills the loop |
| `POST_NOTIFICATIONS` (13+) | silent service notification, optional evening summary | service still runs, no summary |
| `SCHEDULE_EXACT_ALARM` | refresh at exactly 00:00 | reset happens on the next tick instead |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Doze exemption for the service | more OEM kills; the worker revives it |
| `RECEIVE_BOOT_COMPLETED` | restart after reboot | service starts on next app open |

There is **no** `INTERNET` permission.

---

## Adding a mascot (no code changes)

Create a folder under `feature/mascot/src/main/assets/mascots/<id>/` (or `app/src/main/assets/mascots/<id>/`;
assets are merged) containing:

```
manifest.json
stage_100.json  stage_80.json  stage_60.json  stage_40.json  stage_20.json  stage_0.json   (Lottie)
reaction.wav                                                                               (optional)
```

`manifest.json` schema (version 1):

```jsonc
{
  "schemaVersion": 1,
  "id": "cactus",                          // must equal the folder name
  "name": { "he": "קקטוס", "en": "Cactus" },
  "themeColor": "#7ED957",                 // seeds the whole app theme when selected
  "surfaceTint": "#8D94A3",                // optional: hue for backgrounds when it should differ from the accent
  "personality": "grumpy",                 // free text; known keys get a localised label
  "unlock": "free",                        // or { "achievement": "streak_7" } (see AchievementId keys)
  "reaction": "pulse",                     // pulse | wilt | flip | turn_away | glitch | roll
  "sound": "reaction.wav",
  "stages": {
    "100": { "asset": "stage_100.json", "texts": { "he": [8+ strings], "en": [8+ strings] } },
    "80":  { ... }, "60": { ... }, "40": { ... }, "20": { ... }, "0": { ... }
  },
  "blockMessages": { "he": { "morning": [2+], "day": [2+], "evening": [2+], "night": [2+] }, "en": { ... } },
  "summary": { "he": { "100": "...", "80": "...", "60": "...", "40": "...", "20": "...", "0": "..." }, "en": { ... } }
}
```

Rules enforced by `MascotManifestParser` at startup (invalid folders are skipped and listed at the bottom of
the gallery): all six stages present, ≥ 8 texts per stage in at least one language, ≥ 2 block messages per
time-of-day bucket, a summary for every stage, a known reaction preset, a parseable colour. A stage whose
Lottie file is missing falls back to the built-in animated `FallbackMascot` (never a static image).

`BuiltInMascotsTest` runs the parser over the real asset folders, so `./gradlew :feature:mascot:testDebugUnitTest`
validates a new folder before it reaches a device.

### Regenerating the built-in art

The six shipped mascots are the design-canvas SVGs (`tools/mascots/mascot_art.py`, verbatim) converted to
Lottie by `tools/mascots/svg_lottie.py` (paths, gradients, transforms → shape layers) and animated per stage
by the generator: three face variants (healthy / mid / rotten), mould, flies and stink from the rot-cycle
sheet; colours slide from `#dda94a` toward `#6f6428` as the score drops. Deterministic output:

```bash
python3 tools/mascots/generate_lottie.py   # 36 Lottie files, 200×200 @ 30 fps
python3 tools/mascots/generate_sounds.py   # reaction + score sounds (WAV)
```

Idle loops: healthy 3.2 s breathing, mid 2.4 s shallow breathing, rotten 2.4 s stepped twitch (the rotten
files are 9.6 s long so the two fly orbits and the stink lines loop seamlessly). Hand-drawn replacements are
specified in [docs/LOTTIE_ASSETS.md](docs/LOTTIE_ASSETS.md); drop them into the folder with the same names and
nothing else changes.

---

## Free tier and premium

`Tier.FREE`: 3 limited apps, 1 schedule, two mascots immediately (the rest are earned through achievements).
`Tier.PREMIUM`: no caps, every mascot. The only integration point is `BillingGateway`; this build ships
`NoBillingGateway`, so the purchase button is disabled with an explanation and there is no paywall anywhere.

## Privacy

Zero network, zero analytics, zero third-party SDKs. Data lives in Room/DataStore inside the app sandbox,
history is pruned after 90 days, backup is a JSON file the user writes through the system file picker.
Play-facing text: [docs/PLAY.md](docs/PLAY.md).
