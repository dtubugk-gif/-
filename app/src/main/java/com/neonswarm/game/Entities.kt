package com.neonswarm.game

interface Poolable {
    var alive: Boolean
}

class Bullet : Poolable {
    override var alive = false
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var r = 10f
    var dmg = 1f
    var color = 0
}

class Particle : Poolable {
    override var alive = false
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var life = 0f
    var maxLife = 1f
    var size = 10f
    var color = 0
    var drag = 3f
}

class Ring : Poolable {
    override var alive = false
    var x = 0f
    var y = 0f
    var maxR = 100f
    var life = 0f
    var maxLife = 1f
    var color = 0
    var width = 8f
}

class Gem : Poolable {
    override var alive = false
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var t = 0f
}

class Enemy(val type: Int) {
    var alive = true
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var hp = 1f
    var maxHp = 1f
    var r = 30f
    var t = 0f
    var color = 0
    var flash = 0f
    var value = 100
    var fireCd = 0f
    var baseX = 0f
    var param = 0f
    var rot = 0f
    var phase = 0
    var phaseT = 0f
    var spiral = 0f
    var entered = false
}

class PowerUp(val type: Int, var x: Float, var y: Float) {
    var t = 0f
    var alive = true
}

class FloatText(
    val text: String,
    var x: Float,
    var y: Float,
    val color: Int,
    val size: Float,
    var life: Float,
) {
    val maxLife = life
}

class Spawn(val time: Float, val type: Int, val x: Float, val param: Float = 0f)

class TouchEv(val type: Int, val x: Float, val y: Float)
