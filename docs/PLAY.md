# Google Play submission pack

## 0. Publishing checklist (do these in order)

1. **Google Play Console account** at play.google.com/console (one-time 25 USD). A personal account created
   after November 2023 must first run a **closed test with at least 12 testers opted in for 14 continuous
   days** and then apply for production access from the Console; plan two to three weeks for that.
2. **Create the app**: name "רקבון · Rikavon", default language Hebrew (he-IL) or English (en-US), "App",
   "Free". Once created, the app can never become paid; premium is in-app only anyway.
3. **Signing**: create the upload keystore and `keystore/release.properties` exactly as in
   [RELEASE.md](RELEASE.md), then in Console → Setup → App signing accept **Play App Signing** (Google keeps
   the app signing key, you keep the upload key). Back the keystore up in two places.
4. **Build the bundle**: bump `versionCode` (and `versionName`) in `app/build.gradle.kts`, run the quality
   gate, then `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
5. **Privacy policy URL**: host `docs/play/privacy-policy.html` anywhere public (GitHub Pages of this repo
   is enough: Settings → Pages → deploy from `docs/`, then the URL is
   `https://<user>.github.io/<repo>/play/privacy-policy.html`). Paste the URL under Policy → App content →
   Privacy policy. Play rejects the submission without it because the app asks for sensitive permissions.
6. **App content declarations** (Policy → App content), each with the text from section 2 below:
   Data safety (section 1), Ads ("No, my app does not contain ads"), App access ("All functionality is
   available without special access": no login; add a note that the block screen needs the "display over
   other apps" and "usage access" grants the app itself asks for), Content rating questionnaire (section
   5), Target audience (18 and over; the app is not designed for children), News app (No), COVID-19 (No),
   Government app (No), Financial features (None), Health (see section 5), Package visibility
   (QUERY_ALL_PACKAGES), Foreground service permissions (specialUse), Accessibility API usage, Device admin,
   Full-screen intent (Android 14 form), Photo and video permissions (not used).
7. **Store listing** (section 3) and **graphics** (section 4): icon 512×512, feature graphic 1024×500, at
   least two phone screenshots between 16:9 and 9:16 (the ones in `docs/play/screenshots/` are 720×1280,
   exactly 9:16; the developer screenshots in `docs/screenshots/` are 9:20 and will be **rejected**).
8. **Testing → Internal testing**: upload the bundle, add your own e-mail as a tester, install through the
   opt-in link, walk through onboarding on a real phone (usage access, overlay, accessibility, full-screen
   intent on Android 14, a call, a website block). Then **Closed testing** with the 12 testers.
9. **Production**: after production access is granted, promote the same bundle, add release notes
   (section 6), roll out to 100 %. Expect a review of a few days to a week because of the accessibility
   service and the sensitive permissions; the declarations in section 2 are what the reviewer reads.
10. After launch: watch Policy → Policy status and the pre-launch report (section 7); answer any appeal
    with the same wording as section 2.

## 1. Data Safety form

**Does your app collect or share any of the required user data types?** – No.

Rationale for every category:

| Category | Collected | Shared | Notes |
|---|---|---|---|
| Location | No | No | – |
| Personal info | No | No | No account, no name, no e-mail |
| Financial info | No | No | Premium (if wired) goes through Play Billing; the app never sees payment data |
| Health & fitness | No | No | – |
| Messages | No | No | – |
| Photos & videos | No | No | – |
| Audio files | No | No | – |
| Files & docs | No | No | Backup export/import uses the system file picker; the file is written by the user, not uploaded |
| Calendar | No | No | – |
| Contacts | No | No | – |
| App activity → *Installed apps* | **No** (on-device only) | No | The list of installed apps and their usage time is processed and stored **only on the device** and never leaves it. Play's definition of "collection" excludes on-device processing that is not transmitted off the device. |
| App activity → *Other user-generated content / actions* | No | No | Limits, schedules, achievements: on-device only |
| Web browsing | No | No | – |
| App info & performance | No | No | No crash reporting, no diagnostics SDK |
| Device or other IDs | No | No | – |

**Security practices**
- Data is encrypted in transit: not applicable (no transmission).
- Users can request data deletion: uninstalling deletes everything; there is no server copy.
- Independent security review: no.

**Privacy policy URL**: host the text of `PrivacyScreen` (strings `privacy_*`) at a public URL and paste it.

## 2. Permission declarations

### QUERY_ALL_PACKAGES (Play Console → App content → "Package visibility")

> Rikavon is a screen-time limiter. Its core, user-facing feature is letting the user choose which of their
> installed apps to limit and then measuring the time spent in those apps. The app picker must list every
> launchable app on the device; a `<queries>` allow-list is impossible because the set of distracting apps
> is different for every user and cannot be known in advance. The package list is displayed to the user only,
> stored locally, and never transmitted. Category: "Device search / app management / launcher-like
> functionality" → *app management: parental control / digital wellbeing*.

### SYSTEM_ALERT_WINDOW (display over other apps)

> When an app the user chose to limit exceeds its daily limit or falls inside a schedule the user configured,
> Rikavon shows a full-screen block reminder on top of that app and sends the app to the background. It
> appears only for apps the user explicitly selected, only after the user's own limit is reached, and has a
> single close button. The permission is requested during onboarding with a plain-language explanation and
> can be declined; the app then runs in a "limited mode" that tracks without blocking.

### AccessibilityService (Play Console → App content → "Accessibility API usage")

> Rikavon offers an optional "instant blocking" mode powered by an AccessibilityService that the user
> enables manually in system settings after an in-app disclosure page (onboarding step "Instant blocking",
> also reachable from Settings). Purpose: close an app the moment it opens when it is over the daily limit
> the user set for it or inside a schedule the user configured, like a parental control would; and, only
> when the user has added websites or feeds to their block list, back out of a blocked website in the
> browser and of the Shorts / Reels feed in YouTube / Instagram. The service subscribes to
> `TYPE_WINDOW_STATE_CHANGED` and `TYPE_WINDOW_CONTENT_CHANGED`, is marked `isAccessibilityTool="false"`,
> and reads content in exactly two narrow cases: the single address-bar view of a known browser (a fixed list
> of package names and view ids), matched against the user's own domain list; and the presence, by view id,
> of the Shorts / Reels player in YouTube / Instagram. It never reads any other view, never reads any other
> app, and stores or transmits nothing; there is no INTERNET permission. Actions used: `GLOBAL_ACTION_HOME`
> for a blocked app, `GLOBAL_ACTION_BACK` for a blocked page or feed. Prominent disclosure text (shown
> before the settings page opens): "Rikavon uses the service to see which app came to the front and send
> you home, and, only if you block websites or feeds, to read the address bar of known browsers and spot the
> Shorts / Reels player. It never reads anything else on your screen and never stores or sends it."
> The core feature is not gated on it: with the service off the enforcement loop polls every second;
> website and feed blocking are unavailable without it and say so.

### USE_FULL_SCREEN_INTENT

> Used for one feature the user switches on: an "incoming call" from their pet when an app they limited is
> at 95 % of its limit while on screen, or when a limit is reached and the app is blocked. The call-style
> notification carries a full-screen intent so it behaves like a real call on the lock screen. Android 14
> does not grant this by default to a screen-time app; the app checks `canUseFullScreenIntent()` and offers
> the system settings page from its own Settings, and falls back to a heads-up notification when the grant
> is missing. Never used for promotion or re-engagement.

### RECORD_AUDIO

> Used for talking to the pet: the conversation screen and the pet's calls. The first mic tap or the first
> call asks for the runtime permission and hands the audio to the device's own `SpeechRecognizer`; the app
> receives only the recognised text, never the audio, and stores neither. There is no INTERNET permission, so
> nothing can leave the app. Typing works without the permission, and speech recognition is skipped on devices
> without a recogniser.

### MODIFY_AUDIO_SETTINGS

> Used only during a call with the pet: the app sets the voice-call audio mode and switches between the
> earpiece and the loudspeaker so the call behaves like a normal phone call. It changes nothing outside the
> call and is released when the call ends.

### Device admin / profile owner (Play Console → App content → "Device admin")

> Rikavon can optionally create a work profile ("focus profile") of which it is the profile owner, started
> by the user from Settings through the system provisioning flow (`ACTION_PROVISION_MANAGED_PROFILE`). The
> `DeviceAdminReceiver` requests no device policies. Ownership is used for exactly one thing: greying out
> (suspending) the apps the user installed inside that profile while the user's own limit or schedule applies,
> and restoring them afterwards. No data leaves the device, no other policy is applied, and the user can
> remove the profile at any time from system settings.

### PACKAGE_USAGE_STATS

> Required to measure minutes and open counts for the apps the user limits (UsageStatsManager). Requested via
> `Settings.ACTION_USAGE_ACCESS_SETTINGS` with an explanation screen. Data is processed and stored on-device.

### Foreground service type `specialUse` (Play Console → App content → "Foreground service permissions")

> Type: `specialUse`. Purpose: user-configured screen-time enforcement. The service polls UsageStats every
> 2–15 seconds while the screen is on and shows the block overlay the moment a user-defined limit or schedule
> applies. It stops polling entirely when the screen is off. It runs only after the user has granted Usage
> Access and completed onboarding, and can be stopped from Settings. No other foreground service type
> (dataSync, mediaPlayback, …) describes this work.
> Demo video: record 30 s showing onboarding → limit set → overlay appearing → service notification.

### REQUEST_IGNORE_BATTERY_OPTIMIZATIONS

> Some manufacturers kill foreground services; the exemption keeps the enforcement loop alive. It is optional,
> explained on its own onboarding page, and the app functions without it.

## 3. Store listing

Character limits: title 30, short description 80, full description 4000. Both languages below fit.

### Hebrew (he-IL)

**Title**: רקבון: חוסם אפליקציות עם מחמד

**Short description**: מחמד שנרקב כשאתם גוללים יותר מדי. גבולות, חסימה, ושיחות ממנו. בלי רשת.

**Full description**:

רקבון הוא חוסם אפליקציות עם טוויסט: במקום גרפים ונזיפות, יש לכם מחמד. מוח ציני, עציץ דרמטי, דג זהב מבולבל,
חתול שיפוטי, רובוט ביורוקרטי או תפוח אדמה אדיש. כל אחד נרקב בדרך שלו, מול העיניים שלכם, ככל שאתם חורגים
מהגבולות שהגדרתם, ומתאושש כשאתם עומדים בהם.

וכשאתם ממשיכים בכל זאת, הוא מתקשר. שיחה אמיתית, כמו באפליקציית טלפון: הוא מדבר בקול, מקשיב לכם, ועונה.
אפשר לענות לו, להבטיח לו שתפסיקו, או לנתק. אפשר גם להתקשר אליו.

מה יש בפנים:
• גבול יומי לכל אפליקציה (5–240 דקות), חסימה מלאה, גבול פתיחות ביום וגבול לרצף אחד עם הפסקה כפויה
• "לחסום עכשיו": 15 דקות, שעה, 3 שעות או עד מחר, בלחיצה
• מסך חסימה שהמחמד נכנס אליו באנימציה, עם טיימר "נסה שוב בעוד…"
• שיחות והודעות מהמחמד: ב-80% מהגבול, בגבול, בכל חסימה, ובכל פתיחה של אפליקציה עם גבול (אפשר לכבות לכל אפליקציה)
• שיחה קולית דו-כיוונית בעברית ובאנגלית, בלי שרת: מנוע הדיבור של המכשיר ותסריט באישיות של המחמד
• חסימת אתרים בדפדפן, ו-Shorts ביוטיוב או Reels באינסטגרם לזמן שתבחרו
• לוחות זמנים: עבודה, לימודים, שינה או מותאם אישית
• נעילת הגדרות עם קוד, ומצב קפדני עם 10 שניות לחשוב
• ציון מיקוד גלוי ושקוף: 50% דקות, 30% פתיחות, 20% רצף נקי
• סטטיסטיקות ל-7 ו-30 יום, רצפים, 12 הישגים, מחמדים שנפתחים בהישגים
• ווידג׳ט למסך הבית, עברית ואנגלית, RTL מלא, גיבוי לקובץ מקומי

פרטיות: אפס רשת. לאפליקציה אין הרשאת אינטרנט בכלל. אין חשבון, אין אנליטיקס, אין SDK של צד שלישי.
כל הנתונים נשארים בטלפון ונמחקים אחרי 90 יום. שירות הנגישות אופציונלי, ורק אם חסמתם אתר או פיד הוא
קורא את שורת הכתובת בדפדפן או מזהה את נגן ה-Shorts / Reels, ולא שומר כלום.

הגרסה החינמית שלמה: 3 אפליקציות, לוח זמנים אחד, שני מחמדים מיידיים והשאר בהישגים. פרימיום מסיר את
המכסות. בלי מודעות, בלי פיוול, בלי תזכורות תשלום.

### English (en-US)

**Title**: Rikavon: app blocker with a pet

**Short description**: A pet that rots when you scroll too much. Limits, blocks, and calls from it. No network.

**Full description**:

Rikavon is an app blocker with a twist: instead of graphs and scolding, you get a pet. A cynical brain, a
dramatic plant, a confused goldfish, a judgmental cat, a bureaucratic robot or an indifferent potato. Each
one rots its own way, in front of your eyes, as you go past the limits you set, and recovers when you keep
them.

And when you keep going anyway, it calls. A real call, like the phone app: it talks out loud, listens to
you, and answers. You can talk back, promise to stop, or hang up. You can call it too.

What's inside:
• A daily limit per app (5–240 minutes), a full block, an opens-per-day cap and a one-sitting cap with a
  forced break
• "Block now": 15 minutes, an hour, 3 hours or until tomorrow, in one tap
• A block screen the pet drops into, with a "try again in…" timer
• Calls and messages from the pet: at 80 % of a limit, at the limit, on every block, and on every open of
  a limited app (you can switch that off per app)
• Two-way voice conversation in English and Hebrew, no server: the device's own speech engine and a script
  in the pet's personality
• Block websites in the browser, and YouTube Shorts or Instagram Reels for as long as you choose
• Schedules: work, study, sleep or custom
• A settings lock with a PIN, and a strict mode with ten seconds to think
• A visible, transparent focus score: 50 % minutes, 30 % opens, 20 % clean streak
• 7- and 30-day statistics, streaks, 12 achievements, pets unlocked by achievements
• A home-screen widget, English and Hebrew with full RTL, backup to a local file

Privacy: zero network. The app has no internet permission at all. No account, no analytics, no third-party
SDK. Everything stays on the phone and is deleted after 90 days. The accessibility service is optional, and
only if you block a website or a feed does it read the browser's address bar or look for the Shorts / Reels
player; it stores nothing.

The free tier is complete: 3 apps, one schedule, two pets at once and the rest through achievements.
Premium removes the caps. No ads, no paywall, no payment nags.

**Category**: Productivity (alternative: Health & Fitness → Digital wellbeing). **Tags**: productivity,
digital wellbeing, screen time. **Contact e-mail**: the developer account's e-mail. **Content rating**:
Everyone (section 5).

## 4. Graphics and screenshots

Everything here is generated from the real app by the screenshot harness, so it never drifts from it:

```bash
./gradlew :app:testDebugUnitTest --tests 'il.rikavon.screenshots.PlayAssetsTest' -Pscreenshots=true
```

| Asset | Play requirement | File |
|---|---|---|
| App icon | 512×512 PNG, 32-bit, under 1 MB; Play rounds the corners itself | `docs/play/icon_512.png` |
| Feature graphic | 1024×500 PNG or JPEG, under 15 MB | `docs/play/feature_graphic_en.png`, `docs/play/feature_graphic_he.png` |
| Phone screenshots | 2 to 8 per language, each side 320 to 3840 px, aspect between 16:9 and 9:16 | `docs/play/screenshots/en_*.png`, `he_*.png` (1080×1920, 9:16, captioned) |
| 7-inch / 10-inch tablet | optional; skip for the first release | – |

The captions on the screenshots are marketing copy and live in `PlayAssetsTest` (the `Copy` class), not in
the app's strings. Upload the Hebrew set (`he_*`) under the he-IL listing with `feature_graphic_he.png`, and
the English set (`en_*`) under en-US with `feature_graphic_en.png`. The files are numbered in the suggested
order: home, block screen, the pet's call, limit editor with "block now", websites & feeds, gallery,
statistics, talk.

## 5. Questionnaires: the answers

**Content rating (IARC)**: category "Utility, Productivity, Communication, or Other". Violence: none.
Sexuality: none. Language: none (the pet's lines are dry, not profane). Controlled substances: none.
User-generated content or user interaction: **no** (nothing is shared, there is no network). Location: no.
Purchases: digital goods (premium) if you wire billing, otherwise no. Result: Everyone / PEGI 3.

**Target audience**: 18 and over. Not designed for children; do not tick any under-13 group, which would
pull the app into the Families policy.

**Health**: the 2024 "Health apps" declaration lists categories; Rikavon fits none of the medical ones.
If the form lists "digital wellbeing / screen time" as a category, select it and nothing else. Otherwise
declare "My app does not have any health features".

**Data safety**: section 1 (every row "No"). Play's definition of collection excludes data that is processed
on the device and never transmitted, which is the whole app.

**App access**: "All or some functionality is restricted" → No. Add in the notes: "The block screen and the
statistics need the Usage access and Display over other apps grants, which the app requests in its own
onboarding; no login or credentials exist."

## 6. Release notes (500 characters each)

**he-IL**

גרסה ראשונה. מחמד שנרקב כשגוללים יותר מדי, ומתקשר אליכם בשיחה אמיתית כשממשיכים בכל זאת. גבולות יומיים,
חסימה מלאה, "לחסום עכשיו", גבול פתיחות וגבול רצף, לוחות זמנים, חסימת אתרים ו-Shorts / Reels, נעילה עם
קוד. אפס רשת: שום דבר לא יוצא מהטלפון.

**en-US**

First release. A pet that rots when you scroll too much, and calls you, a real call, when you keep going.
Daily limits, full blocks, "block now", an opens cap and a sitting cap, schedules, website and Shorts /
Reels blocking, a settings PIN. Zero network: nothing leaves the phone.

## 7. What to expect after upload

- **Pre-launch report** (automatic, on Firebase Test Lab devices): warnings about the accessibility service
  and "display over other apps" are normal. The robot cannot grant Usage access, so it will only see
  onboarding; that is fine.
- **Review**: the accessibility declaration is the one reviewers read closely. It must match the service's
  description string and the in-app disclosure word for word in spirit; all three were written together
  (section 2, `accessibility_service_description`, `onboarding_instant_body`).
- **Policy risks, in order**: (1) Accessibility API usage: the app is a digital-wellbeing blocker, an
  accepted use, as long as the disclosure stays accurate. (2) QUERY_ALL_PACKAGES: accepted for app
  blockers; keep the section 2 text. (3) USE_FULL_SCREEN_INTENT: Android 14 only grants it to calling
  and alarm apps by default; the app asks the user for it and falls back to a heads-up notification.
  (4) Foreground service `specialUse`: keep the property text in the manifest in sync with section 2.
- **Rejection**: appeal from the Policy status page with the section 2 paragraph for that permission; do
  not change the app between appeals unless the reason says to.

## 8. Before every later release

1. Bump `versionCode`; `versionName` follows semver.
2. `./gradlew ktlintCheck detekt testDebugUnitTest :app:lintRelease :app:bundleRelease`.
3. Regenerate screenshots and Play assets if any screen changed (section 4).
4. If a permission or the accessibility behaviour changed, update section 2, the service description
   string, the in-app disclosure and the privacy page together, then the declarations in the Console.
