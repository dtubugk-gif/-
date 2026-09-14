package il.rikavon.feature.blocker.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
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

/** Hosts the Compose block screen in a system overlay window owned by the service. */
@Singleton
class OverlayController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sounds: MascotSoundPlayer,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager: WindowManager get() =
        context.getSystemService(
            Context.WINDOW_SERVICE,
        ) as WindowManager
    private var view: ComposeView? = null
    private var owner: OverlayLifecycleOwner? = null
    private var shownPackage: String? = null

    fun isShowing(packageName: String): Boolean = shownPackage == packageName && view != null

    /** Visual preferences resolved by the caller off the main thread. */
    data class Appearance(val reducedMotion: Boolean, val dynamicColor: Boolean)

    fun show(decision: BlockDecision, skin: MascotSkin, message: String, appearance: Appearance, onClose: () -> Unit) {
        mainHandler.post { showOnMain(decision, skin, message, appearance, onClose) }
    }

    fun appearance(reduceMotion: ReduceMotionMode, dynamicColor: Boolean): Appearance =
        Appearance(
            reducedMotion =
                when (reduceMotion) {
                    ReduceMotionMode.ON -> true
                    ReduceMotionMode.OFF -> false
                    ReduceMotionMode.SYSTEM -> systemReducedMotion(context)
                },
            dynamicColor = dynamicColor,
        )

    fun hide() {
        mainHandler.post { hideOnMain() }
    }

    private fun showOnMain(
        decision: BlockDecision,
        skin: MascotSkin,
        message: String,
        appearance: Appearance,
        onClose: () -> Unit,
    ) {
        hideOnMain()
        val lifecycleOwner = OverlayLifecycleOwner().also { owner = it }
        val composeView =
            ComposeView(context).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setContent {
                    BlockOverlayContent(
                        decision = decision,
                        skin = skin,
                        message = message,
                        reducedMotion = appearance.reducedMotion,
                        dynamicColor = appearance.dynamicColor,
                        onClose = onClose,
                    )
                }
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
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
        runCatching { windowManager.addView(composeView, params) }
            .onSuccess {
                view = composeView
                shownPackage = decision.packageName
                lifecycleOwner.moveTo(Lifecycle.State.RESUMED)
                skin.soundAsset?.let { sounds.play(it, BLOCK_SOUND_VOLUME) }
            }
    }

    private fun hideOnMain() {
        val current = view ?: return
        owner?.moveTo(Lifecycle.State.DESTROYED)
        runCatching { windowManager.removeViewImmediate(current) }
        view = null
        owner = null
        shownPackage = null
    }

    /** Minimal lifecycle so Compose (and Lottie's idle loop) run and stop with the window. */
    private class OverlayLifecycleOwner :
        LifecycleOwner,
        SavedStateRegistryOwner {
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
