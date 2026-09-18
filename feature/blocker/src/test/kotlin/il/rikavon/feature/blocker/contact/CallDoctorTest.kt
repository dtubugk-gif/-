package il.rikavon.feature.blocker.contact

import il.rikavon.core.data.model.AppLimit
import il.rikavon.core.data.model.Settings
import il.rikavon.core.data.permissions.PermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallDoctorTest {
    private val now = 1_000_000L
    private val ready = Settings.DEFAULT.copy(trackingEnabled = true, petCallsEnabled = true)
    private val granted =
        PermissionState(
            usageAccess = true,
            overlay = true,
            notifications = true,
            exactAlarm = true,
            ignoresBatteryOptimization = true,
        )
    private val begging =
        listOf(AppLimit("com.instagram.android", 30, fullBlock = false, enabled = true, createdAt = 0L))
    private val awake = CallHealthState(lastPollAt = now - 10_000L)

    private fun diagnose(
        prefs: Settings = ready,
        limits: List<AppLimit> = begging,
        state: CallHealthState = awake,
        perms: PermissionState = granted,
    ) = CallDoctor.diagnose(prefs, limits, state, perms, now)

    @Test
    fun `the first missing precondition wins, in the order a person fixes them`() {
        assertEquals(CallDiagnosis.TrackingOff, diagnose(prefs = ready.copy(trackingEnabled = false)))
        assertEquals(CallDiagnosis.NoUsageAccess, diagnose(perms = granted.copy(usageAccess = false, overlay = false)))
        assertEquals(CallDiagnosis.CallsOff, diagnose(prefs = ready.copy(petCallsEnabled = false)))
        assertEquals(CallDiagnosis.ServiceAsleep(0L), diagnose(state = CallHealthState()))
        val stale = now - CallDoctor.ASLEEP_AFTER_MILLIS - 1
        assertEquals(CallDiagnosis.ServiceAsleep(stale), diagnose(state = CallHealthState(lastPollAt = stale)))
        assertEquals(CallDiagnosis.NothingToCallAbout, diagnose(limits = emptyList()))
        assertEquals(
            CallDiagnosis.NothingToCallAbout,
            diagnose(limits = begging.map { it.copy(callOnOpen = false) } + begging.map { it.copy(enabled = false) }),
        )
        assertEquals(CallDiagnosis.NoOverlay(notifications = true), diagnose(perms = granted.copy(overlay = false)))
        assertEquals(
            CallDiagnosis.NoOverlay(notifications = false),
            diagnose(perms = granted.copy(overlay = false, notifications = false)),
        )
    }

    @Test
    fun `with everything in place the diagnosis carries what the machinery last did`() {
        val state = awake.copy(lastRing = Ring("com.instagram.android", now - 60_000L, RingPath.OVERLAY))
        val diagnosis = diagnose(state = state)
        assertTrue(diagnosis is CallDiagnosis.Ready)
        assertEquals(state, (diagnosis as CallDiagnosis.Ready).health)
    }
}
