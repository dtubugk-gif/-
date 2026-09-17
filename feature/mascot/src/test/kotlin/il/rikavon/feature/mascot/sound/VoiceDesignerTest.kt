package il.rikavon.feature.mascot.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceDesignerTest {
    @Test
    fun `every personality gets its own description, in the pet's language`() {
        val personalities = listOf("cynical", "dramatic", "confused", "judgmental", "bureaucratic", "indifferent")
        val hebrew = personalities.map { VoiceDesigner.describe(it, "he-IL") }
        assertEquals(hebrew.size, hebrew.toSet().size)
        hebrew.forEach { assertTrue(it, it.contains("Hebrew")) }
        val english = VoiceDesigner.describe("cynical", "en-US")
        assertTrue(english, english.contains("English") && !english.contains("Hebrew"))
        assertTrue(VoiceDesigner.describe("mysterious", "en").contains("natural"))
    }
}
