package com.dtubugk.shakelight.projector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.max
import kotlin.math.min

/**
 * Shows one photo or video, fitted to the screen, with optional rotate-180° / mirror.
 * Used both on the phone screen and on an external projector screen (Presentation).
 * A TextureView is used for video because, unlike SurfaceView, it can be flipped.
 */
class MediaStage @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs), TextureView.SurfaceTextureListener {

    private val image = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    private val texture = TextureView(context)

    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var uri: Uri? = null
    private var loop = true
    private var startAtMs = 0
    private var mediaWidth = 0
    private var mediaHeight = 0
    private var loadToken = 0

    var onError: (() -> Unit)? = null

    val positionMs: Int
        get() = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)

    init {
        setBackgroundColor(Color.BLACK)
        addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(texture, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
        texture.surfaceTextureListener = this
        image.visibility = View.GONE
        texture.visibility = View.GONE
    }

    fun show(uri: Uri, isVideo: Boolean, loop: Boolean, startAtMs: Int = 0) {
        release()
        this.uri = uri
        this.loop = loop
        this.startAtMs = startAtMs
        if (isVideo) {
            image.visibility = View.GONE
            texture.visibility = View.VISIBLE
            texture.surfaceTexture?.takeIf { texture.isAvailable }?.let { startPlayer(it) }
        } else {
            texture.visibility = View.GONE
            image.visibility = View.VISIBLE
            loadImage(uri)
        }
    }

    fun setTransforms(rotate180: Boolean, mirror: Boolean) {
        for (view in listOf(image, texture)) {
            view.rotation = if (rotate180) 180f else 0f
            view.scaleX = if (mirror) -1f else 1f
        }
    }

    /** Returns true if the video is now playing, null if there is no video. */
    fun togglePlay(): Boolean? {
        val p = player ?: return null
        if (p.isPlaying) p.pause() else p.start()
        return p.isPlaying
    }

    fun release() {
        loadToken++
        releasePlayer()
        image.setImageDrawable(null)
        uri = null
    }

    private fun loadImage(uri: Uri) {
        val token = ++loadToken
        Thread {
            val bitmap = runCatching { decodeSampled(uri) }.getOrNull()
            post {
                if (token != loadToken) return@post
                if (bitmap == null) onError?.invoke() else image.setImageBitmap(bitmap)
            }
        }.start()
    }

    private fun decodeSampled(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_IMAGE_DIM) {
                    val scale = MAX_IMAGE_DIM.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_DIM) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun startPlayer(surfaceTexture: SurfaceTexture) {
        val source = uri ?: return
        if (player != null) return
        val s = Surface(surfaceTexture).also { surface = it }
        player = MediaPlayer().apply {
            setSurface(s)
            setOnPreparedListener { mp ->
                mediaWidth = mp.videoWidth
                mediaHeight = mp.videoHeight
                fitVideo()
                mp.isLooping = loop
                if (startAtMs > 0) mp.seekTo(startAtMs)
                mp.start()
            }
            setOnVideoSizeChangedListener { _, w, h ->
                mediaWidth = w
                mediaHeight = h
                fitVideo()
            }
            setOnErrorListener { _, _, _ ->
                onError?.invoke()
                true
            }
            try {
                setDataSource(context, source)
                prepareAsync()
            } catch (e: Exception) {
                onError?.invoke()
            }
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        surface?.release()
        surface = null
        mediaWidth = 0
        mediaHeight = 0
    }

    private fun fitVideo() {
        if (mediaWidth == 0 || mediaHeight == 0 || width == 0 || height == 0) return
        val scale = min(width / mediaWidth.toFloat(), height / mediaHeight.toFloat())
        val lp = texture.layoutParams as LayoutParams
        lp.width = (mediaWidth * scale).toInt()
        lp.height = (mediaHeight * scale).toInt()
        lp.gravity = Gravity.CENTER
        texture.layoutParams = lp
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { fitVideo() }
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        if (texture.visibility == View.VISIBLE) startPlayer(surfaceTexture)
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        releasePlayer()
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit

    companion object {
        private const val MAX_IMAGE_DIM = 2560
    }
}
