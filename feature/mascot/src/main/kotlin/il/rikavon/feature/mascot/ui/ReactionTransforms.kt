package il.rikavon.feature.mascot.ui

import il.rikavon.feature.mascot.model.ReactionPreset
import kotlin.math.PI
import kotlin.math.sin

/** Transform applied to the mascot layer at a given point of a long-press reaction. */
data class ReactionFrame(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val rotationZ: Float = 0f,
    val rotationY: Float = 0f,
    val alpha: Float = 1f,
)

typealias ReactionTransform = (progress: Float, widthPx: Float) -> ReactionFrame

/** Each preset maps progress 0..1 to a frame; all return to identity at 0 and 1. */
object ReactionTransforms {
    private const val PULSES = 3f
    private const val PULSE_AMOUNT = 0.14f
    private const val WILT_ANGLE = 38f
    private const val WILT_DROP = 0.08f
    private const val FULL_TURN = 360f
    private const val TURN_AWAY_ANGLE = -55f
    private const val TURN_AWAY_SHIFT = 0.12f
    private const val GLITCH_FREQ = 42f
    private const val GLITCH_AMPLITUDE = 0.05f
    private const val GLITCH_FLICKER_FREQ = 27f
    private const val GLITCH_FLICKER = 0.35f
    private const val ROLL_SHIFT = 0.18f
    private const val ROLL_HOP = 0.05f
    private const val TWO_PI = (2 * PI).toFloat()
    private const val PI_F = PI.toFloat()

    fun forPreset(preset: ReactionPreset): ReactionTransform =
        when (preset) {
            ReactionPreset.PULSE -> { p, _ ->
                val s = 1f + PULSE_AMOUNT * sin(p * PI_F * PULSES) * envelope(p)
                ReactionFrame(scaleX = s, scaleY = s)
            }
            ReactionPreset.WILT -> { p, w ->
                val bend = sin(p * PI_F)
                ReactionFrame(rotationZ = -WILT_ANGLE * bend, translationY = w * WILT_DROP * bend)
            }
            ReactionPreset.FLIP -> { p, _ -> ReactionFrame(rotationY = FULL_TURN * p) }
            ReactionPreset.TURN_AWAY -> { p, w ->
                val t = sin(p * PI_F)
                ReactionFrame(rotationY = TURN_AWAY_ANGLE * t, translationX = -w * TURN_AWAY_SHIFT * t)
            }
            ReactionPreset.GLITCH -> { p, w ->
                val env = envelope(p)
                ReactionFrame(
                    translationX = sin(p * GLITCH_FREQ * TWO_PI) * w * GLITCH_AMPLITUDE * env,
                    alpha = 1f - GLITCH_FLICKER * env * (0.5f + 0.5f * sin(p * GLITCH_FLICKER_FREQ * TWO_PI)),
                )
            }
            ReactionPreset.ROLL -> { p, w ->
                ReactionFrame(
                    rotationZ = FULL_TURN * p,
                    translationX = sin(p * PI_F) * w * ROLL_SHIFT,
                    translationY = -kotlin.math.abs(sin(p * TWO_PI)) * w * ROLL_HOP,
                )
            }
        }

    /** Smooth 0 -> 1 -> 0 envelope so every reaction eases in and out. */
    private fun envelope(p: Float): Float = sin(p * PI_F)
}
