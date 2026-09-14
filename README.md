# חיבור Claude Code ל-Unity MCP

הריפו הזה מוגדר כך שכשפותחים אותו ב-Claude Code **על המחשב המקומי** (ליד עורך Unity פתוח),
Claude מקבל אוטומטית:

1. **Unity MCP** – שליטה חיה בעורך: יצירה ועריכה של GameObjects, סצנות, נכסים, קריאת ה-Console והרצת C#.
2. **הפלאגין הרשמי של Unity ל-Claude Code** – 29 סקילים שכתבו מהנדסי Unity (UI Toolkit, uGUI, URP, 2D, Multiplayer, IAP, Localization ועוד).

> **חשוב:** MCP של Unity רץ מול עורך Unity שפתוח על המחשב שלך. אי אפשר להתחבר אליו
> מסשן Claude Code בענן (claude.ai/code) – הסביבה בענן לא רואה את המחשב שלך.
> ההגדרה כאן עובדת רק ב-Claude Code מקומי (טרמינל, אפליקציית Desktop, או תוסף IDE).

## דרישות

| רכיב | דרישה |
| --- | --- |
| Unity | Unity 6 (6000.0) ומעלה |
| חבילת Pipeline בפרויקט | `com.unity.pipeline` (מותקנת בפקודה אחת, ראו שלב 2) |
| Unity CLI | ערוץ beta (ראו שלב 1) |
| Claude Code | גרסה עדכנית שתומכת ב-`/plugin` |

## התקנה (פעם אחת)

### שלב 1 – התקנת Unity CLI

**Windows (PowerShell):**

```powershell
$env:UNITY_CLI_CHANNEL='beta'; irm https://public-cdn.cloud.unity3d.com/hub/prod/cli/install.ps1 | iex
```

**macOS / Linux:**

```bash
curl -fsSL https://public-cdn.cloud.unity3d.com/hub/prod/cli/install.sh | UNITY_CLI_CHANNEL=beta bash
```

פותחים טרמינל חדש ובודקים:

```bash
unity --version
```

### שלב 2 – הוספת חבילת Pipeline לפרויקט Unity

מריצים מתוך תיקיית פרויקט ה-Unity:

```bash
unity pipeline install
```

### שלב 3 – פתיחת הריפו ב-Claude Code

```bash
claude
```

בפעם הראשונה Claude Code יבקש אישור לשני דברים:

- **הפלאגין `unity@unity-agent-plugin`** – מוגדר ב-`.claude/settings.json`. מאשרים.
- **שרת ה-MCP `unity-editor-mcp`** – מוגדר ב-`.mcp.json` ומריץ `unity mcp`. מאשרים.

השם `unity-editor-mcp` זהה לשם ש-`unity mcp configure claude-code` כותב ברמת המשתמש,
כך שאם תריצו את הפקודה הזו בעצמכם לא תיווצר כפילות של כלים.

## בדיקה שהחיבור עובד

1. פותחים את פרויקט ה-Unity בעורך.
2. בטרמינל, מתוך תיקיית הפרויקט:

   ```bash
   unity status
   ```

   צריך להופיע מופע עורך במצב `ready`.
3. בתוך Claude Code מריצים `/mcp` – השרת `unity-editor-mcp` צריך להופיע כמחובר.
4. מקלידים `/unity:` – רשימת הסקילים של Unity צריכה להופיע.
5. פרומפט לבדיקה:

   > תקרא את הודעות ה-Console של Unity ותסכם שגיאות ואזהרות.

## פתרון תקלות

- **`unity status` לא מוצא עורך** – בודקים שהעורך פתוח ושהפרויקט מכיל את `com.unity.pipeline`.
- **העורך פתוח אבל אין חיבור** – כנראה **Safe Mode** בגלל שגיאות קומפילציה ב-C#.
  מריצים `unity pipeline list` לאימות, מתקנים את שגיאות הקומפילציה ומפעילים מחדש את Unity.
- **כמה עורכים פתוחים במקביל** – מוסיפים ל-`.mcp.json` את הנתיב לפרויקט:

  ```json
  "args": ["mcp", "--project-path", "C:/Projects/MyGame"]
  ```

- **הסקילים לא מופיעים** – `rm -rf ~/.claude/plugins/cache`, ואז `/reload-plugins`.
- **בסשן ענן (claude.ai/code) השרת מופיע כ-failed** – זה צפוי. בענן אין Unity CLI ואין עורך,
  ולכן `unity mcp` לא יכול לעלות שם. זה לא משפיע על העבודה המקומית.
- **השרת מחובר אבל אין כלים** – `unity mcp` עולה גם בלי עורך ומדווח 0 כלים. הכלים מופיעים רק
  אחרי שעורך עם `com.unity.pipeline` רץ ו-`unity status` מראה `ready`.

אומת מול Unity CLI 1.0.0-beta.9.

## חלופה ל-Unity ישן מ-6

הנתיב הרשמי דורש Unity 6. לפרויקטים ב-2021.3 עד 2022.3 LTS משתמשים ב-**MCP for Unity** של CoplayDev
(קוד פתוח): <https://github.com/CoplayDev/unity-mcp>. מתקינים דרך Package Manager ומריצים
Window > MCP for Unity > Auto-Setup. במקרה כזה מחליפים את התוכן של `.mcp.json` בהגדרה שה-Auto-Setup מייצר.

## מקורות

- Unity – פלאגין רשמי ל-Claude Code: <https://docs.unity.com/en-us/ai/unity-plugin/claude-code>
- Unity – Unity MCP Server: <https://unity.com/blog/unity-ai-mcp-how-to-get-started>
- Unity – ריפו הפלאגין: <https://github.com/Unity-Technologies/unity-agent-plugin>
