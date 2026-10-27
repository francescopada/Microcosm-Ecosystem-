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
// Dimensioni a scala logaritmica compressa (virus < batteri < lieviti/alghe < flagellati < ciliati/rotiferi/amebe)
private val SIZE   = floatArrayOf(30f,   32f,    56f,    92f,    38f,    22f,    90f,    84f,    110f,   96f,    58f,    34f,    44f)
// velocita' di crociera in lunghezze-corpo/s (reali: batteri ~10, paramecio ~8, rotifero ~2, euglena ~1, diatomea ~0.5,
// tardigrado ~0.1, ameba ~0.01, alga/lievito/virus solo moto browniano); sullo schermo e' compressa con esponente 0.55
private val CRUISE = floatArrayOf(8f,    0.15f,  0.4f,   2f,     3f,     0.8f,   6f,     0.12f,  0.012f, 8f,     1.2f,   0.05f,  5f)
private val SPD55  = FloatArray(13) { Math.pow(CRUISE[it].toDouble(), 0.55).toFloat() }
private val SIGHT  = floatArrayOf(0.30f,  0f,     0f,     0.22f,  0.25f,  0.07f,  0.30f,  0.20f,  0.15f,  0.20f,  0.15f,  0.12f,  0f)
private val DECAY  = floatArrayOf(0.012f, 0.004f, 0.015f, 0.020f, 0.020f, 0.060f, 0.008f, 0.010f, 0.010f, 0.014f, 0.008f, 0.007f, 0.004f)
private val GAIN   = floatArrayOf(0.12f,  0f,     0f,     0.40f,  0.50f,  0f,     0.30f,  0.30f,  0.35f,  0.22f,  0.12f,  0.15f,  0f)
private val SPLIT  = floatArrayOf(1.0f,   1.0f,   1.0f,   1.1f,   1.0f,   99f,    1.2f,   1.1f,   1.1f,   1.0f,   1.0f,   1.0f,   1.0f)
// tempo di generazione minimo in secondi (batteri < lieviti < alghe/paramecio < euglena < rotiferi < amebe < Didinium < tardigradi)
private val CYCLE  = floatArrayOf(10f,   30f,    40f,    45f,    14f,    0f,     70f,    120f,   60f,    30f,    40f,    25f,    35f)
// durata della vita in secondi di gioco (virioni breve, batteri/alghe breve, tardigradi molto lunga)
private val LIFE   = floatArrayOf(300f,  420f,    500f,   800f,   300f,   40f,    600f,   2400f,  1500f,  900f,   500f,   360f,   450f)
// pausa del predatore mentre ingerisce (s) e posizione della bocca come frazione della lunghezza
private val EATPAUSE = floatArrayOf(0.2f, 0.2f, 0.2f, 0.3f, 0.2f, 0.2f, 1.5f, 1.0f, 1.5f, 0.2f, 0.2f, 0.2f, 0.2f)
private val MOUTH  = floatArrayOf(0.2f, 0.2f, 0.2f, 0.30f, 0.2f, 0.2f, 0.33f, 0.28f, 0.20f, 0.20f, 0.2f, 0.2f, 0.2f)
private const val EA = 0.8f   // cattura e ingestione
private const val EB = 6f     // digestione nel vacuolo
private const val EE = 0.8f   // espulsione dei residui
private val MAXP   = intArrayOf(70, 30, 12, 5, 10, 24, 3, 3, 2, 8, 8, 10, 14)
private val MINP   = intArrayOf(28, 10, 3, 1, 3, 0, 0, 1, 0, 2, 2, 3, 3)
// il paramecio sfugge a Didinium (reazione di evitamento); gli altri non hanno predatori "percepiti"
private val THREAT = intArrayOf(-1, -1, -1, -1, -1, -1, -1, -1, -1, MACRO, -1, -1, -1)
// riproduzione sessuata con meiosi (paramecio e Didinium: coniugazione; rotifero: partenogenesi)
private val SEXUAL = booleanArrayOf(false, false, true, false, false, false, true, true, false, true, false, false, false)

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
        var age = 0f
        var eatT = 0f
        var maxAge = 600f
    }
    private class Ghost(val x: Float, val y: Float, val angle: Float, val size: Float) { var t = 0f }
    private class Food(var x: Float, var y: Float, val phase: Float, var vx: Float = 0f, var vy: Float = 0f) { var life = 30f + Random.nextFloat() * 30f }
    private class Dust(val bx: Float, val by: Float, val size: Float, val par: Float,
                       val color: FloatArray, val alpha: Float, val ph: Float)

    private val orgs = ArrayList<Org>()
    private val born = ArrayList<Org>()
    private class Engulf(val host: Org, val kind: Int, val color: FloatArray, val x0: Float, val y0: Float, val size: Float) { var t = 0f }
    private val engulfs = ArrayList<Engulf>()
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
        orgs.clear(); engulfs.clear(); born.clear(); ghosts.clear(); foods.clear(); dusts.clear()
        val worldW = w * 1.6f
        fun seed(kind: Int, c: Int) = repeat(c) { orgs.add(newOrg(kind, Random.nextFloat() * worldW, Random.nextFloat() * h)) }
        seed(ALGA, 14); seed(MICROBE, 36); seed(DIATOM, 5); seed(SPIRILLUM, 4)
        seed(ROTIFER, 2); seed(MACRO, 1); seed(TARDI, 2)
        seed(PARAM, 3); seed(EUGLENA, 3); seed(YEAST, 4); seed(PLANKTON, 5)
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
        o.maxAge = LIFE[kind] * (0.8f + 0.4f * Random.nextFloat())
        o.age = Random.nextFloat() * 0.6f * o.maxAge
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
        o.age = 0f
        o.cool = CYCLE[kind] * (0.6f + 0.4f * Random.nextFloat())
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

    private fun addEngulf(host: Org, prey: Org) {
        if (engulfs.size >= 60) return
        engulfs.add(Engulf(host, prey.kind, prey.color, prey.x, prey.y, prey.size))
        host.eatT = EATPAUSE[host.kind]
    }

    private fun startDivision(o: Org) {
        if (counts[o.kind] >= MAXP[o.kind]) return
        o.divT = 0.01f
    }

    private fun finishDivision(o: Org) {
        o.divT = 0f
        o.energy *= 0.5f
        o.cool = CYCLE[o.kind] * (0.8f + 0.4f * Random.nextFloat())
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
            ROTIFER -> t.kind == ALGA || t.kind == DIATOM || t.kind == YEAST || t.kind == PLANKTON || (t.kind == MICROBE && t.state != 3)
            TARDI -> t.kind == ALGA || t.kind == DIATOM
            MACRO -> t.kind == PARAM
            VIRUS -> t.kind == MICROBE && t.state == 0
            AMOEBA -> t.kind == ALGA || t.kind == YEAST || t.kind == EUGLENA || (t.kind == MICROBE && t.state == 0 && counts[MICROBE] > 25)
            PARAM -> t.kind == YEAST || (t.kind == MICROBE && t.state == 0 && counts[MICROBE] > 20)
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

        if (k == TARDI || k == MACRO) { // criptobiosi (tardigrado) / incistamento (Didinium)
            if (o.state == 0 && o.energy < 0.2f) { o.state = 2; o.timer = 20f }
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
                repeat(5) { addBorn(VIRUS, o.x, o.y, 0.6f) }
                return
            }
        }

        when (k) {
            ALGA -> {
                o.energy += 0.05f * light * dt
                if (foods.size < 200 && Random.nextFloat() < 0.02f * dt * 60f / 60f) foods.add(Food(o.x, o.y, Random.nextFloat()))
            }
            EUGLENA -> o.energy += 0.035f * light * dt
            DIATOM -> o.energy += 0.04f * light * dt
            PLANKTON -> o.energy += 0.04f * light * dt
            PARAM -> if (o.state == 1) o.energy += 0.025f * light * dt // simbiosi con le alghe
        }
        o.energy -= DECAY[k] * (0.6f + 0.4f * o.spd) * (0.7f + 0.3f * o.szm) * dt
        if (o.energy <= 0f) { o.dead = true; starved(o); return }
        o.age += dt
        if (o.age > o.maxAge) { o.dead = true; starved(o); return } // senescenza

        if (o.eatT > 0f) {
            o.eatT -= dt
            o.speed *= (1f - 5f * dt).coerceAtLeast(0f)
            o.x += cos(o.heading) * o.speed * dt
            o.y += sin(o.heading) * o.speed * dt
            return
        }

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
                    if (k == PARAM || k == MACRO) {
                        if (o.leader) { val sp = (o.spd + m.spd) / 2f; o.spd = sp; m.spd = sp }
                        o.energy = (o.energy + 0.1f).coerceAtMost(1.3f)
                    } else {
                        if (o.leader) addBorn(k, (o.x + m.x) / 2f, (o.y + m.y) / 2f, 0.45f, o, m)
                        o.energy *= 0.55f
                    }
                    o.cool = CYCLE[k]; o.mate = null; o.leader = false
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
        if (SIGHT[k] > 0f) {
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

        val base = 0.22f * o.size * px * SPD55[k] // px/s dalla velocita' in lunghezze-corpo/s
        val targetSpeed: Float
        if (fleeing) {
            o.heading += angDiff(fa, o.heading).coerceIn(-5f * dt, 5f * dt)
            targetSpeed = base * 1.8f
        } else if (seek) {
            o.heading += angDiff(atan2(sy - o.y, sx - o.x), o.heading).coerceIn(-3f * dt, 3f * dt)
            targetSpeed = base * 1.4f
        } else if (haveT) {
            o.heading += angDiff(atan2(tY - o.y, tX - o.x), o.heading).coerceIn(-3f * dt, 3f * dt)
            targetSpeed = base * 1.4f
        } else if (o.mig > 0f) {
            o.mig -= dt
            targetSpeed = base * 2.5f
        } else {
            when (k) {
                // batteri: corsa e capriola (run-and-tumble)
                MICROBE, SPIRILLUM -> if (Random.nextFloat() < dt) o.heading += (Random.nextFloat() - 0.5f) * 3f
                // virus, alghe immobili, lieviti: moto browniano
                VIRUS, ALGA, YEAST -> o.heading += (Random.nextFloat() - 0.5f) * 8f * dt
                else -> {
                    o.turn += (Random.nextFloat() - 0.5f) * 6f * dt
                    o.turn *= 1f - 1.5f * dt
                    o.heading += o.turn * dt * 4f
                }
            }
            if (k == EUGLENA) o.heading += angDiff(-PIF / 2f, o.heading) * 0.4f * light * dt // fototassi positiva
            if (k == PLANKTON) o.heading += angDiff(if (light > 0.5f) -PIF / 2f else PIF / 2f, o.heading) * 0.3f * dt // migrazione verticale giorno/notte
            if (k == PARAM || k == EUGLENA || k == PLANKTON) o.heading += 0.9f * sin(time * 3.5f + o.size) * dt // nuoto elicoidale
            targetSpeed = base
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
                    addEngulf(o, tOrg)
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

        // riproduzione (rispettando il tempo di generazione della specie)
        if (o.energy >= SPLIT[k] && o.cool <= 0f) {
            if (k == ROTIFER) {
                // partenogenesi: la femmina depone un uovo
                if (counts[k] < MAXP[k]) {
                    val back = o.size * px * 0.45f
                    addBorn(k, o.x - cos(o.heading) * back, o.y - sin(o.heading) * back, 0.3f, o)
                    o.energy *= 0.6f
                }
                o.cool = CYCLE[k] * (0.8f + 0.4f * Random.nextFloat())
            } else if (!SEXUAL[k]) startDivision(o)
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
                if (foods.size > 200) foods.subList(0, foods.size - 200).clear()
                burst(t[0] + scrollPx, t[1], 40)
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
            f.life -= dt
        }
        foods.removeAll { it.life <= 0f }

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

        val ei = engulfs.iterator()
        while (ei.hasNext()) {
            val e = ei.next()
            e.t += dt
            if (e.host.dead) { ei.remove(); continue }
            if (e.t >= EA + EB + EE) {
                ei.remove()
                val sz = e.host.size * px * 0.32f
                if (foods.size < 250) foods.add(Food(e.host.x - cos(e.host.heading) * sz, e.host.y - sin(e.host.heading) * sz, Random.nextFloat(),
                    -cos(e.host.heading) * 12f, -sin(e.host.heading) * 12f))
            }
        }

        immTimer += dt
        if (immTimer > 2f) {
            immTimer = 0f
            for (kd in 0 until NSP) if (MINP[kd] > 0 && counts[kd] < MINP[kd])
                repeat(if (kd == MICROBE) 4 else 1) { orgs.add(newOrg(kd, offscreenX(), Random.nextFloat() * h)) }
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
                TARDI, MACRO -> if (o.state == 2) 1f else 0f
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
        // fagocitosi: cattura alla bocca, vacuolo digestivo che scorre nell'intestino, espulsione dei residui
        for (e in engulfs) {
            val ho = e.host
            val sh = ho.size * px
            val hc = cos(ho.heading); val hs = sin(ho.heading)
            val mx = ho.x + hc * MOUTH[ho.kind] * sh
            val my = ho.y + hs * MOUTH[ho.kind] * sh
            val gx = ho.x - hc * 0.03f * sh
            val gy = ho.y - hs * 0.03f * sh
            val st = if (e.kind == MICROBE) 0.6f else 0f
            if (e.t < EA) {
                val u = e.t / EA
                val ease = u * u * (3f - 2f * u)
                put(e.x0 + (mx - e.x0) * ease, e.y0 + (my - e.y0) * ease, -ho.heading, e.size * (1f - 0.45f * ease), e.kind, st, 0.3f, e.color)
            } else if (e.t < EA + EB) {
                val u = (e.t - EA) / EB
                val tr = (u * 3f).coerceAtMost(1f)
                val x = mx + (gx - mx) * tr
                val y = my + (gy - my) * tr
                put(x, y, 0f, e.size * 0.85f, GHOST, 0.7f, 0f, WHITE)
                val ps = e.size * 0.55f * (1f - u)
                if (ps > 3f) put(x, y, -ho.heading, ps, e.kind, st, 0f, e.color)
            } else {
                val u = (e.t - EA - EB) / EE
                val rx = ho.x - hc * 0.32f * sh
                val ry = ho.y - hs * 0.32f * sh
                put(gx + (rx - gx) * u, gy + (ry - gy) * u, 0f, e.size * 0.85f * (1f - 0.6f * u), GHOST, 0.7f * (1f - u), 0f, WHITE)
            }
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
float band(float d, float w) { return clamp(1.0 - sq(d / w), 0.0, 1.0); }
float blob(vec2 v, float rad) { return clamp(1.0 - length(v) / rad, 0.0, 1.0); }
float sdSeg(vec2 v, vec2 a, vec2 b) { vec2 pa = v - a; vec2 ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }
float sdEll(vec2 v, vec2 ab) { return (length(v / ab) - 1.0) * min(ab.x, ab.y); }
float skin(float d) { return 0.55 * band(d, 0.045) + 0.16 * clamp(-d * 6.0, 0.0, 1.0); }
float legD(vec2 v, float x, float ph) { return min(sdSeg(v, vec2(x, 0.11), vec2(x - 0.03 + ph, 0.19)), sdSeg(v, vec2(x, -0.11), vec2(x - 0.03 - ph, -0.19))); }
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
    float d = length(p) - (0.30 + 0.008 * sin(ang * 3.0 + vT * 1.2));
    float cup = band(length(p) - 0.19, 0.05) * smoothstep(-0.3, 0.4, cos(ang - 2.4));
    float pyr = blob(p - vec2(-0.08, 0.06), 0.07);
    a = skin(d) + cup * 0.5 + pyr * 0.6;
    col = vColor * 1.1 + pyr * 0.3 + vFlash * 0.4;
  } else if (k < 2.5) {
    float d = max(abs(q.y) - 0.10 * (1.0 - sq(q.x / 0.44)), abs(q.x) - 0.44);
    float ins = clamp(-d * 20.0, 0.0, 1.0);
    float raphe = band(q.y, 0.02) * step(abs(q.x), 0.36) * 0.4 * ins;
    float stri = ins * (0.5 + 0.5 * sin(q.x * 75.0)) * 0.2;
    a = skin(d) + raphe + stri + blob(q, 0.07) * 0.5 + vFlash * 0.25 * ins;
    col = vColor * 1.1 + vFlash * 0.5;
  } else if (k < 3.5) {
    float d = sdEll(q - vec2(-0.01, 0.0), vec2(0.23, mix(0.13, 0.08, smoothstep(0.0, 0.2, q.x))));
    vec2 c1 = q - vec2(0.29, 0.09);
    vec2 c2 = q - vec2(0.29, -0.09);
    float cor = band(length(c1) - 0.06, 0.05) * (0.78 + 0.22 * sin(atan(c1.y, c1.x) * 5.0 - vT * 14.0))
              + band(length(c2) - 0.06, 0.05) * (0.78 + 0.22 * sin(atan(c2.y, c2.x) * 5.0 + vT * 14.0));
    float neck = band(sdSeg(q, vec2(0.18, 0.0), vec2(0.27, 0.0)), 0.03) * 0.4;
    float foot = band(sdSeg(q, vec2(-0.22, 0.0), vec2(-0.40, 0.0)), 0.03) * 0.5;
    float toes = band(min(sdSeg(q, vec2(-0.40, 0.0), vec2(-0.46, 0.04)), sdSeg(q, vec2(-0.40, 0.0), vec2(-0.46, -0.04))), 0.02) * 0.5;
    float mastax = blob(q - vec2(0.07, 0.0), 0.06);
    float eye = blob(q - vec2(0.24, 0.0), 0.03);
    a = skin(d) + cor * 0.55 + neck + foot + toes + mastax * 0.5 + eye;
    col = vColor * 1.1 + mastax * 0.2 + vFlash * 0.5;
    col = mix(col, vec3(1.0, 0.3, 0.25), eye * 0.8);
  } else if (k < 4.5) {
    float ax = abs(q.x);
    float wave = sin(q.x * 17.0 - vT * 7.0) * 0.075;
    float helix = band(abs(q.y - wave), 0.05) * smoothstep(0.38, 0.30, ax) * 0.8;
    vec2 e = vec2(sign(q.x) * 0.36, 0.0);
    float sg = sign(q.x);
    float tuft = band(min(sdSeg(q, e, e + vec2(sg * 0.10, 0.0)), min(sdSeg(q, e, e + vec2(sg * 0.095, 0.04)), sdSeg(q, e, e + vec2(sg * 0.095, -0.04)))), 0.02) * 0.45 * step(0.35, ax);
    a = helix + tuft;
    col = vColor * 1.1 + 0.15 + vFlash * 0.5;
  } else if (k < 5.5) {
    vec2 hp = abs(q - vec2(0.15, 0.0));
    float hexd = max(hp.x * 0.866 + hp.y * 0.5, hp.y) - 0.11;
    float tail = band(sdSeg(q, vec2(0.04, 0.0), vec2(-0.22, 0.0)), 0.03) * (0.55 + 0.25 * sin(q.x * 60.0));
    float base = band(sdSeg(q, vec2(-0.24, 0.05), vec2(-0.24, -0.05)), 0.025) * 0.6;
    float legs = band(min(sdSeg(q, vec2(-0.24, 0.0), vec2(-0.36, 0.11)), min(sdSeg(q, vec2(-0.24, 0.0), vec2(-0.36, -0.11)), sdSeg(q, vec2(-0.24, 0.0), vec2(-0.40, 0.0)))), 0.02) * 0.5;
    a = skin(hexd) + blob(q - vec2(0.15, 0.0), 0.09) * 0.35 + tail + base + legs;
    col = vColor * 1.1 + (sin(vT * 18.0) * 0.3 + 0.3) * 0.3 + vFlash * 0.5;
  } else if (k < 6.5) {
    if (vState > 0.5) {
      float tr = clamp(1.0 - sq((r - 0.5) * 16.0), 0.0, 0.95);
      float tc = clamp((0.3 - r) * 10.0, 0.0, 0.6);
      col = vec3(0.98, 0.82, 0.25) + tc * 0.3;
      a = tr + tc;
    } else {
      vec2 b = q - vec2(-0.05, 0.0);
      float d = sdEll(b, vec2(0.21, 0.17));
      float m1 = clamp(1.0 - sq(q.y / 0.17), 0.0, 1.0);
      float g1 = band(q.x + 0.10 + 0.5 * q.y * q.y, 0.03) * m1 * (0.6 + 0.4 * sin(q.y * 40.0 - vT * 18.0));
      float g2 = band(q.x - 0.02 + 0.5 * q.y * q.y, 0.03) * m1 * (0.6 + 0.4 * sin(q.y * 40.0 + vT * 18.0));
      float probo = band(sdSeg(q, vec2(0.14, 0.0), vec2(0.31, 0.0)), 0.045) * 0.6;
      float tip = blob(q - vec2(0.33, 0.0), 0.04);
      float mac = band(length(b) - 0.08, 0.04) * smoothstep(0.5, 0.0, cos(atan(b.y, b.x)));
      a = skin(d) + (g1 + g2) * 0.5 + probo + tip * 0.6 + mac * 0.5;
      col = vColor * 1.1 + vFlash * 0.6;
    }
  } else if (k < 7.5) {
    if (vState > 0.5) {
      float tr = clamp(1.0 - sq((r - 0.5) * 16.0), 0.0, 0.95);
      float tc = clamp((0.3 - r) * 10.0, 0.0, 0.6);
      col = vec3(0.98, 0.82, 0.25) + tc * 0.3;
      a = tr + tc;
    } else {
      vec2 t = vec2(q.x, q.y - 0.03 * sin(q.x * 9.0 + vT * 0.8));
      float d = sdEll(t, vec2(0.27, 0.14));
      float w = 0.012 * sin(vT * 4.0);
      float lg = min(min(legD(t, -0.17, w), legD(t, -0.06, -w)), min(legD(t, 0.05, w), legD(t, 0.16, -w)));
      float seg = (0.5 + 0.5 * sin(q.x * 34.0)) * 0.10 * clamp(-d * 8.0, 0.0, 1.0);
      float head = band(sdEll(t - vec2(0.26, 0.0), vec2(0.06, 0.075)), 0.04) * 0.4;
      float mouth = blob(t - vec2(0.29, 0.0), 0.03);
      a = skin(d) + band(lg, 0.05) * 0.5 + seg + head + mouth * 0.5;
      col = vColor * 1.1 + vFlash * 0.5;
    }
  } else if (k < 8.5) {
    float fa = atan(q.y, q.x);
    float rad = 0.27 + 0.03 * sin(ang * 3.0 + vT * 1.3) + 0.02 * sin(ang * 5.0 - vT * 2.1) + 0.01 * sin(ang * 7.0 + vT * 2.9) + 0.07 * exp(-fa * fa * 1.5) * (0.6 + 0.4 * sin(vT * 2.0));
    float d = length(p) - rad;
    vec2 np = p - vec2(0.03 * cos(vT * 0.6), 0.03 * sin(vT * 0.6));
    float nuc = band(length(np) - 0.07, 0.035) * 0.8 + blob(np, 0.025);
    float cv = blob(p - vec2(-0.13, 0.08), 0.045 * (0.55 + 0.45 * sin(vT * 1.6)) + 0.01);
    float fv = band(length(p - vec2(0.11, -0.10)) - 0.03, 0.02) * 0.5;
    a = skin(d) + clamp(-d * 4.0, 0.0, 0.2) + band(d + 0.05, 0.03) * 0.15 + nuc * 0.5 + cv * 0.5 + fv;
    col = vColor * 1.1 + nuc * 0.15 + vFlash * 0.5;
  } else if (k < 9.5) {
    float hw = mix(0.075, 0.115, smoothstep(-0.2, 0.15, q.x));
    float d = sdEll(q, vec2(0.28, hw));
    float ins = clamp(-d * 20.0, 0.0, 1.0);
    float cil = band(d - 0.02, 0.03) * (0.4 + 0.3 * sin(q.x * 55.0 - vT * 20.0)) * 0.5;
    float groove = band(sdSeg(q, vec2(0.15, 0.06), vec2(0.0, -0.005)), 0.02) * 0.45 * ins;
    float mac = blob(vec2(q.x, (q.y + 0.01) * 1.8), 0.075);
    float mic = blob(q - vec2(0.03, 0.04), 0.02);
    float v1 = band(length(q - vec2(0.20, 0.0)) - 0.03 * (0.5 + 0.5 * sin(vT * 1.2)), 0.02) * 0.5;
    float v2 = band(length(q - vec2(-0.19, 0.0)) - 0.03 * (0.5 + 0.5 * sin(vT * 1.2 + 3.14)), 0.02) * 0.5;
    float sym = step(0.5, vState) * ins * smoothstep(0.45, 0.8, sin(q.x * 40.0) * sin(q.y * 70.0 + 1.0));
    a = skin(d) + cil + groove + mac * 0.5 + mic * 0.6 + v1 + v2 + sym * 0.5;
    col = vColor * 1.1 + mac * 0.15 + vFlash * 0.5;
    if (sym > 0.0) col = mix(col, vec3(0.3, 0.95, 0.4), 0.7);
  } else if (k < 10.5) {
    float met = 1.0 + 0.15 * sin(vT * 3.0 + q.x * 14.0);
    float d = sdEll(q - vec2(-0.02, 0.0), vec2(0.24, 0.065 * met));
    float fl = band(q.y - 0.05 * sin(q.x * 30.0 - vT * 16.0), 0.025) * 0.55 * step(0.21, q.x) * step(q.x, 0.46);
    float tl = band(sdSeg(q, vec2(-0.24, 0.0), vec2(-0.31, 0.0)), 0.03) * 0.4;
    float eye = blob(q - vec2(0.15, 0.03), 0.03);
    float chl = (blob(q - vec2(-0.12, 0.0), 0.05) + blob(q - vec2(-0.02, -0.01), 0.05) + blob(q - vec2(0.07, 0.01), 0.04)) * 0.4;
    float nuc = blob(q - vec2(-0.07, 0.0), 0.035);
    a = skin(d) + fl + tl + eye + chl + nuc * 0.5;
    col = vColor * 1.1 + vFlash * 0.5;
    col = mix(col, vec3(1.0, 0.2, 0.15), eye * 0.9);
  } else if (k < 11.5) {
    float d = sdEll(q, vec2(0.30, 0.25));
    float vac = band(length(q - vec2(-0.04, 0.03)) - 0.09, 0.035) * 0.5;
    float gran = blob(q - vec2(0.09, -0.08), 0.03) + blob(q - vec2(-0.15, -0.08), 0.025);
    float scar = band(length(q - vec2(0.29, 0.0)) - 0.03, 0.018) * 0.5;
    a = skin(d) + band(d + 0.04, 0.03) * 0.25 + vac + gran * 0.5 + scar;
    col = vColor * 1.1 + vFlash * 0.5;
  } else if (k < 12.5) {
    float d = length(q) - 0.18;
    float gird = band(q.x - 0.03 * sin(q.y * 20.0), 0.03) * clamp(-d * 15.0, 0.0, 1.0) * 0.55;
    float lf = band(q.y - 0.045 * sin(q.x * 36.0 - vT * 15.0), 0.03) * 0.5 * step(q.x, -0.18) * step(-0.46, q.x);
    float plates = clamp(-d * 10.0, 0.0, 1.0) * (0.5 + 0.5 * sin(q.y * 38.0)) * 0.12;
    float glow = exp(-5.0 * r * r) * (0.18 + 0.9 * vFlash);
    a = skin(d) + gird + lf + plates + glow;
    col = mix(vColor * 1.1, vec3(0.4, 0.8, 1.0), clamp(vFlash, 0.0, 1.0)) + vFlash * 0.4;
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
