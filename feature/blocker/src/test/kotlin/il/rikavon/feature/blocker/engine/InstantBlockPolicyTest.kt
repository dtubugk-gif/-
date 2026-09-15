package il.rikavon.feature.blocker.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstantBlockPolicyTest {
    private val policy = InstantBlockPolicy(ownPackage = "il.rikavon")
    private val blocked = setOf("com.zhiliaoapp.musically", "com.instagram.android")

    @Test
    fun `a blocked package coming to the front is sent home`() {
        assertTrue(policy.shouldSendHome("com.instagram.android", blocked))
    }

    @Test
    fun `other packages and missing package names are left alone`() {
        assertFalse(policy.shouldSendHome("com.whatsapp", blocked))
        assertFalse(policy.shouldSendHome(null, blocked))
        assertFalse(policy.shouldSendHome("com.instagram.android", emptySet()))
    }

    @Test
    fun `our own windows never trigger`() {
        assertFalse(policy.shouldSendHome("il.rikavon", blocked + "il.rikavon"))
    }
}
