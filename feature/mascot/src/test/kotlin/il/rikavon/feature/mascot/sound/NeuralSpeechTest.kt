package il.rikavon.feature.mascot.sound

import il.rikavon.feature.mascot.model.VoiceProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeuralSpeechTest {
    @Test
    fun `the request names the steerable model, the voice, the directions and the format`() {
        val body =
            Json
                .parseToJsonElement(
                    NeuralSpeech.requestBody("שלום, \"חבר\".", "onyx", "Speak slowly."),
                ).jsonObject
        assertEquals("שלום, \"חבר\".", body["input"]?.jsonPrimitive?.content)
        assertEquals(NeuralSpeech.MODEL, body["model"]?.jsonPrimitive?.content)
        assertEquals("onyx", body["voice"]?.jsonPrimitive?.content)
        assertEquals("Speak slowly.", body["instructions"]?.jsonPrimitive?.content)
        assertEquals("mp3", body["response_format"]?.jsonPrimitive?.content)
        assertEquals("en", NeuralSpeech.languageCode("en-US"))
        assertEquals("he", NeuralSpeech.languageCode("iw"))
    }

    @Test
    fun `a line longer than the endpoint accepts is cut, not refused`() {
        val body = Json.parseToJsonElement(NeuralSpeech.requestBody("x".repeat(5000), "onyx", "")).jsonObject
        assertEquals(NeuralSpeech.MAX_INPUT_CHARS, body["input"]?.jsonPrimitive?.content?.length)
    }

    @Test
    fun `a refusal is summed up in the service's own sentence, or the raw detail when there is none`() {
        val refusal =
            VoiceAttempt.Failed(
                401,
                """{"error":{"message":"Incorrect API key provided.","type":"invalid_request_error","code":null}}""",
            )
        assertEquals("401 Incorrect API key provided.", refusal.summary())
        assertEquals("402 Unauthorized", VoiceAttempt.Failed(402, """{"detail":"Unauthorized"}""").summary())
        assertEquals(
            "0 java.net.UnknownHostException",
            VoiceAttempt.Failed(0, "java.net.UnknownHostException").summary(),
        )
        assertEquals("500", VoiceAttempt.Failed(500, "").summary())
    }

    @Test
    fun `a profile carries its personality for the casting, and a manifest may name a voice outright`() {
        val personalities = listOf("cynical", "dramatic", "confused", "judgmental", "bureaucratic", "indifferent")
        personalities.forEach { assertEquals(it, VoiceProfile.forPersonality(it).personality) }
        assertNull(VoiceProfile.forPersonality("cynical").neuralVoiceId)
        assertEquals("mysterious", VoiceProfile.forPersonality("mysterious").personality)
        val named = VoiceProfile.clamped(0.7f, 1f, "custom", "cynical")
        assertEquals("custom", named.neuralVoiceId)
        assertEquals("cynical", named.personality)
        assertTrue(NeuralSpeech.ENDPOINT.startsWith("https://api.openai.com/"))
    }
}
