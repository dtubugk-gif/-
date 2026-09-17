package il.rikavon.feature.mascot.sound

import android.media.AudioAttributes
import android.media.MediaDataSource
import android.media.MediaPlayer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** Plays one in-memory audio clip at a time and suspends until it is over, however it ends. Main thread. */
class ClipPlayer {
    private var current: MediaPlayer? = null

    suspend fun play(clip: ByteArray, attributes: AudioAttributes) =
        suspendCancellableCoroutine { continuation ->
            stop()
            val player = MediaPlayer()
            current = player
            val done = AtomicBoolean(false)

            fun finish() {
                if (!done.compareAndSet(false, true)) return
                release(player)
                if (continuation.isActive) continuation.resume(Unit)
            }
            runCatching {
                player.setAudioAttributes(attributes)
                player.setDataSource(BytesDataSource(clip))
                player.setOnPreparedListener { it.start() }
                player.setOnCompletionListener { finish() }
                player.setOnErrorListener { _, _, _ ->
                    finish()
                    true
                }
                player.prepareAsync()
            }.onFailure { finish() }
            continuation.invokeOnCancellation { finish() }
        }

    fun stop() {
        current?.let { release(it) }
    }

    private fun release(player: MediaPlayer) {
        if (current === player) current = null
        runCatching { player.stop() }
        runCatching { player.release() }
    }

    private class BytesDataSource(private val bytes: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= bytes.size) return -1
            val start = position.toInt()
            val count = minOf(size, bytes.size - start)
            System.arraycopy(bytes, start, buffer, offset, count)
            return count
        }

        override fun getSize(): Long = bytes.size.toLong()

        override fun close() = Unit
    }
}
