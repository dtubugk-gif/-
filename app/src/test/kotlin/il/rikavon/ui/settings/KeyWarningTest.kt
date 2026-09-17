package il.rikavon.ui.settings

import il.rikavon.core.data.repo.CloudKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyWarningTest {
    @Test
    fun `an Anthropic key in the voice dialog, or a foreign key in the brain dialog, is called out`() {
        assertEquals(KeyWarning.ANTHROPIC_FOR_VOICE, KeyWarning.of(CloudKey.VOICE, " sk-ant-api03-abc "))
        assertEquals(KeyWarning.NOT_ANTHROPIC_FOR_BRAIN, KeyWarning.of(CloudKey.BRAIN, "sk-proj-abc"))
        assertNull(KeyWarning.of(CloudKey.VOICE, "sk-proj-abc"))
        assertNull(KeyWarning.of(CloudKey.BRAIN, "sk-ant-api03-abc"))
        assertNull(KeyWarning.of(CloudKey.VOICE, ""))
        assertNull(KeyWarning.of(CloudKey.BRAIN, "   "))
    }
}
