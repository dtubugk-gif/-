package com.neonswarm.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Every sound effect and the music loop are synthesised at startup (no audio assets),
 * written to the cache as WAV and played through SoundPool for low-latency, pitchable playback.
 */
class Sfx(context: Context, private val prefs: Prefs) {
    companion object {
        const val SHOOT = 0
        const val HIT = 1
        const val EXPLODE = 2
        const val BIG = 3
        const val POWER = 4
        const val HURT = 5
        const val GEM = 6
        const val ALARM = 7
        const val OVERDRIVE = 8
        const val CLICK = 9
        const val MUSIC = 10
        private const val COUNT = 11
        private const val RATE = 22050
    }

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(16)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = IntArray(COUNT)
    private val loaded = AtomicIntegerArray(COUNT)
    private var musicStream = 0
    private val rnd = Random(7)

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                val idx = synchronized(ids) { ids.indexOf(sampleId) }
                if (idx >= 0) {
                    loaded.set(idx, 1)
                    if (idx == MUSIC) startMusic()
                }
            }
        }
        val dir = context.cacheDir
        Thread({ generateAll(dir) }, "sfx-synth").start()
    }

    fun play(id: Int, volume: Float, rate: Float = 1f, priority: Int = 1) {
        if (!prefs.sound || loaded.get(id) == 0) return
        val v = volume.coerceIn(0f, 1f)
        pool.play(ids[id], v, v, priority, 0, rate.coerceIn(0.5f, 2f))
    }

    @Synchronized
    fun startMusic() {
        if (!prefs.sound || loaded.get(MUSIC) == 0 || musicStream != 0) return
        musicStream = pool.play(ids[MUSIC], 0.4f, 0.4f, 10, -1, 1f)
    }

    @Synchronized
    fun stopMusic() {
        if (musicStream != 0) {
            pool.stop(musicStream)
            musicStream = 0
        }
    }

    fun onPause() = pool.autoPause()
    fun onResume() = pool.autoResume()
    fun release() = pool.release()

    // ---------------------------------------------------------------- synthesis

    private fun generateAll(dir: File) {
        val makers: Array<() -> DoubleArray> = arrayOf(
            ::shoot, ::hit, { explode(0.5, 7.0, 1.0) }, { explode(1.4, 2.4, 1.4) }, ::power,
            ::hurt, ::gem, ::alarm, ::overdrive, ::click, ::music,
        )
        for (i in makers.indices) {
            try {
                val f = File(dir, "sfx_$i.wav")
                writeWav(f, makers[i]())
                synchronized(ids) { ids[i] = pool.load(f.absolutePath, 1) }
            } catch (_: Exception) {
                // A missing sound must never take the game down.
            }
        }
    }

    private fun buf(sec: Double) = DoubleArray((sec * RATE).toInt())
    private fun noise() = rnd.nextDouble() * 2.0 - 1.0
    private fun sq(ph: Double, duty: Double = 0.5) = if (ph % 1.0 < duty) 1.0 else -1.0
    private fun saw(ph: Double) = 2.0 * (ph % 1.0) - 1.0
    private fun tri(ph: Double) = 1.0 - 4.0 * abs((ph % 1.0) - 0.5)

    private fun shoot(): DoubleArray {
        val d = 0.08
        val b = buf(d)
        var ph = 0.0
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            ph += (300 + 1500 * exp(-t * 35)) / RATE
            val env = (1 - t / d).pow(2)
            b[i] = (sq(ph) * 0.3 + tri(ph) * 0.7) * env * 0.5
        }
        return b
    }

    private fun hit(): DoubleArray {
        val d = 0.06
        val b = buf(d)
        var ph = 0.0
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            ph += (1800 - t * 12000) / RATE
            val env = (1 - t / d).pow(3)
            b[i] = (noise() * 0.6 + sin(ph * 2 * PI) * 0.4) * env * 0.6
        }
        return b
    }

    private fun explode(d: Double, decay: Double, boom: Double): DoubleArray {
        val b = buf(d)
        var lp = 0.0
        var ph = 0.0
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            val a = 0.5 * exp(-t * 5) + 0.03
            lp += (noise() - lp) * a
            ph += (35 + 130 * exp(-t * 14)) / RATE
            val env = exp(-t * decay)
            val attack = min(1.0, t * 400)
            b[i] = (lp * 1.6 + sin(ph * 2 * PI) * 0.9 * boom * exp(-t * decay * 0.8)) * env * attack * 0.8
        }
        return b
    }

    private fun power(): DoubleArray {
        val d = 0.42
        val b = buf(d)
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.5, 1318.5)
        var ph = 0.0
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            val n = min(notes.size - 1, (t / 0.07).toInt())
            val nt = t - n * 0.07
            ph += notes[n] * (1 + 0.01 * sin(t * 40)) / RATE
            val env = exp(-nt * 14) * (1 - t / d)
            b[i] = sq(ph) * env * 0.3
        }
        return b
    }

    private fun hurt(): DoubleArray {
        val d = 0.55
        val b = buf(d)
        var ph = 0.0
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            ph += (70 + 520 * exp(-t * 5)) / RATE
            val env = 1 - t / d
            b[i] = (saw(ph) * 0.6 + noise() * 0.3 * exp(-t * 10)) * env * 0.6
        }
        return b
    }

    private fun gem(): DoubleArray {
        val d = 0.12
        val b = buf(d)
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            b[i] = (sin(2 * PI * 1568 * t) * 0.6 + sin(2 * PI * 3136 * t) * 0.25) * exp(-t * 35) * 0.5
        }
        return b
    }

    private fun alarm(): DoubleArray {
        val d = 1.1
        val b = buf(d)
        var ph = 0.0
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            val f = if ((t / 0.14).toInt() % 2 == 0) 660.0 else 880.0
            ph += f / RATE
            val env = min(1.0, (d - t) * 8)
            b[i] = sq(ph) * env * 0.28
        }
        return b
    }

    private fun overdrive(): DoubleArray {
        val d = 1.0
        val b = buf(d)
        var p1 = 0.0
        var p2 = 0.0
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            val f = 180 * 2.0.pow(t * 2.6)
            p1 += f / RATE
            p2 += f * 1.007 / RATE
            val env = min(1.0, t * 20) * (1 - t / d).pow(0.7)
            b[i] = (saw(p1) + saw(p2)) * env * 0.25
        }
        return b
    }

    private fun click(): DoubleArray {
        val d = 0.04
        val b = buf(d)
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            b[i] = sin(2 * PI * 1100 * t) * exp(-t * 90) * 0.5
        }
        return b
    }

    /** 4-bar synthwave loop: Am – F – C – G, 128 BPM. */
    private fun music(): DoubleArray {
        val step = 60.0 / 128.0 / 4.0
        val steps = 64
        val b = buf(step * steps)
        val roots = doubleArrayOf(110.0, 87.31, 130.81, 98.0)
        val minor = intArrayOf(0, 3, 7, 12, 15, 12, 7, 3)
        val major = intArrayOf(0, 4, 7, 12, 16, 12, 7, 4)
        var bassPh = 0.0
        var bassLp = 0.0
        var arpPh = 0.0
        var kickPh = 0.0
        var hatLp = 0.0
        val padPh = DoubleArray(3)
        var lastBeat = -1
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            val s = (t / step).toInt().coerceAtMost(steps - 1)
            val st = t - s * step
            val bar = s / 16
            val root = roots[bar]
            val tones = if (bar == 0) minor else major

            val e8 = s / 2
            val st8 = t - e8 * 2 * step
            bassPh += root * (if (e8 % 2 == 1) 2.0 else 1.0) / RATE
            bassLp += (saw(bassPh) - bassLp) * (0.08 + 0.25 * exp(-st8 * 20))
            val bass = bassLp * exp(-st8 * 5) * 0.55

            arpPh += root * 4 * 2.0.pow(tones[s % 8] / 12.0) / RATE
            val arp = sq(arpPh, 0.3) * exp(-st * 16) * 0.09

            var pad = 0.0
            for (k in 0..2) {
                padPh[k] += root * 2 * 2.0.pow(tones[k] / 12.0) * (1 + k * 0.002) / RATE
                pad += tri(padPh[k])
            }
            pad *= 0.035

            val beat = s / 4
            val stb = t - beat * 4 * step
            if (beat != lastBeat) {
                lastBeat = beat
                kickPh = 0.0
            }
            kickPh += (45 + 110 * exp(-stb * 35)) / RATE
            val kick = sin(kickPh * 2 * PI) * exp(-stb * 9) * 0.9

            val snare = if (beat % 2 == 1) {
                noise() * exp(-stb * 16) * 0.3 + sin(2 * PI * 190 * stb) * exp(-stb * 25) * 0.25
            } else 0.0

            val n = noise()
            hatLp += (n - hatLp) * 0.5
            val hat = if (s % 4 == 2) (n - hatLp) * exp(-st * 70) * 0.18 else 0.0

            b[i] = tanh((bass + arp + pad + kick + snare + hat) * 1.1) * 0.85
        }
        return b
    }

    private fun writeWav(f: File, d: DoubleArray) {
        val n = d.size
        val bb = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray())
        bb.putInt(36 + n * 2)
        bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray())
        bb.putInt(16)
        bb.putShort(1.toShort())
        bb.putShort(1.toShort())
        bb.putInt(RATE)
        bb.putInt(RATE * 2)
        bb.putShort(2.toShort())
        bb.putShort(16.toShort())
        bb.put("data".toByteArray())
        bb.putInt(n * 2)
        for (v in d) bb.putShort((v.coerceIn(-1.0, 1.0) * 32767).toInt().toShort())
        FileOutputStream(f).use { it.write(bb.array()) }
    }
}
