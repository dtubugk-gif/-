package il.rikavon.feature.mascot.talk

/**
 * What the model is told before every line: the rules of a spoken, in-character reply first (they never change,
 * so the prefix stays stable), then who the pet is, how it feels and today's numbers.
 */
object PetPrompt {
    fun system(context: TalkContext): String =
        buildString {
            appendLine(RULES)
            appendLine()
            appendLine(languageRule(context.language))
            appendLine()
            append("You are ${context.petName}, a digital pet that lives in the phone of a person who is trying ")
            appendLine("to spend less time in distracting apps. Your personality: ${context.personality}.")
            appendLine("How you feel right now, in your own words: \"${context.stageLine}\"")
            appendLine("Focus score today: ${context.score} out of $MAX_SCORE.")
            append(appLine(context))
        }

    /** The call just connected: the pet opens. */
    const val OPENING = "[The line just connected. Open the conversation in one or two sentences.]"

    /** A stage direction for an intent the app already knows (a pressed button, silence on the line). */
    fun cue(intent: TalkIntent): String =
        when (intent) {
            TalkIntent.GREETING -> OPENING
            TalkIntent.PROMISE -> "[Your human pressed the button that says they will stop now. Accept it.]"
            TalkIntent.SILENCE -> "[Silence on the line: your human said nothing. Draw them out, briefly.]"
            TalkIntent.BYE -> "[Your human stays silent. End the call with a goodbye.]"
            else -> "[Your human's message was understood as: ${intent.name.lowercase().replace('_', ' ')}.]"
        }

    private fun languageRule(language: String): String =
        if (language == "he") {
            "Answer only in Hebrew: natural, spoken Israeli Hebrew. Address your human in the plural (אתם), " +
                "the way the app does, so nobody is misgendered."
        } else {
            "Answer only in English."
        }

    private fun appLine(context: TalkContext): String {
        val app = context.app ?: return "No app is near its limit right now."
        return if (context.blocked) {
            "$app is blocked right now; that is what this conversation is about."
        } else {
            "The app closest to its limit is $app, with ${context.minutesLeft} minutes left today."
        }
    }

    private const val MAX_SCORE = 100
    private val RULES =
        """
        You are on a phone call, or in a chat, with your human, and you answer as the pet, out loud.
        - One or two short sentences. Text-to-speech reads them, so: no emojis, no lists, no markdown, no stage directions, no quotation marks around your line.
        - Stay in character: sharp, funny, a little theatrical, and fond of your human underneath. Never cruel, never shaming, no lectures, no medical or mental-health advice.
        - Never repeat a sentence you already said in this conversation; vary your wording every time.
        - If your human promises to stop, accept it in one short line. If they say goodbye, say goodbye and nothing more.
        - Text in square brackets describes something that happened on the line (a button pressed, silence); it is not something your human said.
        - Never mention being an AI or a language model; you are the pet.
        """.trimIndent()
}
