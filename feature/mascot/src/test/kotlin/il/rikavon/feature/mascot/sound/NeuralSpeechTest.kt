package il.rikavon.feature.mascot.sound

import il.rikavon.feature.mascot.model.VoiceProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `a refusal is summed up in the service's own sentence, or the raw detail when there is none`() {
        val refusal =
            VoiceAttempt.Failed(
                402,
                """{"detail":{"status":"paid_plan_required","message":"Free users cannot use library voices."}}""",
            )
        assertEquals("402 Free users cannot use library voices.", refusal.summary())
        assertEquals("401 Unauthorized", VoiceAttempt.Failed(401, """{"detail":"Unauthorized"}""").summary())
        assertEquals(
            "0 java.net.UnknownHostException",
            VoiceAttempt.Failed(0, "java.net.UnknownHostException").summary(),
        )
        assertEquals("500", VoiceAttempt.Failed(500, "").summary())
    }

    @Test
    fun `a profile carries its personality for the catalogue, and a manifest may name a voice outright`() {
        val personalities = listOf("cynical", "dramatic", "confused", "judgmental", "bureaucratic", "indifferent")
        personalities.forEach { assertEquals(it, VoiceProfile.forPersonality(it).personality) }
        assertNull(VoiceProfile.forPersonality("cynical").neuralVoiceId)
        assertEquals("mysterious", VoiceProfile.forPersonality("mysterious").personality)
        val named = VoiceProfile.clamped(0.7f, 1f, "custom", "cynical")
        assertEquals("custom", named.neuralVoiceId)
        assertEquals("cynical", named.personality)
        assertTrue(NeuralSpeech.endpoint("abc").contains("/abc?"))
    }
}
