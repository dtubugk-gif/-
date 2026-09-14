package il.rikavon.core.data.domain

import il.rikavon.core.data.model.AppLimit
import il.rikavon.core.data.model.AppUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusScoreCalculatorTest {
    private val calculator = FocusScoreCalculator()
    private val dayStart = 0L
    private val hour = 3_600_000L

    private fun limit(pkg: String, minutes: Int, fullBlock: Boolean = false) =
        AppLimit(pkg, minutes, fullBlock, enabled = true, createdAt = 0)

    private fun usage(pkg: String, minutes: Int, opens: List<Long>) = AppUsage(pkg, minutes, opens.size, opens)

    @Test
    fun `no limits gives a perfect score`() {
        val score = calculator.calculate(emptyList(), dayStart, dayStart + 8 * hour)
        assertEquals(100, score.total)
    }

    @Test
    fun `untouched apps and a long clean streak score 100`() {
        val apps = listOf(ScoredApp(limit("a", 60), usage("a", 0, emptyList())))
        val score = calculator.calculate(apps, dayStart, dayStart + 4 * hour)
        assertEquals(50, score.minutesPart)
        assertEquals(30, score.opensPart)
        assertEquals(20, score.streakPart)
        assertEquals(100, score.total)
    }

    @Test
    fun `minutes at the limit zero out the minutes part`() {
        val apps = listOf(ScoredApp(limit("a", 60), usage("a", 60, listOf(hour))))
        val score = calculator.calculate(apps, dayStart, dayStart + 3 * hour)
        assertEquals(0, score.minutesPart)
    }

    @Test
    fun `minutes are aggregated across apps against the total limit`() {
        val apps =
            listOf(
                ScoredApp(limit("a", 60), usage("a", 30, emptyList())),
                ScoredApp(limit("b", 40), usage("b", 20, emptyList())),
            )
        val score = calculator.calculate(apps, dayStart, dayStart + 3 * hour)
        // 50 of 100 minutes used -> half of 50 points.
        assertEquals(25, score.minutesPart)
    }

    @Test
    fun `opens above double the target zero out the opens part`() {
        val opens = List(20) { dayStart + it * 60_000L }
        val apps = listOf(ScoredApp(limit("a", 30), usage("a", 5, opens)))
        val score = calculator.calculate(apps, dayStart, dayStart + 3 * hour)
        assertEquals(0, score.opensPart)
    }

    @Test
    fun `opens target is at least three per app`() {
        val opens = listOf(dayStart + hour, dayStart + 2 * hour, dayStart + 3 * hour)
        val apps = listOf(ScoredApp(limit("a", 5), usage("a", 1, opens)))
        val score = calculator.calculate(apps, dayStart, dayStart + 4 * hour)
        // 3 opens against a target of 3 -> ratio 1 -> zero points; two opens would still leave a third.
        assertEquals(0, score.opensPart)
        val fewer =
            calculator.calculate(
                listOf(ScoredApp(limit("a", 5), usage("a", 1, opens.take(2)))),
                dayStart,
                dayStart + 4 * hour,
            )
        assertEquals(10, fewer.opensPart)
    }

    @Test
    fun `longest clean streak is the largest gap between opens including day edges`() {
        val opens = listOf(dayStart + hour, dayStart + 2 * hour)
        val streak = calculator.longestCleanStreakMinutes(opens, dayStart, dayStart + 6 * hour)
        assertEquals(240, streak)
    }

    @Test
    fun `streak part saturates at the target`() {
        val apps = listOf(ScoredApp(limit("a", 60), usage("a", 10, listOf(dayStart + hour))))
        val score = calculator.calculate(apps, dayStart, dayStart + 10 * hour)
        assertEquals(20, score.streakPart)
    }

    @Test
    fun `full block counts any usage as over the limit`() {
        val apps = listOf(ScoredApp(limit("a", 60, fullBlock = true), usage("a", 2, listOf(dayStart + hour))))
        val score = calculator.calculate(apps, dayStart, dayStart + 2 * hour)
        assertEquals(0, score.minutesPart)
        assertEquals(0, score.opensPart)
    }

    @Test
    fun `disabled limits are ignored`() {
        val disabled = limit("a", 10).copy(enabled = false)
        val apps = listOf(ScoredApp(disabled, usage("a", 500, List(50) { dayStart + it * 1000L })))
        val score = calculator.calculate(apps, dayStart, dayStart + 2 * hour)
        assertEquals(100, score.total)
    }

    @Test
    fun `total never leaves the 0 to 100 range`() {
        val opens = List(500) { dayStart + it * 10_000L }
        val apps = listOf(ScoredApp(limit("a", 5), usage("a", 900, opens)))
        val score = calculator.calculate(apps, dayStart, dayStart + 2 * hour)
        assertTrue(score.total in 0..100)
    }
}
