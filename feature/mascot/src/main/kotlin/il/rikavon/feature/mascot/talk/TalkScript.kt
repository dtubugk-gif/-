package il.rikavon.feature.mascot.talk

/** What the user meant, as far as a keyword script can tell. */
enum class TalkIntent {
    GREETING,
    HOW_ARE_YOU,
    MORE_TIME,
    PROMISE,
    INSULT,
    SCORE,
    WHY_BLOCKED,
    THANKS,
    BYE,
    LOVE,

    /** Nothing came back on the line; never matched from text. */
    SILENCE,
    UNKNOWN,
}

/**
 * The pet's side of a conversation, in English and Hebrew, without a network. Keyword intents in both
 * languages, shared lines for the plain intents, and one voice per personality for the ones that matter:
 * begging for more time, insults, "how are you", promises and nonsense.
 *
 * Placeholders: {pet} the pet's name, {score} today's score, {app} the app closest to its limit,
 * {minutes} its minutes left, {stage} the pet's own line for its current stage (from the manifest).
 */
object TalkScript {
    private val keywords: Map<String, List<Pair<TalkIntent, List<String>>>> =
        mapOf(
            "en" to
                listOf(
                    TalkIntent.BYE to listOf("bye", "goodbye", "see you", "later", "good night"),
                    TalkIntent.THANKS to listOf("thank", "thanks", "cheers"),
                    TalkIntent.INSULT to
                        listOf("stupid", "shut up", "idiot", "hate you", "dumb", "ugly", "annoying", "useless"),
                    TalkIntent.MORE_TIME to
                        listOf(
                            "more time",
                            "five minutes",
                            "5 minutes",
                            "ten minutes",
                            "one more",
                            "just a bit",
                            "a little more",
                            "please",
                            "let me",
                            "extend",
                            "unblock",
                            "open it",
                            "just once",
                        ),
                    TalkIntent.PROMISE to
                        listOf(
                            "i'll stop",
                            "i will stop",
                            "i promise",
                            "ok fine",
                            "okay fine",
                            "fine",
                            "you're right",
                            "you are right",
                            "i'm done",
                            "done",
                        ),
                    TalkIntent.WHY_BLOCKED to listOf("why", "blocked", "block"),
                    TalkIntent.SCORE to
                        listOf(
                            "score",
                            "how much",
                            "how many",
                            "minutes left",
                            "time left",
                            "how long",
                        ),
                    TalkIntent.HOW_ARE_YOU to
                        listOf("how are you", "how do you feel", "what's up", "whats up", "you ok", "you okay"),
                    TalkIntent.LOVE to listOf("love you", "cute", "good boy", "good girl", "sweet", "adorable"),
                    TalkIntent.GREETING to listOf("hi", "hello", "hey", "yo", "good morning", "good evening"),
                ),
            "he" to
                listOf(
                    TalkIntent.BYE to listOf("ביי", "להתראות", "לילה טוב", "נתראה"),
                    TalkIntent.THANKS to listOf("תודה"),
                    TalkIntent.INSULT to
                        listOf(
                            "טיפש",
                            "שתוק",
                            "שתקי",
                            "מעצבן",
                            "שונא אותך",
                            "שונאת אותך",
                            "מכוער",
                            "חסר תועלת",
                            "אידיוט",
                        ),
                    TalkIntent.MORE_TIME to
                        listOf(
                            "עוד זמן",
                            "חמש דקות",
                            "5 דקות",
                            "עשר דקות",
                            "עוד דקה",
                            "עוד קצת",
                            "רק קצת",
                            "בבקשה",
                            "תן לי",
                            "תני לי",
                            "להאריך",
                            "תפתח",
                            "תפתחי",
                            "רק פעם",
                        ),
                    TalkIntent.PROMISE to
                        listOf("אפסיק", "מבטיח", "מבטיחה", "בסדר", "צודק", "צודקת", "סיימתי", "טוב נו", "אוקיי"),
                    TalkIntent.WHY_BLOCKED to listOf("למה", "חסום", "חסומה", "חסמת"),
                    TalkIntent.SCORE to listOf("ציון", "כמה", "נשאר", "נשארו"),
                    TalkIntent.HOW_ARE_YOU to listOf("מה שלומך", "מה קורה", "איך אתה", "איך את", "מה נשמע", "מה המצב"),
                    TalkIntent.LOVE to listOf("אוהב אותך", "אוהבת אותך", "חמוד", "חמודה", "מתוק", "מתוקה", "ילד טוב"),
                    TalkIntent.GREETING to listOf("שלום", "היי", "הלו", "בוקר טוב", "ערב טוב", "הי"),
                ),
        )

    private val shared: Map<String, Map<TalkIntent, List<String>>> =
        mapOf(
            "en" to
                mapOf(
                    TalkIntent.GREETING to
                        listOf("Hi. {pet} here. What do you want?", "Oh, it's you. Score is {score}. Talk."),
                    TalkIntent.SCORE to listOf("Focus score {score}. {appline}"),
                    TalkIntent.WHY_BLOCKED to
                        listOf(
                            "Because you set a limit and then reached it. {app} is done for today. That was the deal.",
                            "You asked me to. Yesterday-you was smarter than now-you.",
                        ),
                    TalkIntent.THANKS to listOf("Don't thank me. Put the phone down.", "You're welcome. Now go."),
                    TalkIntent.BYE to
                        listOf(
                            "Bye. Don't come back for a while, that's the point.",
                            "Go. Live. I'll be here, rotting or not.",
                        ),
                    TalkIntent.LOVE to listOf("I know. Prove it with {app}.", "Sweet. Still not extending your limit."),
                    TalkIntent.SILENCE to
                        listOf(
                            "Still there? I can hear the silence.",
                            "Hello? Say something. Or don't, I'm used to it.",
                        ),
                ),
            "he" to
                mapOf(
                    TalkIntent.GREETING to listOf("היי. כאן {pet}. מה רוצים?", "אה, שוב. הציון {score}. דברו."),
                    TalkIntent.SCORE to listOf("ציון מיקוד {score}. {appline}"),
                    TalkIntent.WHY_BLOCKED to
                        listOf(
                            "כי קבעתם גבול והגעתם אליו. {app} נגמרה להיום. זו הייתה העסקה.",
                            "כי ביקשתם ממני. הגרסה של אתמול שלכם הייתה חכמה יותר.",
                        ),
                    TalkIntent.THANKS to listOf("לא להודות לי. להניח את הטלפון.", "בבקשה. עכשיו ללכת."),
                    TalkIntent.BYE to
                        listOf(
                            "ביי. לא לחזור זמן מה, זו כל הפואנטה.",
                            "ללכת. לחיות. אני פה, נרקב או לא.",
                        ),
                    TalkIntent.LOVE to listOf("אני יודע. להוכיח את זה עם {app}.", "מתוק. עדיין לא מאריך את הגבול."),
                    TalkIntent.SILENCE to
                        listOf(
                            "עדיין שם? אני שומע את השקט.",
                            "הלו? תגידו משהו. או לא, התרגלתי.",
                        ),
                ),
        )

    private val appLine: Map<String, Triple<String, String, String>> =
        mapOf(
            "en" to
                Triple(
                    "{app}: {minutes} minutes left.",
                    "{app} is blocked for today.",
                    "Nothing tracked yet, so I'm fine. Suspiciously fine.",
                ),
            "he" to
                Triple(
                    "{app}: נשארו {minutes} דקות.",
                    "{app} חסומה להיום.",
                    "עדיין לא עוקבים אחרי כלום, אז אני בסדר. חשוד.",
                ),
        )

    private val noBlock: Map<String, String> =
        mapOf(
            "en" to "Nothing is blocked right now. You're free. Use it wisely.",
            "he" to "שום דבר לא חסום עכשיו. חופשי. להשתמש בזה בחוכמה.",
        )

    private val flavour: Map<String, Map<String, Map<TalkIntent, List<String>>>> =
        mapOf(
            "indifferent" to
                mapOf(
                    "en" to
                        mapOf(
                            TalkIntent.MORE_TIME to
                                listOf(
                                    "No. I don't care enough to argue, and the answer is still no.",
                                    "Five minutes. Sure. That's what you said last time. No.",
                                ),
                            TalkIntent.INSULT to listOf("Okay.", "Noted. Still a potato. Still not opening it."),
                            TalkIntent.HOW_ARE_YOU to
                                listOf(
                                    "{stage} Score {score}. Whatever.",
                                    "Fine. Or not. Hard to tell from inside a potato.",
                                ),
                            TalkIntent.UNKNOWN to
                                listOf(
                                    "Didn't get that. Doesn't matter. Put the phone down.",
                                    "Hm. Try shorter words. Or don't.",
                                ),
                            TalkIntent.PROMISE to listOf("Fine. We'll see.", "Good. I'll sprout a little less."),
                        ),
                    "he" to
                        mapOf(
                            TalkIntent.MORE_TIME to
                                listOf(
                                    "לא. לא אכפת לי מספיק כדי להתווכח, והתשובה עדיין לא.",
                                    "חמש דקות. בטח. זה מה שאמרתם בפעם הקודמת. לא.",
                                ),
                            TalkIntent.INSULT to listOf("בסדר.", "נרשם. עדיין תפוח אדמה. עדיין לא פותח."),
                            TalkIntent.HOW_ARE_YOU to
                                listOf("{stage} ציון {score}. מה שתגידו.", "בסדר. או לא. קשה לדעת מתוך תפוח אדמה."),
                            TalkIntent.UNKNOWN to
                                listOf("לא הבנתי. לא משנה. להניח את הטלפון.", "המ. לנסות מילים קצרות יותר. או לא."),
                            TalkIntent.PROMISE to listOf("בסדר. נראה.", "יופי. אנבוט קצת פחות."),
                        ),
                ),
            "cynical" to
                mapOf(
                    "en" to
                        mapOf(
                            TalkIntent.MORE_TIME to
                                listOf(
                                    "Ah, negotiation. The last refuge of the addicted. No.",
                                    "Five more minutes is how we got here. No.",
                                ),
                            TalkIntent.INSULT to
                                listOf(
                                    "Insulting a brain. Bold, for someone who can't stop scrolling.",
                                    "Cute. Still no.",
                                ),
                            TalkIntent.HOW_ARE_YOU to
                                listOf(
                                    "{stage} Score {score}. Draw your own conclusions.",
                                    "Thinking. Mostly about your choices.",
                                ),
                            TalkIntent.UNKNOWN to
                                listOf(
                                    "I understood every word and chose to ignore them.",
                                    "Say it like you mean it. Then don't open the app.",
                                ),
                            TalkIntent.PROMISE to
                                listOf("I'll believe it when the score does.", "A promise. How novel. Keep it."),
                        ),
                    "he" to
                        mapOf(
                            TalkIntent.MORE_TIME to
                                listOf(
                                    "אה, משא ומתן. המפלט האחרון של המכורים. לא.",
                                    "עוד חמש דקות זה בדיוק איך הגענו לפה. לא.",
                                ),
                            TalkIntent.INSULT to
                                listOf("להעליב מוח. אמיץ, בשביל מי שלא מצליח להפסיק לגלול.", "חמוד. עדיין לא."),
                            TalkIntent.HOW_ARE_YOU to
                                listOf("{stage} ציון {score}. תסיקו מסקנות.", "חושב. בעיקר על הבחירות שלכם."),
                            TalkIntent.UNKNOWN to
                                listOf(
                                    "הבנתי כל מילה ובחרתי להתעלם.",
                                    "להגיד את זה כאילו מתכוונים. ואז לא לפתוח את האפליקציה.",
                                ),
                            TalkIntent.PROMISE to listOf("אאמין כשהציון יאמין.", "הבטחה. איזה חידוש. לקיים אותה."),
                        ),
                ),
            "dramatic" to
                mapOf(
                    "en" to
                        mapOf(
                            TalkIntent.MORE_TIME to
                                listOf(
                                    "Five minutes?! Do you hear yourself? My leaves are falling. No.",
                                    "Every minute you ask for is a leaf. No.",
                                ),
                            TalkIntent.INSULT to
                                listOf("Wounded. Wilting. Yet still: no.", "You say that to a dying plant?"),
                            TalkIntent.HOW_ARE_YOU to
                                listOf(
                                    "{stage} Score {score}. Water me with attention, not scrolling.",
                                    "Barely hanging on. Dramatically.",
                                ),
                            TalkIntent.UNKNOWN to
                                listOf(
                                    "The wind took your words. Say it again, slower.",
                                    "I felt that more than I understood it.",
                                ),
                            TalkIntent.PROMISE to
                                listOf("Yes! Sunlight! Keep it.", "A promise. I could weep. Don't make me."),
                        ),
                    "he" to
                        mapOf(
                            TalkIntent.MORE_TIME to
                                listOf(
                                    "חמש דקות?! שומעים את עצמכם? העלים שלי נושרים. לא.",
                                    "כל דקה שמבקשים היא עלה. לא.",
                                ),
                            TalkIntent.INSULT to listOf("פצוע. קמל. ובכל זאת: לא.", "ככה מדברים לצמח גוסס?"),
                            TalkIntent.HOW_ARE_YOU to
                                listOf(
                                    "{stage} ציון {score}. להשקות אותי בתשומת לב, לא בגלילה.",
                                    "בקושי מחזיק מעמד. בדרמטיות.",
                                ),
                            TalkIntent.UNKNOWN to
                                listOf(
                                    "הרוח לקחה את המילים. שוב, לאט.",
                                    "הרגשתי את זה יותר משהבנתי.",
                                ),
                            TalkIntent.PROMISE to
                                listOf(
                                    "כן! אור שמש! לקיים.",
                                    "הבטחה. אני עלול לבכות. לא לגרום לי.",
                                ),
                        ),
                ),
            "confused" to
                mapOf(
                    "en" to
                        mapOf(
                            TalkIntent.MORE_TIME to
                                listOf(
                                    "Wait, more? Weren't we... no. I think the answer is no.",
                                    "Five minutes? I forget what five minutes is. Still no.",
                                ),
                            TalkIntent.INSULT to listOf("What? Who? Oh. Rude.", "I already forgot that. Lucky you."),
                            TalkIntent.HOW_ARE_YOU to
                                listOf(
                                    "{stage} Score {score}, I think. The water is... a colour.",
                                    "Swimming. In circles. Like you and {app}.",
                                ),
                            TalkIntent.UNKNOWN to listOf("Blub? Try that again.", "I lost the thread. Say it shorter."),
                            TalkIntent.PROMISE to
                                listOf(
                                    "Okay! Wait, what did you promise? Oh. Good.",
                                    "Great. I'll remember that for eight seconds.",
                                ),
                        ),
                    "he" to
                        mapOf(
                            TalkIntent.MORE_TIME to
                                listOf(
                                    "רגע, עוד? לא היינו... לא. נראה לי שהתשובה היא לא.",
                                    "חמש דקות? שכחתי מה זה חמש דקות. עדיין לא.",
                                ),
                            TalkIntent.INSULT to listOf("מה? מי? אה. לא יפה.", "כבר שכחתי את זה. יש לכם מזל."),
                            TalkIntent.HOW_ARE_YOU to
                                listOf(
                                    "{stage} ציון {score}, נראה לי. המים בצבע... צבע.",
                                    "שוחה. במעגלים. כמוכם עם {app}.",
                                ),
                            TalkIntent.UNKNOWN to listOf("בלאב? שוב?", "איבדתי את החוט. קצר יותר."),
                            TalkIntent.PROMISE to
                                listOf("אוקיי! רגע, מה הבטחתם? אה. יופי.", "מעולה. אזכור את זה שמונה שניות."),
                        ),
                ),
            "judgmental" to
                mapOf(
                    "en" to
                        mapOf(
                            TalkIntent.MORE_TIME to
                                listOf(
                                    "No. And I saw how long you hesitated before asking.",
                                    "Five minutes. For {app}. I'm not even going to blink.",
                                ),
                            TalkIntent.INSULT to
                                listOf("I've been called worse by better.", "Look at you. Then look at me. No."),
                            TalkIntent.HOW_ARE_YOU to
                                listOf(
                                    "{stage} Score {score}. I've seen better days. Yours, mostly.",
                                    "Judging. It's going well.",
                                ),
                            TalkIntent.UNKNOWN to
                                listOf("I'm choosing not to understand that.", "Say it properly. I have standards."),
                            TalkIntent.PROMISE to listOf("Acceptable. Barely.", "We'll see. I'm watching."),
                        ),
                    "he" to
                        mapOf(
                            TalkIntent.MORE_TIME to
                                listOf(
                                    "לא. וראיתי כמה זמן היססתם לפני ששאלתם.",
                                    "חמש דקות. בשביל {app}. אני אפילו לא אמצמץ.",
                                ),
                            TalkIntent.INSULT to
                                listOf("קראו לי גרוע מזה, אנשים טובים מכם.", "להסתכל עליכם. ואז עליי. לא."),
                            TalkIntent.HOW_ARE_YOU to
                                listOf("{stage} ציון {score}. ראיתי ימים יפים יותר. שלכם, בעיקר.", "שופט. הולך טוב."),
                            TalkIntent.UNKNOWN to
                                listOf("אני בוחר לא להבין את זה.", "להגיד את זה כמו שצריך. יש לי סטנדרטים."),
                            TalkIntent.PROMISE to listOf("מקובל. בקושי.", "נראה. אני צופה."),
                        ),
                ),
            "bureaucratic" to
                mapOf(
                    "en" to
                        mapOf(
                            TalkIntent.MORE_TIME to
                                listOf(
                                    "Request denied. Form 7B: limit extensions are not available today.",
                                    "Extension policy: none. Have a productive day.",
                                ),
                            TalkIntent.INSULT to
                                listOf("Feedback logged. Priority: low.", "Insult received. Processing. Rejected."),
                            TalkIntent.HOW_ARE_YOU to
                                listOf(
                                    "{stage} Score {score}. Systems nominal. Yours are not.",
                                    "Operational. Rust at acceptable levels.",
                                ),
                            TalkIntent.UNKNOWN to
                                listOf(
                                    "Input not recognised. Please rephrase in triplicate.",
                                    "Error 404: meaning not found.",
                                ),
                            TalkIntent.PROMISE to
                                listOf(
                                    "Commitment recorded. Non-compliance will be noted.",
                                    "Acknowledged. Ticket closed.",
                                ),
                        ),
                    "he" to
                        mapOf(
                            TalkIntent.MORE_TIME to
                                listOf(
                                    "הבקשה נדחתה. טופס 7ב: הארכות גבול אינן זמינות היום.",
                                    "מדיניות הארכות: אין. יום פרודוקטיבי.",
                                ),
                            TalkIntent.INSULT to listOf("המשוב תועד. עדיפות: נמוכה.", "עלבון התקבל. בעיבוד. נדחה."),
                            TalkIntent.HOW_ARE_YOU to
                                listOf("{stage} ציון {score}. המערכות תקינות. שלכם לא.", "פעיל. רמת החלודה סבירה."),
                            TalkIntent.UNKNOWN to
                                listOf("הקלט לא זוהה. לנסח מחדש בשלושה עותקים.", "שגיאה 404: משמעות לא נמצאה."),
                            TalkIntent.PROMISE to listOf("ההתחייבות תועדה. אי-עמידה תירשם.", "אושר. הפנייה נסגרה."),
                        ),
                ),
        )

    private val personalityOrder =
        listOf(
            TalkIntent.MORE_TIME,
            TalkIntent.INSULT,
            TalkIntent.HOW_ARE_YOU,
            TalkIntent.UNKNOWN,
            TalkIntent.PROMISE,
        )

    fun language(tag: String): String = if (tag.startsWith("he") || tag.startsWith("iw")) "he" else "en"

    /** First matching intent in priority order; Latin single words must match whole words, Hebrew by substring. */
    fun intentOf(text: String, language: String): TalkIntent {
        val normalized = text.lowercase().trim()
        if (normalized.isBlank()) return TalkIntent.UNKNOWN
        val table = keywords[language(language)] ?: keywords.getValue("en")
        for ((intent, words) in table) {
            if (words.any { matches(normalized, it) }) return intent
        }
        return TalkIntent.UNKNOWN
    }

    /** All candidate replies for the intent, before placeholder substitution. */
    fun candidates(intent: TalkIntent, personality: String, language: String): List<String> {
        val lang = language(language)
        if (intent in personalityOrder) {
            val voice = flavour[personality] ?: flavour.getValue("indifferent")
            (voice[lang] ?: voice.getValue("en"))[intent]?.let { return it }
        }
        return (shared[lang] ?: shared.getValue("en"))[intent]
            ?: (flavour.getValue("indifferent").getValue(lang)).getValue(TalkIntent.UNKNOWN)
    }

    /** Fills {app}/{minutes}/{appline} from what is known; the caller fills {pet}, {score} and {stage}. */
    fun appLine(language: String, app: String?, minutesLeft: Int, blocked: Boolean): String {
        val (left, done, none) = appLine.getValue(language(language))
        return when {
            app == null -> none
            blocked -> done.replace("{app}", app)
            else -> left.replace("{app}", app).replace("{minutes}", minutesLeft.toString())
        }
    }

    fun noBlockLine(language: String): String = noBlock.getValue(language(language))

    private fun matches(text: String, keyword: String): Boolean {
        val latinWord = keyword.all { it.code < LATIN_LIMIT } && ' ' !in keyword
        return if (latinWord) Regex("\\b${Regex.escape(keyword)}\\b").containsMatchIn(text) else keyword in text
    }

    private const val LATIN_LIMIT = 0x250
}
