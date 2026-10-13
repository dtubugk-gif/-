package com.dtubugk.shakelight.cast

import kotlin.math.hypot

/** One mouse-pointer sample from the computer, already converted to phone screen pixels. */
data class TouchPoint(val x: Float, val y: Float, val timeMs: Long)

/** What a mouse press-move-release from the computer should become on the phone's touch screen. */
sealed interface GesturePlan {
    data class Tap(val x: Float, val y: Float) : GesturePlan
    data class LongPress(val x: Float, val y: Float, val durationMs: Long) : GesturePlan
    data class Swipe(val points: List<TouchPoint>, val durationMs: Long) : GesturePlan

    /** Press and hold, then drag (moving home-screen icons, selecting text…). */
    data class HoldThenDrag(val start: TouchPoint, val holdMs: Long, val points: List<TouchPoint>, val moveMs: Long) : GesturePlan

    companion object {
        const val LONG_PRESS_MS = 450L
        private const val MAX_DURATION_MS = 10_000L

        fun from(points: List<TouchPoint>, slopPx: Float): GesturePlan? {
            if (points.isEmpty()) return null
            val start = points.first()
            val total = (points.last().timeMs - start.timeMs).coerceIn(1, MAX_DURATION_MS)
            fun moved(p: TouchPoint) = hypot(p.x - start.x, p.y - start.y) > slopPx

            val firstMove = points.indexOfFirst(::moved)
            if (firstMove < 0) {
                return if (total < LONG_PRESS_MS) Tap(start.x, start.y) else LongPress(start.x, start.y, total)
            }

            val holdMs = points[firstMove - 1].timeMs - start.timeMs
            if (holdMs >= LONG_PRESS_MS) {
                val moving = points.subList(firstMove, points.size)
                val moveMs = (points.last().timeMs - points[firstMove - 1].timeMs).coerceIn(20, MAX_DURATION_MS)
                return HoldThenDrag(start, holdMs, moving, moveMs)
            }
            return Swipe(points, total.coerceAtLeast(20))
        }
    }
}
