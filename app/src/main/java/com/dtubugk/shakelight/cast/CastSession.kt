package com.dtubugk.shakelight.cast

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Size
import android.view.Display
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.dtubugk.shakelight.R
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

/**
 * Streams the phone screen (H.264) and, on Android 10+, the phone's sound (PCM) to a computer
 * running ShakeLight Receiver. All network and codec work runs on background threads.
 */
class CastSession(
    private val context: Context,
    private val projection: MediaProjection,
    private val host: String,
    private val port: Int,
    private val sendAudio: Boolean,
    private val onConnected: () -> Unit,
    /** Called once when the session ends; the argument is an error message, or null if stopped normally. */
    private val onEnded: (Int?) -> Unit,
) {
    private val running = AtomicBoolean(true)
    private val writeLock = Any()
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var socket: Socket? = null
    @Volatile private var failure: Int? = null
    @Volatile private var pendingSize: Size? = null
    @Volatile private var keyframeRequested = false

    private lateinit var out: DataOutputStream
    private var encoder: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var audioThread: Thread? = null
    private var encodedWidth = 0
    private var encodedHeight = 0
    private var lastScreen: Size? = null

    // Rotating the phone changes the screen shape: re-create the encoder at the new size.
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            val size = screenSize()
            if (size != lastScreen) {
                lastScreen = size
                pendingSize = size
            }
        }

        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
    }

    fun start() {
        thread(name = "cast", block = ::run)
    }

    /** Stops streaming. Safe to call more than once, from any thread. */
    fun stop() {
        if (!running.getAndSet(false)) return
        runCatching { socket?.close() }
    }

    private fun fail(message: Int) {
        if (failure == null && running.get()) failure = message
        stop()
    }

    private fun run() {
        try {
            connect()
            val screen = screenSize().also { lastScreen = it }
            startEncoder(screen)
            virtualDisplay = projection.createVirtualDisplay(
                "ShakeLight", encodedWidth, encodedHeight, context.resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, inputSurface, null, null,
            )
            main.post { displayManager.registerDisplayListener(displayListener, main) }
            if (sendAudio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startAudio()
            onConnected()
            drainEncoder()
        } catch (e: UnknownHostException) {
            fail(R.string.cast_error_connect)
        } catch (e: SocketTimeoutException) {
            fail(R.string.cast_error_connect)
        } catch (e: java.net.ConnectException) {
            fail(R.string.cast_error_connect)
        } catch (e: IOException) {
            fail(R.string.cast_error_lost)
        } catch (e: Exception) {
            fail(R.string.cast_error_encoder)
        } finally {
            stop()
            cleanup()
            onEnded(failure)
        }
    }

    private fun connect() {
        val s = Socket()
        socket = s
        s.tcpNoDelay = true
        s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        out = DataOutputStream(BufferedOutputStream(s.getOutputStream(), 64 * 1024))
        synchronized(writeLock) { out.write(CastProtocol.MAGIC.toByteArray(Charsets.US_ASCII)) }
        send(CastProtocol.HELLO, 0, JSONObject().put("name", deviceName()).toString().toByteArray())

        thread(name = "cast-control", isDaemon = true) {
            try {
                val input = s.getInputStream()
                while (running.get()) {
                    when (input.read()) {
                        -1 -> break
                        CastProtocol.CTRL_REQUEST_KEYFRAME -> keyframeRequested = true
                    }
                }
            } catch (e: IOException) {
                // socket closed
            }
            fail(R.string.cast_error_lost)
        }
    }

    private fun send(type: Int, pts: Long, payload: ByteArray, length: Int = payload.size) {
        synchronized(writeLock) {
            out.writeByte(type)
            out.writeInt(length)
            out.writeLong(pts)
            out.write(payload, 0, length)
            out.flush()
        }
    }

    private fun drainEncoder() {
        val info = MediaCodec.BufferInfo()
        while (running.get()) {
            pendingSize?.let {
                pendingSize = null
                restartEncoder(it)
            }
            val codec = encoder ?: return
            if (keyframeRequested) {
                keyframeRequested = false
                codec.setParameters(android.os.Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) })
            }

            val index = codec.dequeueOutputBuffer(info, 100_000)
            if (index < 0) continue
            val buffer = codec.getOutputBuffer(index)
            if (buffer != null && info.size > 0) {
                val data = ByteArray(info.size)
                buffer.position(info.offset)
                buffer.limit(info.offset + info.size)
                buffer.get(data)
                when {
                    info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0 -> {
                        val payload = ByteBuffer.allocate(4 + data.size)
                            .putShort(encodedWidth.toShort())
                            .putShort(encodedHeight.toShort())
                            .put(data)
                            .array()
                        send(CastProtocol.CONFIG, 0, payload)
                    }
                    info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0 ->
                        send(CastProtocol.KEY_FRAME, info.presentationTimeUs, data)
                    else -> send(CastProtocol.FRAME, info.presentationTimeUs, data)
                }
            }
            codec.releaseOutputBuffer(index, false)
        }
    }

    /** Picks the largest size this phone's encoder supports (≤ 1920 on the long side) and starts it. */
    private fun startEncoder(screen: Size) {
        val codec = MediaCodec.createEncoderByType(MIME)
        val caps = codec.codecInfo.getCapabilitiesForType(MIME).videoCapabilities
        for (maxSide in MAX_SIDES) {
            val scale = min(1f, maxSide.toFloat() / max(screen.width, screen.height))
            val width = align16(screen.width * scale)
            val height = align16(screen.height * scale)
            if (!caps.isSizeSupported(width, height)) continue

            val bitrate = caps.bitrateRange.clamp((width * height * 4).coerceIn(2_000_000, 8_000_000))
            val format = MediaFormat.createVideoFormat(MIME, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                setInteger(MediaFormat.KEY_PRIORITY, 0) // real-time
                // Keep sending frames while the screen is still, so the computer always has a picture.
                setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000)
            }
            try {
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = codec.createInputSurface()
                codec.start()
                encoder = codec
                encodedWidth = width
                encodedHeight = height
                return
            } catch (e: Exception) {
                codec.reset()
            }
        }
        codec.release()
        throw IllegalStateException("No supported encoder size")
    }

    private fun restartEncoder(screen: Size) {
        val oldEncoder = encoder
        val oldSurface = inputSurface
        startEncoder(screen)
        virtualDisplay?.resize(encodedWidth, encodedHeight, context.resources.displayMetrics.densityDpi)
        virtualDisplay?.surface = inputSurface
        runCatching { oldEncoder?.stop() }
        runCatching { oldEncoder?.release() }
        runCatching { oldSurface?.release() }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    private fun startAudio() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val record = try {
            val capture = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()
            val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(max(minBuffer, AUDIO_CHUNK) * 4)
                .setAudioPlaybackCaptureConfig(capture)
                .build()
        } catch (e: Exception) {
            return // sound is optional; keep streaming the picture
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return
        }

        audioThread = thread(name = "cast-audio") {
            val buffer = ByteArray(AUDIO_CHUNK)
            var frames = 0L
            try {
                record.startRecording()
                while (running.get()) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read < 0) break
                    if (read == 0) continue
                    val pts = frames * 1_000_000 / SAMPLE_RATE
                    frames += read / 4
                    if (buffer.isSilent(read)) continue // don't send silence over the network
                    send(CastProtocol.AUDIO, pts, buffer, read)
                }
            } catch (e: Exception) {
                // connection closed
            } finally {
                runCatching { record.stop() }
                record.release()
            }
        }
    }

    private fun cleanup() {
        main.post { displayManager.unregisterDisplayListener(displayListener) }
        runCatching { audioThread?.join(500) }
        runCatching { virtualDisplay?.release() }
        runCatching { encoder?.stop() }
        runCatching { encoder?.release() }
        runCatching { inputSurface?.release() }
        runCatching { socket?.close() }
        virtualDisplay = null
        encoder = null
        inputSurface = null
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Size {
        val metrics = DisplayMetrics()
        displayManager.getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(metrics)
        return Size(metrics.widthPixels, metrics.heightPixels)
    }

    private fun deviceName(): String =
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: "${Build.MANUFACTURER} ${Build.MODEL}"

    private fun ByteArray.isSilent(length: Int): Boolean {
        for (i in 0 until length) if (this[i].toInt() != 0) return false
        return true
    }

    private fun align16(value: Float): Int = max(16, (value.toInt() / 16) * 16)

    private companion object {
        const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
        const val CONNECT_TIMEOUT_MS = 5000
        const val SAMPLE_RATE = 48_000
        const val AUDIO_CHUNK = 3840 // 20 ms of 16-bit stereo
        val MAX_SIDES = intArrayOf(1920, 1600, 1280, 1024, 800, 640)
    }
}
