package il.rikavon.feature.mascot.sound

import il.rikavon.feature.mascot.model.VoiceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCastingTest {
    @Test
    fun `every personality is cast to a real voice, and the six differ from one another`() {
        val personalities = listOf("cynical", "dramatic", "confused", "judgmental", "bureaucratic", "indifferent")
        val cast = personalities.map { VoiceCasting.voice(VoiceProfile.forPersonality(it)) }
        cast.forEach { assertTrue(it, it in VoiceCasting.VOICES) }
        assertEquals(cast.size, cast.toSet().size)
        assertTrue(VoiceCasting.voice(VoiceProfile.forPersonality("mysterious")) in VoiceCasting.VOICES)
    }

    @Test
    fun `the user's pick wins over the manifest, the manifest over the casting, unknown names are ignored`() {
        val manifest = VoiceProfile.clamped(1f, 1f, "nova", "cynical")
        assertEquals("nova", VoiceCasting.voice(manifest))
        assertEquals("shimmer", VoiceCasting.voice(manifest, choice = "shimmer"))
        assertEquals("nova", VoiceCasting.voice(manifest, choice = "Bill"))
        assertEquals("onyx", VoiceCasting.voice(VoiceProfile.clamped(1f, 1f, "eleven_bill", "cynical")))
    }

    @Test
    fun `the directions name the character, the language with its accent, and the pace`() {
        val hebrew = VoiceCasting.instructions(VoiceProfile.forPersonality("cynical"), "he-IL")
        assertTrue(hebrew, hebrew.contains("Israeli Hebrew"))
        assertTrue(hebrew, hebrew.contains("sardonic"))
        assertTrue(hebrew, hebrew.contains("natural, conversational pace"))
        val english = VoiceCasting.instructions(VoiceProfile.forPersonality("indifferent"), "en")
        assertTrue(english, english.contains("clear English"))
        assertTrue(english, english.contains("slowly"))
        val brisk = VoiceCasting.instructions(VoiceProfile.forPersonality("bureaucratic"), "en")
        assertTrue(brisk, brisk.contains("briskly"))
    }
}
