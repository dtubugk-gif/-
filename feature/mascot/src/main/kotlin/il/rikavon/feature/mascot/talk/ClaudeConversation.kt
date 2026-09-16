package il.rikavon.feature.mascot.talk

/** One line of the conversation as the model sees it. */
data class Turn(val fromPet: Boolean, val text: String)

/** The network half of the AI brain: the prompt and the conversation so far in, the pet's next line out. */
fun interface PetBrain {
    /** Null on any failure (network, key, refusal, empty answer); the caller falls back to the script. */
    suspend fun ask(system: String, turns: List<Turn>): String?
}

/**
 * The AI engine: every line is written fresh by a language model in the pet's personality with today's
 * numbers, so the pet never says the same thing twice. The offline script stays behind it: when the network,
 * the key or the model fails, the line comes from the script instead and the conversation carries on.
 */
class ClaudeConversation(
    private val brain: PetBrain,
    private val fallback: ConversationEngine = ScriptedConversation(),
) : ConversationEngine {
    private val history = ArrayDeque<Turn>()

    override suspend fun reply(userText: String, context: TalkContext): String =
        exchange(userText, context) { fallback.reply(userText, context) }

    override suspend fun greeting(context: TalkContext): String =
        exchange(PetPrompt.OPENING, context) { fallback.greeting(context) }

    override suspend fun answer(intent: TalkIntent, context: TalkContext): String =
        exchange(PetPrompt.cue(intent), context) { fallback.answer(intent, context) }

    private suspend fun exchange(userTurn: String, context: TalkContext, orElse: suspend () -> String): String {
        val turns = history + Turn(fromPet = false, text = userTurn)
        val line =
            brain.ask(PetPrompt.system(context), turns)?.let(::tidy)?.takeIf { it.isNotBlank() } ?: orElse()
        remember(Turn(fromPet = false, text = userTurn))
        if (line.isNotBlank()) remember(Turn(fromPet = true, text = line))
        return line
    }

    private fun remember(turn: Turn) {
        history.addLast(turn)
        while (history.size > MAX_TURNS) history.removeFirst()
    }

    /** Spoken text: no markdown, no wrapping quotes, one line, and no longer than a breath or two. */
    private fun tidy(raw: String): String {
        val flat =
            raw
                .replace(MARKDOWN, "")
                .replace(WHITESPACE, " ")
                .trim()
                .trim { it in QUOTES }
                .trim()
        if (flat.length <= MAX_CHARS) return flat
        val cut = flat.take(MAX_CHARS)
        val end = cut.lastIndexOfAny(SENTENCE_ENDS)
        return if (end > MIN_CUT) cut.substring(0, end + 1) else cut
    }

    private companion object {
        /** Six exchanges of context: enough not to repeat itself, small enough to stay fast. */
        const val MAX_TURNS = 12
        const val MAX_CHARS = 280
        const val MIN_CUT = 40
        val MARKDOWN = Regex("[*_`#>]")
        val WHITESPACE = Regex("\\s+")
        const val QUOTES = "\"“”«»"
        val SENTENCE_ENDS = charArrayOf('.', '!', '?')
    }
}
