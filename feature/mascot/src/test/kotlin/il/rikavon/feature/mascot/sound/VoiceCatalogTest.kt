package il.rikavon.feature.mascot.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCatalogTest {
    private val voices =
        listOf(
            CloudVoice("lib1", "Zed", category = "professional", labels = mapOf("gender" to "male", "age" to "old")),
            CloudVoice("p1", "Bea", category = "premade", labels = mapOf("gender" to "female", "age" to "young")),
            CloudVoice("p2", "Cal", category = "premade", labels = mapOf("gender" to "male", "age" to "middle_aged")),
            CloudVoice("p3", "Dot", category = "premade", labels = mapOf("gender" to "female", "age" to "middle_aged")),
            CloudVoice("p4", "Abe", category = "premade", labels = mapOf("gender" to "male", "age" to "old")),
        )

    @Test
    fun `premade voices win over library ones, then gender and age decide`() {
        assertEquals("p4", VoiceCatalog.pick(voices, "indifferent")?.id)
        assertEquals("p2", VoiceCatalog.pick(voices, "bureaucratic")?.id)
        assertEquals("p1", VoiceCatalog.pick(voices, "confused")?.id)
        assertEquals("p3", VoiceCatalog.pick(voices, "judgmental")?.id)
    }

    @Test
    fun `an unknown personality still gets a premade voice, and no voices means none`() {
        assertEquals("premade", VoiceCatalog.pick(voices, "mysterious")?.category)
        assertNull(VoiceCatalog.pick(emptyList(), "cynical"))
    }

    @Test
    fun `the pick is stable across calls`() {
        val first = VoiceCatalog.pick(voices.shuffled(), "dramatic")?.id
        repeat(5) { assertEquals(first, VoiceCatalog.pick(voices.shuffled(), "dramatic")?.id) }
    }
}
