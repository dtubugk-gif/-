package io.github.dtubugk.island

import io.github.dtubugk.island.data.IslandConfig
import io.github.dtubugk.island.data.TextSide
import io.github.dtubugk.island.island.Hole
import io.github.dtubugk.island.island.IslandGeometry
import io.github.dtubugk.island.island.ScreenSpec
import io.github.dtubugk.island.island.Spring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class IslandGeometryTest {
    // Galaxy S24-like: 1080 px wide at 2.8125 density, 52 px punch hole centered 50 px down.
    private val density = 2.8125f
    private val galaxy = ScreenSpec(1080f, 110f, density, Hole(540f, 50f, 52f))
    private val glyph = 20f * density * 0.56f * 0.7f

    @Test
    fun idleIslandIsCenteredOnTheCameraAndSwallowsIt() {
        val l = IslandGeometry.compute(galaxy, IslandConfig(), glyph)
        assertEquals(540f, l.centerX, 0.01f)
        assertEquals(0f, l.holeOffsetX, 0.01f)
        val top = l.top
        val bottom = l.top + l.idle.height
        assertTrue("island covers the hole vertically", top <= 50f - 26f && bottom >= 50f + 26f)
        assertTrue("island covers the hole horizontally", l.idle.width / 2f >= 26f)
        assertEquals(l.idle.height / 2f, l.idle.radius, 0.01f)
    }

    @Test
    fun textNeverOverlapsTheCamera() {
        for (width in listOf(0f, 40f, 64f, 104f, 220f)) {
            val l = IslandGeometry.compute(galaxy, IslandConfig(widthDp = width), glyph)
            val textInner = abs(l.textCenterX) - glyph / 2f
            assertTrue("width $width: text inner edge $textInner must clear the hole", textInner >= l.holeRadius)
            val textOuter = abs(l.textCenterX) + glyph / 2f
            assertTrue("width $width: text stays inside", textOuter <= l.idle.width / 2f)
        }
    }

    @Test
    fun textSideFollowsTheSetting() {
        val right = IslandGeometry.compute(galaxy, IslandConfig(textSide = TextSide.RIGHT), glyph)
        val left = IslandGeometry.compute(galaxy, IslandConfig(textSide = TextSide.LEFT), glyph)
        assertTrue(right.textCenterX > 0f)
        assertEquals(-right.textCenterX, left.textCenterX, 0.01f)
    }

    @Test
    fun heightIsRaisedToFitALargeCamera() {
        val bigHole = galaxy.copy(hole = Hole(540f, 60f, 110f))
        val l = IslandGeometry.compute(bigHole, IslandConfig(heightDp = 22f), glyph)
        assertTrue(l.idle.height >= 110f)
    }

    @Test
    fun islandNeverLeavesTheScreen() {
        val l = IslandGeometry.compute(galaxy, IslandConfig(widthDp = 220f, offsetXDp = 60f), glyph)
        assertTrue(l.centerX + l.idle.width / 2f <= galaxy.width)
        assertTrue(l.centerX - l.expanded.width / 2f >= 0f)
        assertTrue(l.centerX + l.expanded.width / 2f <= galaxy.width)
        assertTrue(l.top >= 0f)
    }

    @Test
    fun expandedCardIsWiderAndTallerThanIdle() {
        val l = IslandGeometry.compute(galaxy, IslandConfig(), glyph)
        assertTrue(l.expanded.width > l.charging.width && l.charging.width >= l.idle.width)
        assertTrue(l.expanded.height > l.idle.height)
    }

    @Test
    fun screensWithoutCutoutFallBackToStatusBarCenter() {
        val plain = galaxy.copy(hole = null)
        val l = IslandGeometry.compute(plain, IslandConfig(), glyph)
        assertEquals(540f, l.centerX, 0.01f)
        assertEquals(55f, l.top + l.idle.height / 2f, 0.01f)
    }

    @Test
    fun springSettlesOnTargetAndOvershootsWhenUnderdamped() {
        val s = Spring(0f, 0.5f)
        s.animateTo(100f, stiffness = 260f, dampingRatio = 0.72f)
        var peak = 0f
        var t = 0f
        while (s.step(1f / 120f) && t < 5f) {
            peak = maxOf(peak, s.value)
            t += 1f / 120f
        }
        assertEquals(100f, s.value, 0f)
        assertTrue("bouncy open overshoots a little", peak in 101f..110f)
        assertTrue("settles well under a second", t < 1f)
        assertFalse(s.step(1f / 120f))
    }
}
