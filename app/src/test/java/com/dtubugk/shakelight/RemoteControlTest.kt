package com.dtubugk.shakelight

import com.dtubugk.shakelight.cast.GesturePlan
import com.dtubugk.shakelight.cast.TextEdit
import com.dtubugk.shakelight.cast.TouchPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteControlTest {

    private val slop = 12f

    private fun pts(vararg p: Triple<Number, Number, Long>) =
        p.map { TouchPoint(it.first.toFloat(), it.second.toFloat(), it.third) }

    @Test
    fun quickClickIsTap() {
        val plan = GesturePlan.from(pts(Triple(100, 200, 0L), Triple(102, 201, 90L)), slop)
        assertEquals(GesturePlan.Tap(100f, 200f), plan)
    }

    @Test
    fun heldClickIsLongPress() {
        val plan = GesturePlan.from(pts(Triple(100, 200, 0L), Triple(101, 200, 800L)), slop)
        assertEquals(GesturePlan.LongPress(100f, 200f, 800L), plan)
    }

    @Test
    fun dragIsSwipe() {
        val plan = GesturePlan.from(pts(Triple(500, 1500, 0L), Triple(500, 1000, 80L), Triple(500, 400, 160L)), slop)
        assertTrue(plan is GesturePlan.Swipe)
        assertEquals(160L, (plan as GesturePlan.Swipe).durationMs)
    }

    @Test
    fun holdThenMoveIsHoldThenDrag() {
        val plan = GesturePlan.from(
            pts(Triple(300, 300, 0L), Triple(301, 300, 600L), Triple(400, 300, 700L), Triple(600, 300, 900L)), slop,
        )
        assertTrue(plan is GesturePlan.HoldThenDrag)
        plan as GesturePlan.HoldThenDrag
        assertEquals(600L, plan.holdMs)
        assertEquals(300L, plan.moveMs)
        assertEquals(2, plan.points.size)
    }

    @Test
    fun noPointsNoGesture() {
        assertNull(GesturePlan.from(emptyList(), slop))
    }

    @Test
    fun typingInsertsAtCursor() {
        assertEquals(TextEdit.Result("helXlo", 4), TextEdit.insert("hello", 3, 3, "X"))
        assertEquals(TextEdit.Result("hello!", 6), TextEdit.insert("hello", -1, -1, "!"))
        assertEquals(TextEdit.Result("hi there", 8), TextEdit.insert("hi you", 3, 6, "there"))
        assertEquals(TextEdit.Result("שלום", 4), TextEdit.insert("שלו", 3, 3, "ם"))
    }

    @Test
    fun backspaceDeletesOneCharacterOrSelection() {
        assertEquals(TextEdit.Result("helo", 3), TextEdit.backspace("hello", 4, 4))
        assertEquals(TextEdit.Result("ho", 1), TextEdit.backspace("hello", 1, 4))
        assertEquals(TextEdit.Result("abc", 0), TextEdit.backspace("abc", 0, 0))
        assertEquals(TextEdit.Result("hi", 2), TextEdit.backspace("hi😀", -1, -1))
    }
}
