package il.rikavon.feature.mascot.sound

import il.rikavon.feature.mascot.model.VoiceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AzureSpeechTest {
    @Test
    fun `hebrew lines get a native hebrew voice and english lines a multilingual one, by personality`() {
        val personalities = listOf("cynical", "dramatic", "confused", "judgmental", "bureaucratic", "indifferent")
        personalities.forEach {
            val profile = VoiceProfile.forPersonality(it)
            assertTrue(AzureSpeech.voice(profile, "he-IL").startsWith("he-IL-"))
            assertTrue(AzureSpeech.voice(profile, "en-US").startsWith("en-US-"))
        }
        assertEquals("he-IL-AvriNeural", AzureSpeech.voice(VoiceProfile.forPersonality("cynical"), "iw"))
        assertEquals("he-IL-HilaNeural", AzureSpeech.voice(VoiceProfile.forPersonality("dramatic"), "he"))
        assertEquals("he-IL-AvriNeural", AzureSpeech.voice(VoiceProfile.forPersonality("mysterious"), "he"))
        assertEquals(
            "he-IL-HilaNeural",
            AzureSpeech.voice(VoiceProfile.forPersonality("cynical"), "he", "he-IL-HilaNeural"),
        )
        assertEquals("he-IL-AvriNeural", AzureSpeech.voice(VoiceProfile.forPersonality("cynical"), "he", "onyx"))
    }

    @Test
    fun `the manifest's pitch and rate become prosody, within what keeps a neural voice natural`() {
        assertEquals("-12%", AzureSpeech.pitch(0.75f))
        assertEquals("+18%", AzureSpeech.pitch(1.35f))
        assertEquals("-20%", AzureSpeech.pitch(0.5f))
        assertEquals("+0%", AzureSpeech.pitch(1f))
        assertEquals("-20%", AzureSpeech.rate(0.8f))
        assertEquals("+15%", AzureSpeech.rate(1.15f))
        assertEquals("+30%", AzureSpeech.rate(2f))
    }

    @Test
    fun `the ssml names the locale, the voice and the prosody, and escapes the text`() {
        val ssml =
            AzureSpeech.ssml(
                "א <b> & \"ב\" 'ג'",
                "he-IL-AvriNeural",
                "he-IL",
                VoiceProfile.forPersonality("cynical"),
            )
        assertTrue(
            ssml,
            ssml.startsWith("<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='he-IL'>"),
        )
        assertTrue(ssml, ssml.contains("<voice name='he-IL-AvriNeural'>"))
        assertTrue(ssml, ssml.contains("<prosody pitch='-12%' rate='-5%'>"))
        assertTrue(ssml, ssml.contains("א &lt;b&gt; &amp; &quot;ב&quot; &apos;ג&apos;"))
        assertTrue(ssml, ssml.endsWith("</prosody></voice></speak>"))
        assertTrue(
            AzureSpeech
                .ssml(
                    "hi",
                    "en-US-AvaMultilingualNeural",
                    "en",
                    VoiceProfile.NEUTRAL,
                ).contains("xml:lang='en-US'"),
        )
    }

    @Test
    fun `the region is cleaned into an id and put into the endpoint`() {
        assertEquals("westeurope", AzureSpeech.normalizeRegion(" West Europe "))
        assertEquals("https://eastus2.tts.speech.microsoft.com/cognitiveservices/v1", AzureSpeech.endpoint("eastus2"))
        assertEquals("", AzureSpeech.normalizeRegion("  "))
    }
}
