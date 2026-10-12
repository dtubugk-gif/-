package com.dtubugk.shakelight.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object Haptics {

    /** One buzz when the light turns on, two short buzzes when it turns off. */
    fun torchFeedback(context: Context, turnedOn: Boolean) {
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        val effect = if (turnedOn) {
            VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE)
        } else {
            VibrationEffect.createWaveform(longArrayOf(0, 40, 90, 40), -1)
        }
        vibrator.vibrate(effect)
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
}
