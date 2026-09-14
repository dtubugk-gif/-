package il.rikavon.core.data.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageSessionAggregatorTest {
    private fun resumed(t: Long, pkg: String = "a") = UsageEvent(t, pkg, UsageEventKind.RESUMED)

    private fun paused(t: Long, pkg: String = "a") = UsageEvent(t, pkg, UsageEventKind.PAUSED)

    private fun screenOff(t: Long) = UsageEvent(t, "android", UsageEventKind.SCREEN_OFF)

    @Test
    fun `a resume followed by a pause counts one open and the elapsed minutes`() {
        val agg = UsageSessionAggregator()
        agg.feed(listOf(resumed(0), paused(5 * 60_000L)))
        val snapshot = agg.snapshot(10 * 60_000L)
        assertEquals(1, snapshot.getValue("a").opens)
        assertEquals(5, snapshot.getValue("a").minutes)
    }

    @Test
    fun `returning within two seconds is not a new open`() {
        val agg = UsageSessionAggregator()
        agg.feed(listOf(resumed(0), paused(10_000), resumed(11_500), paused(20_000)))
        assertEquals(1, agg.snapshot(30_000).getValue("a").opens)
    }

    @Test
    fun `returning after more than two seconds is a new open`() {
        val agg = UsageSessionAggregator()
        agg.feed(listOf(resumed(0), paused(10_000), resumed(12_100), paused(20_000)))
        assertEquals(2, agg.snapshot(30_000).getValue("a").opens)
    }

    @Test
    fun `activity switches inside the same app are not opens`() {
        val agg = UsageSessionAggregator()
        // Activity A1 resumes, then A2 resumes without an intervening pause of the package.
        agg.feed(listOf(resumed(0), resumed(30_000), paused(60_000)))
        assertEquals(1, agg.snapshot(60_000).getValue("a").opens)
        assertEquals(1, agg.snapshot(60_000).getValue("a").minutes)
    }

    @Test
    fun `switching to another app closes the first session`() {
        val agg = UsageSessionAggregator()
        agg.feed(listOf(resumed(0, "a"), resumed(120_000, "b"), paused(180_000, "b")))
        val snapshot = agg.snapshot(180_000)
        assertEquals(2, snapshot.getValue("a").minutes)
        assertEquals(1, snapshot.getValue("b").minutes)
        assertEquals(1, snapshot.getValue("a").opens)
        assertEquals(1, snapshot.getValue("b").opens)
    }

    @Test
    fun `screen off closes the session and clears the foreground`() {
        val agg = UsageSessionAggregator()
        agg.feed(listOf(resumed(0), screenOff(60_000)))
        assertNull(agg.foregroundPackage)
        assertEquals(1, agg.snapshot(600_000).getValue("a").minutes)
    }

    @Test
    fun `an open session keeps accruing minutes until now`() {
        val agg = UsageSessionAggregator()
        agg.feed(listOf(resumed(0)))
        assertEquals("a", agg.foregroundPackage)
        assertEquals(7, agg.snapshot(7 * 60_000L + 500).getValue("a").minutes)
    }

    @Test
    fun `incremental feeding matches one-shot feeding`() {
        val events =
            listOf(
                resumed(0),
                paused(30_000),
                resumed(45_000),
                paused(90_000),
                resumed(200_000, "b"),
                paused(260_000, "b"),
            )
        val oneShot = UsageSessionAggregator().apply { feed(events) }
        val incremental = UsageSessionAggregator().apply { events.forEach { feed(it) } }
        assertEquals(oneShot.snapshot(300_000), incremental.snapshot(300_000))
    }

    @Test
    fun `open timestamps are recorded for deduplicated opens only`() {
        val agg = UsageSessionAggregator()
        agg.feed(listOf(resumed(1_000), paused(5_000), resumed(6_000), paused(9_000), resumed(20_000)))
        assertEquals(listOf(1_000L, 20_000L), agg.snapshot(25_000).getValue("a").openTimestamps)
    }
}
