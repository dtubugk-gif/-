package com.callsoundboard.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log

/**
 * Plays a single local audio clip out the media path.
 *
 * Notes tied to the platform reality (see README):
 * - We play on USAGE_MEDIA and rely on the acoustic path (loudspeaker -> the
 *   phone's own mic -> the far party). We do NOT inject into the call uplink;
 *   Android has no public API for that.
 * - We do NOT request audio focus: an active call already holds focus and a
 *   GAIN request would be denied or pointless. We simply play.
 * - We max out STREAM_MUSIC volume first to give the acoustic path its best shot.
 */
object SoundPlayer {

    private const val TAG = "SoundPlayer"
    private var player: MediaPlayer? = null

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
            mp.setOnPreparedListener { it.start() }
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
            mp.release()
            onDone?.invoke()
        }
    }

    @Synchronized
    fun stop() {
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
