package com.neonswarm.game

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The whole game: simulation and rendering. All state is touched only by the render thread;
 * the UI thread talks to it through [touch], [resize], [setTopInset], [systemPause] and [back],
 * which queue events under [lock].
 *
 * World space is 1000 units wide; height follows the screen's aspect ratio.
 */
class Game(private val sfx: Sfx, private val haptics: Haptics, private val prefs: Prefs) {

    enum class State { MENU, PLAYING, PAUSED, DYING, GAME_OVER }

    companion object {
        const val W = 1000f

        const val EV_DOWN = 0
        const val EV_MOVE = 1
        const val EV_UP = 2
        const val EV_REANCHOR = 3
        private const val EV_SYS_PAUSE = 4
        private const val EV_BACK = 5

        private const val DRIFTER = 0
        private const val ZIGZAG = 1
        private const val CHASER = 2
        private const val TANK = 3
        private const val SPLITTER = 4
        private const val MINI = 5
        private const val BOSS = 6

        private const val P_WEAPON = 0
        private const val P_RAPID = 1
        private const val P_SHIELD = 2
        private const val P_MAGNET = 3
        private const val P_BOMB = 4
        private const val P_LIFE = 5

        private const val MAX_LIVES = 5
        private const val MAX_WEAPON = 5
        private const val OVERDRIVE_TIME = 6f
        private const val SENS = 1.3f
        private const val TAU = (PI * 2).toFloat()

        private val CYAN = 0xFF00E5FF.toInt()
        private val MAGENTA = 0xFFFF2BD6.toInt()
        private val YELLOW = 0xFFFFE53B.toInt()
        private val GREEN = 0xFF39FF88.toInt()
        private val ORANGE = 0xFFFF8A2B.toInt()
        private val PURPLE = 0xFFA45BFF.toInt()
        private val RED = 0xFFFF3B5C.toInt()
        private val PINK = 0xFFFF5FA8.toInt()
        private val MINT = 0xFF7CFFD4.toInt()
        private val WHITE = Color.WHITE
    }

    @Volatile
    var state = State.MENU
        private set

    // ------------------------------------------------------------------ cross-thread input
    private val lock = Any()
    private val pending = ArrayList<TouchEv>()
    private val work = ArrayList<TouchEv>()
    private var pendingW = 0
    private var pendingH = 0
    private var pendingTopPx = -1

    fun touch(type: Int, sx: Float, sy: Float) {
        synchronized(lock) { pending.add(TouchEv(type, sx, sy)) }
    }

    fun resize(w: Int, h: Int) {
        synchronized(lock) {
            pendingW = w
            pendingH = h
        }
    }

    fun setTopInset(px: Int) {
        synchronized(lock) { pendingTopPx = px }
    }

    fun systemPause() = touch(EV_SYS_PAUSE, 0f, 0f)

    /** @return true if the game consumed the back press. */
    fun back(): Boolean {
        if (state == State.MENU) return false
        touch(EV_BACK, 0f, 0f)
        return true
    }

    // ------------------------------------------------------------------ screen
    private var screenW = 0
    private var screenH = 0
    private var scale = 1f
    private var H = 1800f
    private var safeTopPx = 0
    private var safeTop = 0f

    private val rnd = Random(System.nanoTime())
    private fun rf(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)

    // ------------------------------------------------------------------ player
    private var px = W / 2
    private var py = 1500f
    private var ptx = px
    private var pty = py
    private var bank = 0f
    private var playerAlive = false
    private var lives = 3
    private var invuln = 0f
    private var shield = 0f
    private var rapid = 0f
    private var magnet = 0f
    private var weapon = 1
    private var fireCd = 0f
    private var shotCount = 0
    private var overdrive = 0f
    private var meter = 0f
    private var dragging = false
    private var lastTx = 0f
    private var lastTy = 0f

    // ------------------------------------------------------------------ score
    private var score = 0L
    private var displayScore = 0f
    private var best = prefs.best
    private var newRecord = false
    private var combo = 0
    private var comboTimer = 0f
    private var comboPop = 0f
    private var maxCombo = 0
    private var kills = 0
    private var killsSinceDrop = 0
    private var gemChain = 0
    private var gemChainT = 0f

    // ------------------------------------------------------------------ waves
    private var wave = 0
    private val spawns = ArrayList<Spawn>()
    private var waveTime = 0f
    private var waveClearT = -1f
    private var damagedThisWave = false
    private var boss: Enemy? = null
    private var bannerText = ""
    private var bannerSub = ""
    private var bannerColor = CYAN
    private var bannerT = 0f

    // ------------------------------------------------------------------ fx / timing
    private var clock = 0f
    private var trauma = 0f
    private var freeze = 0f
    private var dyingT = 0f
    private var gameOverT = 0f
    private var flashT = 0f
    private var flashColor = WHITE
    private var hitSfxCd = 0f
    private var starSpeed = 0.6f
    private var gridScroll = 0f

    // ------------------------------------------------------------------ entities
    private val pBullets = Array(260) { Bullet() }
    private val eBullets = Array(600) { Bullet() }
    private val particles = Array(1500) { Particle() }
    private val rings = Array(48) { Ring() }
    private val gems = Array(240) { Gem() }
    private val cursors = IntArray(5)
    private val enemies = ArrayList<Enemy>()
    private val newEnemies = ArrayList<Enemy>()
    private val powerUps = ArrayList<PowerUp>()
    private val texts = ArrayList<FloatText>()

    private val starN = 150
    private val starX = FloatArray(starN)
    private val starY = FloatArray(starN)
    private val starZ = FloatArray(starN)
    private var starsReady = false

    // ------------------------------------------------------------------ paints & shapes
    private val glowPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        if (Build.VERSION.SDK_INT >= 29) blendMode = BlendMode.PLUS
        else xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val starPaint = Paint().apply { color = WHITE }
    private val bgPaint = Paint()
    private val nebulaPaint1 = Paint()
    private val nebulaPaint2 = Paint()
    private val vignettePaint = Paint()
    private val meterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boldTf: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val monoTf: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = boldTf
    }
    private val dst = RectF()

    private val pathShip = Path().apply {
        moveTo(0f, -52f); lineTo(36f, 32f); lineTo(12f, 18f); lineTo(0f, 30f)
        lineTo(-12f, 18f); lineTo(-36f, 32f); close()
    }
    private val pathDrifter = poly(4, 34f)
    private val pathDrifterIn = poly(4, 14f)
    private val pathZig = poly(3, 36f, PI.toFloat())
    private val pathChaser = Path().apply {
        moveTo(0f, 36f); lineTo(-28f, -28f); lineTo(0f, -12f); lineTo(28f, -28f); close()
    }
    private val pathTank = poly(6, 58f, (PI / 6).toFloat())
    private val pathTankIn = poly(6, 28f, (PI / 6).toFloat())
    private val pathSplit = poly(5, 50f, 0f, 0.5f)
    private val pathMini = poly(3, 22f, PI.toFloat())
    private val pathBoss = poly(8, 140f, 0f, 0.62f)
    private val pathBossIn = poly(8, 72f, (PI / 8).toFloat())
    private val pathGem = poly(4, 11f)
    private val pathHeart = Path().apply {
        moveTo(0f, 18f)
        cubicTo(-30f, -2f, -18f, -26f, 0f, -12f)
        cubicTo(18f, -26f, 30f, -2f, 0f, 18f)
        close()
    }

    private val btnPause = RectF()
    private val btnSound = RectF()
    private val btnVibe = RectF()
    private val btnMenu = RectF()

    private fun poly(n: Int, r: Float, rot: Float = 0f, inner: Float = 0f): Path {
        val p = Path()
        val pts = if (inner > 0f) n * 2 else n
        for (i in 0 until pts) {
            val a = rot + i * TAU / pts - (PI / 2).toFloat()
            val rr = if (inner > 0f && i % 2 == 1) r * inner else r
            val x = cos(a) * rr
            val y = sin(a) * rr
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return p
    }

    // ================================================================== loop

    fun step(rawDt: Float) {
        synchronized(lock) {
            if (pendingW > 0) {
                applySize(pendingW, pendingH)
                pendingW = 0
            }
            if (pendingTopPx >= 0) {
                safeTopPx = pendingTopPx
                pendingTopPx = -1
                if (screenW > 0) applySize(screenW, screenH)
            }
            work.addAll(pending)
            pending.clear()
        }
        if (screenW == 0) {
            work.clear()
            return
        }
        for (i in work.indices) handle(work[i])
        work.clear()

        val dt = rawDt.coerceIn(0f, 0.05f)
        if (state == State.PAUSED) return
        clock += dt

        val targetStar = when {
            overdrive > 0f -> 4f
            state == State.PLAYING -> 1f
            else -> 0.6f
        }
        starSpeed += (targetStar - starSpeed) * min(1f, dt * 3f)
        updateStars(dt)
        trauma = max(0f, trauma - dt * 1.6f)
        flashT = max(0f, flashT - dt)
        bannerT = max(0f, bannerT - dt)
        comboPop = max(0f, comboPop - dt * 4f)
        hitSfxCd -= dt

        when (state) {
            State.MENU -> {
                val mx = W / 2 + sin(clock * 1.1f) * 140f
                engineTrail(mx, H * 0.56f + 40f, CYAN, 1)
                updateFx(dt)
            }
            State.PLAYING -> {
                if (freeze > 0f) freeze -= dt else updateWorld(dt)
            }
            State.DYING -> {
                dyingT += dt
                updateWorld(dt * 0.3f)
                if (dyingT > 1.7f) toGameOver()
            }
            State.GAME_OVER -> {
                gameOverT += dt
                updateWorld(dt)
            }
            State.PAUSED -> {}
        }
        displayScore += (score - displayScore) * min(1f, dt * 9f)
        if (abs(score - displayScore) < 1f) displayScore = score.toFloat()
    }

    private fun applySize(w: Int, h: Int) {
        val firstTime = screenW == 0
        screenW = w
        screenH = h
        scale = w / W
        H = h / scale
        safeTop = safeTopPx / scale
        val top = safeTop + 30f

        btnPause.set(W - 120f, top - 10f, W - 20f, top + 90f)
        btnSound.set(W / 2 - 330f, H - 240f, W / 2 - 20f, H - 140f)
        btnVibe.set(W / 2 + 20f, H - 240f, W / 2 + 330f, H - 140f)
        btnMenu.set(W / 2 - 170f, H * 0.68f, W / 2 + 170f, H * 0.68f + 100f)

        val fw = w.toFloat()
        val fh = h.toFloat()
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, fh,
            intArrayOf(0xFF140733.toInt(), 0xFF07021A.toInt(), 0xFF000000.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        nebulaPaint1.shader = RadialGradient(
            fw * 0.15f, fh * 0.22f, fw * 0.75f,
            intArrayOf(0x38FF2BD6, 0x00FF2BD6), null, Shader.TileMode.CLAMP,
        )
        nebulaPaint2.shader = RadialGradient(
            fw * 0.9f, fh * 0.62f, fw * 0.7f,
            intArrayOf(0x2600E5FF, 0x0000E5FF), null, Shader.TileMode.CLAMP,
        )
        vignettePaint.shader = RadialGradient(
            fw / 2, fh / 2, max(fw, fh) * 0.72f,
            intArrayOf(0x00000000, 0x00000000, 0xA0000000.toInt()),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
        )
        val mw = 560f
        meterPaint.shader = LinearGradient(
            W / 2 - mw / 2, 0f, W / 2 + mw / 2, 0f,
            intArrayOf(MAGENTA, YELLOW), null, Shader.TileMode.CLAMP,
        )

        if (!starsReady) {
            for (i in 0 until starN) {
                starX[i] = rf(0f, W)
                starY[i] = rf(0f, H)
                starZ[i] = rnd.nextFloat()
            }
            starsReady = true
        }
        if (firstTime) {
            px = W / 2; py = H * 0.8f; ptx = px; pty = py
        }
        ptx = ptx.coerceIn(40f, W - 40f)
        pty = pty.coerceIn(H * 0.2f, H - 70f)
    }

    // ================================================================== input

    private fun handle(e: TouchEv) {
        val x = e.x / scale
        val y = e.y / scale
        when (e.type) {
            EV_SYS_PAUSE -> if (state == State.PLAYING) pauseGame()
            EV_BACK -> when (state) {
                State.PLAYING -> pauseGame()
                State.PAUSED, State.GAME_OVER -> toMenu()
                else -> {}
            }
            EV_DOWN -> onDown(x, y)
            EV_MOVE -> if (state == State.PLAYING && dragging) {
                ptx = (ptx + (x - lastTx) * SENS).coerceIn(40f, W - 40f)
                pty = (pty + (y - lastTy) * SENS).coerceIn(H * 0.2f, H - 70f)
                lastTx = x
                lastTy = y
            }
            EV_REANCHOR -> {
                lastTx = x
                lastTy = y
            }
            EV_UP -> dragging = false
        }
    }

    private fun near(r: RectF, x: Float, y: Float, pad: Float = 20f) =
        x >= r.left - pad && x <= r.right + pad && y >= r.top - pad && y <= r.bottom + pad

    private fun onDown(x: Float, y: Float) {
        when (state) {
            State.MENU -> when {
                near(btnSound, x, y, 0f) -> toggleSound()
                near(btnVibe, x, y, 0f) -> toggleVibe()
                else -> startDragging(x, y)
            }
            State.PLAYING -> {
                if (near(btnPause, x, y, 30f)) {
                    pauseGame()
                } else {
                    dragging = true
                    lastTx = x
                    lastTy = y
                }
            }
            State.PAUSED -> when {
                near(btnSound, x, y, 0f) -> toggleSound()
                near(btnVibe, x, y, 0f) -> toggleVibe()
                near(btnMenu, x, y, 0f) -> toMenu()
                else -> {
                    state = State.PLAYING
                    sfx.play(Sfx.CLICK, 0.5f)
                    dragging = true
                    lastTx = x
                    lastTy = y
                }
            }
            State.GAME_OVER -> if (gameOverT > 1.0f) {
                if (near(btnMenu, x, y, 0f)) toMenu() else startDragging(x, y)
            }
            State.DYING -> {}
        }
    }

    /** Starts a run from the tap that began it, so the same finger can steer immediately. */
    private fun startDragging(x: Float, y: Float) {
        newGame()
        dragging = true
        lastTx = x
        lastTy = y
    }

    private fun toggleSound() {
        prefs.sound = !prefs.sound
        if (prefs.sound) {
            sfx.startMusic()
            sfx.play(Sfx.CLICK, 0.6f)
        } else {
            sfx.stopMusic()
        }
    }

    private fun toggleVibe() {
        prefs.vibration = !prefs.vibration
        sfx.play(Sfx.CLICK, 0.6f)
        haptics.pulse(40, 180)
    }

    private fun pauseGame() {
        state = State.PAUSED
        dragging = false
    }

    // ================================================================== game flow

    private fun clearWorld() {
        enemies.clear()
        newEnemies.clear()
        powerUps.clear()
        texts.clear()
        spawns.clear()
        for (b in pBullets) b.alive = false
        for (b in eBullets) b.alive = false
        for (p in particles) p.alive = false
        for (r in rings) r.alive = false
        for (g in gems) g.alive = false
        boss = null
    }

    private fun newGame() {
        clearWorld()
        score = 0; displayScore = 0f; newRecord = false
        combo = 0; comboTimer = 0f; maxCombo = 0; kills = 0; killsSinceDrop = 0
        gemChain = 0; gemChainT = 0f
        lives = 3; weapon = 1; invuln = 1.5f; shield = 0f; rapid = 0f; magnet = 0f
        overdrive = 0f; meter = 0f; fireCd = 0.3f
        px = W / 2; py = H * 0.8f; ptx = px; pty = py; bank = 0f
        playerAlive = true
        dragging = false
        trauma = 0f; freeze = 0f; flashT = 0f
        best = prefs.best
        state = State.PLAYING
        sfx.play(Sfx.CLICK, 0.6f)
        sfx.startMusic()
        startWave(1)
    }

    private fun toMenu() {
        clearWorld()
        playerAlive = false
        bannerT = 0f
        state = State.MENU
        sfx.play(Sfx.CLICK, 0.5f)
    }

    private fun toGameOver() {
        state = State.GAME_OVER
        gameOverT = 0f
        prefs.games = prefs.games + 1
        if (score > prefs.best) {
            prefs.best = score
            newRecord = true
            sfx.play(Sfx.POWER, 0.7f, 0.75f)
        }
        if (wave > prefs.bestWave) prefs.bestWave = wave
        best = prefs.best
    }

    private fun banner(text: String, sub: String, color: Int) {
        bannerText = text
        bannerSub = sub
        bannerColor = color
        bannerT = 2.3f
    }

    private fun startWave(n: Int) {
        wave = n
        waveTime = 0f
        waveClearT = -1f
        damagedThisWave = false
        spawns.clear()
        if (n % 5 == 0) {
            banner("אזהרה!", "בוס מתקרב", RED)
            sfx.play(Sfx.ALARM, 0.7f, 1f, 3)
            haptics.pulse(250, 160)
            spawns.add(Spawn(2.6f, BOSS, W / 2))
        } else {
            banner("גל $n", waveHint(n), CYAN)
            buildWave(n)
        }
        spawns.sortBy { it.time }
    }

    private fun waveHint(n: Int) = when (n) {
        1 -> "גרור כדי לזוז"
        2 -> "רודפים בדרך"
        3 -> "טנקים יורים בחזרה"
        4 -> "מתפצלים: פוצץ את כולם"
        6 -> "נחיל!"
        else -> if (n > 5) "קשה יותר, מהר יותר" else ""
    }

    private fun buildWave(n: Int) {
        var t = 1.8f
        var budget = 8 + n * 3
        val gap = max(0.5f, 1.6f - n * 0.07f)
        val available = ArrayList<Int>()
        available.addAll(listOf(0, 1, 2))
        if (n >= 2) available.add(3)
        if (n >= 3) available.add(4)
        if (n >= 4) available.add(5)
        if (n >= 6) available.add(6)
        if (n >= 8) available.add(4)
        while (budget > 0) {
            var extra = 0f
            when (available[rnd.nextInt(available.size)]) {
                0 -> {
                    for (i in 0 until 3) spawns.add(Spawn(t + i * 0.35f, DRIFTER, rf(80f, W - 80f)))
                    budget -= 3; extra = 0.7f
                }
                1 -> {
                    val cnt = 5
                    for (i in 0 until cnt) spawns.add(Spawn(t, DRIFTER, 120f + i * (W - 240f) / (cnt - 1)))
                    budget -= 5
                }
                2 -> {
                    val x = rf(260f, W - 260f)
                    for (i in 0 until 6) spawns.add(Spawn(t + i * 0.2f, ZIGZAG, x))
                    budget -= 5; extra = 1.2f
                }
                3 -> {
                    spawns.add(Spawn(t, CHASER, rf(100f, W / 2 - 50f)))
                    spawns.add(Spawn(t + 0.3f, CHASER, rf(W / 2 + 50f, W - 100f)))
                    budget -= 4
                }
                4 -> {
                    spawns.add(Spawn(t, TANK, rf(150f, W - 150f), rf(H * 0.12f, H * 0.3f)))
                    budget -= 6; extra = 1f
                }
                5 -> {
                    spawns.add(Spawn(t, SPLITTER, rf(150f, W - 150f)))
                    budget -= 5
                }
                6 -> {
                    for (i in 0 until 10) spawns.add(Spawn(t + i * 0.12f, MINI, rf(60f, W - 60f)))
                    budget -= 6; extra = 1.2f
                }
            }
            t += gap + extra
        }
    }

    private fun onWaveCleared() {
        if (wave <= 0) return
        val perfect = !damagedThisWave
        var bonus = wave * 250L
        if (perfect) bonus *= 2
        score += bonus
        if (perfect) banner("גל $wave · מושלם!", "+$bonus בונוס ללא פגיעה", YELLOW)
        else banner("גל $wave הושלם", "+$bonus", GREEN)
        sfx.play(Sfx.POWER, 0.5f, 0.85f)
    }

    // ================================================================== world update

    private fun updateWorld(dt: Float) {
        if (playerAlive) updatePlayer(dt)
        if (state == State.PLAYING) updateWaves(dt)
        updateEnemies(dt)
        updatePlayerBullets(dt)
        updateEnemyBullets(dt)
        updateGems(dt)
        updatePowerUps(dt)
        updateFx(dt)
        collide()
        if (comboTimer > 0f) {
            comboTimer -= dt
            if (comboTimer <= 0f) combo = 0
        }
        if (gemChainT > 0f) {
            gemChainT -= dt
            if (gemChainT <= 0f) gemChain = 0
        }
    }

    private fun updateFx(dt: Float) {
        updateParticles(dt)
        updateRings(dt)
        updateTexts(dt)
        gridScroll += dt * starSpeed * 0.6f
    }

    private fun updateStars(dt: Float) {
        for (i in 0 until starN) {
            starY[i] += (40f + starZ[i] * 320f) * starSpeed * dt
            if (starY[i] > H + 20f) {
                starY[i] = -20f
                starX[i] = rf(0f, W)
                starZ[i] = rnd.nextFloat()
            }
        }
    }

    private fun updatePlayer(dt: Float) {
        val k = 1f - exp(-dt * 22f)
        val ox = px
        px += (ptx - px) * k
        py += (pty - py) * k
        val vx = (px - ox) / max(dt, 1e-4f)
        bank += ((vx / 1600f).coerceIn(-1f, 1f) - bank) * min(1f, dt * 10f)

        invuln = max(0f, invuln - dt)
        shield = max(0f, shield - dt)
        rapid = max(0f, rapid - dt)
        magnet = max(0f, magnet - dt)
        if (overdrive > 0f) {
            overdrive -= dt
            if (overdrive <= 0f) {
                overdrive = 0f
                invuln = max(invuln, 1f)
                texts.add(FloatText("OVERDRIVE נגמר", W / 2, H * 0.5f, YELLOW, 44f, 1.0f))
            }
        }

        val rate = when {
            overdrive > 0f -> 0.055f
            rapid > 0f -> 0.065f
            else -> 0.115f
        }
        fireCd -= dt
        if (fireCd < -rate) fireCd = 0f
        if (fireCd <= 0f) {
            fire()
            fireCd += rate
        }
        engineTrail(px, py + 34f, if (overdrive > 0f) YELLOW else CYAN, if (overdrive > 0f) 3 else 1)
    }

    private fun engineTrail(x: Float, y: Float, color: Int, n: Int) {
        for (i in 0 until n) {
            particle(x + rf(-8f, 8f), y, rf(-40f, 40f), rf(300f, 520f), rf(0.18f, 0.3f), rf(10f, 18f), color, 2f)
        }
    }

    private fun fire() {
        val lvl = if (overdrive > 0f) 6 else weapon
        val y = py - 50f
        val color = if (overdrive > 0f) YELLOW else CYAN
        when (lvl) {
            1 -> shot(px, y, 0f, color)
            2 -> { shot(px - 16f, y, 0f, color); shot(px + 16f, y, 0f, color) }
            3 -> { shot(px, y, 0f, color); shot(px - 18f, y, -0.12f, color); shot(px + 18f, y, 0.12f, color) }
            4 -> {
                shot(px - 12f, y, 0f, color); shot(px + 12f, y, 0f, color)
                shot(px - 24f, y, -0.16f, color); shot(px + 24f, y, 0.16f, color)
            }
            5 -> {
                shot(px, y, 0f, color)
                shot(px - 14f, y, -0.08f, color); shot(px + 14f, y, 0.08f, color)
                shot(px - 26f, y, -0.22f, color); shot(px + 26f, y, 0.22f, color)
            }
            else -> for (i in -3..3) shot(px + i * 10f, y, i * 0.07f, color)
        }
        shotCount++
        val everyOther = rapid > 0f || overdrive > 0f
        if (!everyOther || shotCount % 2 == 0) sfx.play(Sfx.SHOOT, 0.16f, rf(0.95f, 1.05f))
    }

    private fun shot(x: Float, y: Float, ang: Float, color: Int) {
        val b = take(pBullets, 0)
        val speed = 2000f
        b.x = x; b.y = y
        b.vx = sin(ang) * speed
        b.vy = -cos(ang) * speed
        b.r = 12f
        b.dmg = 1f
        b.color = color
    }

    private fun updateWaves(dt: Float) {
        waveTime += dt
        while (spawns.isNotEmpty() && spawns[0].time <= waveTime) spawnEnemy(spawns.removeAt(0))
        if (spawns.isEmpty() && enemies.isEmpty()) {
            if (waveClearT < 0f) {
                waveClearT = 0f
                onWaveCleared()
            } else {
                waveClearT += dt
                if (waveClearT > 2.4f) startWave(wave + 1)
            }
        }
    }

    private val speedMul get() = min(1.6f, 1f + wave * 0.035f)

    private fun spawnEnemy(s: Spawn) {
        val e = Enemy(s.type)
        e.x = s.x; e.baseX = s.x; e.y = -80f; e.param = s.param
        val sm = speedMul
        when (s.type) {
            DRIFTER -> {
                e.r = 34f; e.hp = 1f + wave / 4; e.vy = rf(220f, 300f) * sm; e.vx = rf(-40f, 40f)
                e.color = CYAN; e.value = 100
            }
            ZIGZAG -> {
                e.r = 34f; e.hp = 2f + wave / 4; e.vy = 200f * sm; e.color = MAGENTA; e.value = 150
            }
            CHASER -> {
                e.r = 32f; e.hp = 2f + wave / 3; e.vy = 350f; e.color = ORANGE; e.value = 200
            }
            TANK -> {
                e.r = 58f; e.hp = 10f + wave * 1.5f; e.vy = 150f; e.color = PURPLE; e.value = 600
                e.fireCd = 1.4f; e.y = -100f
            }
            SPLITTER -> {
                e.r = 50f; e.hp = 5f + wave / 2; e.vy = 140f * sm; e.color = GREEN; e.value = 300
            }
            MINI -> {
                e.r = 22f; e.hp = 1f; e.vy = rf(380f, 480f) * sm; e.vx = rf(-80f, 80f)
                e.color = YELLOW; e.value = 60
            }
            BOSS -> {
                val idx = max(1, wave / 5)
                e.r = 140f; e.hp = 300f + (idx - 1) * 240f; e.vy = 140f; e.color = RED
                e.value = 5000 * idx; e.y = -200f; e.fireCd = 1f
                boss = e
            }
        }
        e.maxHp = e.hp
        enemies.add(e)
    }

    private fun updateEnemies(dt: Float) {
        for (i in enemies.indices) {
            val e = enemies[i]
            if (!e.alive) continue
            e.t += dt
            e.flash -= dt
            when (e.type) {
                DRIFTER, MINI -> {
                    e.x += e.vx * dt; e.y += e.vy * dt; e.rot += dt * 90f
                }
                ZIGZAG -> {
                    e.y += e.vy * dt
                    e.x = (e.baseX + sin(e.t * 2.6f) * 160f).coerceIn(40f, W - 40f)
                    e.rot = cos(e.t * 2.6f) * 25f
                }
                CHASER -> {
                    if (e.y < 220f && e.t < 1.5f) {
                        e.y += e.vy * dt
                    } else if (e.t < 7f && playerAlive) {
                        val dx = px - e.x
                        val dy = py - e.y
                        val d = max(1f, sqrt(dx * dx + dy * dy))
                        val spd = 330f * speedMul
                        e.vx += (dx / d * spd - e.vx) * dt * 1.8f
                        e.vy += (dy / d * spd - e.vy) * dt * 1.8f
                        e.x += e.vx * dt; e.y += e.vy * dt
                    } else {
                        e.vy += 500f * dt
                        e.x += e.vx * dt; e.y += e.vy * dt
                    }
                    e.rot = Math.toDegrees(atan2(e.vy, e.vx).toDouble()).toFloat() - 90f
                }
                TANK -> {
                    if (e.y < e.param) {
                        e.y += e.vy * dt
                    } else if (e.t < 15f) {
                        e.x = (e.x + cos(e.t * 0.9f) * 90f * dt).coerceIn(80f, W - 80f)
                    } else {
                        e.y += 120f * dt
                    }
                    e.rot += dt * 20f
                    e.fireCd -= dt
                    if (e.fireCd <= 0f && e.y > 0f) {
                        val count = when {
                            wave < 6 -> 1
                            wave < 12 -> 3
                            else -> 5
                        }
                        fan(e.x, e.y + 40f, aimAt(e.x, e.y), count, 0.22f, bulletSpeed())
                        e.fireCd = max(0.9f, 2.0f - wave * 0.05f)
                        e.flash = 0.05f
                    }
                }
                SPLITTER -> {
                    e.y += e.vy * dt; e.rot += dt * 60f
                }
                BOSS -> updateBoss(e, dt)
            }
            if (e.y > H + 160f || e.x < -220f || e.x > W + 220f) e.alive = false
        }
        enemies.removeAll { !it.alive }
        if (newEnemies.isNotEmpty()) {
            enemies.addAll(newEnemies)
            newEnemies.clear()
        }
    }

    private fun bulletSpeed() = min(560f, 360f + wave * 8f)

    private fun aimAt(x: Float, y: Float): Float =
        if (playerAlive) atan2(py - y, px - x) else (PI / 2).toFloat()

    private fun fan(x: Float, y: Float, center: Float, count: Int, spread: Float, speed: Float) {
        val start = center - spread * (count - 1) / 2f
        for (i in 0 until count) enemyShot(x, y, start + i * spread, speed)
    }

    private fun enemyShot(x: Float, y: Float, ang: Float, speed: Float, color: Int = PINK) {
        val b = take(eBullets, 1)
        b.x = x; b.y = y
        b.vx = cos(ang) * speed
        b.vy = sin(ang) * speed
        b.r = 13f
        b.color = color
    }

    private fun updateBoss(e: Enemy, dt: Float) {
        if (!e.entered) {
            e.y += e.vy * dt
            if (e.y >= H * 0.18f + 60f) e.entered = true
            return
        }
        e.x = W / 2 + sin(e.t * 0.55f) * (W / 2 - 190f)
        e.rot += dt * 25f
        val enraged = e.hp < e.maxHp * 0.5f
        e.phaseT += dt
        if (e.phaseT > 5f) {
            e.phaseT = 0f
            e.phase = (e.phase + 1) % 3
            e.fireCd = 0.6f
        }
        e.fireCd -= dt
        if (e.fireCd > 0f) return
        val idx = max(1, wave / 5)
        val spd = min(330f + idx * 25f, 480f)
        when (e.phase) {
            0 -> {
                val n = if (enraged) 26 else 18
                val off = e.t * 0.7f
                for (i in 0 until n) enemyShot(e.x, e.y, off + i * TAU / n, spd, RED)
                e.fireCd = if (enraged) 0.8f else 1.2f
                trauma = min(1f, trauma + 0.08f)
            }
            1 -> {
                e.spiral += 0.37f
                val arms = if (enraged) 3 else 2
                for (k in 0 until arms) enemyShot(e.x, e.y + 40f, e.spiral + k * TAU / arms, spd * 0.85f, MAGENTA)
                e.fireCd = 0.09f
            }
            else -> {
                fan(e.x, e.y + 60f, aimAt(e.x, e.y), if (enraged) 7 else 5, 0.18f, spd * 1.15f)
                e.fireCd = 0.75f
            }
        }
    }

    private fun updatePlayerBullets(dt: Float) {
        for (b in pBullets) {
            if (!b.alive) continue
            b.x += b.vx * dt
            b.y += b.vy * dt
            if (b.y < -60f || b.x < -40f || b.x > W + 40f) b.alive = false
        }
    }

    private fun updateEnemyBullets(dt: Float) {
        for (b in eBullets) {
            if (!b.alive) continue
            b.x += b.vx * dt
            b.y += b.vy * dt
            if (b.y < -80f || b.y > H + 80f || b.x < -80f || b.x > W + 80f) b.alive = false
        }
    }

    private fun spawnGem(x: Float, y: Float) {
        val g = take(gems, 3)
        g.x = x; g.y = y
        g.vx = rf(-260f, 260f)
        g.vy = rf(-380f, -60f)
        g.t = 0f
    }

    private fun updateGems(dt: Float) {
        val attract = if (magnet > 0f || overdrive > 0f) 950f else 200f
        for (g in gems) {
            if (!g.alive) continue
            g.t += dt
            val dx = px - g.x
            val dy = py - g.y
            val d2 = dx * dx + dy * dy
            if (playerAlive && d2 < attract * attract && g.t > 0.25f) {
                val d = max(1f, sqrt(d2))
                g.vx += dx / d * 3400f * dt
                g.vy += dy / d * 3400f * dt
                val sp = sqrt(g.vx * g.vx + g.vy * g.vy)
                if (sp > 1700f) {
                    g.vx *= 1700f / sp; g.vy *= 1700f / sp
                }
            } else {
                g.vx *= exp(-2.5f * dt)
                g.vy += (150f - g.vy) * min(1f, dt * 2f)
            }
            g.x += g.vx * dt
            g.y += g.vy * dt
            if (playerAlive && d2 < 48f * 48f) {
                g.alive = false
                collectGem(g.x, g.y)
            } else if (g.y > H + 40f || g.t > 12f) {
                g.alive = false
            }
        }
    }

    private fun collectGem(x: Float, y: Float) {
        score += 10L * multiplier()
        gemChain++
        gemChainT = 0.6f
        sfx.play(Sfx.GEM, 0.32f, min(2f, 0.8f + gemChain * 0.045f))
        particle(x, y, 0f, 0f, 0.2f, 26f, MINT, 0f)
        if (overdrive <= 0f) {
            meter += 0.022f
            if (meter >= 1f) activateOverdrive()
        }
    }

    private fun activateOverdrive() {
        overdrive = OVERDRIVE_TIME
        meter = 0f
        banner("OVERDRIVE!", "כוח אש מקסימלי · חסינות", YELLOW)
        sfx.play(Sfx.OVERDRIVE, 0.8f, 1f, 3)
        haptics.pulse(220, 220)
        ring(px, py, 700f, YELLOW, 0.6f, 14f)
        flash(YELLOW, 0.25f)
        trauma = min(1f, trauma + 0.35f)
    }

    private fun updatePowerUps(dt: Float) {
        val attract = if (magnet > 0f || overdrive > 0f) 950f else 150f
        for (i in powerUps.indices.reversed()) {
            val p = powerUps[i]
            p.t += dt
            val dx = px - p.x
            val dy = py - p.y
            val d2 = dx * dx + dy * dy
            if (playerAlive && d2 < attract * attract) {
                val d = max(1f, sqrt(d2))
                p.x += dx / d * 900f * dt
                p.y += dy / d * 900f * dt
            } else {
                p.y += 170f * dt
                p.x += sin(p.t * 2f) * 40f * dt
            }
            if (playerAlive && d2 < 66f * 66f) {
                applyPowerUp(p.type)
                powerUps.removeAt(i)
            } else if (p.y > H + 60f) {
                powerUps.removeAt(i)
            }
        }
    }

    private fun dropPowerUp(x: Float, y: Float) {
        killsSinceDrop = 0
        val weights = intArrayOf(
            if (weapon < MAX_WEAPON) 32 else 10, 20, 18, 15, 12,
            if (lives < MAX_LIVES) 6 else 0,
        )
        var roll = rnd.nextInt(weights.sum())
        var type = 0
        for (i in weights.indices) {
            if (roll < weights[i]) {
                type = i; break
            }
            roll -= weights[i]
        }
        powerUps.add(PowerUp(type, x, y))
    }

    private fun applyPowerUp(type: Int) {
        val color = powerColor(type)
        val label = when (type) {
            P_WEAPON -> if (weapon < MAX_WEAPON) {
                weapon++
                "כוח אש $weapon"
            } else {
                score += 1000L
                "כוח אש מקס · +1000"
            }
            P_RAPID -> { rapid = 8f; "ירי מהיר!" }
            P_SHIELD -> { shield = 15f; "מגן!" }
            P_MAGNET -> { magnet = 10f; "מגנט!" }
            P_BOMB -> { bomb(); "בום!" }
            else -> { lives = min(MAX_LIVES, lives + 1); "חיים +1" }
        }
        texts.add(FloatText(label, px, py - 90f, color, 50f, 1.1f))
        sfx.play(Sfx.POWER, 0.6f)
        haptics.pulse(30, 140)
        ring(px, py, 180f, color, 0.35f, 8f)
    }

    private fun bomb() {
        ring(px, py, 1700f, WHITE, 0.7f, 22f)
        ring(px, py, 1200f, MAGENTA, 0.55f, 14f)
        flash(WHITE, 0.35f)
        trauma = min(1f, trauma + 0.7f)
        sfx.play(Sfx.BIG, 0.9f, 1.1f, 3)
        haptics.pulse(140, 255)
        bulletsToGems(40)
        for (i in enemies.indices) {
            val e = enemies[i]
            if (e.alive) damage(e, if (e.type == BOSS) 40f else 14f, e.x, e.y)
        }
    }

    private fun bulletsToGems(maxGems: Int) {
        var n = 0
        for (b in eBullets) {
            if (!b.alive) continue
            b.alive = false
            sparks(b.x, b.y, b.color, 2)
            if (n < maxGems) {
                spawnGem(b.x, b.y)
                n++
            }
        }
    }

    // ================================================================== collisions & damage

    private fun collide() {
        for (b in pBullets) {
            if (!b.alive) continue
            for (i in enemies.indices) {
                val e = enemies[i]
                if (!e.alive) continue
                val rr = (if (e.type == BOSS) e.r * 0.8f else e.r) + b.r
                val dx = e.x - b.x
                val dy = e.y - b.y
                if (dx * dx + dy * dy < rr * rr) {
                    b.alive = false
                    damage(e, b.dmg, b.x, b.y)
                    break
                }
            }
        }
        if (!playerAlive) return

        if (invuln <= 0f && overdrive <= 0f) {
            for (b in eBullets) {
                if (!b.alive) continue
                val rr = b.r + 14f
                val dx = b.x - px
                val dy = b.y - py
                if (dx * dx + dy * dy < rr * rr) {
                    b.alive = false
                    playerHit()
                    break
                }
            }
        }
        for (i in enemies.indices) {
            val e = enemies[i]
            if (!e.alive || !playerAlive) continue
            val rr = e.r * 0.8f + 26f
            val dx = e.x - px
            val dy = e.y - py
            if (dx * dx + dy * dy < rr * rr) {
                if (overdrive > 0f) {
                    if (e.type != BOSS) damage(e, 999f, e.x, e.y)
                } else if (invuln <= 0f) {
                    playerHit()
                    if (e.type != BOSS) damage(e, 4f, e.x, e.y)
                }
            }
        }
    }

    private fun damage(e: Enemy, dmg: Float, hx: Float, hy: Float) {
        if (!e.alive) return
        e.hp -= dmg
        e.flash = 0.06f
        sparks(hx, hy, e.color, 3)
        if (hitSfxCd <= 0f) {
            sfx.play(Sfx.HIT, 0.22f, rf(0.9f, 1.2f))
            hitSfxCd = 0.05f
        }
        if (e.hp <= 0f) kill(e)
    }

    private fun multiplier() = min(10, 1 + combo / 10)

    private fun kill(e: Enemy) {
        e.alive = false
        kills++
        killsSinceDrop++
        combo++
        comboTimer = 2f
        comboPop = 1f
        if (combo > maxCombo) maxCombo = combo
        val pts = e.value.toLong() * multiplier()
        score += pts
        texts.add(FloatText("+$pts", e.x, e.y - 10f, e.color, if (e.value >= 500) 52f else 36f, 0.8f))

        if (e.type == BOSS) {
            bossDeath(e)
        } else {
            explode(e.x, e.y, e.color, e.r)
            val big = e.type == TANK || e.type == SPLITTER
            sfx.play(if (big) Sfx.BIG else Sfx.EXPLODE, if (big) 0.55f else 0.45f, rf(0.85f, 1.15f))
            trauma = min(1f, trauma + if (big) 0.35f else 0.1f)
            haptics.pulse(if (big) 45L else 14L, if (big) 200 else 70)
            val gemsN = when (e.type) {
                TANK -> 7
                SPLITTER -> 3
                else -> if (rnd.nextFloat() < 0.35f) 2 else 1
            }
            for (i in 0 until gemsN) spawnGem(e.x, e.y)
            val chance = when (e.type) {
                TANK -> 0.4f
                SPLITTER -> 0.15f
                else -> 0.045f
            }
            if (rnd.nextFloat() < chance || killsSinceDrop >= 24) dropPowerUp(e.x, e.y)
            if (e.type == SPLITTER) {
                for (k in -1..1) {
                    val m = Enemy(MINI)
                    m.x = e.x; m.y = e.y; m.r = 22f; m.hp = 1f + wave / 6; m.maxHp = m.hp
                    m.vx = k * 230f; m.vy = 320f; m.color = YELLOW; m.value = 60
                    newEnemies.add(m)
                }
            }
        }

        if (combo > 0 && combo % 25 == 0) {
            texts.add(FloatText("רצף $combo!", W / 2, H * 0.56f, YELLOW, 70f, 1.2f))
            sfx.play(Sfx.POWER, 0.5f, 1.5f)
            haptics.pulse(60, 180)
        }
    }

    private fun bossDeath(e: Enemy) {
        boss = null
        freeze = 0.2f
        flash(WHITE, 0.5f)
        trauma = 1f
        sfx.play(Sfx.BIG, 1f, 0.7f, 3)
        sfx.play(Sfx.EXPLODE, 0.8f, 0.6f, 3)
        haptics.pulse(450, 255)
        explode(e.x, e.y, RED, 140f)
        for (i in 0 until 5) {
            explode(e.x + rf(-120f, 120f), e.y + rf(-120f, 120f), if (i % 2 == 0) MAGENTA else YELLOW, 60f)
        }
        ring(e.x, e.y, 1400f, WHITE, 0.9f, 24f)
        bulletsToGems(60)
        for (i in 0 until 40) spawnGem(e.x + rf(-60f, 60f), e.y + rf(-60f, 60f))
        powerUps.add(PowerUp(P_WEAPON, e.x - 90f, e.y))
        powerUps.add(PowerUp(P_SHIELD, e.x + 90f, e.y))
        dropPowerUp(e.x, e.y + 60f)
        banner("הבוס הושמד!", "+${e.value.toLong() * multiplier()}", YELLOW)
    }

    private fun playerHit() {
        if (shield > 0f) {
            shield = 0f
            invuln = 1f
            ring(px, py, 260f, GREEN, 0.4f, 12f)
            sfx.play(Sfx.HIT, 0.6f, 0.6f)
            haptics.pulse(70, 200)
            trauma = min(1f, trauma + 0.4f)
            clearBulletsNear(px, py, 260f)
            return
        }
        lives--
        damagedThisWave = true
        combo = 0
        comboTimer = 0f
        weapon = max(1, weapon - 1)
        invuln = 2.2f
        trauma = min(1f, trauma + 0.8f)
        flash(RED, 0.35f)
        sfx.play(Sfx.HURT, 0.8f, 1f, 3)
        haptics.pulse(180, 255)
        explode(px, py, CYAN, 34f)
        clearBulletsNear(px, py, 380f)
        if (lives <= 0) die()
    }

    private fun clearBulletsNear(x: Float, y: Float, r: Float) {
        for (b in eBullets) {
            if (!b.alive) continue
            val dx = b.x - x
            val dy = b.y - y
            if (dx * dx + dy * dy < r * r) {
                b.alive = false
                sparks(b.x, b.y, b.color, 2)
            }
        }
    }

    private fun die() {
        playerAlive = false
        dragging = false
        state = State.DYING
        dyingT = 0f
        explode(px, py, CYAN, 120f)
        explode(px, py, MAGENTA, 70f)
        ring(px, py, 900f, WHITE, 0.8f, 18f)
        sfx.play(Sfx.BIG, 1f, 0.8f, 3)
        haptics.pulse(500, 255)
        trauma = 1f
    }

    // ================================================================== particles

    private fun <T : Poolable> take(arr: Array<T>, slot: Int): T {
        val n = arr.size
        var i = cursors[slot]
        val scan = min(n, 160)
        for (k in 0 until scan) {
            val o = arr[i]
            i = if (i + 1 == n) 0 else i + 1
            if (!o.alive) {
                cursors[slot] = i
                o.alive = true
                return o
            }
        }
        val o = arr[i]
        cursors[slot] = if (i + 1 == n) 0 else i + 1
        o.alive = true
        return o
    }

    private fun particle(x: Float, y: Float, vx: Float, vy: Float, life: Float, size: Float, color: Int, drag: Float) {
        val p = take(particles, 2)
        p.x = x; p.y = y; p.vx = vx; p.vy = vy
        p.life = life; p.maxLife = life; p.size = size; p.color = color; p.drag = drag
    }

    private fun sparks(x: Float, y: Float, color: Int, n: Int) {
        for (i in 0 until n) {
            val a = rf(0f, TAU)
            val s = rf(120f, 460f)
            particle(x, y, cos(a) * s, sin(a) * s, rf(0.12f, 0.3f), rf(6f, 12f), color, 5f)
        }
    }

    private fun explode(x: Float, y: Float, color: Int, r: Float) {
        val k = (r / 34f).coerceIn(0.6f, 4f)
        val sk = sqrt(k)
        val n = (20 * k).toInt().coerceAtMost(110)
        for (i in 0 until n) {
            val a = rf(0f, TAU)
            val s = rf(120f, 640f) * sk
            particle(
                x, y, cos(a) * s, sin(a) * s, rf(0.35f, 0.85f), rf(10f, 24f) * sk,
                if (rnd.nextFloat() < 0.22f) WHITE else color, 3.2f,
            )
        }
        for (i in 0 until 6) {
            val a = rf(0f, TAU)
            val s = rf(600f, 1100f) * sk
            particle(x, y, cos(a) * s, sin(a) * s, rf(0.4f, 0.7f), 7f, WHITE, 1.6f)
        }
        ring(x, y, r * 3.2f, color, 0.35f, 10f)
        particle(x, y, 0f, 0f, 0.16f, r * 2.6f, WHITE, 0f)
    }

    private fun ring(x: Float, y: Float, maxR: Float, color: Int, life: Float, width: Float) {
        val r = take(rings, 4)
        r.x = x; r.y = y; r.maxR = maxR; r.color = color
        r.life = life; r.maxLife = life; r.width = width
    }

    private fun flash(color: Int, time: Float) {
        flashColor = color
        flashT = time
    }

    private fun updateParticles(dt: Float) {
        for (p in particles) {
            if (!p.alive) continue
            p.life -= dt
            if (p.life <= 0f) {
                p.alive = false
                continue
            }
            val f = exp(-p.drag * dt)
            p.vx *= f; p.vy *= f
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
    }

    private fun updateRings(dt: Float) {
        for (r in rings) {
            if (!r.alive) continue
            r.life -= dt
            if (r.life <= 0f) r.alive = false
        }
    }

    private fun updateTexts(dt: Float) {
        for (i in texts.indices.reversed()) {
            val t = texts[i]
            t.life -= dt
            t.y -= 70f * dt
            if (t.life <= 0f) texts.removeAt(i)
        }
    }

    // ================================================================== render

    fun render(c: Canvas) {
        c.drawColor(Color.BLACK)
        if (screenW == 0) return
        c.drawPaint(bgPaint)
        c.drawPaint(nebulaPaint1)
        c.drawPaint(nebulaPaint2)

        c.save()
        c.scale(scale, scale)
        val s = trauma * trauma * 30f
        if (s > 0.1f && state != State.PAUSED) c.translate(rf(-s, s), rf(-s, s))
        drawGrid(c)
        drawStars(c)
        if (state != State.MENU) {
            drawPlayerBullets(c)
            drawEnemies(c)
            drawGems(c)
            drawPowerUps(c)
        }
        drawParticles(c)
        drawRings(c)
        if (state == State.MENU) {
            drawShip(c, W / 2 + sin(clock * 1.1f) * 140f, H * 0.56f, cos(clock * 1.1f) * 0.6f, false)
        } else if (playerAlive) {
            drawPlayer(c)
        }
        drawEnemyBullets(c)
        drawTexts(c)
        c.restore()

        c.drawPaint(vignettePaint)
        if (overdrive > 0f) {
            val a = (22 + 14 * sin(clock * 10f)).toInt()
            c.drawColor(Color.argb(a, 255, 229, 59))
        }
        if (flashT > 0f) {
            val a = (min(1f, flashT / 0.35f) * 140).toInt()
            c.drawColor(Color.argb(a, Color.red(flashColor), Color.green(flashColor), Color.blue(flashColor)))
        }

        c.save()
        c.scale(scale, scale)
        when (state) {
            State.MENU -> drawMenu(c)
            State.PLAYING, State.DYING -> { drawHud(c); drawBanner(c) }
            State.PAUSED -> { drawHud(c); drawPause(c) }
            State.GAME_OVER -> drawGameOver(c)
        }
        c.restore()
    }

    private fun glow(c: Canvas, bmp: android.graphics.Bitmap, x: Float, y: Float, rx: Float, ry: Float, alpha: Int) {
        if (alpha <= 0) return
        dst.set(x - rx, y - ry, x + rx, y + ry)
        glowPaint.alpha = alpha.coerceAtMost(255)
        c.drawBitmap(bmp, null, dst, glowPaint)
    }

    private fun drawGrid(c: Canvas) {
        val hy = H * 0.78f
        val vpx = W / 2
        strokePaint.strokeWidth = 2f
        for (i in 0 until 10) {
            val z = ((i + gridScroll) % 10f) / 10f
            val y = hy + (H - hy) * z * z
            strokePaint.color = Color.argb((10 + 60 * z).toInt(), 255, 43, 214)
            c.drawLine(0f, y, W, y, strokePaint)
        }
        strokePaint.color = Color.argb(40, 255, 43, 214)
        for (k in -8..8) {
            c.drawLine(vpx + k * 22f, hy, vpx + k * 190f, H, strokePaint)
        }
    }

    private fun drawStars(c: Canvas) {
        val streak = starSpeed > 1.8f
        for (i in 0 until starN) {
            val z = starZ[i]
            starPaint.alpha = (70 + z * 185).toInt()
            val sz = 1.5f + z * 3f
            if (streak) {
                val len = (40f + z * 320f) * starSpeed * 0.06f
                c.drawRect(starX[i] - sz / 2, starY[i] - len, starX[i] + sz / 2, starY[i], starPaint)
            } else {
                c.drawRect(starX[i] - sz / 2, starY[i] - sz / 2, starX[i] + sz / 2, starY[i] + sz / 2, starPaint)
            }
        }
    }

    private fun drawPlayerBullets(c: Canvas) {
        linePaint.color = WHITE
        linePaint.strokeWidth = 5f
        for (b in pBullets) {
            if (!b.alive) continue
            glow(c, Glow.hot(b.color), b.x, b.y, 15f, 36f, 255)
            c.drawLine(b.x, b.y - 14f, b.x, b.y + 12f, linePaint)
        }
    }

    private fun drawEnemyBullets(c: Canvas) {
        fillPaint.color = WHITE
        for (b in eBullets) {
            if (!b.alive) continue
            glow(c, Glow.hot(b.color), b.x, b.y, b.r * 2.6f, b.r * 2.6f, 255)
            c.drawCircle(b.x, b.y, b.r * 0.55f, fillPaint)
        }
    }

    private fun pathFor(type: Int) = when (type) {
        DRIFTER -> pathDrifter
        ZIGZAG -> pathZig
        CHASER -> pathChaser
        TANK -> pathTank
        SPLITTER -> pathSplit
        MINI -> pathMini
        else -> pathBoss
    }

    private fun drawEnemies(c: Canvas) {
        for (i in enemies.indices) {
            val e = enemies[i]
            if (!e.alive) continue
            val col = if (e.flash > 0f) WHITE else e.color
            glow(c, Glow.soft(e.color), e.x, e.y, e.r * 2.6f, e.r * 2.6f, 255)
            c.save()
            c.translate(e.x, e.y)
            c.rotate(e.rot)
            fillPaint.color = (e.color and 0x00FFFFFF) or 0x40000000
            val path = pathFor(e.type)
            c.drawPath(path, fillPaint)
            strokePaint.color = col
            strokePaint.strokeWidth = if (e.type == BOSS) 7f else 5f
            c.drawPath(path, strokePaint)
            when (e.type) {
                DRIFTER -> c.drawPath(pathDrifterIn, strokePaint)
                TANK -> c.drawPath(pathTankIn, strokePaint)
                BOSS -> {
                    c.rotate(-e.rot * 2f)
                    c.drawPath(pathBossIn, strokePaint)
                }
            }
            c.restore()
            val core = if (e.type == BOSS) 50f + sin(clock * 8f) * 10f else e.r * 0.5f
            glow(c, Glow.hot(e.color), e.x, e.y, core, core, if (e.type == BOSS) 255 else 160)
            if ((e.type == TANK || e.type == SPLITTER) && e.hp < e.maxHp) {
                val w = e.r * 1.6f
                val frac = (e.hp / e.maxHp).coerceIn(0f, 1f)
                fillPaint.color = 0x55FFFFFF
                c.drawRect(e.x - w / 2, e.y + e.r + 14f, e.x + w / 2, e.y + e.r + 20f, fillPaint)
                fillPaint.color = e.color
                c.drawRect(e.x - w / 2, e.y + e.r + 14f, e.x - w / 2 + w * frac, e.y + e.r + 20f, fillPaint)
            }
        }
    }

    private fun drawGems(c: Canvas) {
        fillPaint.color = WHITE
        for (g in gems) {
            if (!g.alive) continue
            val fade = if (g.t > 10f) ((12f - g.t) / 2f).coerceIn(0f, 1f) else 1f
            glow(c, Glow.hot(MINT), g.x, g.y, 36f, 36f, (255 * fade).toInt())
            c.save()
            c.translate(g.x, g.y)
            c.rotate(g.t * 240f)
            fillPaint.alpha = (255 * fade).toInt()
            c.drawPath(pathGem, fillPaint)
            c.restore()
        }
        fillPaint.alpha = 255
    }

    private fun powerColor(type: Int) = when (type) {
        P_WEAPON -> CYAN
        P_RAPID -> YELLOW
        P_SHIELD -> GREEN
        P_MAGNET -> PURPLE
        P_BOMB -> RED
        else -> MAGENTA
    }

    private fun drawPowerUps(c: Canvas) {
        for (i in powerUps.indices) {
            val p = powerUps[i]
            val col = powerColor(p.type)
            val pulse = 1f + sin(p.t * 6f) * 0.08f
            glow(c, Glow.soft(col), p.x, p.y, 90f * pulse, 90f * pulse, 255)
            strokePaint.color = col
            strokePaint.strokeWidth = 5f
            c.drawCircle(p.x, p.y, 36f * pulse, strokePaint)
            strokePaint.color = WHITE
            strokePaint.strokeWidth = 3f
            val a = p.t * 200f
            c.drawArc(p.x - 46f, p.y - 46f, p.x + 46f, p.y + 46f, a, 70f, false, strokePaint)
            c.drawArc(p.x - 46f, p.y - 46f, p.x + 46f, p.y + 46f, a + 180f, 70f, false, strokePaint)
            if (p.type == P_LIFE) {
                c.save()
                c.translate(p.x, p.y + 2f)
                fillPaint.color = MAGENTA
                c.drawPath(pathHeart, fillPaint)
                c.restore()
            } else {
                val letter = when (p.type) {
                    P_WEAPON -> "P"
                    P_RAPID -> "R"
                    P_SHIELD -> "S"
                    P_MAGNET -> "M"
                    else -> "B"
                }
                text(c, letter, p.x, p.y + 15f, 42f, WHITE, col, monoTf)
            }
        }
    }

    private fun drawParticles(c: Canvas) {
        for (p in particles) {
            if (!p.alive) continue
            val f = p.life / p.maxLife
            val r = p.size * (0.3f + 0.7f * f)
            glow(c, Glow.hot(p.color), p.x, p.y, r, r, (255 * f).toInt())
        }
    }

    private fun drawRings(c: Canvas) {
        for (r in rings) {
            if (!r.alive) continue
            val f = 1f - r.life / r.maxLife
            val ease = 1f - (1f - f) * (1f - f) * (1f - f)
            strokePaint.color = r.color
            strokePaint.alpha = (255 * (1f - f)).toInt()
            strokePaint.strokeWidth = r.width * (1f - f * 0.7f)
            c.drawCircle(r.x, r.y, r.maxR * ease, strokePaint)
        }
        strokePaint.alpha = 255
    }

    private fun drawShip(c: Canvas, x: Float, y: Float, bankAmt: Float, od: Boolean) {
        val col = if (od) YELLOW else CYAN
        val flick = rf(0.85f, 1.15f)
        glow(c, Glow.hot(if (od) YELLOW else MAGENTA), x, y + 40f, 22f * flick, 40f * flick, 230)
        glow(c, Glow.soft(col), x, y, 110f, 110f, 255)
        c.save()
        c.translate(x, y)
        c.rotate(bankAmt * 12f)
        c.scale(1f - abs(bankAmt) * 0.3f, 1f)
        fillPaint.color = (col and 0x00FFFFFF) or 0x40000000
        c.drawPath(pathShip, fillPaint)
        strokePaint.color = col
        strokePaint.strokeWidth = 5f
        c.drawPath(pathShip, strokePaint)
        c.restore()
        glow(c, Glow.hot(WHITE), x, y - 8f, 16f, 16f, 255)
    }

    private fun drawPlayer(c: Canvas) {
        val blink = invuln > 0f && overdrive <= 0f && ((clock * 18f).toInt() % 2 == 0)
        if (!blink) drawShip(c, px, py, bank, overdrive > 0f)
        if (shield > 0f) {
            val show = shield > 3f || ((clock * 8f).toInt() % 2 == 0)
            if (show) {
                strokePaint.color = GREEN
                strokePaint.strokeWidth = 4f
                c.drawCircle(px, py, 66f + sin(clock * 6f) * 3f, strokePaint)
                glow(c, Glow.soft(GREEN), px, py, 130f, 130f, 200)
            }
        }
        if (overdrive > 0f) {
            strokePaint.color = YELLOW
            strokePaint.strokeWidth = 3f
            val rr = 80f + (clock * 300f) % 60f
            strokePaint.alpha = (255 * (1f - ((rr - 80f) / 60f))).toInt()
            c.drawCircle(px, py, rr, strokePaint)
            strokePaint.alpha = 255
        }
        if (magnet > 0f) {
            strokePaint.color = PURPLE
            strokePaint.strokeWidth = 2f
            strokePaint.alpha = 90
            c.drawCircle(px, py, 120f + (clock * 120f) % 80f, strokePaint)
            strokePaint.alpha = 255
        }
    }

    private fun text(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, glowColor: Int,
        tf: Typeface = boldTf, align: Paint.Align = Paint.Align.CENTER, alpha: Int = 255,
    ) {
        textPaint.textSize = size
        textPaint.typeface = tf
        textPaint.textAlign = align
        textPaint.color = color
        textPaint.alpha = alpha
        textPaint.setShadowLayer(size * 0.3f, 0f, 0f, (glowColor and 0x00FFFFFF) or ((alpha * 0.9f).toInt() shl 24))
        c.drawText(s, x, y, textPaint)
    }

    private fun drawTexts(c: Canvas) {
        for (i in texts.indices) {
            val t = texts[i]
            val f = t.life / t.maxLife
            val pop = if (f > 0.85f) 1f + (f - 0.85f) * 2f else 1f
            text(c, t.text, t.x, t.y, t.size * pop, WHITE, t.color, alpha = (255 * min(1f, f * 2f)).toInt())
        }
    }

    private fun drawHud(c: Canvas) {
        val top = safeTop + 30f
        text(c, displayScore.toLong().toString(), 40f, top + 62f, 66f, WHITE, CYAN, monoTf, Paint.Align.LEFT)
        text(c, "שיא ${max(best, score)}", 40f, top + 108f, 30f, 0xFFB8B8D8.toInt(), PURPLE, align = Paint.Align.LEFT)
        text(c, "גל $wave", W / 2, top + 50f, 38f, WHITE, MAGENTA)

        // pause button
        fillPaint.color = 0x22FFFFFF
        c.drawRoundRect(btnPause, 20f, 20f, fillPaint)
        fillPaint.color = WHITE
        val cx = btnPause.centerX()
        val cy = btnPause.centerY()
        c.drawRoundRect(cx - 18f, cy - 22f, cx - 6f, cy + 22f, 4f, 4f, fillPaint)
        c.drawRoundRect(cx + 6f, cy - 22f, cx + 18f, cy + 22f, 4f, 4f, fillPaint)

        // lives
        for (i in 0 until lives) {
            val hx = btnPause.left - 50f - i * 56f
            val hy = cy + 2f
            glow(c, Glow.soft(MAGENTA), hx, hy, 46f, 46f, 255)
            c.save()
            c.translate(hx, hy)
            fillPaint.color = MAGENTA
            c.drawPath(pathHeart, fillPaint)
            c.restore()
        }

        // combo
        if (combo >= 3) {
            val m = multiplier()
            val sz = 58f * (1f + comboPop * 0.35f)
            text(c, "x$m", 40f, top + 185f, sz, YELLOW, ORANGE, monoTf, Paint.Align.LEFT)
            text(c, "רצף $combo", 40f, top + 228f, 28f, WHITE, ORANGE, align = Paint.Align.LEFT)
            fillPaint.color = 0x33FFFFFF
            c.drawRect(40f, top + 242f, 240f, top + 248f, fillPaint)
            fillPaint.color = YELLOW
            c.drawRect(40f, top + 242f, 40f + 200f * (comboTimer / 2f).coerceIn(0f, 1f), top + 248f, fillPaint)
        }

        // boss health
        val b = boss
        if (b != null && b.alive) {
            val bw = 760f
            val bx = W / 2 - bw / 2
            val by = top + 130f
            text(c, "בוס", W / 2, by - 12f, 30f, WHITE, RED)
            fillPaint.color = 0x44FFFFFF
            c.drawRoundRect(bx, by, bx + bw, by + 16f, 8f, 8f, fillPaint)
            fillPaint.color = RED
            c.drawRoundRect(bx, by, bx + bw * (b.hp / b.maxHp).coerceIn(0f, 1f), by + 16f, 8f, 8f, fillPaint)
        }

        // overdrive meter
        val mw = 560f
        val mx = W / 2 - mw / 2
        val my = H - 70f
        fillPaint.color = 0x30FFFFFF
        c.drawRoundRect(mx, my, mx + mw, my + 16f, 8f, 8f, fillPaint)
        val frac = if (overdrive > 0f) overdrive / OVERDRIVE_TIME else meter
        if (frac > 0f) {
            c.drawRoundRect(mx, my, mx + mw * frac.coerceIn(0f, 1f), my + 16f, 8f, 8f, meterPaint)
            glow(c, Glow.hot(YELLOW), mx + mw * frac, my + 8f, 24f, 24f, 220)
        }
        val ready = overdrive > 0f || meter > 0.85f
        val la = if (ready) (180 + 75 * sin(clock * 12f)).toInt() else 170
        text(c, "OVERDRIVE", W / 2, my - 14f, 26f, WHITE, YELLOW, monoTf, alpha = la)

        // weapon level pips
        for (i in 0 until MAX_WEAPON) {
            val x = 50f + i * 30f
            if (i < weapon) {
                fillPaint.color = CYAN
                c.drawCircle(x, my + 8f, 9f, fillPaint)
            } else {
                strokePaint.color = 0x66FFFFFF
                strokePaint.strokeWidth = 2f
                c.drawCircle(x, my + 8f, 8f, strokePaint)
            }
        }

        // active power-up timers
        var ix = W - 60f
        ix = timer(c, ix, my + 8f, shield, 15f, GREEN, "S")
        ix = timer(c, ix, my + 8f, rapid, 8f, YELLOW, "R")
        timer(c, ix, my + 8f, magnet, 10f, PURPLE, "M")
    }

    private fun timer(c: Canvas, x: Float, y: Float, v: Float, maxV: Float, col: Int, label: String): Float {
        if (v <= 0f) return x
        strokePaint.color = 0x44FFFFFF
        strokePaint.strokeWidth = 5f
        c.drawCircle(x, y, 24f, strokePaint)
        strokePaint.color = col
        c.drawArc(x - 24f, y - 24f, x + 24f, y + 24f, -90f, 360f * (v / maxV).coerceIn(0f, 1f), false, strokePaint)
        text(c, label, x, y + 10f, 26f, WHITE, col, monoTf)
        return x - 64f
    }

    private fun drawBanner(c: Canvas) {
        if (bannerT <= 0f) return
        val total = 2.3f
        val e = total - bannerT
        val a = when {
            e < 0.2f -> e / 0.2f
            bannerT < 0.4f -> bannerT / 0.4f
            else -> 1f
        }
        val pop = if (e < 0.2f) 1.4f - e * 2f else 1f
        val alpha = (255 * a).toInt()
        text(c, bannerText, W / 2, H * 0.38f, 96f * pop, WHITE, bannerColor, alpha = alpha)
        if (bannerSub.isNotEmpty()) text(c, bannerSub, W / 2, H * 0.38f + 70f, 40f, bannerColor, bannerColor, alpha = alpha)
    }

    private fun button(c: Canvas, r: RectF, label: String, col: Int, active: Boolean = true) {
        fillPaint.color = (col and 0x00FFFFFF) or (if (active) 0x30000000 else 0x10000000)
        c.drawRoundRect(r, 26f, 26f, fillPaint)
        strokePaint.color = if (active) col else 0x66FFFFFF
        strokePaint.strokeWidth = 4f
        c.drawRoundRect(r, 26f, 26f, strokePaint)
        text(c, label, r.centerX(), r.centerY() + 14f, 38f, if (active) WHITE else 0xFFAAAAAA.toInt(), col)
    }

    private fun drawToggles(c: Canvas) {
        button(c, btnSound, if (prefs.sound) "צליל: פועל" else "צליל: כבוי", CYAN, prefs.sound)
        button(c, btnVibe, if (prefs.vibration) "רטט: פועל" else "רטט: כבוי", MAGENTA, prefs.vibration)
    }

    private fun drawMenu(c: Canvas) {
        val cy = H * 0.24f
        val pulse = 1f + sin(clock * 2.5f) * 0.03f
        text(c, "NEON", W / 2, cy, 170f * pulse, WHITE, MAGENTA, monoTf)
        text(c, "SWARM", W / 2, cy + 160f, 150f * pulse, WHITE, CYAN, monoTf)
        text(c, "נחיל הנאון", W / 2, cy + 240f, 50f, 0xFFDDDDFF.toInt(), PURPLE)

        val blink = (180 + 75 * sin(clock * 4f)).toInt()
        text(c, "גע כדי להתחיל", W / 2, H * 0.69f, 64f, WHITE, CYAN, alpha = blink)
        if (prefs.best > 0) {
            text(c, "שיא: ${prefs.best}   ·   גל מקסימלי: ${prefs.bestWave}", W / 2, H * 0.745f, 34f, YELLOW, ORANGE)
        }
        text(c, "גרור כדי לזוז · הירי אוטומטי", W / 2, H * 0.79f, 32f, 0xFFB8B8D8.toInt(), PURPLE)
        text(c, "אסוף קריסטלים כדי להפעיל OVERDRIVE", W / 2, H * 0.82f, 32f, 0xFFB8B8D8.toInt(), PURPLE)
        drawToggles(c)
    }

    private fun drawPause(c: Canvas) {
        fillPaint.color = 0xAA000000.toInt()
        c.drawRect(0f, 0f, W, H, fillPaint)
        text(c, "השהיה", W / 2, H * 0.36f, 110f, WHITE, CYAN)
        text(c, "גע כדי להמשיך", W / 2, H * 0.44f, 50f, WHITE, MAGENTA, alpha = (180 + 75 * sin(clock * 4f)).toInt())
        button(c, btnMenu, "לתפריט", PURPLE)
        drawToggles(c)
    }

    private fun drawGameOver(c: Canvas) {
        fillPaint.color = 0x99000000.toInt()
        c.drawRect(0f, 0f, W, H, fillPaint)
        val ease = min(1f, gameOverT / 0.5f)
        text(c, "המשחק נגמר", W / 2, H * 0.22f, 100f * (0.8f + 0.2f * ease), WHITE, MAGENTA, alpha = (255 * ease).toInt())
        text(c, score.toString(), W / 2, H * 0.33f, 130f, WHITE, CYAN, monoTf)
        if (newRecord) {
            val p = 1f + sin(clock * 6f) * 0.06f
            text(c, "שיא חדש!", W / 2, H * 0.39f, 64f * p, YELLOW, ORANGE)
        } else {
            text(c, "שיא: $best", W / 2, H * 0.39f, 44f, 0xFFB8B8D8.toInt(), PURPLE)
        }
        text(c, "הגעת לגל $wave", W / 2, H * 0.46f, 42f, WHITE, MAGENTA)
        text(c, "רצף מקסימלי: $maxCombo   ·   הושמדו: $kills", W / 2, H * 0.505f, 36f, 0xFFDDDDFF.toInt(), PURPLE)
        if (gameOverT > 1f) {
            text(c, "גע כדי לשחק שוב", W / 2, H * 0.61f, 60f, WHITE, CYAN, alpha = (180 + 75 * sin(clock * 4f)).toInt())
            button(c, btnMenu, "לתפריט", PURPLE)
        }
    }
}
