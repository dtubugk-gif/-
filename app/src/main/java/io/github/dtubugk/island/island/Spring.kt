package io.github.dtubugk.island.island

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A critically-/under-damped spring (mass = 1). Retargeting keeps the current velocity, so every
 * animation is interruptible mid-flight without a visible jump, which is what makes the island
 * feel physical.
 */
class Spring(
    var value: Float,
    private val threshold: Float,
) {
    var target: Float = value
        private set
    var velocity: Float = 0f
        private set
    private var stiffness = 300f
    private var dampingRatio = 1f
    private var delay = 0f

    val isAtRest: Boolean get() = delay <= 0f && value == target && velocity == 0f

    fun animateTo(target: Float, stiffness: Float, dampingRatio: Float, delaySeconds: Float = 0f) {
        this.target = target
        this.stiffness = stiffness
        this.dampingRatio = dampingRatio
        this.delay = delaySeconds
    }

    fun snapTo(target: Float) {
        this.target = target
        value = target
        velocity = 0f
        delay = 0f
    }

    /** Advances by [dt] seconds. Returns true while still moving. */
    fun step(dt: Float): Boolean {
        if (isAtRest) return false
        var time = dt
        if (delay > 0f) {
            delay -= time
            if (delay > 0f) return true
            time = -delay
            delay = 0f
        }
        val damping = 2f * dampingRatio * sqrt(stiffness)
        // Semi-implicit Euler in 4 ms sub-steps: stable for every stiffness used here.
        while (time > 0f) {
            val h = min(time, SUB_STEP)
            val acceleration = -stiffness * (value - target) - damping * velocity
            velocity += acceleration * h
            value += velocity * h
            time -= h
        }
        if (abs(value - target) < threshold && abs(velocity) < threshold * 12f) {
            value = target
            velocity = 0f
            return false
        }
        return true
    }

    private companion object {
        const val SUB_STEP = 0.004f
    }
}
