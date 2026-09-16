package il.rikavon.talk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ScriptedConversationTest {
    private val english =
        TalkContext(
            petName = "Potato",
            personality = "indifferent",
            stageLine = "Sprouting. Not on purpose.",
            score = 62,
            language = "en",
            app = "Instagram",
            minutesLeft = 12,
        )
    private val hebrew = english.copy(petName = "תפוח אדמה", language = "he", personality = "cynical")

    @Test
    fun `intents are recognised in both languages`() {
        assertEquals(TalkIntent.MORE_TIME, TalkScript.intentOf("come on, just five minutes please", "en"))
        assertEquals(TalkIntent.PROMISE, TalkScript.intentOf("ok fine, I'll stop", "en"))
        assertEquals(TalkIntent.HOW_ARE_YOU, TalkScript.intentOf("How are you today?", "en"))
        assertEquals(TalkIntent.GREETING, TalkScript.intentOf("hi", "en"))
        assertEquals(TalkIntent.UNKNOWN, TalkScript.intentOf("this thing", "en"))
        assertEquals(TalkIntent.MORE_TIME, TalkScript.intentOf("נו, רק עוד חמש דקות בבקשה", "he"))
        assertEquals(TalkIntent.INSULT, TalkScript.intentOf("אתה ממש מעצבן", "he"))
        assertEquals(TalkIntent.SCORE, TalkScript.intentOf("כמה נשאר לי?", "he"))
        assertEquals(TalkIntent.GREETING, TalkScript.intentOf("ושלום לך", "he"))
    }

    @Test
    fun `replies are filled in and stay in the pet's language`() {
        val engine = ScriptedConversation(Random(1))
        val reply = engine.reply("what's my score", english)
        assertTrue(reply, reply.contains("62") && reply.contains("Instagram") && reply.contains("12"))
        assertFalse(reply, reply.contains("{"))
        val hebrewReply = ScriptedConversation(Random(1)).reply("מה שלומך", hebrew)
        assertTrue(hebrewReply, hebrewReply.contains("Sprouting. Not on purpose.") && hebrewReply.contains("62"))
        assertFalse(hebrewReply, hebrewReply.contains("{"))
    }

    @Test
    fun `asking why nothing is blocked gets the plain answer`() {
        val reply = ScriptedConversation(Random(2)).reply("why is it blocked", english.copy(blocked = false))
        assertEquals(TalkScript.noBlockLine("en"), reply)
        val blocked = ScriptedConversation(Random(2)).reply("why blocked", english.copy(blocked = true))
        assertTrue(blocked, blocked.contains("Instagram") || blocked.contains("Yesterday"))
    }

    @Test
    fun `personalities answer begging differently`() {
        val potato = ScriptedConversation(Random(3)).reply("five minutes", english)
        val robot = ScriptedConversation(Random(3)).reply("five minutes", english.copy(personality = "bureaucratic"))
        assertTrue(robot, robot.contains("denied") || robot.contains("policy"))
        assertTrue(potato, potato.contains("No"))
    }
}
