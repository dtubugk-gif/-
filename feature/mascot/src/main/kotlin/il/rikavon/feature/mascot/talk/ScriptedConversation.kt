package il.rikavon.feature.mascot.talk

import kotlin.random.Random

/** Everything the pet knows when it answers. */
data class TalkContext(
    val petName: String,
    val personality: String,
    /** The pet's own line for its current stage, from the manifest. */
    val stageLine: String,
    val score: Int,
    val language: String,
    /** The tracked app closest to (or over) its limit, if any. */
    val app: String? = null,
    val minutesLeft: Int = 0,
    val blocked: Boolean = false,
)

/** The pet's side of a conversation: the user's words in, one spoken line out. */
interface ConversationEngine {
    /** What the pet says back to [userText]. */
    fun reply(userText: String, context: TalkContext): String

    /** The pet opens the conversation. */
    fun greeting(context: TalkContext): String

    /** A line for an intent the caller already knows (a pressed button, silence on the line). */
    fun answer(intent: TalkIntent, context: TalkContext): String
}

/**
 * The offline engine: keyword intents and personality lines from [TalkScript]. Deterministic given the
 * random source, never repeats the previous reply when there is a choice, needs no network.
 */
class ScriptedConversation(private val random: Random = Random.Default) : ConversationEngine {
    private var last: String? = null

    override fun reply(userText: String, context: TalkContext): String =
        answer(TalkScript.intentOf(userText, context.language), context)

    override fun greeting(context: TalkContext): String = answer(TalkIntent.GREETING, context)

    override fun answer(intent: TalkIntent, context: TalkContext): String {
        val body =
            if (intent == TalkIntent.WHY_BLOCKED && !context.blocked) {
                TalkScript.noBlockLine(context.language)
            } else {
                pick(TalkScript.candidates(intent, context.personality, context.language))
            }
        return fill(body, context).also { last = it }
    }

    private fun pick(pool: List<String>): String {
        if (pool.isEmpty()) return ""
        val fresh = if (pool.size > 1) pool.filter { it != last } else pool
        return fresh[random.nextInt(fresh.size)]
    }

    private fun fill(template: String, context: TalkContext): String {
        val app = context.app ?: NO_APP_PLACEHOLDER
        return template
            .replace(
                "{appline}",
                TalkScript.appLine(context.language, context.app, context.minutesLeft, context.blocked),
            ).replace("{pet}", context.petName)
            .replace("{score}", context.score.toString())
            .replace("{app}", app)
            .replace("{minutes}", context.minutesLeft.toString())
            .replace("{stage}", context.stageLine)
            .replace("  ", " ")
            .trim()
    }

    private companion object {
        /** When nothing is tracked yet the sentences still need a noun. */
        const val NO_APP_PLACEHOLDER = "that app"
    }
}
