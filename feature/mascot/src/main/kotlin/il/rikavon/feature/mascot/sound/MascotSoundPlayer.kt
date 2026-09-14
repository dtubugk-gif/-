package il.rikavon.feature.mascot.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Short, quiet UI sounds. Silent when the ringer is off; never louder than the media stream. */
@Singleton
class MascotSoundPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val pool: SoundPool by lazy {
        SoundPool
            .Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            ).build()
    }
    private val loaded = HashMap<String, Int>()
    private val ready = HashSet<Int>()

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) ready += sampleId }
    }

    fun preload(assetPath: String) {
        if (assetPath in loaded) return
        val id = runCatching { context.assets.openFd(assetPath).use { pool.load(it, PRIORITY) } }.getOrNull() ?: return
        loaded[assetPath] = id
    }

    fun play(assetPath: String, volume: Float = DEFAULT_VOLUME) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id =
            loaded[assetPath] ?: run {
                preload(assetPath)
                return
            }
        if (id !in ready) return
        pool.play(id, volume, volume, PRIORITY, NO_LOOP, NORMAL_RATE)
    }

    companion object {
        const val SCORE_UP = "sounds/score_up.wav"
        const val SCORE_DOWN = "sounds/score_down.wav"
        private const val MAX_STREAMS = 2
        private const val PRIORITY = 1
        private const val NO_LOOP = 0
        private const val NORMAL_RATE = 1f
        private const val DEFAULT_VOLUME = 0.6f
    }
}
