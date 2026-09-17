# רקבון · Rikavon

App blocker, screen-time tracking, and a digital pet that rots in real time.
Native Android, Kotlin + Jetpack Compose, minSdk 26, targetSdk 35. Zero analytics; no network unless the
optional AI brain or realistic voice is switched on with the user's own keys.
UI in English by default; Hebrew (full RTL) is one tap away on the welcome screen or in Settings.

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
| Motion | `anim/AnimationSpecs.kt`, `anim/Motion.kt` | micro 120 ms, component 220 ms, screen 320 ms, M3 emphasized easing, pressed scale 0.97; helpers `enterFromBelow` (staggered rows, 35 ms × index, capped), `popIn`, `floatLoop`, `bounceOn`, `AnimatedNumber`, `FadeThrough`, `pageTransform`, `tickTransform`; reduced-motion = fades only |

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
Choreography (all transform + opacity, every timing in `AnimationSpecs`): bottom-bar tabs fade-through
(fade + scale 0.96 → 1, 240 ms), pushed screens slide in from the end and back out with `slideIntoContainer`
so the direction mirrors in RTL, list rows rise in with a capped stagger, the home score counts to its value
while its colour and bar follow, the mascot floats and its speech bubble fades through, the block screen's
headline lands with a spring punch and its retry clock ticks by sliding, onboarding pages slide by direction
with an animated progress bar, empty-state icons pop in, the navigation icon bounces on selection, skeletons
carry a shimmer sweep. Reduced motion (system or in-app) turns all of it into short fades.

Languages: English resources are the default (`res/values`), Hebrew lives in `res/values-iw`; the welcome
page offers English / עברית and Settings has the full choice (System / Hebrew / English) through
AppCompat per-app locales, so layout direction flips with the language.

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
  of a limit, 2 s at 95 % / while a tracked app is on screen, 1 s while any app is blockable and the instant
  path is off, nothing while the screen is off. The block overlay is a `ComposeView` in a
  `TYPE_APPLICATION_OVERLAY` window with its own lifecycle owner. A block is hard: the moment the overlay is
  attached the blocked app is sent home (so nothing keeps playing behind it) and the overlay stays until the
  user closes it.
* **Instant path (optional)** – `BlockerAccessibilityService` gets window-state events and, when a package
  in `InstantBlockBus.blocked` comes to the front, performs `GLOBAL_ACTION_HOME` at once and asks the service
  for the overlay. This is the closest a third-party app can get to Family Link: Android reserves package
  suspension (greyed-out icons) for device owners and system apps. Without the service the 1 s poll takes
  over. The same service is the only place content is ever looked at, and only when the user has blocked
  websites or feeds: in a known browser it reads the one address-bar view and matches the host against the
  block list; in YouTube / Instagram it checks for the Shorts / Reels player views by id. A hit backs out
  (BACK) and shows the block card. Nothing is read in any other app, nothing is stored.
* **Block now, opens per day, one sitting** – three more knobs on a limit. "Block now" writes a
  `PauseRepository` entry (15 min, 1 h, 3 h, until tomorrow) that the engine honours ahead of the daily
  limit. `maxOpens` blocks the open after the last allowed one until midnight. `sessionMinutes` blocks a
  sitting that runs past it for a ten-minute break, written down as a `BREAK` pause so reopening does not
  reset it. Tightening is instant; loosening goes through the settings lock.
* **Websites and feeds** – `BlockedSitesRepository` holds bare domains (a domain covers its subdomains);
  `FeedBlockRepository` holds YouTube Shorts / Instagram Reels with an expiry the user picks (1 h, 3 h, until
  tomorrow, until switched off). Both are opt-in and need the instant path.
* **Nudge inside apps** – `UsageReminderPolicy`: every N minutes (10 / 15 / 30) of one sitting in a limited
  app, one message from the pet. A sitting starts at the app's last counted open.
* **Settings lock** – an optional PIN (`PinVerifier`, salted SHA-256, never exported) that `PinGate` asks for
  before anything that gives more room: loosening a limit, lifting a block, switching tracking, strict mode,
  calls or the breathing pause off, and removing the lock itself. Then the strict-mode delay, if that is on.
  A speed bump for one's own weaker moments, not a security boundary.
* **Breathing pause** – saving a changed limit arms `BreathingGateRepository` for that package; the next time
  it comes to the front (poll or accessibility event) `BreathingOverlayContent` sits over it for ten seconds
  (circle swelling with a four-second breath, "breathe in / out", countdown) before the way in opens.
  Leaving keeps the pause armed. Off switch in Settings.
* **The pet calls you** – a block is a phone call. When calls are on (`PetContactPolicy` also messages at
  80 %, and rings once at 95 % while the app is on screen), reaching a limit rings instead of showing the
  block card: `PetContactNotifier` rings inside a window over the blocked app (`OverlayController.showCall`,
  the same mechanism as the block screen, so the system cannot refuse it; `CallRinger` plays the device
  ringtone and vibrates, honouring silent and vibrate mode), posts a call-style notification with a
  full-screen intent for the shade and the lock screen, and sends the app home. Answering opens
  `PetCallActivity`; a ring that is declined or left unanswered turns into the block screen. Without
  "display over other apps", or with the phone locked, the call activity is started directly instead. It is a
  real, two-way call, not a
  monologue: `VoiceCallSession` speaks the pet's line, listens through the device recogniser, answers what it
  heard, and loops until someone hangs up. Silence gets a retry, then a nudge, then a goodbye; saying "bye"
  ends it, and a promise ("I'll stop", said or pressed) ends it and sends you home. `CallAudioRoute` gives it
  the voice-call audio mode with an earpiece / speaker switch and screen-off proximity. The screen is the
  phone app's: a pulsing avatar, a live caption of both sides, and round mute / speaker / keyboard / end
  buttons. Two switches in Settings; Android 14+ asks once for full-screen notifications.
* **The pet begs on every open** – `callOnOpen` on a limit, on by default for every limited app (the switch
  at the top of the limit editor turns it off per app): the moment that app comes to the front (the instant
  path, or `OpenDetector` seeing the poll's foreground move there from somewhere else; returning from the
  pet's own call does not count) the pet rings with a plea, "No. No no no. Please. I don't want to rot. Let
  go of it.", whatever the limit says, at most once a minute per app. A promise ends the call and goes home;
  hanging up leaves the app open. Every call also leaves a message in the shade asking to stop, so a declined
  or missed call still says what it wanted; with calls switched off, the message alone is sent. While any
  app is begged about and the instant path is off, polling runs every 3 s.
* **You call the pet** – the phone button on Home dials the pet (`PetCallActivity.outgoing`): it rings for a
  moment, the pet picks up and greets you, and the same spoken conversation runs. The keyboard button drops
  to `TalkScreen`, the typed side of the same engine, for a quiet room or a phone with no recogniser.
* **What the pet says** – `ScriptedConversation`, an offline engine in `:feature:mascot`. `TalkScript` maps
  what you said to an intent (greeting, "give me more time", a promise, an insult, "why am I blocked", the
  score, thanks, love, goodbye, silence) in Hebrew and English and answers in the pet's personality with
  today's numbers filled in (score, stage, the app closest to its limit and its minutes left). Nothing leaves
  the device and nothing is recorded; `ConversationEngine` is the seam the AI brain plugs into.
* **The AI brain (optional)** – `ClaudeConversation` in `:feature:mascot`: with an Anthropic API key entered
  in Settings (`AiSettingsRepository`, outside `Settings` and backups), every line is written fresh by
  `claude-haiku-4-5` (the fastest current model, because the reply is spoken inside a conversational pause)
  from `PetPrompt`: the rules first (one or two spoken sentences, in character, never repeat, brackets are
  stage directions), then the pet, its stage line, the score, the app and its minutes, and the language. The
  last twelve turns ride along as memory. `ClaudeBrain` calls the official Java SDK on the IO dispatcher with
  an 8 s timeout and one retry; any error, refusal or empty answer falls back to the script for that line and
  the conversation carries on. `ConversationEngines` picks the engine per conversation, so a call and the
  talk screen each keep their own short memory.
* **The realistic voice (optional)** – with an ElevenLabs API key entered in Settings (`CloudKeysRepository`,
  the same place as the brain's key), `MascotVoice` speaks through `ElevenLabsSynthesizer` instead of the
  device engine: `eleven_flash_v2_5` (Hebrew and English, low latency), the voice taken from the account's own
  list (`VoiceCatalog` reads `GET /v1/voices` once per key: the user's pick for this pet from the Settings
  dialog, else the manifest's `voice.neural`, else the premade voice whose gender and age fit the personality;
  a free plan may only call the voices in its own list, never an arbitrary library voice), the pet's pace
  mapped to the voice's speed, and each clip played by `ClipPlayer` on the same media / voice-call attributes
  as before. The dialog's "Test the voice" makes one real request and shows a refusal verbatim.
  The next line is synthesised while the current one plays; the first clip that fails hands the rest of the
  lines to the device engine, so the pet is never silent for want of a network. Only the pet's own lines
  travel; nothing the user says does.
* **Focus profile (optional)** – the Family Link behaviour itself, for apps the user installs inside a work
  profile that Rikavon owns. `FocusProfileAdminReceiver` is the profile owner (no device policies),
  `FocusProfileComplianceActivity` answers the Android 12+ provisioning handshake, and inside the profile the
  same enforcement loop calls `FocusProfileManager.applySuspension`, which greys out blocked apps with
  `DevicePolicyManager.setPackagesSuspended` and restores them at midnight or when the limit is removed. A
  suspended app never launches; tapping its icon shows the system dialog with our support message. Personal
  copies of apps cannot be suspended by anyone but the system. A work profile starts with system apps
  switched off, so pre-installed apps such as YouTube are offered for `enableSystemApp` from the profile's
  settings (a curated list plus anything the vendor tagged video / social / game / news / audio).
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
| `BIND_ACCESSIBILITY_SERVICE` (optional, user-enabled) | a blocked app is sent home the instant its window appears | the 1 s poll catches it instead |
| `BIND_DEVICE_ADMIN` (optional, profile owner of the focus profile) | grey out blocked apps installed inside the focus profile | overlay and instant path only |
| `QUERY_ALL_PACKAGES` | the app picker lists launchable apps | the picker would be empty |
| `FOREGROUND_SERVICE` + `_SPECIAL_USE` | keep the enforcement loop alive | Android kills the loop |
| `POST_NOTIFICATIONS` (13+) | silent service notification, optional evening summary, the pet's messages and calls | service still runs, no summary, no messages |
| `USE_FULL_SCREEN_INTENT` (user grant on 14+) | the pet's call takes over the screen like a real one | the call is a heads-up notification instead |
| `VIBRATE` | the call rings | silent ring |
| `RECORD_AUDIO` (asked on the first mic tap or call) | the device's speech recogniser hears what you say to the pet; nothing is stored | type to the pet instead |
| `MODIFY_AUDIO_SETTINGS` | the call uses the voice-call audio mode and the earpiece / speaker switch | the voice plays on the media stream |
| `SCHEDULE_EXACT_ALARM` | refresh at exactly 00:00 | reset happens on the next tick instead |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Doze exemption for the service | more OEM kills; the worker revives it |
| `RECEIVE_BOOT_COMPLETED` | restart after reboot | service starts on next app open |

`INTERNET` is declared only for the optional AI brain and realistic voice (Settings → Pet, the user's own
Anthropic and ElevenLabs keys); nothing touches the network without them.

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
  "sound": "reaction.wav",                 // optional short vocalisation for the long-press reaction
  "voice": { "pitch": 0.8, "rate": 0.8 },  // optional text-to-speech character (0.5–2.0); defaults follow the personality
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

### Voices and sounds

Every pet talks. `MascotVoice` wraps the device's own text-to-speech engine (nothing leaves the phone; if the
engine or the language pack is missing the line is simply not spoken) and applies the mascot's `voice`
profile: the cynical brain is low and slow, the dramatic plant high and drawn out, the confused goldfish
fast and squeaky, the judgmental cat high, the bureaucratic robot flat and quick, the indifferent potato low
and lazy. Lines are spoken when the pet is tapped on the home screen or in the gallery and when the block
screen appears, in the UI language, gated by the *Sounds* and *Voice* switches in Settings and muted with
the ringer. The long-press reaction plays `reaction.wav`, a synthesised vocalisation in the same character
(`tools/mascots/generate_sounds.py`: a glottal pulse through two formant resonators, "hmph", a sigh, two
blubs, "mrrow", two beeps, "meh").

### Regenerating the built-in art

The six shipped mascots are the design-canvas SVGs (`tools/mascots/mascot_art.py`, verbatim) converted to
Lottie by `tools/mascots/svg_lottie.py` (paths, gradients, transforms → shape layers) and animated per stage
by the generator: three face variants (healthy / mid / rotten) shared from the rot-cycle sheet, and then
**each mascot decays its own way**. The potato sprouts, moulds, leaks and draws flies; the brain goes
grey-violet, its gyri smooth out, the lobes sag and it bruises (never any flies); the cat's fur goes to ash,
the ears flatten, the coat mats into clumps and loose hair drifts off; the plant's leaves droop and dry to
straw, the soil moulds, the pot cracks and leaves fall; the goldfish's water goes green then brown, the fish
floats belly-up and bobs under scum and rising algae; the robot rusts, its antenna bends, the screen cracks,
one eye dies and it sparks and smokes. Each has its own rot palette and its own rotten idle (twitch, wobble,
shiver, sway, bob, glitch). Deterministic output:

```bash
python3 tools/mascots/generate_lottie.py   # 36 Lottie files, 200×200 @ 30 fps
python3 tools/mascots/generate_sounds.py   # reaction + score sounds (WAV)
```

Shape lists in Lottie render top-down (index 0 on top) while SVG paints bottom-up, so the generator reverses
every list of drawables (`lottie_order`); faces, mould and flies sit above the body exactly as on the canvas.

Idle loops: healthy 3.2 s breathing, mid 2.4 s shallow breathing, rotten 2.4 s of the mascot's signature
motion (the rotten files are 9.6 s long so fly orbits, drips, wisps and falling leaves loop seamlessly).
Hand-drawn replacements are
specified in [docs/LOTTIE_ASSETS.md](docs/LOTTIE_ASSETS.md); drop them into the folder with the same names and
nothing else changes.

---

## Free tier and premium

`Tier.FREE`: 3 limited apps, 1 schedule, two mascots immediately (the rest are earned through achievements).
`Tier.PREMIUM`: no caps, every mascot. The only integration point is `BillingGateway`; this build ships
`NoBillingGateway`, so the purchase button is disabled with an explanation and there is no paywall anywhere.

## Privacy

Zero analytics, zero third-party trackers, and no network unless the AI brain or the realistic voice is
switched on with the user's own keys (then what they say to the pet and its context go to Anthropic's API,
and the pet's own lines go to ElevenLabs to become sound; nothing else). Data lives in
Room/DataStore inside the app sandbox,
history is pruned after 90 days, backup is a JSON file the user writes through the system file picker.
Play-facing text, the publishing checklist and the generated store graphics (`docs/play/`): [docs/PLAY.md](docs/PLAY.md).
