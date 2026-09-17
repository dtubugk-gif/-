package il.rikavon.feature.mascot.sound

import il.rikavon.feature.mascot.model.VoiceProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeuralSpeechTest {
    @Test
    fun `the request names the fast model, the language and the pet's pace`() {
        val body = Json.parseToJsonElement(NeuralSpeech.requestBody("שלום, \"חבר\".", "he-IL", rate = 0.8f)).jsonObject
        assertEquals("שלום, \"חבר\".", body["text"]?.jsonPrimitive?.content)
        assertEquals(NeuralSpeech.MODEL, body["model_id"]?.jsonPrimitive?.content)
        assertEquals("he", body["language_code"]?.jsonPrimitive?.content)
        val settings = body["voice_settings"]?.jsonObject
        assertNotNull(settings)
        assertEquals("0.8", settings?.get("speed")?.jsonPrimitive?.content)
        assertEquals("en", NeuralSpeech.languageCode("en-US"))
        assertEquals("he", NeuralSpeech.languageCode("iw"))
    }

    @Test
    fun `the pace stays inside what the cloud voice accepts`() {
        assertEquals(0.8f, NeuralSpeech.speed(0.5f))
        assertEquals(1.15f, NeuralSpeech.speed(2f))
        assertEquals(1f, NeuralSpeech.speed(1f))
    }

    @Test
    fun `every personality has its own cloud voice, and a manifest keeps it unless it says otherwise`() {
        val personalities = listOf("cynical", "dramatic", "confused", "judgmental", "bureaucratic", "indifferent")
        val voices = personalities.map { VoiceProfile.forPersonality(it).neuralVoiceId }
        voices.forEach { assertNotNull(it) }
        assertEquals(voices.size, voices.toSet().size)
        assertEquals(voices[0], VoiceProfile.clamped(0.7f, 1f, voices[0]).neuralVoiceId)
        assertEquals("custom", VoiceProfile.clamped(0.7f, 1f, "custom").neuralVoiceId)
        assertTrue(NeuralSpeech.endpoint("abc").contains("/abc?"))
    }
}
