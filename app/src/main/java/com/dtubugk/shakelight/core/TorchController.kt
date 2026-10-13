package com.dtubugk.shakelight.core

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.ceil

/**
 * Single owner of the camera flash. Every feature (shake, flashlight screen, strobe, SOS…)
 * goes through here so the on/off state stays consistent across the app.
 */
object TorchController {

    private var manager: CameraManager? = null
    private val listeners = CopyOnWriteArraySet<(Boolean) -> Unit>()

    var cameraId: String? = null
        private set

    /** Highest brightness step the flash supports; 1 means brightness can't be changed. */
    var maxLevel: Int = 1
        private set

    @Volatile
    var isOn: Boolean = false
        private set

    val isAvailable: Boolean get() = cameraId != null
    val supportsStrength: Boolean get() = maxLevel > 1

    fun init(context: Context) {
        if (manager != null) return
        val cm = context.getSystemService(CameraManager::class.java) ?: return
        manager = cm
        findFlashCamera(cm)?.let { adopt(cm, it) }

        // The system reports every camera that has a flash to this callback right after
        // registering, which finds the flash even on phones where the lookup above fails.
        cm.registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(camId: String, enabled: Boolean) {
                if (cameraId == null) adopt(cm, camId, announce = true)
                if (camId == cameraId) publish(enabled)
            }

            override fun onTorchModeUnavailable(camId: String) {
                if (cameraId == null) adopt(cm, camId, announce = true)
                if (camId == cameraId) publish(false)
            }
        }, Handler(Looper.getMainLooper()))
    }

    /** True when the phone has a flash, even if the exact camera hasn't been identified yet. */
    fun hasFlash(context: Context): Boolean =
        isAvailable || context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)

    /** Turns the flash on/off. [levelPercent] (1–100) is used when the phone supports brightness. */
    fun set(on: Boolean, levelPercent: Int = 100): Boolean {
        val cm = manager ?: return false
        val id = cameraId ?: return probeAndSet(cm, on)
        return try {
            if (on && supportsStrength && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                cm.turnOnTorchWithStrengthLevel(id, percentToLevel(levelPercent))
            } else {
                cm.setTorchMode(id, on)
            }
            publish(on)
            true
        } catch (e: Exception) {
            // Camera busy (another app is using it) or flash unavailable.
            false
        }
    }

    fun toggle(levelPercent: Int = 100): Boolean = set(!isOn, levelPercent)

    fun addListener(listener: (Boolean) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Boolean) -> Unit) {
        listeners.remove(listener)
    }

    private fun percentToLevel(percent: Int): Int =
        ceil(percent.coerceIn(1, 100) / 100f * maxLevel).toInt().coerceIn(1, maxLevel)

    private fun publish(on: Boolean) {
        if (isOn == on) return
        isOn = on
        listeners.forEach { it(on) }
    }

    private fun adopt(cm: CameraManager, id: String, announce: Boolean = false) {
        cameraId = id
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            maxLevel = runCatching {
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)
            }.getOrNull() ?: 1
        }
        if (announce) listeners.forEach { it(isOn) }
    }

    /** Last resort: try each camera until one accepts the torch command, and remember it. */
    private fun probeAndSet(cm: CameraManager, on: Boolean): Boolean {
        val ids = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())
        for (id in ids) {
            if (runCatching { cm.setTorchMode(id, on) }.isSuccess) {
                adopt(cm, id)
                publish(on)
                return true
            }
        }
        return false
    }

    /**
     * Checks each camera on its own: on many phones one hidden camera throws when queried,
     * and that must not hide the real flash.
     */
    private fun findFlashCamera(cm: CameraManager): String? {
        val ids = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())
        val withFlash = ids.filter { id ->
            runCatching {
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }.getOrDefault(false)
        }
        return withFlash.firstOrNull { id ->
            runCatching {
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            }.getOrDefault(false)
        } ?: withFlash.firstOrNull()
    }
}
