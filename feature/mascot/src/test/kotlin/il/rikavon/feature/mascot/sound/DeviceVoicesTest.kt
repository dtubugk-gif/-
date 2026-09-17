package il.rikavon.feature.mascot.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceVoicesTest {
    private val local = DeviceVoice("he-il-x-local", "heb", quality = 300, latency = 200, network = false)
    private val networkA = DeviceVoice("he-il-x-a-network", "heb", quality = 300, latency = 400, network = true)
    private val networkB = DeviceVoice("he-il-x-b-network", "heb", quality = 300, latency = 400, network = true)
    private val english = DeviceVoice("en-us-x-network", "eng", quality = 500, latency = 400, network = true)
    private val legacy = DeviceVoice("iw-il-x-old", "iw", quality = 200, latency = 100, network = false)

    @Test
    fun `online, a network voice of the language wins, and offline the local one`() {
        val voices = listOf(english, local, networkA, legacy)
        assertEquals(networkA, DeviceVoices.pick(voices, "he-IL", "cynical", online = true))
        assertEquals(local, DeviceVoices.pick(voices, "he-IL", "cynical", online = false))
        assertEquals(english, DeviceVoices.pick(voices, "en-US", "cynical", online = true))
        assertNull(DeviceVoices.pick(voices, "en-US", "cynical", online = false))
        assertNull(DeviceVoices.pick(emptyList(), "he", "cynical", online = true))
    }

    @Test
    fun `higher quality beats a network voice, and the legacy Hebrew code still counts as Hebrew`() {
        val better = local.copy(name = "he-il-x-hd-local", quality = 400)
        assertEquals(better, DeviceVoices.pick(listOf(networkA, better, legacy), "he", "cynical", online = true))
        assertEquals(legacy, DeviceVoices.pick(listOf(legacy, english), "iw", "cynical", online = true))
    }

    @Test
    fun `equally good voices are spread across pets, the same pet always getting the same one`() {
        val voices = listOf(local, networkA, networkB)
        val picks =
            listOf("cynical", "dramatic", "confused", "judgmental", "bureaucratic", "indifferent")
                .map { DeviceVoices.pick(voices, "he", it, online = true) }
        assertEquals(setOf(networkA, networkB), picks.toSet())
        assertNotEquals(
            DeviceVoices.pick(voices, "he", "cynical", online = true),
            DeviceVoices.pick(voices, "he", "dramatic", online = true),
        )
        assertEquals(
            DeviceVoices.pick(voices, "he", "cynical", online = true),
            DeviceVoices.pick(voices, "he", "cynical", online = true),
        )
    }
}
