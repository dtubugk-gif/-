package il.rikavon.feature.mascot.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import dagger.hilt.android.qualifiers.ApplicationContext
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.model.MascotStage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rasterises a mascot stage for surfaces that cannot host Lottie (home-screen widget, notifications).
 * Renders the Lottie frame at [progress]; falls back to a tinted blob when the asset is missing.
 */
@Singleton
class MascotBitmapRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun render(skin: MascotSkin, stage: MascotStage, sizePx: Int, progress: Float = 0f): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val asset = skin.stage(stage).lottieAsset
        val composition = asset?.let { LottieCompositionFactory.fromAssetSync(context, it).value }
        if (composition != null) {
            val drawable =
                LottieDrawable().apply {
                    this.composition = composition
                    setBounds(0, 0, sizePx, sizePx)
                    this.progress = progress
                }
            drawable.draw(canvas)
        } else {
            drawFallback(canvas, sizePx, skin, stage)
        }
        return bitmap
    }

    private fun drawFallback(canvas: Canvas, size: Int, skin: MascotSkin, stage: MascotStage) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val base = skin.themeColorArgb.toInt()
        val mixed = mix(base, ROT_COLOR, 1f - stage.health)
        paint.color = mixed
        val inset = size * FALLBACK_INSET
        canvas.drawOval(RectF(inset, inset, size - inset, size - inset), paint)
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * t).toInt()
        return Color.argb(
            ch(Color.alpha(a), Color.alpha(b)),
            ch(Color.red(a), Color.red(b)),
            ch(Color.green(a), Color.green(b)),
            ch(Color.blue(a), Color.blue(b)),
        )
    }

    companion object {
        private const val ROT_COLOR = 0xFF4B5A3A.toInt()
        private const val FALLBACK_INSET = 0.12f
    }
}
