# Google Play submission pack

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
> Rikavon shows a full-screen block reminder on top of that app. The overlay is the only enforcement mechanism
> (the app deliberately does not use an AccessibilityService). It appears only for apps the user explicitly
> selected, only after the user's own limit is reached, and has a single close button. The permission is
> requested during onboarding with a plain-language explanation and can be declined; the app then runs in a
> "limited mode" that tracks without blocking.

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

**Title (30)**: רקבון – חוסם אפליקציות עם מחמד

**Short description (80)**:
חיית מחמד שנרקבת כשאתם גוללים יותר מדי. גבולות, חסימה, בלי נזיפות.

**Full description (he)**:

רקבון הוא חוסם אפליקציות עם טוויסט: במקום גרפים ונזיפות, יש לכם מחמד. מוח ציני, עציץ דרמטי, דג זהב מבולבל,
חתול שיפוטי, רובוט ביורוקרטי או תפוח אדמה אדיש. כל אחד מהם נרקב מול העיניים שלכם ככל שאתם חורגים מהגבולות
שהגדרתם, ומתאושש כשאתם עומדים בהם.

מה יש בפנים:
• גבול יומי לכל אפליקציה (5–240 דקות) או חסימה מלאה
• מסך חסימה שהמחמד נכנס אליו באנימציה, עם טיימר "נסה שוב בעוד…"
• לוחות זמנים: עבודה, לימודים, שינה או מותאם אישית
• ציון מיקוד גלוי ושקוף: 50% דקות, 30% פתיחות, 20% רצף נקי
• סטטיסטיקות ל-7 ו-30 יום, שעות שיא, השוואה לשבוע הקודם
• רצפים, 12 הישגים, ומחמדים שנפתחים בהישגים
• ווידג׳ט למסך הבית בשני גדלים
• מצב קשוח: 10 שניות לפני כל כיבוי גבול. חינם.
• עברית ואנגלית, RTL מלא, נגישות
• גיבוי ושחזור מקובץ מקומי

פרטיות: אפס רשת. לאפליקציה אין הרשאת אינטרנט בכלל. אין חשבון, אין אנליטיקס, אין SDK של צד שלישי.
כל הנתונים נשארים בטלפון ונמחקים אחרי 90 יום.

הגרסה החינמית שלמה: 3 אפליקציות, לוח זמנים אחד, שני מחמדים מיידיים והשאר בהישגים. פרימיום מסיר את המכסות.
בלי מודעות, בלי פיוול, בלי תזכורות תשלום.

**Full description (en)**: translate the above; keep the structure.

**Category**: Productivity (alternative: Health & Fitness → Digital wellbeing). **Content rating**: Everyone.

## 4. Screenshots to capture (phone, 1080×2400, dark)

1. Home (the pet's room, design 1a) – healthy potato, speech bubble, score 100 in green, "היום" rows with thin
   bars, streak pill, bottom bar. (RTL, Hebrew)
2. Home – rotten potato with flies, score in red, one app row orange over its limit, "next limit" line.
3. Block screen (1c) – "די." headline, twitching mascot, bold reason + the pet's line, retry timer card, green pill.
4. Gallery (1d) – hero card "נבחר עכשיו", 3-column grid, two locked cards with the achievement badge.
5. Gallery – stage slider dragged to "rotting" so the hero shows the rot with flies.
6. Limit editor – slider at 45 min, full-block switch, today's progress bar.
7. Schedule editor – "sleep" preset, days chips, time fields, app checklist.
8. Statistics (1e) – range pills, daily chart with "היום" highlighted, "−18% משבוע שעבר", the three tiles.
9. Score explainer – today's breakdown card.
10. Settings – strict mode, summary hour slider, reduce-motion chips, language chips.
11. Onboarding – usage-access page with the wilted mascot.
12. Widget (1f) – the compact row and the full card with the quote and the five-segment bar on one home screen.

Plus the same 12 in English for the en-US listing, and one feature graphic (1024×500): the six mascots
in a row, healthy on the left fading to rotten on the right, dark background, app name in both languages.
