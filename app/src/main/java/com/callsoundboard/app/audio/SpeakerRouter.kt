package com.callsoundboard.app.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Best-effort attempt to route audio to the built-in loudspeaker.
 *
 * IMPORTANT (see README): for a real carrier call the Telecom framework owns
 * the call's audio route, not AudioManager. These calls are unreliable during a
 * live cellular call on Android 11+. Treat this as a hint only; the UI tells the
 * user to press the in-call "Speaker" button if the far party can't hear.
 */
object SpeakerRouter {

    private const val TAG = "SpeakerRouter"

    fun enableSpeaker(context: Context) {
        val am = context.applicationContext
            .getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val speaker = am.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) {
                    am.setCommunicationDevice(speaker)
                } else {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = true
                }
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "enableSpeaker failed", e)
        }
    }

    fun clearSpeaker(context: Context) {
        val am = context.applicationContext
            .getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = false
            }
        } catch (e: Exception) {
            Log.w(TAG, "clearSpeaker failed", e)
        }
    }
}
