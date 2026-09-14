# עותק אחד (OneCopy) – Android duplicate file cleaner

אפליקציית אנדרואיד שסורקת את הטלפון, מוצאת קבצים **זהים לחלוטין** (בית אחר בית),
מראה איפה כל עותק נמצא, מאפשרת לראות את הקובץ לפני המחיקה, ומוחקת את הכפילויות
בלחיצה אחת או אחת-אחת.

An Android app that scans the phone, finds **byte-for-byte identical** files, shows where
every copy lives, lets you preview a file before deleting it, and deletes duplicates in
one tap or one by one.

## מה האפליקציה עושה / Features

| | |
|---|---|
| סריקה | תמונות, סרטונים, אודיו, ובאופן אופציונלי מסמכים וכל שאר הקבצים (כולל כרטיס SD). |
| איתור | השוואת גודל ← hash מהיר של 64KB ← SHA-256 מלא. רק קבצים זהים לחלוטין נחשבים כפולים. |
| מטמון | תוצאות ה-hash נשמרות; סריקה חוזרת של קבצים שלא השתנו מהירה בהרבה. |
| תוצאות | קבוצות ממוינות לפי כמה מקום אפשר לפנות, עם תמונות ממוזערות, נתיב מלא, גודל ותאריך. סינון לפי סוג. |
| תצוגה מקדימה | לחיצה על קובץ פותחת מסך מלא: תמונה עם זום (צביטה / הקשה כפולה), פריים מהסרטון עם נגן, פרטי הקובץ וכל העותקים עם המיקום שלהם. אפשר לפתוח באפליקציה חיצונית. |
| בחירת עותק לשמירה | בכל קבוצה נשמר עותק אחד (★). ברירת המחדל: התמונה מ-DCIM/Camera, אחרת העותק הישן ביותר, ולא עותק מ-WhatsApp/Download. אפשר לשנות בלחיצה. |
| מחיקה | "מחק הכול" למסומנים, מחיקה של קבוצה שלמה מהתצוגה המקדימה, או פח לכל קובץ בנפרד. תמיד עם אישור. |
| שפות | עברית (RTL מלא) ואנגלית. מצב כהה. צבעים דינמיים באנדרואיד 12+. |

## הרשאות / Permissions

| הרשאה | למה |
|---|---|
| `READ_MEDIA_IMAGES / VIDEO / AUDIO` (אנדרואיד 13+) או `READ_EXTERNAL_STORAGE` (ישן יותר) | סריקת ספריית המדיה דרך MediaStore. |
| `MANAGE_EXTERNAL_STORAGE` "גישה לכל הקבצים" (אנדרואיד 11+, אופציונלי) | סריקת מסמכים, הורדות ותיקיות שה-media scanner מדלג עליהן, ומחיקה ישירה בלי חלון אישור מערכת על כל קבוצה. |

בלי "גישה לכל הקבצים" באנדרואיד 11+, המערכת מציגה חלון אישור אחד לכל מנת מחיקה
(עד 500 קבצים), כפי שגוגל דורשת. עם ההרשאה, המחיקה מיידית.

אין הרשאת אינטרנט. שום דבר לא יוצא מהטלפון.

## התקנה / Install

1. הורידו את ה-APK מה-Artifacts של GitHub Actions (או מ-Releases אם נוצר תג `v*`).
2. אם מותקנת הגרסה הישנה של "עותק אחד" (חתומה במפתח אחר) – **הסירו אותה קודם**, אחרת אנדרואיד יסרב להתקין ("App not installed").
3. אפשרו התקנה ממקורות לא ידועים ופתחו את הקובץ.

## בנייה / Build

```bash
./gradlew testDebugUnitTest assembleDebug    # unit tests + debug APK
./gradlew assembleRelease                    # minified release APK (R8)
```

* Kotlin 2.1, Jetpack Compose (Material 3), Coil, coroutines. `minSdk 26`, `targetSdk 35`.
* `keystore/debug.keystore` נמצא בריפו בכוונה כדי שכל בנייה (מקומית או CI) תהיה חתומה באותו מפתח
  ועדכונים יתקינו זה על זה. **לפני פרסום בחנות יש להחליף ב-keystore פרטי.**
* CI: `.github/workflows/android.yml` מריץ את הבדיקות, בונה debug + release ומעלה אותם כ-Artifacts.
  דחיפת תג `v1.2.3` יוצרת Release עם ה-APK מצורף.

## מבנה הקוד / Code layout

```
app/src/main/java/com/duplicatecleaner/app/
├── model/Models.kt              FileEntry, DuplicateGroup, ScanProgress (pure Kotlin)
├── scanner/DuplicateFinder.kt   size → quick hash → full SHA-256 (unit tested)
├── scanner/HashCache.kt         persisted hash memo
├── scanner/MediaStoreSource.kt  MediaStore enumeration (scoped storage)
├── scanner/FileSystemSource.kt  file-system walk (all files access)
├── scanner/StorageRoots.kt      internal storage + SD cards
├── delete/Deleter.kt            direct delete / MediaStore.createDeleteRequest / RecoverableSecurityException
├── Permissions.kt               API 26–35 permission matrix
└── ui/                          Compose: ScanScreen, ResultsScreen, PreviewDialog, AppViewModel
```

## מגבלות ידועות / Known limitations

* מזוהים רק עותקים **זהים לחלוטין**. תמונה שנשלחה ב-WhatsApp ונדחסה מחדש אינה זהה למקור
  ולא תזוהה. זיהוי "דומה" (perceptual hash) הוא שדרוג אפשרי.
* הסריקה רצה בתוך האפליקציה. אם המערכת סוגרת את האפליקציה ברקע במהלך סריקה ארוכה מאוד,
  צריך להתחיל מחדש (המטמון הופך את הסריקה החוזרת למהירה).
* המחיקה היא לצמיתות (לא לסל מחזור).
