package com.callsoundboard.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.util.Log

/**
 * Plays a single local audio clip out the media path, as LOUD as possible.
 *
 * Loudness strategy (see README for why "faint" is partly unavoidable):
 * - STREAM_MUSIC is forced to its maximum before playback.
 * - The MediaPlayer's own volume is set to 1.0.
 * - A LoudnessEnhancer effect adds a large fixed gain (GAIN_MB millibels) on top
 *   of the maximum system volume — this is the main lever for extra loudness.
 * - We rely on the acoustic path (loudspeaker -> the phone's own mic -> the far
 *   party). Android has no public API to inject into the call uplink, and the
 *   uplink's echo canceller actively suppresses the phone's own speaker, so the
 *   far side may still hear it attenuated. Boost + speaker gives the best shot.
 * - We do NOT request audio focus: an active call already holds it.
 */
object SoundPlayer {

    private const val TAG = "SoundPlayer"

    /** Extra gain on top of max volume, in millibels (100 mB = 1 dB). */
    private const val GAIN_MB = 3000

    private var player: MediaPlayer? = null
    private var enhancer: LoudnessEnhancer? = null

    @Synchronized
    fun play(context: Context, uri: Uri, onDone: (() -> Unit)? = null) {
        stop()
        val appCtx = context.applicationContext
        maxMusicVolume(appCtx)

        val mp = MediaPlayer()
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        try {
            mp.setDataSource(appCtx, uri)
            attachLoudnessEnhancer(mp)
            mp.setOnPreparedListener {
                try {
                    it.setVolume(1f, 1f)
                } catch (_: Exception) {
                }
                it.start()
            }
            mp.setOnCompletionListener {
                onDone?.invoke()
                stop()
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "MediaPlayer error what=$what extra=$extra")
                onDone?.invoke()
                stop()
                true
            }
            mp.prepareAsync()
            player = mp
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play $uri", e)
            releaseEnhancer()
            mp.release()
            onDone?.invoke()
        }
    }

    @Synchronized
    fun stop() {
        releaseEnhancer()
        player?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        player = null
    }

    fun isPlaying(): Boolean = try {
        player?.isPlaying == true
    } catch (_: Exception) {
        false
    }

    private fun attachLoudnessEnhancer(mp: MediaPlayer) {
        try {
            val le = LoudnessEnhancer(mp.audioSessionId)
            le.setTargetGain(GAIN_MB)
            le.enabled = true
            enhancer = le
        } catch (e: Exception) {
            // Effect unavailable on some devices; play without the extra boost.
            Log.w(TAG, "LoudnessEnhancer unavailable", e)
        }
    }

    private fun releaseEnhancer() {
        enhancer?.let {
            try {
                it.enabled = false
                it.release()
            } catch (_: Exception) {
            }
        }
        enhancer = null
    }

    private fun maxMusicVolume(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
        } catch (_: Exception) {
            // May throw under Do-Not-Disturb policy; ignore and play anyway.
        }
    }
}
