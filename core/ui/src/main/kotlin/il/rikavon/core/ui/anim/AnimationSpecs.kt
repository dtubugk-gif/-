package il.rikavon.core.ui.anim

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Every animation timing in the app lives here. Screens and components reference these values and
 * never define their own durations, easings or spring constants.
 */
object AnimationSpecs {
    // ---- Easings -------------------------------------------------------------------------------
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Overshoot: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val Standard: Easing = FastOutSlowInEasing

    // ---- Mascot idle ---------------------------------------------------------------------------

    /** Lottie playback speed multiplier per health (1 = healthy). Rotten mascots twitch faster. */
    const val IDLE_SPEED_HEALTHY = 1f
    const val IDLE_SPEED_ROTTEN = 1.15f

    // ---- Mascot stage morph --------------------------------------------------------------------
    const val STAGE_MORPH_MILLIS = 800
    val StageMorph: FiniteAnimationSpec<Float> = tween(STAGE_MORPH_MILLIS, easing = Emphasized)
    const val STAGE_MORPH_SCALE_OUT = 0.92f
    const val STAGE_MORPH_SCALE_IN = 1.08f

    // ---- Touch ---------------------------------------------------------------------------------
    val TapSquash: FiniteAnimationSpec<Float> =
        spring(
            dampingRatio = 0.35f,
            stiffness = Spring.StiffnessMedium,
        )
    const val TAP_SQUASH_X = 1.18f
    const val TAP_SQUASH_Y = 0.82f
    const val FLOAT_TEXT_MILLIS = 1500
    const val FLOAT_TEXT_RISE_DP = 72
    val FloatTextRise: FiniteAnimationSpec<Float> = tween(FLOAT_TEXT_MILLIS, easing = LinearOutSlowInEasing)
    val FloatTextFade: FiniteAnimationSpec<Float> = tween(FLOAT_TEXT_MILLIS, easing = EmphasizedAccelerate)

    // ---- Long press reactions ------------------------------------------------------------------
    const val REACTION_MILLIS = 1100
    val Reaction: FiniteAnimationSpec<Float> = tween(REACTION_MILLIS, easing = Emphasized)
    val ReactionBounce: FiniteAnimationSpec<Float> =
        spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessLow,
        )

    // ---- Block screen entrance -----------------------------------------------------------------
    const val BLOCK_ENTRANCE_MILLIS = 1200
    const val BLOCK_ENTRANCE_DROP_DP = 260
    const val BLOCK_ENTRANCE_ROTATION = -18f
    val BlockEntranceDrop: FiniteAnimationSpec<Float> = tween(BLOCK_ENTRANCE_MILLIS, easing = Overshoot)
    val BlockEntranceFade: FiniteAnimationSpec<Float> = tween(BLOCK_ENTRANCE_MILLIS / 2, easing = LinearEasing)
    const val BLOCK_TEXT_DELAY_MILLIS = 700
    const val BLOCK_TEXT_MILLIS = 450

    // ---- Score changes -------------------------------------------------------------------------
    const val SCORE_UP_MILLIS = 900
    const val SCORE_DOWN_MILLIS = 700
    val ScoreUpPop: FiniteAnimationSpec<Float> =
        spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )
    val ScoreDownFade: FiniteAnimationSpec<Float> = tween(SCORE_DOWN_MILLIS, easing = EmphasizedAccelerate)
    const val PARTICLE_COUNT = 18
    const val PARTICLE_MILLIS = 1100
    const val PARTICLE_SPREAD_DP = 140
    val Particle: FiniteAnimationSpec<Float> = tween(PARTICLE_MILLIS, easing = EmphasizedDecelerate)

    // ---- Charts --------------------------------------------------------------------------------
    const val CHART_STAGGER_MILLIS = 40
    const val CHART_BAR_MILLIS = 450
    val ChartBar: FiniteAnimationSpec<Float> = tween(CHART_BAR_MILLIS, easing = EmphasizedDecelerate)

    // ---- Widget --------------------------------------------------------------------------------
    const val WIDGET_CROSSFADE_MILLIS = 400

    // ---- Durations (design system): micro 100–150, component 200–250, screen 300–350 ------------
    const val MICRO_MILLIS = 120
    const val COMPONENT_MILLIS = 220
    val Micro: FiniteAnimationSpec<Float> = tween(MICRO_MILLIS, easing = Emphasized)
    val Component: FiniteAnimationSpec<Float> = tween(COMPONENT_MILLIS, easing = Emphasized)

    /** Every pressed surface scales to this within [MICRO_MILLIS]. */
    const val PRESSED_SCALE = 0.97f

    /** Skeleton shimmer sweep period while a screen loads. */
    const val SKELETON_PULSE_MILLIS = 1100

    // ---- Choreography (design system): entrances, pops, counters ------------------------------

    /** Stagger between list rows entering; capped so long lists never feel slow. */
    const val LIST_STAGGER_MILLIS = 35
    const val LIST_STAGGER_MAX_INDEX = 8
    const val LIST_ENTER_MILLIS = 280
    const val LIST_ENTER_OFFSET_DP = 24
    val ListEnter: FiniteAnimationSpec<Float> = tween(LIST_ENTER_MILLIS, easing = EmphasizedDecelerate)

    /** Scale-in for elements that appear (empty-state icons, granted pills, hero content). */
    val PopIn: FiniteAnimationSpec<Float> =
        spring(
            dampingRatio = 0.62f,
            stiffness = Spring.StiffnessMediumLow,
        )
    const val POP_IN_FROM_SCALE = 0.8f

    /** Numbers count toward their new value instead of jumping. */
    const val COUNT_MILLIS = 700
    val Count: FiniteAnimationSpec<Float> = tween(COUNT_MILLIS, easing = Emphasized)

    /** Bottom-bar icon bounce on selection. */
    val NavBounce: FiniteAnimationSpec<Float> =
        spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        )
    const val NAV_BOUNCE_SCALE = 1.12f

    /** Gentle float of the mascot on the home screen (transform only). */
    const val FLOAT_LOOP_MILLIS = 3200
    const val FLOAT_AMPLITUDE_DP = 6

    /** Headline punch on the block screen. */
    val HeadlinePunch: FiniteAnimationSpec<Float> =
        spring(
            dampingRatio = 0.45f,
            stiffness = Spring.StiffnessLow,
        )
    const val HEADLINE_FROM_SCALE = 1.35f

    /** Fade-through between the four top-level tabs: fade + scale 0.96 → 1. */
    const val TAB_SWITCH_MILLIS = 240
    const val TAB_SWITCH_SCALE = 0.96f

    // ---- Navigation / generic ------------------------------------------------------------------
    const val SCREEN_TRANSITION_MILLIS = 320
    const val SHARED_ELEMENT_MILLIS = 450
    val SharedElement: FiniteAnimationSpec<Float> = tween(SHARED_ELEMENT_MILLIS, easing = Emphasized)
    const val CROSSFADE_MILLIS = 250
    val Crossfade: FiniteAnimationSpec<Float> = tween(CROSSFADE_MILLIS, easing = LinearEasing)
    const val STRICT_MODE_DELAY_SECONDS = 10

    // Breathing pause: ten seconds, one breath in and out every four seconds, the circle between 55 % and 100 %.
    const val BREATHING_GATE_SECONDS = 10
    const val BREATH_HALF_CYCLE_MILLIS = 2_000
    const val BREATH_MIN_SCALE = 0.55f

    // Pet calls: how long the phone rings before the pet gives up, and the pause between spoken lines.
    const val CALL_RING_MILLIS = 30_000L
    const val CALL_LINE_GAP_MILLIS = 2_600L

    // ---- Reduced motion ------------------------------------------------------------------------
    const val REDUCED_MILLIS = 180
    val Reduced: FiniteAnimationSpec<Float> = tween(REDUCED_MILLIS, easing = LinearEasing)

    /** Picks the full spec or the reduced-motion fade. */
    fun <T> FiniteAnimationSpec<T>.orReduced(
        reduced: Boolean,
        fallback: FiniteAnimationSpec<T>,
    ): FiniteAnimationSpec<T> =
        if (reduced) fallback else this

    fun reducedTween(): FiniteAnimationSpec<Float> = Reduced
}
