package com.retro.microbes

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private const val PIF = 3.14159265f

// specie
private const val MICROBE = 0
private const val ALGA = 1
private const val DIATOM = 2
private const val ROTIFER = 3
private const val SPIRILLUM = 4
private const val VIRUS = 5
private const val MACRO = 6
private const val TARDI = 7
private const val AMOEBA = 8
private const val PARAM = 9
private const val EUGLENA = 10
private const val YEAST = 11
private const val PLANKTON = 12
private const val GHOST = 13
private const val FOOD = 14
private const val DUST = 15
private const val NSP = 13
private const val DIV_TIME = 1.6f

//                                  micr    alga    diat    roti    spir    virus   macro   tardi   amoe    para    eugl    yeast   plank
private val SIZE   = floatArrayOf(30f,   20f,    38f,    62f,    30f,    18f,    80f,    56f,    90f,    64f,    52f,    26f,    28f)
private val WANDER = floatArrayOf(0.015f, 0.004f, 0.012f, 0.012f, 0.020f, 0.015f, 0.010f, 0.006f, 0.006f, 0.020f, 0.014f, 0.003f, 0.008f)
private val CHASE  = floatArrayOf(0.030f, 0f,     0.028f, 0.050f, 0.040f, 0.035f, 0.045f, 0.015f, 0.028f, 0.040f, 0.020f, 0.006f, 0f)
private val SIGHT  = floatArrayOf(0.30f,  0f,     0.25f,  0.30f,  0.25f,  0.30f,  0.30f,  0.20f,  0.25f,  0.28f,  0.22f,  0.12f,  0f)
private val DECAY  = floatArrayOf(0.012f, 0.004f, 0.015f, 0.020f, 0.020f, 0.060f, 0.008f, 0.010f, 0.010f, 0.014f, 0.008f, 0.007f, 0.004f)
private val GAIN   = floatArrayOf(0.12f,  0f,     0.35f,  0.40f,  0.50f,  0f,     0.30f,  0.30f,  0.35f,  0.22f,  0.12f,  0.15f,  0f)
private val SPLIT  = floatArrayOf(1.0f,   1.0f,   1.0f,   1.1f,   1.0f,   99f,    1.2f,   1.1f,   1.1f,   1.0f,   1.0f,   1.0f,   1.0f)
private val MAXP   = intArrayOf(26, 30, 12, 5, 10, 24, 3, 3, 4, 8, 14, 20, 26)
private val MINP   = intArrayOf(8, 10, 3, 1, 3, 0, 1, 1, 1, 2, 4, 5, 6)
private val THREAT = intArrayOf(ROTIFER, -1, ROTIFER, -1, -1, MACRO, -1, -1, MACRO, ROTIFER, ROTIFER, -1, ROTIFER)
// riproduzione sessuata con meiosi (il paramecio fa coniugazione)
private val SEXUAL = booleanArrayOf(false, false, true, true, false, false, true, true, false, true, false, false, false)

private val GOOGLE = arrayOf(
    floatArrayOf(0.92f, 0.26f, 0.21f), floatArrayOf(0.98f, 0.74f, 0.02f),
    floatArrayOf(0.20f, 0.66f, 0.33f), floatArrayOf(0.26f, 0.52f, 0.96f)
)
private val WHITE = floatArrayOf(1f, 1f, 1f)

private fun colorFor(kind: Int): FloatArray = when (kind) {
    MICROBE -> GOOGLE[Random.nextInt(GOOGLE.size)]
    ALGA -> floatArrayOf(0.25f, 0.85f, 0.45f)
    DIATOM -> floatArrayOf(0.90f, 0.68f, 0.25f)
    ROTIFER -> floatArrayOf(0.75f, 0.45f, 0.95f)
    SPIRILLUM -> floatArrayOf(0.45f, 0.90f, 0.95f)
    VIRUS -> floatArrayOf(1.0f, 0.30f, 0.70f)
    MACRO -> floatArrayOf(0.55f, 0.75f, 1.0f)
    AMOEBA -> floatArrayOf(0.88f, 0.62f, 0.78f)
    PARAM -> floatArrayOf(0.80f, 0.86f, 0.95f)
    EUGLENA -> floatArrayOf(0.60f, 0.90f, 0.30f)
    YEAST -> floatArrayOf(0.95f, 0.88f, 0.60f)
    PLANKTON -> floatArrayOf(0.30f, 0.60f, 1.0f)
    else -> floatArrayOf(0.80f, 0.65f, 0.40f)
}

class MicrobesRenderer(density: Float) : GLSurfaceView.Renderer {
    @Volatile var xOffset = 0.5f
    val touches = ConcurrentLinkedQueue<FloatArray>()

    private val px = density / 2f

    private class Org(val kind: Int, var x: Float, var y: Float) {
        var heading = Random.nextFloat() * 2f * PIF
        var speed = 0f
        var turn = 0f
        var energy = 0.6f
        var flash = 0f
        var size = 30f
        var color = WHITE
        var dead = false
        var timer = 0f
        var state = 0 // 0 normale, 1 infetto (microbo) / simbionte (paramecio), 2 tun (tardigrado), 3 spora (microbo)
        var spd = 1f      // tratti ereditari
        var szm = 1f
        var mate: Org? = null
        var meiT = 0f
        var leader = false
        var cool = 0f
        var divT = 0f     // >0 durante mitosi / gemmazione
        var mig = 0f      // >0 durante la migrazione
    }
    private class Ghost(val x: Float, val y: Float, val angle: Float, val size: Float) { var t = 0f }
    private class Food(var x: Float, var y: Float, val phase: Float, var vx: Float = 0f, var vy: Float = 0f)
    private class Dust(val bx: Float, val by: Float, val size: Float, val par: Float,
                       val color: FloatArray, val alpha: Float, val ph: Float)

    private val orgs = ArrayList<Org>()
    private val born = ArrayList<Org>()
    private val ghosts = ArrayList<Ghost>()
    private val foods = ArrayList<Food>()
    private val dusts = ArrayList<Dust>()
    private val counts = IntArray(NSP)

    private var seeded = false
    private var w = 1f
    private var h = 1f
    private var time = 0f
    private var light = 0.5f
    private var lastNs = 0L
    private var foodTimer = 0f
    private var immTimer = 0f
    private var outbreakTimer = 0f
    private var migTimer = 0f
    private var moveCd = 0f
    private var maxPoint = 64f
    private var tx = 0f; private var ty = 0f; private var tz = 0f; private var tw = 0f

    private val buf: FloatBuffer = ByteBuffer.allocateDirect(8192 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val bgBuf: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        .apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }
    private var n = 0
    private var pSprite = 0
    private var pBg = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        pSprite = link(SPRITE_V, SPRITE_F)
        pBg = link(BG_V, BG_F)
        val range = FloatArray(2)
        GLES20.glGetFloatv(GLES20.GL_ALIASED_POINT_SIZE_RANGE, range, 0)
        maxPoint = if (range[1] > 8f) range[1] else 64f
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ZERO, GLES20.GL_ONE)
        lastNs = 0L
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        if (seeded && w == width.toFloat() && h == height.toFloat()) return
        seeded = true
        w = width.toFloat(); h = height.toFloat()
        orgs.clear(); born.clear(); ghosts.clear(); foods.clear(); dusts.clear()
        val worldW = w * 1.6f
        fun seed(kind: Int, c: Int) = repeat(c) { orgs.add(newOrg(kind, Random.nextFloat() * worldW, Random.nextFloat() * h)) }
        seed(ALGA, 14); seed(MICROBE, 14); seed(DIATOM, 5); seed(SPIRILLUM, 4)
        seed(ROTIFER, 2); seed(MACRO, 1); seed(TARDI, 2)
        seed(AMOEBA, 1); seed(PARAM, 3); seed(EUGLENA, 6); seed(YEAST, 8); seed(PLANKTON, 10)
        repeat(60) { foods.add(Food(Random.nextFloat() * worldW, Random.nextFloat() * h, Random.nextFloat())) }
        val tints = arrayOf(
            floatArrayOf(0.30f, 0.45f, 0.95f), floatArrayOf(0.20f, 0.75f, 0.85f),
            floatArrayOf(0.55f, 0.30f, 0.85f), floatArrayOf(0.85f, 0.55f, 0.20f)
        )
        repeat(22) {
            val d = Random.nextFloat()
            dusts.add(Dust(Random.nextFloat() * w, Random.nextFloat() * h, 120f + 140f * d, 0.1f + 0.5f * d,
                tints[Random.nextInt(tints.size)], 0.05f + 0.05f * Random.nextFloat(), Random.nextFloat() * 6.28f))
        }
    }

    // x casuale nella parte di mondo non visibile (a sinistra o a destra dello schermo)
    private fun offscreenX(): Float {
        val worldW = w * 1.6f
        val scrollPx = xOffset * (worldW - w)
        val m = 0.08f * w
        val leftLen = (scrollPx - m).coerceAtLeast(0f)
        val rightStart = scrollPx + w + m
        val rightLen = (worldW - rightStart).coerceAtLeast(0f)
        if (leftLen + rightLen <= 0f) return if (scrollPx > worldW / 2f) 0f else worldW
        val r = Random.nextFloat() * (leftLen + rightLen)
        return if (r < leftLen) r else rightStart + (r - leftLen)
    }

    private fun newOrg(kind: Int, x: Float, y: Float): Org {
        val o = Org(kind, x, y)
        o.spd = 0.85f + 0.35f * Random.nextFloat()
        o.szm = 0.9f + 0.3f * Random.nextFloat()
        o.size = SIZE[kind] * o.szm
        o.color = colorFor(kind)
        if (kind == PARAM && Random.nextFloat() < 0.3f) o.state = 1 // simbionte (alghe verdi)
        return o
    }

    // mutazione: piccole variazioni, ogni tanto un salto grande
    private fun mutate(v: Float, lo: Float, hi: Float): Float {
        val big = Random.nextFloat() < 0.1f
        val d = (Random.nextFloat() - 0.5f) * (if (big) 0.4f else 0.12f)
        return (v + d).coerceIn(lo, hi)
    }

    private fun mixColor(a: FloatArray, b: FloatArray) =
        floatArrayOf((a[0] + b[0]) / 2f, (a[1] + b[1]) / 2f, (a[2] + b[2]) / 2f)

    private fun drift(c: FloatArray) = floatArrayOf(
        (c[0] + (Random.nextFloat() - 0.5f) * 0.08f).coerceIn(0.15f, 1f),
        (c[1] + (Random.nextFloat() - 0.5f) * 0.08f).coerceIn(0.15f, 1f),
        (c[2] + (Random.nextFloat() - 0.5f) * 0.08f).coerceIn(0.15f, 1f)
    )

    // p1 (e p2 se sessuata) trasmettono i tratti; p2 = ricombinazione (meiosi)
    private fun addBorn(kind: Int, x: Float, y: Float, energy: Float, p1: Org? = null, p2: Org? = null) {
        if (counts[kind] >= MAXP[kind]) return
        counts[kind]++
        val o = newOrg(kind, x, y)
        o.energy = energy
        if (p1 != null) {
            val q = p2 ?: p1
            o.spd = mutate(if (Random.nextBoolean()) p1.spd else q.spd, 0.7f, 1.45f)
            o.szm = mutate(if (Random.nextBoolean()) p1.szm else q.szm, 0.8f, 1.3f)
            o.size = SIZE[kind] * o.szm
            o.color = drift(if (p2 != null) mixColor(p1.color, q.color) else p1.color)
            if (kind == PARAM) o.state = if (p1.state == 1 || q.state == 1) 1 else 0
        }
        born.add(o)
    }

    private fun startDivision(o: Org) {
        if (counts[o.kind] >= MAXP[o.kind]) return
        o.divT = 0.01f
    }

    private fun finishDivision(o: Org) {
        o.divT = 0f
        o.energy *= 0.5f
        val a = o.heading
        if (o.kind == YEAST) {
            val d = o.size * px * 0.55f
            addBorn(YEAST, o.x + cos(a) * d, o.y + sin(a) * d, o.energy, o)
        } else {
            val off = 0.45f * o.size * px
            addBorn(o.kind, o.x + cos(a) * off, o.y + sin(a) * off, o.energy, o)
            o.x -= cos(a) * off; o.y -= sin(a) * off
        }
    }

    // banco di plancton che attraversa lo schermo
    private fun migrate() {
        if (counts[PLANKTON] + 8 > MAXP[PLANKTON]) return
        val worldW = w * 1.6f
        val scrollPx = xOffset * (worldW - w)
        val x0 = offscreenX()
        val heading = if (x0 < scrollPx + w / 2f) 0f else PIF
        val y0 = Random.nextFloat() * h
        repeat(8) {
            val x = (x0 + (Random.nextFloat() - 0.5f) * 0.1f * w).coerceIn(0f, worldW)
            val y = (y0 + (Random.nextFloat() - 0.5f) * 0.3f * h).coerceIn(0f, h)
            val o = newOrg(PLANKTON, x, y)
            o.heading = heading; o.mig = 35f; o.energy = 0.8f
            orgs.add(o)
        }
    }

    private fun burst(cx: Float, cy: Float, cnt: Int) {
        val spread = 0.1f * w
        repeat(cnt) {
            val a = Random.nextFloat() * 2f * PIF
            val sp = Random.nextFloat() * spread
            foods.add(Food(cx, cy, Random.nextFloat(), cos(a) * sp, sin(a) * sp))
        }
    }

    private fun angDiff(a: Float, b: Float): Float {
        var d = a - b
        while (d > PIF) d -= 2f * PIF
        while (d < -PIF) d += 2f * PIF
        return d
    }

    private fun isPrey(h: Int, t: Org): Boolean {
        if (t.dead) return false
        return when (h) {
            DIATOM, TARDI -> t.kind == ALGA
            ROTIFER -> t.kind == DIATOM || t.kind == EUGLENA || t.kind == PLANKTON || (t.kind == MICROBE && t.state != 3)
            MACRO -> t.kind == VIRUS || t.kind == AMOEBA || (t.kind == MICROBE && t.state == 1)
            VIRUS -> t.kind == MICROBE && t.state == 0
            AMOEBA -> t.kind == ALGA || t.kind == YEAST || (t.kind == MICROBE && t.state == 0)
            PARAM -> t.kind == MICROBE && t.state == 0
            else -> false
        }
    }

    private fun starved(o: Org) {
        when (o.kind) {
            VIRUS -> {}
            ALGA -> if (foods.size < 250) foods.add(Food(o.x, o.y, Random.nextFloat()))
            else -> {
                if (ghosts.size < 40) ghosts.add(Ghost(o.x, o.y, -o.heading, o.size))
                if (foods.size < 250) repeat(2) { foods.add(Food(o.x, o.y, Random.nextFloat(), (Random.nextFloat() - 0.5f) * 20f, (Random.nextFloat() - 0.5f) * 20f)) }
            }
        }
    }

    private fun stepOrg(o: Org, dt: Float, worldW: Float) {
        if (o.dead) return
        o.flash = (o.flash - dt * 2.5f).coerceAtLeast(0f)
        o.cool = (o.cool - dt).coerceAtLeast(0f)
        val k = o.kind

        if (k == TARDI) {
            if (o.state == 0 && o.energy < 0.2f) { o.state = 2; o.timer = 15f }
            if (o.state == 2) {
                o.timer -= dt
                if (o.timer <= 0f) { o.state = 0; o.energy = 0.35f }
                return
            }
        }
        // sporulazione del microbo: sopravvive alla fame come spora
        if (k == MICROBE) {
            if (o.state == 0 && o.energy < 0.14f && Random.nextFloat() < 0.5f * dt) { o.state = 3; o.timer = 25f }
            if (o.state == 3) {
                o.timer -= dt
                if (o.timer <= 0f) { o.state = 0; o.energy = 0.4f }
                return
            }
        }
        if (k == MICROBE && o.state == 1) {
            o.timer -= dt
            o.flash = 0.3f + 0.2f * sin(time * 14f)
            if (o.timer <= 0f) {
                o.dead = true
                if (ghosts.size < 40) ghosts.add(Ghost(o.x, o.y, -o.heading, o.size))
                repeat(3) { addBorn(VIRUS, o.x, o.y, 0.6f) }
                return
            }
        }

        when (k) {
            ALGA -> {
                o.energy += 0.05f * light * dt
                if (foods.size < 200 && Random.nextFloat() < 0.02f * dt * 60f / 60f) foods.add(Food(o.x, o.y, Random.nextFloat()))
            }
            EUGLENA -> o.energy += 0.035f * light * dt
            PLANKTON -> o.energy += 0.04f * light * dt
            PARAM -> if (o.state == 1) o.energy += 0.025f * light * dt // simbiosi con le alghe
        }
        o.energy -= DECAY[k] * (0.6f + 0.4f * o.spd) * (0.7f + 0.3f * o.szm) * dt
        if (o.energy <= 0f) { o.dead = true; starved(o); return }

        // mitosi / gemmazione in corso
        if (o.divT > 0f) {
            o.divT += dt
            o.speed *= (1f - 4f * dt).coerceAtLeast(0f)
            if (o.divT >= DIV_TIME) finishDivision(o)
            return
        }

        // meiosi (o coniugazione) in corso
        val m = o.mate
        if (m != null) {
            if (m.dead) { o.mate = null; o.leader = false }
            else {
                o.meiT -= dt
                o.speed *= (1f - 4f * dt).coerceAtLeast(0f)
                o.flash = 0.5f + 0.5f * sin(time * 10f)
                if (o.meiT <= 0f) {
                    if (k == PARAM) {
                        if (o.leader) { val sp = (o.spd + m.spd) / 2f; o.spd = sp; m.spd = sp }
                        o.energy = (o.energy + 0.1f).coerceAtMost(1.3f)
                    } else {
                        if (o.leader) addBorn(k, (o.x + m.x) / 2f, (o.y + m.y) / 2f, 0.45f, o, m)
                        o.energy *= 0.55f
                    }
                    o.cool = 8f; o.mate = null; o.leader = false
                }
                return
            }
        }

        // coniugazione batterica: trasferimento di un tratto
        if (k == MICROBE && o.state == 0 && Random.nextFloat() < 0.15f * dt) {
            for (q in orgs) {
                if (q === o || q.dead || q.kind != MICROBE || q.state != 0) continue
                if (hypot(q.x - o.x, q.y - o.y) < 0.07f * w) {
                    o.spd = q.spd; o.flash = 0.6f; q.flash = 0.6f
                    break
                }
            }
        }

        // ricerca del partner
        var seek = false
        var sx = 0f; var sy = 0f
        if (SEXUAL[k] && o.cool <= 0f && o.energy >= SPLIT[k] * 0.9f) {
            var bd = 0.35f * w
            var cand: Org? = null
            for (q in orgs) {
                if (q === o || q.dead || q.kind != k || q.mate != null || q.cool > 0f || q.divT > 0f ||
                    q.energy < SPLIT[k] * 0.9f) continue
                val d = hypot(q.x - o.x, q.y - o.y)
                if (d < bd) { bd = d; cand = q }
            }
            if (cand != null) {
                if (bd < (o.size + cand.size) * 0.22f * px) {
                    o.mate = cand; cand.mate = o
                    o.leader = true; cand.leader = false
                    o.meiT = 1.6f; cand.meiT = 1.6f
                    return
                }
                seek = true; sx = cand.x; sy = cand.y
            }
        }

        // bersaglio piu' vicino
        var best = Float.MAX_VALUE
        var tOrg: Org? = null
        var tFood: Food? = null
        var tGhost: Ghost? = null
        var tX = 0f; var tY = 0f
        val sight = SIGHT[k] * w
        if (CHASE[k] > 0f) {
            for (q in orgs) {
                if (q === o || !isPrey(k, q)) continue
                val d = hypot(q.x - o.x, q.y - o.y)
                if (d < best && d < sight) { best = d; tOrg = q; tX = q.x; tY = q.y }
            }
            if (k == MICROBE || k == EUGLENA || k == YEAST) for (f in foods) {
                val d = hypot(f.x - o.x, f.y - o.y)
                if (d < best && d < sight) { best = d; tFood = f; tX = f.x; tY = f.y }
            }
            if (k == SPIRILLUM || k == YEAST) for (g in ghosts) {
                val d = hypot(g.x - o.x, g.y - o.y)
                if (d < best && d < sight) { best = d; tGhost = g; tX = g.x; tY = g.y }
            }
        }
        val haveT = tOrg != null || tFood != null || tGhost != null

        // fuga dai predatori
        var fleeing = false
        var fa = 0f
        val th = THREAT[k]
        if (th >= 0) {
            var fd = 0.16f * w
            for (q in orgs) {
                if (q.kind != th || q.dead) continue
                val d = hypot(o.x - q.x, o.y - q.y)
                if (d < fd) { fd = d; fa = atan2(o.y - q.y, o.x - q.x); fleeing = true }
            }
        }

        val targetSpeed: Float
        if (fleeing) {
            o.heading += angDiff(fa, o.heading).coerceIn(-5f * dt, 5f * dt)
            targetSpeed = CHASE[k] * 1.3f * w
        } else if (seek) {
            o.heading += angDiff(atan2(sy - o.y, sx - o.x), o.heading).coerceIn(-3f * dt, 3f * dt)
            targetSpeed = CHASE[k] * w
        } else if (haveT) {
            o.heading += angDiff(atan2(tY - o.y, tX - o.x), o.heading).coerceIn(-3f * dt, 3f * dt)
            targetSpeed = CHASE[k] * w
        } else if (o.mig > 0f) {
            o.mig -= dt
            targetSpeed = WANDER[k] * 2.5f * w
        } else {
            o.turn += (Random.nextFloat() - 0.5f) * 6f * dt
            o.turn *= 1f - 1.5f * dt
            o.heading += o.turn * dt * 4f
            targetSpeed = WANDER[k] * w
        }
        o.speed += (targetSpeed * o.spd - o.speed) * 2f * dt
        o.x += cos(o.heading) * o.speed * dt
        o.y += sin(o.heading) * o.speed * dt
        if (o.x < 0f) { o.x = 0f; o.heading = PIF - o.heading }
        if (o.x > worldW) { o.x = worldW; o.heading = PIF - o.heading }
        if (o.y < 0f) { o.y = 0f; o.heading = -o.heading }
        if (o.y > h) { o.y = h; o.heading = -o.heading }

        // mangia
        if (haveT) {
            val other = tOrg?.size ?: 18f
            if (best < (o.size + other) * 0.22f * px) {
                if (tOrg != null) {
                    if (k == VIRUS) {
                        tOrg.state = 1; tOrg.timer = 6f
                        o.dead = true
                        return
                    }
                    tOrg.dead = true
                }
                if (tFood != null) foods.remove(tFood)
                if (tGhost != null) {
                    ghosts.remove(tGhost)
                    if (foods.size < 250) repeat(3) { foods.add(Food(o.x, o.y, Random.nextFloat(), (Random.nextFloat() - 0.5f) * 30f, (Random.nextFloat() - 0.5f) * 30f)) }
                }
                o.energy = (o.energy + GAIN[k]).coerceAtMost(1.3f)
                o.flash = 1f
            }
        }

        // riproduzione: le specie sessuate si dividono da sole solo se non trovano un partner
        if (o.energy >= SPLIT[k]) {
            if (!SEXUAL[k]) startDivision(o)
            else if (o.energy >= minOf(SPLIT[k] * 1.25f, 1.28f)) startDivision(o)
        }
    }

    private fun update(dt: Float) {
        val worldW = w * 1.6f
        val scrollPx = xOffset * (worldW - w)
        light = 0.5f + 0.5f * sin(time * 0.07f)
        moveCd -= dt

        while (true) {
            val t = touches.poll() ?: break
            if (t[2] > 0f) {
                if (foods.size < 260) burst(t[0] + scrollPx, t[1], 40)
                for (q in orgs) if (q.kind == PLANKTON && hypot(q.x - (t[0] + scrollPx), q.y - t[1]) < 0.3f * w) q.flash = 1f
            } else if (foods.size < 260 && moveCd <= 0f) {
                burst(t[0] + scrollPx, t[1], 5)
                moveCd = 0.06f
            }
        }
        foodTimer += dt
        if (foodTimer > 0.8f && foods.size < 60) {
            foodTimer = 0f
            foods.add(Food(offscreenX(), Random.nextFloat() * h, Random.nextFloat()))
        }
        for (f in foods) {
            f.x += f.vx * dt; f.y += f.vy * dt
            val kk = 1f - 2.2f * dt
            f.vx *= kk; f.vy *= kk
        }

        java.util.Arrays.fill(counts, 0)
        for (o in orgs) if (!o.dead) counts[o.kind]++
        for (o in orgs) stepOrg(o, dt, worldW)
        orgs.removeAll { it.dead }
        orgs.addAll(born)
        born.clear()

        val gi = ghosts.iterator()
        while (gi.hasNext()) {
            val g = gi.next()
            g.t += dt
            if (g.t > 14f) {
                gi.remove()
                if (foods.size < 250) repeat(2) { foods.add(Food(g.x, g.y, Random.nextFloat())) }
            }
        }

        immTimer += dt
        if (immTimer > 2f) {
            immTimer = 0f
            for (kd in 0 until NSP) if (MINP[kd] > 0 && counts[kd] < MINP[kd])
                orgs.add(newOrg(kd, offscreenX(), Random.nextFloat() * h))
        }
        migTimer += dt
        if (migTimer > 100f) { migTimer = 0f; migrate() }
        outbreakTimer += dt
        if (outbreakTimer > 60f) {
            outbreakTimer = 0f
            if (counts[VIRUS] == 0 && counts[MICROBE] > 10)
                repeat(2) { orgs.add(newOrg(VIRUS, offscreenX(), Random.nextFloat() * h)) }
        }
    }

    private fun put(x: Float, y: Float, a: Float, sz: Float, kind: Int, state: Float, flash: Float, c: FloatArray) {
        if ((n + 1) * 10 > buf.capacity()) return
        buf.put(x); buf.put(y); buf.put(a)
        buf.put(sz); buf.put(kind.toFloat()); buf.put(state); buf.put(flash)
        buf.put(c[0]); buf.put(c[1]); buf.put(c[2])
        n++
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = if (lastNs == 0L) 0.016f else ((now - lastNs) / 1e9f).coerceAtMost(0.05f)
        lastNs = now
        time += dt
        update(dt)

        val worldW = w * 1.6f
        val scrollPx = xOffset * (worldW - w)
        tx = 2f / w; ty = -2f / h; tz = -1f - 2f * scrollPx / w; tw = 1f

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // sfondo con sfumatura e vignetta, luminosita' legata al ciclo giorno/notte
        GLES20.glUseProgram(pBg)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(pBg, "uLight"), light)
        val aP = GLES20.glGetAttribLocation(pBg, "aP")
        bgBuf.position(0)
        GLES20.glEnableVertexAttribArray(aP)
        GLES20.glVertexAttribPointer(aP, 2, GLES20.GL_FLOAT, false, 8, bgBuf)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aP)

        // sprite: macchie, cibo, resti, organismi
        buf.clear(); n = 0
        for (d in dusts) {
            val x = d.bx + sin(time * 0.05f + d.ph) * 40f
            val y = d.by + cos(time * 0.04f + d.ph) * 30f
            put(x + scrollPx * (1f - d.par), y, 0f, d.size, DUST, d.alpha * (0.6f + 0.8f * light), 0f, d.color)
        }
        for (f in foods) put(f.x, f.y, f.phase, 20f, FOOD, 0f, 0f, WHITE)
        for (g in ghosts) put(g.x, g.y, g.angle, g.size, GHOST, 1f - g.t / 14f, 0f, WHITE)
        for (o in orgs) {
            val st = when (o.kind) {
                MICROBE -> if (o.state == 3) -1f else o.energy.coerceIn(0f, 1f)
                TARDI -> if (o.state == 2) 1f else 0f
                PARAM -> if (o.state == 1) 1f else 0f
                else -> 0f
            }
            val c = if (o.kind == MICROBE && o.state == 1) colorFor(VIRUS) else o.color
            val fl = if (o.kind == PLANKTON) o.flash + (1f - light) * 0.4f else o.flash
            val sz = if (o.kind == MICROBE && o.state == 3) o.size * 0.55f else o.size
            if (o.divT > 0f) {
                val p = (o.divT / DIV_TIME).coerceAtMost(1f)
                if (o.kind == YEAST) {
                    put(o.x, o.y, -o.heading, sz, o.kind, st, fl, c)
                    val d = sz * px * (0.30f + 0.25f * p)
                    put(o.x + cos(o.heading) * d, o.y + sin(o.heading) * d, -o.heading, sz * (0.25f + 0.45f * p), o.kind, st, fl, c)
                } else {
                    val off = 0.45f * sz * px * p
                    val dx = cos(o.heading) * off
                    val dy = sin(o.heading) * off
                    put(o.x - dx, o.y - dy, -o.heading, sz, o.kind, st, fl, c)
                    put(o.x + dx, o.y + dy, -o.heading, sz, o.kind, st, fl, c)
                }
            } else put(o.x, o.y, -o.heading, sz, o.kind, st, fl, c)
        }

        GLES20.glUseProgram(pSprite)
        GLES20.glUniform4f(GLES20.glGetUniformLocation(pSprite, "uTrans"), tx, ty, tz, tw)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(pSprite, "time"), time)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(pSprite, "uPx"), px)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(pSprite, "uMax"), maxPoint)
        val names = arrayOf("aPos", "aInfo", "aColor")
        val sizes = intArrayOf(3, 4, 3)
        val offs = intArrayOf(0, 3, 7)
        val locs = IntArray(3)
        for (i in 0..2) {
            locs[i] = GLES20.glGetAttribLocation(pSprite, names[i])
            buf.position(offs[i])
            GLES20.glEnableVertexAttribArray(locs[i])
            GLES20.glVertexAttribPointer(locs[i], sizes[i], GLES20.GL_FLOAT, false, 40, buf)
        }
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, n)
        for (l in locs) GLES20.glDisableVertexAttribArray(l)
    }

    private fun link(v: String, f: String): Int {
        fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src); GLES20.glCompileShader(s)
            val ok = IntArray(1); GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { GLES20.glGetShaderInfoLog(s) }
            return s
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, v))
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, f))
        GLES20.glLinkProgram(p)
        return p
    }

    private companion object {
        const val BG_V = """
attribute vec2 aP;
varying vec2 vUV;
void main() { vUV = aP * 0.5 + 0.5; gl_Position = vec4(aP, 0.0, 1.0); }"""
        const val BG_F = """
precision mediump float;
varying vec2 vUV;
uniform float uLight;
void main() {
  vec3 top = mix(vec3(0.0, 0.008, 0.02), vec3(0.02, 0.07, 0.12), uLight);
  vec3 bot = mix(vec3(0.0, 0.0, 0.004), vec3(0.0, 0.02, 0.04), uLight);
  vec3 c = mix(bot, top, vUV.y);
  float v = length(vUV - 0.5) * 1.3;
  c *= 1.0 - 0.5 * v * v;
  gl_FragColor = vec4(c, 1.0);
}"""
        const val SPRITE_V = """
precision highp float;
uniform vec4 uTrans;
uniform float time;
uniform float uPx;
uniform float uMax;
attribute vec3 aPos;
attribute vec4 aInfo;
attribute vec3 aColor;
varying vec3 vColor;
varying float vKind;
varying float vState;
varying float vFlash;
varying float vT;
varying vec2 vH;
void main() {
  gl_Position = vec4(aPos.xy * uTrans.xy + uTrans.zw, 0.0, 1.0);
  float sz = aInfo.x;
  if (aInfo.y > 13.5 && aInfo.y < 14.5) sz *= cos(time * 2.0 + aPos.z * 10.0) * 0.2 + 0.7;
  gl_PointSize = min(sz * uPx, uMax);
  vKind = aInfo.y; vState = aInfo.z; vFlash = aInfo.w;
  vColor = aColor; vT = time;
  vH = vec2(cos(aPos.z), sin(aPos.z));
}"""
        // Ogni forma e' moltiplicata per una maschera circolare (mask): niente quadrati.
        const val SPRITE_F = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec3 vColor;
varying float vKind;
varying float vState;
varying float vFlash;
varying float vT;
varying vec2 vH;
float sq(float x) { return x * x; }
void main() {
  vec2 p = gl_PointCoord - 0.5;
  float r = length(p) * 2.0;
  vec2 q = vec2(p.x * vH.x - p.y * vH.y, p.x * vH.y + p.y * vH.x);
  float ang = atan(p.y, p.x);
  vec3 col = vColor;
  float a = 0.0;
  float k = vKind;
  if (k < 0.5 && vState < 0.0) {
    float spc = clamp((0.24 - r) * 10.0, 0.0, 0.9);
    float ring = clamp(1.0 - sq((r - 0.24) * 30.0), 0.0, 0.9);
    a = spc * 0.5 + ring * 0.6;
    col = vec3(0.65, 0.75, 0.9);
  } else if (k < 0.5) {
    float ws = 1.0 / mix(0.5, 0.9, vState);
    float hh = length(q * vec2(1.0, ws)) * 2.0;
    a = clamp(-sq(hh - 0.4) * 30.0 + 0.5, 0.0, 0.5)
      + clamp(1.0 - length(q * vec2(1.0, (ws - 1.0) * 0.2 + 1.0) * 2.0), 0.0, 0.5);
    col = vColor * 1.1 + vFlash;
  } else if (k < 1.5) {
    float wob = 0.5 + 0.05 * sin(ang * 4.0 + vT * 1.5);
    float body = clamp((wob - r) * 14.0, 0.0, 1.0);
    float nuc = clamp((0.18 - length(p - vec2(0.04 * cos(vT), 0.04 * sin(vT)))) * 14.0, 0.0, 1.0);
    a = body * 0.45 + nuc * 0.5 + max(0.0, 1.0 - r) * 0.12;
    col = vColor + nuc * 0.3 + vFlash * 0.4;
  } else if (k < 2.5) {
    float boat = q.x * q.x * 4.5 + q.y * q.y * 18.0;
    float shell = clamp(1.0 - sq((boat - 0.22) * 16.0), 0.0, 0.9);
    float inside = step(boat, 0.22);
    float striae = inside * (sin(q.y * 65.0) * 0.18 + 0.18);
    a = shell + striae + vFlash * 0.3 * inside;
    col = vColor + vec3(striae * 0.8, striae, striae * 1.2) + vFlash * 0.5;
  } else if (k < 3.5) {
    float cil = (q.x > 0.0) ? sin(atan(q.y, q.x) * 12.0 + vT * 28.0) * 0.15 : 0.0;
    float cup = clamp(1.0 - sq((r - (0.42 + cil)) * 14.0), 0.0, 0.85);
    float eye = clamp((0.07 - length(q - vec2(-0.12, 0.0))) * 30.0, 0.0, 0.8);
    a = cup + max(0.0, 1.0 - r) * 0.25 + eye;
    col = vColor + eye * 0.4 + vFlash * 0.5;
  } else if (k < 4.5) {
    float wave = sin(q.x * 24.0 + vT * 25.0) * 0.12;
    float helix = clamp(1.0 - sq((q.y - wave) * 26.0), 0.0, 0.95);
    float cap = clamp((0.45 - abs(q.x)) * 12.0, 0.0, 1.0);
    a = helix * cap;
    col = vColor + 0.2 + vFlash * 0.5;
  } else if (k < 5.5) {
    vec2 pa = abs(p) * 2.0;
    float capsid = clamp((0.75 - (pa.x + pa.y)) * 12.0, 0.0, 1.0);
    float core = clamp((0.35 - r) * 14.0, 0.0, 1.0);
    a = capsid * 0.6 + core;
    col = vColor + (sin(vT * 18.0) * 0.3 + 0.3) * 0.4 + vFlash * 0.5;
  } else if (k < 6.5) {
    float wob = 0.52 + 0.08 * sin(ang * 5.0 + vT * 2.2) + 0.04 * cos(ang * 3.0 - vT * 1.7);
    float mem = clamp(1.0 - sq((r - wob) * 18.0), 0.0, 0.9);
    float nuc = clamp((0.24 - length(p + vec2(0.05 * cos(vT * 1.2), 0.05 * sin(vT * 1.2)))) * 14.0, 0.0, 0.75);
    a = mem * 0.8 + nuc + max(0.0, 1.0 - r) * 0.25;
    col = vColor + nuc * 0.35 + vFlash * 0.6;
  } else if (k < 7.5) {
    if (vState > 0.5) {
      float tr = clamp(1.0 - sq((r - 0.5) * 16.0), 0.0, 0.95);
      float tc = clamp((0.3 - r) * 10.0, 0.0, 0.6);
      col = vec3(0.98, 0.82, 0.25) + tc * 0.3;
      a = tr + tc;
    } else {
      vec2 dd = vec2(max(abs(q.x) - 0.18, 0.0), q.y);
      float body = clamp((0.16 - length(dd)) * 18.0, 0.0, 0.9);
      float legs = step(0.16, abs(q.y)) * step(abs(q.y), 0.27) * step(0.8, abs(sin(q.x * 30.0))) * step(abs(q.x), 0.2);
      a = body + legs * 0.6;
      col = vColor + vFlash * 0.5;
    }
  } else if (k < 8.5) {
    float wob = 0.40 + 0.09 * sin(ang * 3.0 + vT * 1.3) + 0.06 * sin(ang * 5.0 - vT * 2.1) + 0.04 * sin(ang * 7.0 + vT * 2.9);
    float body = clamp((wob - r) * 14.0, 0.0, 1.0);
    float mem = clamp(1.0 - sq((r - wob) * 20.0), 0.0, 0.8);
    float nuc = clamp((0.14 - length(p - vec2(0.05 * cos(vT * 0.6), 0.05 * sin(vT * 0.6)))) * 18.0, 0.0, 0.9);
    float vac = clamp((0.07 - length(p - vec2(-0.14, 0.1))) * 30.0, 0.0, 0.6) + clamp((0.06 - length(p - vec2(0.12, -0.12))) * 30.0, 0.0, 0.6);
    a = body * 0.30 + mem * 0.7 + nuc * 0.6 + vac * 0.4;
    col = vColor + nuc * 0.2 + vFlash * 0.5;
  } else if (k < 9.5) {
    float s2 = q.x * q.x * 3.4 + q.y * q.y * 15.0;
    float ring = clamp(1.0 - sq((s2 - 0.22) * 16.0), 0.0, 0.9);
    float inside = step(s2, 0.22);
    float cil = ring * (0.35 + 0.35 * sin(q.x * 70.0 - vT * 22.0));
    float mac = clamp((0.07 - length(q - vec2(0.03, 0.0))) * 26.0, 0.0, 0.8);
    float sym = step(0.5, vState) * inside * step(0.82, sin(q.x * 48.0) * sin(q.y * 80.0 + 1.0));
    a = ring * 0.6 + cil + inside * 0.12 + mac * 0.5 + sym * 0.7;
    col = vColor + mac * 0.15 + vFlash * 0.5;
    if (sym > 0.0) col = mix(col, vec3(0.3, 0.95, 0.4), 0.8);
  } else if (k < 10.5) {
    float s3 = q.x * q.x * 6.0 + q.y * q.y * 40.0;
    float body = clamp((0.2 - s3) * 12.0, 0.0, 1.0);
    float fl = clamp(1.0 - sq((q.y - 0.08 * sin(q.x * 40.0 - vT * 18.0)) * 40.0), 0.0, 0.9) * step(0.18, q.x) * step(q.x, 0.48);
    float eye = clamp((0.04 - length(q - vec2(0.10, 0.0))) * 40.0, 0.0, 0.9);
    float chl = body * step(0.8, sin(q.x * 30.0 + 1.0) * sin(q.y * 90.0));
    a = body * 0.55 + fl * 0.7 + chl * 0.3 + eye * 0.9;
    col = vColor + vFlash * 0.5;
    col = mix(col, vec3(1.0, 0.15, 0.1), eye);
  } else if (k < 11.5) {
    float wall = clamp(1.0 - sq((r - 0.78) * 14.0), 0.0, 0.9);
    float body = clamp((0.78 - r) * 12.0, 0.0, 1.0);
    float vac = clamp((0.16 - length(p - vec2(0.04, -0.05))) * 16.0, 0.0, 0.7);
    a = body * 0.22 + wall * 0.8 + vac * 0.35;
    col = vColor + vFlash * 0.5;
  } else if (k < 12.5) {
    float core = clamp((0.30 - r) * 12.0, 0.0, 1.0);
    float girdle = clamp(1.0 - sq(q.x * 30.0), 0.0, 0.6) * step(r, 0.34);
    float glow = exp(-5.0 * r * r) * (0.25 + 0.9 * vFlash);
    a = core * 0.55 + girdle * 0.4 + glow;
    col = mix(vColor, vec3(0.4, 0.8, 1.0), clamp(vFlash, 0.0, 1.0)) + vFlash * 0.4;
  } else if (k < 13.5) {
    float hh = length(q * vec2(1.0, 2.333)) * 2.0;
    a = clamp(-sq(hh - 0.4) * 30.0 + 0.5, 0.0, 0.5) * vState;
    col = vec3(1.0);
  } else if (k < 14.5) {
    a = exp(-4.13 * r * r);
    col = vec3(1.0);
  } else {
    a = vState * exp(-3.2 * r * r);
  }
  float mask = 1.0 - smoothstep(0.9, 1.0, r);
  float alpha = clamp(a, 0.0, 0.95) * mask;
  if (alpha < 0.003) discard;
  gl_FragColor = vec4(col, alpha);
}"""
    }
}
