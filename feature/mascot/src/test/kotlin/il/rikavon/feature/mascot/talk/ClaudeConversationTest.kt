package il.rikavon.feature.mascot.talk

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeConversationTest {
    private val context =
        TalkContext(
            petName = "Potato",
            personality = "indifferent",
            stageLine = "Sprouting. Not on purpose.",
            score = 62,
            language = "en",
            app = "Instagram",
            minutesLeft = 12,
        )

    @Test
    fun `the model's line is used, tidied for speech`() =
        runTest {
            val brain = FakeBrain(mutableListOf("  \"**Five** minutes?  No.\"  \n\n"))
            val engine = ClaudeConversation(brain, fallback = Stub("script"))
            assertEquals("Five minutes? No.", engine.reply("five more minutes", context))
        }

    @Test
    fun `a failing or empty brain falls back to the script and the conversation carries on`() =
        runTest {
            val brain = FakeBrain(mutableListOf(null, "   ", "Back."))
            val engine = ClaudeConversation(brain, fallback = Stub("script"))
            assertEquals("script", engine.reply("hi", context))
            assertEquals("script", engine.reply("still there?", context))
            assertEquals("Back.", engine.reply("and now?", context))
            // The script's lines are part of the memory the model sees, so it does not contradict them.
            assertEquals(listOf("hi", "script", "still there?", "script", "and now?"), brain.lastTurns.map { it.text })
        }

    @Test
    fun `openings and known intents are stage directions in brackets, not the human's words`() =
        runTest {
            val brain = FakeBrain(mutableListOf("Hello.", "Fine."))
            val engine = ClaudeConversation(brain, fallback = Stub("script"))
            engine.greeting(context)
            assertTrue(
                brain.lastTurns
                    .single()
                    .text
                    .startsWith("["),
            )
            engine.answer(TalkIntent.PROMISE, context)
            val cue = brain.lastTurns.last().text
            assertTrue(cue, cue.startsWith("[") && cue.contains("stop"))
        }

    @Test
    fun `memory alternates and stays bounded`() =
        runTest {
            val brain = FakeBrain(MutableList(40) { "Line $it." })
            val engine = ClaudeConversation(brain, fallback = Stub("script"))
            repeat(20) { engine.reply("turn $it", context) }
            val turns = brain.lastTurns
            assertTrue(turns.size.toString(), turns.size <= MAX_SENT)
            turns.zipWithNext().forEach { (a, b) -> assertFalse(a.fromPet == b.fromPet) }
            assertFalse(turns.last().fromPet)
        }

    @Test
    fun `a long answer is cut at a sentence end`() =
        runTest {
            val long = List(30) { "Sentence number $it is here." }.joinToString(" ")
            val engine = ClaudeConversation(FakeBrain(mutableListOf(long)), fallback = Stub("script"))
            val line = engine.reply("go on", context)
            assertTrue(line.length.toString(), line.length <= MAX_LINE)
            assertTrue(line, line.endsWith("."))
        }

    @Test
    fun `the prompt carries the pet, the numbers, the language and the block`() {
        val english = PetPrompt.system(context)
        listOf("Potato", "indifferent", "Sprouting", "62", "Instagram", "12", "English").forEach {
            assertTrue(it, english.contains(it))
        }
        assertTrue(PetPrompt.system(context.copy(language = "he")).contains("Hebrew"))
        assertTrue(PetPrompt.system(context.copy(blocked = true)).contains("blocked"))
        assertTrue(PetPrompt.system(context.copy(app = null)).contains("No app"))
    }

    private class FakeBrain(private val answers: MutableList<String?>) : PetBrain {
        var lastTurns: List<Turn> = emptyList()

        override suspend fun ask(system: String, turns: List<Turn>): String? {
            lastTurns = turns
            return if (answers.isEmpty()) null else answers.removeAt(0)
        }
    }

    private class Stub(private val line: String) : ConversationEngine {
        override suspend fun reply(userText: String, context: TalkContext) = line

        override suspend fun greeting(context: TalkContext) = line

        override suspend fun answer(intent: TalkIntent, context: TalkContext) = line
    }

    private companion object {
        const val MAX_SENT = 13
        const val MAX_LINE = 280
    }
}
