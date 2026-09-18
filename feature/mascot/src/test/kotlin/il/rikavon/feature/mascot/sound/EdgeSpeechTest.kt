package il.rikavon.feature.mascot.sound

import il.rikavon.feature.mascot.model.VoiceProfile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeSpeechTest {
    @Test
    fun `the clock proof is the five-minute slot in windows ticks with the token, hashed`() {
        assertEquals(
            "42301B335578FEFDAE2637DED1ABD614505D432559EC08032B82048483726AFF",
            EdgeSpeech.securityToken(1_700_000_000L),
        )
        // Anywhere inside the same five-minute slot gives the same proof; the next slot gives another.
        assertEquals(EdgeSpeech.securityToken(1_700_000_000L), EdgeSpeech.securityToken(1_700_000_099L))
        assertTrue(EdgeSpeech.securityToken(1_700_000_000L) != EdgeSpeech.securityToken(1_700_000_100L))
    }

    @Test
    fun `the url carries the token, the proof for the corrected clock, the version and the connection id`() {
        val url = EdgeSpeech.url(nowMillis = 1_700_000_000_000L, skewMillis = 0L, connectionId = "abc123")
        assertTrue(url, url.startsWith("wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?"))
        assertTrue(url, url.contains("TrustedClientToken=6A5AA1D4EAFF4E9FB37E23D68491D6F4"))
        assertTrue(url, url.contains("&Sec-MS-GEC=42301B335578FEFDAE2637DED1ABD614505D432559EC08032B82048483726AFF&"))
        assertTrue(url, url.contains("&Sec-MS-GEC-Version=1-130.0.2849.68&"))
        assertTrue(url, url.endsWith("&ConnectionId=abc123"))
        val skewed = EdgeSpeech.url(nowMillis = 1_700_000_000_000L, skewMillis = 600_000L, connectionId = "abc123")
        assertTrue(skewed, !skewed.contains(EdgeSpeech.securityToken(1_700_000_000L)))
    }

    @Test
    fun `the timestamp and the two messages are shaped the way the browser shapes them`() {
        val timestamp = EdgeSpeech.timestamp(1_700_000_000_000L)
        assertEquals("Tue Nov 14 2023 22:13:20 GMT+0000 (Coordinated Universal Time)", timestamp)
        val config = EdgeSpeech.configMessage(timestamp)
        assertTrue(
            config,
            config.startsWith(
                "X-Timestamp:$timestamp\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n{",
            ),
        )
        assertTrue(config, config.contains("\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\""))
        val ssml = EdgeSpeech.ssmlMessage("id1", timestamp, "<speak/>")
        assertEquals(
            "X-RequestId:id1\r\nContent-Type:application/ssml+xml\r\n" +
                "X-Timestamp:${timestamp}Z\r\nPath:ssml\r\n\r\n<speak/>",
            ssml,
        )
        assertTrue(EdgeSpeech.isTurnEnd("X-RequestId:id1\r\nPath:turn.end\r\n\r\n{}"))
        assertTrue(!EdgeSpeech.isTurnEnd("X-RequestId:id1\r\nPath:turn.start\r\n\r\n{}"))
    }

    @Test
    fun `audio is read out of binary messages by their header, other messages are ignored`() {
        val header = "X-RequestId:id1\r\nContent-Type:audio/mpeg\r\nPath:audio\r\n".toByteArray()
        val audio = byteArrayOf(1, 2, 3, 4)
        val frame = byteArrayOf((header.size shr 8).toByte(), header.size.toByte()) + header + audio
        assertArrayEquals(audio, EdgeSpeech.audioIn(frame))
        val other = "Path:audio.metadata\r\n".toByteArray()
        assertNull(EdgeSpeech.audioIn(byteArrayOf(0, other.size.toByte()) + other + audio))
        assertNull(EdgeSpeech.audioIn(byteArrayOf(0)))
        assertNull(EdgeSpeech.audioIn(byteArrayOf(0, 9, 1)))
    }

    @Test
    fun `the clock skew comes from the service's date header`() {
        val skew = EdgeSpeech.skewMillis("Tue, 14 Nov 2023 22:23:20 GMT", nowMillis = 1_700_000_000_000L)
        assertEquals(600_000L, skew)
        assertNull(EdgeSpeech.skewMillis("yesterday", 0L))
        assertNull(EdgeSpeech.skewMillis(null, 0L))
    }

    @Test
    fun `hebrew gets avri or hila, english a voice the channel has, and the picker wins`() {
        val cynic = VoiceProfile.forPersonality("cynical")
        assertEquals("he-IL-AvriNeural", EdgeSpeech.voice(cynic, "he-IL"))
        assertEquals("en-US-AndrewMultilingualNeural", EdgeSpeech.voice(cynic, "en"))
        assertEquals("he-IL-HilaNeural", EdgeSpeech.voice(VoiceProfile.forPersonality("dramatic"), "iw"))
        assertEquals("en-US-GuyNeural", EdgeSpeech.voice(cynic, "en", "en-US-GuyNeural"))
        assertEquals("he-IL-AvriNeural", EdgeSpeech.voice(cynic, "he", "onyx"))
        listOf("cynical", "dramatic", "confused", "judgmental", "bureaucratic", "indifferent").forEach {
            assertTrue(EdgeSpeech.voice(VoiceProfile.forPersonality(it), "en") in EdgeSpeech.VOICES)
        }
    }
}
