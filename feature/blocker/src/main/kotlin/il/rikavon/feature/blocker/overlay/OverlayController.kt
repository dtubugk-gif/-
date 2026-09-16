package il.rikavon.feature.blocker.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import il.rikavon.core.data.model.ReduceMotionMode
import il.rikavon.core.ui.anim.systemReducedMotion
import il.rikavon.feature.blocker.engine.BlockDecision
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.sound.MascotSoundPlayer
import javax.inject.Inject
import javax.inject.Singleton

/** Hosts a Compose screen (the block screen or the breathing pause) in a system overlay window. */
@Singleton
class OverlayController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sounds: MascotSoundPlayer,
) {
    enum class Kind { BLOCK, BREATHE }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager: WindowManager get() = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: ComposeView? = null
    private var owner: OverlayLifecycleOwner? = null
    private var shownPackage: String? = null
    private var shownKind: Kind? = null

    /** Visual preferences resolved by the caller off the main thread. */
    data class Appearance(val reducedMotion: Boolean)

    /** Everything the block screen needs, resolved by the service off the main thread. */
    data class Request(
        val decision: BlockDecision,
        val skin: MascotSkin,
        val appLabel: String,
        val limitMinutes: Int,
        val message: String,
        val appearance: Appearance,
        val onClose: () -> Unit,
        /** Runs once the window is attached, so the caller can send the blocked app home behind it. */
        val onShown: () -> Unit = {},
    )

    /** The breathing pause shown on the first open after a limit changed. */
    data class BreatheRequest(
        val packageName: String,
        val skin: MascotSkin,
        val appLabel: String,
        val seconds: Int,
        val appearance: Appearance,
        val onEnter: () -> Unit,
        val onLeave: () -> Unit,
    )

    fun isShowing(packageName: String): Boolean = shownPackage == packageName && view != null

    /** The package the current overlay is about, or null when nothing is shown. */
    val shownPackageName: String? get() = if (view != null) shownPackage else null

    val kind: Kind? get() = if (view != null) shownKind else null

    fun appearance(reduceMotion: ReduceMotionMode): Appearance =
        Appearance(
            reducedMotion =
                when (reduceMotion) {
                    ReduceMotionMode.ON -> true
                    ReduceMotionMode.OFF -> false
                    ReduceMotionMode.SYSTEM -> systemReducedMotion(context)
                },
        )

    fun show(request: Request) {
        mainHandler.post {
            showOnMain(request.decision.packageName, Kind.BLOCK, request.skin.soundAsset, request.onShown) {
                BlockOverlayContent(
                    decision = request.decision,
                    skin = request.skin,
                    appLabel = request.appLabel,
                    limitMinutes = request.limitMinutes,
                    message = request.message,
                    reducedMotion = request.appearance.reducedMotion,
                    onClose = request.onClose,
                )
            }
        }
    }

    fun showBreathing(request: BreatheRequest) {
        mainHandler.post {
            showOnMain(request.packageName, Kind.BREATHE, sound = null, onShown = {}) {
                BreathingOverlayContent(
                    skin = request.skin,
                    appLabel = request.appLabel,
                    seconds = request.seconds,
                    reducedMotion = request.appearance.reducedMotion,
                    onEnter = request.onEnter,
                    onLeave = request.onLeave,
                )
            }
        }
    }

    fun hide() {
        mainHandler.post { hideOnMain() }
    }

    /** Hides the overlay only when it is of [kind]; used to drop a stale breathing pause. */
    fun hideIf(kind: Kind) {
        mainHandler.post { if (shownKind == kind) hideOnMain() }
    }

    private fun showOnMain(
        packageName: String,
        kind: Kind,
        sound: String?,
        onShown: () -> Unit,
        content: @Composable () -> Unit,
    ) {
        hideOnMain()
        val lifecycleOwner = OverlayLifecycleOwner().also { owner = it }
        val composeView =
            ComposeView(context).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setContent(content)
            }
        val params =
            WindowManager
                .LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
        runCatching { windowManager.addView(composeView, params) }
            .onSuccess {
                view = composeView
                shownPackage = packageName
                shownKind = kind
                lifecycleOwner.moveTo(Lifecycle.State.RESUMED)
                sound?.let { sounds.play(it, BLOCK_SOUND_VOLUME) }
                onShown()
            }
    }

    private fun hideOnMain() {
        val current = view ?: return
        owner?.moveTo(Lifecycle.State.DESTROYED)
        runCatching { windowManager.removeViewImmediate(current) }
        view = null
        owner = null
        shownPackage = null
        shownKind = null
    }

    /** Minimal lifecycle so Compose (and Lottie's idle loop) run and stop with the window. */
    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)

        init {
            savedStateController.performAttach()
            savedStateController.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
        }

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

        fun moveTo(state: Lifecycle.State) {
            registry.currentState = state
        }
    }

    private companion object {
        const val BLOCK_SOUND_VOLUME = 0.4f
    }
}
