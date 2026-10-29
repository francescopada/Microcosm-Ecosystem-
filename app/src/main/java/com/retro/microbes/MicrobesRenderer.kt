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
import kotlin.math.sqrt
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
private val SIZE   = floatArrayOf(30f,   32f,    56f,    92f,    38f,    22f,    90f,    84f,    110f,   96f,    58f,    34f,    44f).also { for (i in it.indices) it[i] *= 0.75f } // scala microscopio
// velocita' di crociera in lunghezze-corpo/s (reali: batteri ~10, paramecio ~8, rotifero ~2, euglena ~1, diatomea ~0.5,
// tardigrado ~0.1, ameba ~0.01, alga/lievito/virus solo moto browniano); sullo schermo e' compressa con esponente 0.55
private val CRUISE = floatArrayOf(8f,    0.15f,  0.4f,   2f,     3f,     0.8f,   6f,     0.12f,  0.012f, 8f,     1.2f,   0.05f,  5f)
private val SPD55  = FloatArray(13) { Math.pow(CRUISE[it].toDouble(), 0.55).toFloat() }
private val SIGHT  = floatArrayOf(0.30f,  0f,     0f,     0.22f,  0.25f,  0f,  0.30f,  0.20f,  0.15f,  0.20f,  0.15f,  0.12f,  0f)
private val DECAY  = floatArrayOf(0.004f, 0.004f, 0.015f, 0.020f, 0.020f, 0.015f, 0.008f, 0.010f, 0.010f, 0.014f, 0.008f, 0.007f, 0.004f)
private val GAIN   = floatArrayOf(0.20f,  0f,     0f,     0.40f,  0.50f,  0f,     0.30f,  0.30f,  0.35f,  0.22f,  0.12f,  0.15f,  0f)
private val SPLIT  = floatArrayOf(1.0f,   1.0f,   1.0f,   1.1f,   1.0f,   99f,    1.2f,   1.1f,   1.1f,   1.0f,   1.0f,   1.0f,   1.0f)
// tempo di generazione minimo in secondi (batteri < lieviti < alghe/paramecio < euglena < rotiferi < amebe < Didinium < tardigradi)
private val CYCLE  = floatArrayOf(10f,   30f,    40f,    45f,    14f,    0f,     70f,    120f,   60f,    30f,    40f,    25f,    35f)
// durata della vita in secondi di gioco (virioni breve, batteri/alghe breve, tardigradi molto lunga)
private val LIFE   = floatArrayOf(300f,  420f,    500f,   800f,   300f,   150f,    600f,   2400f,  1500f,  900f,   500f,   360f,   450f)
// pausa del predatore mentre ingerisce (s) e posizione della bocca come frazione della lunghezza
// fagocitosi: durata della cattura, della digestione e pausa del predatore (s)
//                                  micr  alga  diat  roti  spir  virus macro tardi amoe  para  eugl  yeast plank
private val CAPT   = floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 2.6f, 2.0f, 3.2f, 1.4f, 1.0f, 1.0f, 1.0f)
private val DIG    = floatArrayOf(6.0f, 6.0f, 6.0f, 6.0f, 6.0f, 6.0f, 12f,  8.0f, 9.0f, 7.0f, 6.0f, 6.0f, 6.0f)
private val EATPAUSE = floatArrayOf(0.3f, 0.3f, 0.3f, 0.4f, 0.3f, 0.3f, 2.6f, 2.0f, 3.2f, 0.5f, 0.3f, 0.3f, 0.3f)
// divisione cellulare: durata (s) e semi-lunghezza del corpo (frazione dello sprite)
private val DIVT   = floatArrayOf(1.8f, 4.5f, 4.5f, 1.6f, 2.5f, 1.0f, 6.0f, 6.0f, 6.5f, 4.5f, 4.5f, 6.0f, 4.5f)
private val DIVE   = floatArrayOf(0.22f, 0.30f, 0.44f, 0.28f, 0.38f, 0.20f, 0.26f, 0.30f, 0.34f, 0.28f, 0.24f, 0.30f, 0.19f)
private const val NUPT = 0.5f // nutrienti consumati per unita' di energia fotosintetica
private const val SPEEDK = 0.11f // scala globale delle velocita' sullo schermo
private const val EE = 1.0f   // espulsione dei residui
private val MAXP   = intArrayOf(220, 96, 36, 5, 16, 45, 3, 3, 2, 8, 24, 32, 44)
// livello trofico: i predatori in cima alla catena sono piu' lenti (e meno numerosi)
private val TROPH  = floatArrayOf(1.0f, 1.0f, 1.0f, 0.70f, 1.0f, 1.0f, 0.50f, 0.85f, 0.80f, 0.75f, 1.0f, 1.0f, 1.0f)
private val PRED   = booleanArrayOf(false, false, false, true, false, false, true, true, true, true, false, false, false)
// probabilita' di cattura per tentativo (un attacco fallito costa energia e la preda scatta via)
private val CATCH  = floatArrayOf(1f, 1f, 1f, 0.50f, 1f, 1f, 0.55f, 0.60f, 0.45f, 0.50f, 1f, 1f, 1f)
private val MINP   = intArrayOf(90, 32, 10, 1, 5, 0, 0, 1, 0, 2, 6, 10, 10)
// il paramecio sfugge a Didinium (reazione di evitamento); gli altri non hanno predatori "percepiti"
private val THREAT = intArrayOf(-1, -1, -1, -1, -1, -1, -1, -1, -1, MACRO, ROTIFER, -1, ROTIFER)
// riproduzione sessuata con meiosi (paramecio e Didinium: coniugazione; rotifero: partenogenesi)
private val REFUGE = IntArray(13) { maxOf(3, MAXP[it] / 5) } // sotto questa soglia la specie trova rifugio e non viene predata
// raggio del corpo (frazione della dimensione) usato per evitare che gli organismi si sovrappongano
private val BODYR  = floatArrayOf(0.20f, 0.26f, 0.34f, 0.30f, 0.30f, 0.20f, 0.26f, 0.26f, 0.28f, 0.24f, 0.22f, 0.26f, 0.17f)
// chi mangia chi (per esentare dalla repulsione i predatori affamati e i fagi)
private val PREYREL = Array(13) { BooleanArray(13) }.also {
    for (t in intArrayOf(ALGA, DIATOM, YEAST, PLANKTON, MICROBE)) it[ROTIFER][t] = true
    it[TARDI][ALGA] = true; it[TARDI][DIATOM] = true
    it[MACRO][PARAM] = true
    it[VIRUS][MICROBE] = true
    for (t in intArrayOf(ALGA, YEAST, EUGLENA, MICROBE)) it[AMOEBA][t] = true
    it[PARAM][YEAST] = true; it[PARAM][MICROBE] = true
}
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
    ROTIFER -> floatArrayOf(0.85f, 0.82f, 1.0f)
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
        var attT = 0f        // tempo dall'adsorbimento del fago
        var tumble = 0f      // durata residua della virata (corsa e capriola)
        var hTarget = 0f
        var curv = 0f        // curvatura istantanea della traiettoria (-1..1), per la flessione del corpo
        var escape = 0f      // scatto di fuga dopo un attacco fallito
        var sc = 1f          // scala corporea (i nati crescono)
        var feed = 0f        // intensita' dell'atto di alimentazione (per lo shader)
        var feedAng = 0f
        var host: Org? = null   // fago adsorbito su una cellula
        var attAng = 0f
        val ph = Random.nextFloat() * 6.2832f
        var maxAge = 600f
    }
    private class Ghost(val x: Float, val y: Float, val angle: Float, val size: Float) { var t = 0f }
    private class Food(var x: Float, var y: Float, val phase: Float, var vx: Float = 0f, var vy: Float = 0f) { var life = 30f + Random.nextFloat() * 30f }
    private class Dust(val bx: Float, val by: Float, val size: Float, val par: Float,
                       val color: FloatArray, val alpha: Float, val ph: Float)

    private val orgs = ArrayList<Org>()
    private val born = ArrayList<Org>()
    // preda in coordinate locali dell'ospite (frazioni del corpo): la segue mentre nuota
    private class Engulf(val host: Org, val kind: Int, val color: FloatArray, val lx0: Float, val ly0: Float, val size: Float, val rot: Float) {
        var t = 0f
        var shell = false
    }
    private val engulfs = ArrayList<Engulf>()
    private class Egg(val parent: Org, val color: FloatArray) { var x = 0f; var y = 0f; var t = 0f; var carried = true }
    private val eggs = ArrayList<Egg>()
    private class Fusion(val a: Org, val b: Org, val conj: Boolean) { var t = 0f }
    private val fusions = ArrayList<Fusion>()
    private val tmpC = FloatArray(3)
    private val tmpP = FloatArray(2)
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
    private var nutr = 60f // nutrienti disciolti (azoto/fosforo): limitano i produttori e tornano dalla decomposizione
    private var driftX = 0f
    private var driftY = 0f
    private var moveCd = 0f
    private var maxPoint = 64f
    private var tx = 0f; private var ty = 0f; private var tz = 0f; private var tw = 0f

    private val buf: FloatBuffer = ByteBuffer.allocateDirect(32768 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
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
        orgs.clear(); engulfs.clear(); eggs.clear(); fusions.clear(); born.clear(); ghosts.clear(); foods.clear(); dusts.clear()
        val worldW = w * 1.6f
        fun seed(kind: Int, c: Int) = repeat(c) { orgs.add(newOrg(kind, Random.nextFloat() * worldW, Random.nextFloat() * h)) }
        seed(ALGA, 44); seed(MICROBE, 120); seed(DIATOM, 16); seed(SPIRILLUM, 7)
        seed(ROTIFER, 2); seed(MACRO, 1); seed(TARDI, 2)
        seed(PARAM, 3); seed(EUGLENA, 12); seed(YEAST, 14); seed(PLANKTON, 16)
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
    private fun addBorn(kind: Int, x: Float, y: Float, energy: Float, p1: Org? = null, p2: Org? = null): Org? {
        if (counts[kind] >= MAXP[kind]) return null
        counts[kind]++
        val o = newOrg(kind, x, y)
        o.energy = energy
        o.age = 0f
        o.sc = 0.65f
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
        return o
    }

    private fun wantsEat(o: Org): Boolean = o.kind == VIRUS || (PRED[o.kind] && o.energy < 0.8f)

    // i corpi non si sovrappongono: si respingono dolcemente, tranne il predatore affamato verso la sua preda e il fago verso il batterio
    private fun separate(dt: Float) {
        val n = orgs.size
        val rate = minOf(1f, 6f * dt)
        for (i in 0 until n) {
            val a = orgs[i]
            if (a.dead || (a.kind == VIRUS && a.host != null)) continue
            val ra = BODYR[a.kind] * a.size * a.sc * px
            for (j in i + 1 until n) {
                val b = orgs[j]
                if (b.dead || (b.kind == VIRUS && b.host != null)) continue
                val rb = BODYR[b.kind] * b.size * b.sc * px
                val minD = ra + rb
                val dx = b.x - a.x
                if (dx > minD || dx < -minD) continue
                val dy = b.y - a.y
                if (dy > minD || dy < -minD) continue
                val d2 = dx * dx + dy * dy
                if (d2 >= minD * minD || d2 < 1e-4f) continue
                if ((PREYREL[a.kind][b.kind] && wantsEat(a)) || (PREYREL[b.kind][a.kind] && wantsEat(b))) continue
                if (a.mate === b || b.mate === a) continue
                val d = sqrt(d2)
                val ma = ra * ra; val mb = rb * rb
                val move = (minD - d) * rate
                val nx = dx / d; val ny = dy / d
                val fa = mb / (ma + mb)
                a.x -= nx * move * fa; a.y -= ny * move * fa
                b.x += nx * move * (1f - fa); b.y += ny * move * (1f - fa)
            }
        }
    }

    private fun smooth(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun addEngulf(host: Org, prey: Org) {
        if (engulfs.size >= 60) return
        val sh = host.size * px * host.sc
        val hc = cos(host.heading); val hs = sin(host.heading)
        val dx = prey.x - host.x; val dy = prey.y - host.y
        engulfs.add(Engulf(host, prey.kind, prey.color, (dx * hc + dy * hs) / sh, (-dx * hs + dy * hc) / sh, prey.size, prey.heading - host.heading))
        host.eatT = EATPAUSE[host.kind]
    }

    private fun startDivision(o: Org) {
        if (counts[o.kind] >= MAXP[o.kind]) return
        o.divT = 0.01f
    }

    // La cellula si e' allungata di 1.8 volte: le due figlie occupano i due lobi e hanno gia' la dimensione giusta (0.9)
    private fun finishDivision(o: Org) {
        o.divT = 0f
        o.energy *= 0.5f
        o.cool = CYCLE[o.kind] * (0.8f + 0.4f * Random.nextFloat())
        val a = o.heading
        if (o.kind == YEAST) {
            val d = o.size * px * o.sc * 0.55f
            addBorn(YEAST, o.x + cos(a) * d, o.y + sin(a) * d, o.energy, o)
        } else {
            val off = 0.9f * DIVE[o.kind] * o.size * px * o.sc
            val child = addBorn(o.kind, o.x + cos(a) * off, o.y + sin(a) * off, o.energy, o)
            if (child != null) { child.sc = 0.9f; child.heading = a + (Random.nextFloat() - 0.5f) * 0.5f }
            o.x -= cos(a) * off; o.y -= sin(a) * off
            o.sc = 0.9f
        }
    }

    // partenogenesi del rotifero: l'uovo viene portato sul corpo, poi deposto, poi si schiude
    private fun layEgg(o: Org) {
        if (eggs.size >= 24) return
        val g = Egg(o, o.color)
        g.x = o.x; g.y = o.y
        o.flash = 1f
        eggs.add(g)
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
        if (counts[t.kind] <= REFUGE[t.kind]) return false
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
        if (o.kind != VIRUS) nutr = minOf(100f, nutr + 3f) // la decomposizione restituisce i nutrienti
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
        o.feed = 0f
        if (o.sc < 1f) o.sc = (o.sc + 0.012f * dt).coerceAtMost(1f)
        o.flash = (o.flash - dt * 2.5f).coerceAtLeast(0f)
        o.curv *= (1f - 3f * dt).coerceAtLeast(0f)
        o.cool = (o.cool - dt).coerceAtLeast(0f)
        val k = o.kind

        if (k == VIRUS) {
            val hh = o.host
            if (hh != null) {
                // capside gia' adsorbito: resta attaccato alla cellula (ha iniettato il DNA) fino alla lisi
                if (hh.dead || hh.state != 1) { o.dead = true; return }
                // la guaina si contrae e la testa si avvicina alla cellula mentre il DNA viene iniettato
                o.attT += dt
                o.feed = (o.attT / 3f).coerceAtMost(1f)
                val rr = BODYR[hh.kind] * hh.size * hh.sc * px + o.size * px * (0.40f - 0.08f * smooth(0.1f, 0.5f, o.feed))
                o.x = hh.x + cos(o.attAng) * rr
                o.y = hh.y + sin(o.attAng) * rr
                o.heading = o.attAng
                return
            }
            // adsorbimento per contatto casuale con un batterio
            for (q in orgs) {
                if (q.kind != MICROBE || q.dead || q.state == 3) continue
                if (hypot(q.x - o.x, q.y - o.y) < (o.size + q.size) * 0.25f * px && Random.nextFloat() < 6f * dt) {
                    if (q.state == 0) { q.state = 1; q.timer = 12f }
                    o.host = q
                    o.attAng = atan2(o.y - q.y, o.x - q.x)
                    break
                }
            }
        }
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
            if (o.timer <= 0f) {
                o.dead = true
                if (ghosts.size < 40) ghosts.add(Ghost(o.x, o.y, -o.heading, o.size))
                repeat(8) { addBorn(VIRUS, o.x, o.y, 0.6f) }
                return
            }
        }

        val nf = nutr / (nutr + 25f) // limitazione da nutrienti (cinetica di Monod)
        when (k) {
            ALGA -> {
                val g = 0.05f * light * nf * dt
                o.energy += g; nutr -= g * NUPT
                if (foods.size < 200 && Random.nextFloat() < 0.03f * nf * dt) foods.add(Food(o.x, o.y, Random.nextFloat())) // essudati
            }
            EUGLENA -> { val g = 0.035f * light * nf * dt; o.energy += g; nutr -= g * NUPT }
            DIATOM -> { val g = 0.04f * light * nf * dt; o.energy += g; nutr -= g * NUPT }
            PLANKTON -> { val g = 0.04f * light * nf * dt; o.energy += g; nutr -= g * NUPT }
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
            if (o.divT >= DIVT[k]) finishDivision(o)
            return
        }

        // meiosi (o coniugazione) in corso
        val m = o.mate
        if (m != null) {
            if (m.dead) { o.mate = null; o.leader = false }
            else {
                o.meiT -= dt
                o.speed *= (1f - 4f * dt).coerceAtLeast(0f)
                // i due partner si avvicinano e si dispongono affiancati
                val dd = hypot(m.x - o.x, m.y - o.y)
                val want = (o.size + m.size) * 0.32f * px
                if (dd > want && dd > 1f) {
                    val mv = minOf((dd - want) * 1.5f * dt, 30f * dt)
                    o.x += (m.x - o.x) / dd * mv; o.y += (m.y - o.y) / dd * mv
                }
                o.heading += angDiff(atan2(m.y - o.y, m.x - o.x) + PIF / 2f, o.heading).coerceIn(-2f * dt, 2f * dt)
                o.flash = 0.25f + 0.25f * sin(time * 10f)
                if (o.meiT <= 0f) {
                    if (k == PARAM || k == MACRO) {
                        if (o.leader) { val sp = (o.spd + m.spd) / 2f; o.spd = sp; m.spd = sp }
                        o.energy = (o.energy + 0.1f).coerceAtMost(1.3f)
                    } else {
                        if (o.leader) addBorn(k, (o.x + m.x) / 2f, (o.y + m.y) / 2f, 0.45f, o, m)?.sc = 0.5f
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
                    o.meiT = 3f; cand.meiT = 3f
                    fusions.add(Fusion(o, cand, k == PARAM || k == MACRO))
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
        val canHunt = !PRED[k] || o.energy < 0.8f // un predatore sazio smette di cacciare
        if (SIGHT[k] > 0f) {
            for (q in orgs) {
                if (!canHunt) break
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
        if (fleeing && k == PLANKTON) o.flash = 1f // bioluminescenza di allarme

        val h0 = o.heading
        val base = SPEEDK * o.size * px * SPD55[k] * TROPH[k] // px/s dalla velocita' in lunghezze-corpo/s
        val targetSpeed: Float
        if (fleeing) {
            o.heading += angDiff(fa, o.heading).coerceIn(-5f * dt, 5f * dt)
            targetSpeed = base * 1.8f
        } else if (o.escape > 0f) {
            o.escape -= dt
            targetSpeed = base * 2.2f
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
                MICROBE, SPIRILLUM -> {
                    if (o.tumble <= 0f && Random.nextFloat() < dt) { o.hTarget = o.heading + (Random.nextFloat() - 0.5f) * 3f; o.tumble = 0.6f }
                    if (o.tumble > 0f) { o.tumble -= dt; o.heading += angDiff(o.hTarget, o.heading).coerceIn(-5f * dt, 5f * dt) }
                }
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
        if (dt > 1e-4f) {
            val tr = (angDiff(o.heading, h0) / dt / 1.2f).coerceIn(-1f, 1f)
            o.curv += (tr - o.curv) * 6f * dt
        }
        o.x += (cos(o.heading) * o.speed + driftX) * dt
        o.y += (sin(o.heading) * o.speed + driftY) * dt
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
                    if (Random.nextFloat() > CATCH[k]) {
                        // tentativo fallito: la preda scatta via
                        o.eatT = 0.6f
                        o.energy = (o.energy - 0.02f).coerceAtLeast(0.05f)
                        tOrg.escape = 1.2f
                        tOrg.heading = atan2(tOrg.y - o.y, tOrg.x - o.x)
                        tOrg.flash = 1f
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
                    layEgg(o)
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
        nutr = (nutr + (1f - 0.01f * nutr) * dt).coerceIn(0f, 100f) // apporto dall'esterno e diluizione
        driftX = 4f * cos(time * 0.05f); driftY = 3f * sin(time * 0.037f) // lenta corrente del vetrino
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
        if (foodTimer > 0.7f && foods.size < 140) { // detrito che arriva dai bordi del vetrino
            foodTimer = 0f
            foods.add(Food(offscreenX(), Random.nextFloat() * h, Random.nextFloat()))
        }
        for (f in foods) {
            f.x += (f.vx + driftX) * dt; f.y += (f.vy + driftY) * dt
            val kk = 1f - 2.2f * dt
            f.vx *= kk; f.vy *= kk
            f.life -= dt
        }
        val nf0 = foods.size
        foods.removeAll { it.life <= 0f }
        nutr = minOf(100f, nutr + (nf0 - foods.size) * 1f) // il detrito non mangiato si mineralizza

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

        // fagocitosi: segnale per lo shader dell'ospite, spoglia del tardigrado, espulsione dei residui
        val ei = engulfs.iterator()
        while (ei.hasNext()) {
            val e = ei.next()
            val ho = e.host
            e.t += dt
            if (ho.dead) { ei.remove(); continue }
            val cap = CAPT[ho.kind]; val dig = DIG[ho.kind]
            var f = 0f
            if (e.t < cap) {
                val u = e.t / cap
                f = when (ho.kind) {
                    AMOEBA -> smooth(0f, 0.8f, u)
                    MACRO -> smooth(0f, 0.4f, u)
                    TARDI -> smooth(0f, 0.5f, u)
                    else -> u
                }
                ho.feedAng = atan2(e.ly0, e.lx0)
            } else if (e.t < cap + dig) {
                val v = (e.t - cap) / dig
                f = when (ho.kind) {
                    AMOEBA -> (1f - (e.t - cap) / 0.6f).coerceAtLeast(0f)
                    MACRO -> 1f - 0.7f * v
                    TARDI -> (1f - (e.t - cap) / 0.5f).coerceAtLeast(0f)
                    else -> 0f
                }
            }
            if (f > ho.feed) ho.feed = f
            val sh = ho.size * px * ho.sc
            val hc = cos(ho.heading); val hs = sin(ho.heading)
            if (ho.kind == TARDI && !e.shell && e.t >= cap) {
                // il tardigrado ha succhiato il contenuto: resta il guscio vuoto
                e.shell = true
                ghosts.add(Ghost(ho.x + (hc * e.lx0 - hs * e.ly0) * sh, ho.y + (hs * e.lx0 + hc * e.ly0) * sh, -(ho.heading + e.rot), e.size))
            }
            if (e.t >= cap + dig + EE) {
                ei.remove()
                val rx = ho.x - hc * 0.34f * sh
                val ry = ho.y - hs * 0.34f * sh
                nutr = minOf(100f, nutr + 0.5f)
                if (foods.size < 250) repeat(2) {
                    foods.add(Food(rx, ry, Random.nextFloat(), -hc * 14f + (Random.nextFloat() - 0.5f) * 10f, -hs * 14f + (Random.nextFloat() - 0.5f) * 10f))
                }
            }
        }

        separate(dt)

        // uova del rotifero
        val gi2 = eggs.iterator()
        while (gi2.hasNext()) {
            val g = gi2.next()
            val ho = g.parent
            g.t += dt
            if (g.carried) {
                if (g.t > 4f || ho.dead) g.carried = false
                else {
                    val sh = ho.size * px * ho.sc
                    val hc = cos(ho.heading); val hs = sin(ho.heading)
                    g.x = ho.x + (-0.30f * hc - 0.08f * hs) * sh
                    g.y = ho.y + (-0.30f * hs + 0.08f * hc) * sh
                }
            }
            if (g.t >= 9f) {
                gi2.remove()
                addBorn(ROTIFER, g.x, g.y, 0.35f, ho)?.let { it.sc = 0.5f; it.flash = 1f }
            }
        }

        // fusione dei gameti / coniugazione
        val fi = fusions.iterator()
        while (fi.hasNext()) {
            val fu = fi.next()
            fu.t += dt
            if (fu.a.dead || fu.b.dead || fu.t >= 3f) fi.remove()
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

    // posizione del vacuolo digerente in coordinate locali dell'ospite
    private fun digestPath(kind: Int, v: Float, t: Float, mx: Float, my: Float) {
        var lx = 0f; var ly = 0f
        when (kind) {
            PARAM -> { // ciclosi: il vacuolo gira nel citoplasma e arriva al citoprocto posteriore
                val ph0 = 0.65f
                val phi = ph0 + (PIF - ph0 + 2f * PIF) * v
                lx = 0.17f * cos(phi); ly = 0.05f * sin(phi)
            }
            ROTIFER -> { // mastax che tritura, poi intestino
                if (v < 0.1f) { val s = smooth(0f, 0.1f, v); lx = mx + (0.07f - mx) * s; ly = my * (1f - s) }
                else if (v < 0.3f) { lx = 0.07f + 0.012f * sin(t * 40f); ly = 0.012f * cos(t * 37f) }
                else { lx = 0.07f - 0.28f * smooth(0.3f, 1f, v); ly = 0f }
            }
            AMOEBA -> { // correnti citoplasmatiche
                val s = smooth(0f, 0.25f, v)
                val orb = smooth(0.25f, 0.6f, v)
                lx = mx + (-0.02f - mx) * s + 0.05f * cos(PIF * 3f * v) * orb - 0.2f * smooth(0.6f, 1f, v)
                ly = my * (1f - s) + 0.05f * sin(PIF * 3f * v) * orb
            }
            MACRO -> { lx = -0.05f - 0.15f * smooth(0.6f, 1f, v); ly = 0.01f * sin(t * 3f) }
            else -> { lx = mx + (-0.03f - mx) * smooth(0f, 0.3f, v) - 0.19f * smooth(0.6f, 1f, v); ly = 0f }
        }
        tmpP[0] = lx; tmpP[1] = ly
    }

    private fun drawEngulf(e: Engulf) {
        val ho = e.host
        val sh = ho.size * px * ho.sc
        val hc = cos(ho.heading); val hs = sin(ho.heading)
        val cap = CAPT[ho.kind]; val dig = DIG[ho.kind]
        val t = e.t
        val st = if (e.kind == MICROBE) 0.6f else 0f
        val ang = -(ho.heading + e.rot)
        val a0 = atan2(e.ly0, e.lx0)
        val mx: Float; val my: Float
        when (ho.kind) {
            AMOEBA -> { mx = 0.14f * cos(a0); my = 0.14f * sin(a0) }
            PARAM -> { mx = 0.18f; my = 0.04f }
            ROTIFER -> { mx = 0.30f; my = 0f }
            MACRO -> { mx = 0.33f; my = 0f }
            TARDI -> { mx = 0.29f; my = 0f }
            else -> { mx = 0.2f; my = 0f }
        }
        var lx = e.lx0; var ly = e.ly0
        var ps = e.size
        var ring = 0f
        var alpha = 0.7f
        var v = 0f
        if (t < cap) {
            val u = t / cap
            when (ho.kind) {
                AMOEBA -> { // gli pseudopodi si chiudono attorno alla preda, poi la tirano dentro
                    val s = smooth(0.55f, 1f, u)
                    lx = e.lx0 + (mx - e.lx0) * s + 0.012f * sin(t * 18f) * (1f - u)
                    ly = e.ly0 + (my - e.ly0) * s + 0.012f * cos(t * 21f) * (1f - u)
                    ps = e.size * (1f - 0.15f * s)
                }
                MACRO -> { // arpione: preda paralizzata, poi ingoiata intera
                    val s = smooth(0.35f, 0.75f, u)
                    val s2 = smooth(0.75f, 1f, u)
                    lx = e.lx0 + (mx - e.lx0) * s + (-0.05f - mx) * s2
                    ly = e.ly0 * (1f - s)
                    ps = e.size * (1f - 0.1f * s2)
                }
                TARDI -> { // stiletti: il contenuto viene succhiato, la cellula si svuota
                    lx = e.lx0 + 0.006f * sin(t * 25f); ly = e.ly0
                    ps = e.size * (1f - 0.7f * smooth(0.2f, 1f, u))
                }
                else -> { // le ciglia trascinano la preda verso la bocca lungo una curva
                    val s = u * u * (1.2f - 0.2f * u)
                    val dx = mx - e.lx0; val dy = my - e.ly0
                    val arc = sin(u * PIF) * 0.35f
                    lx = e.lx0 + dx * s - dy * arc; ly = e.ly0 + dy * s + dx * arc
                    ps = e.size * (1f - 0.25f * s)
                }
            }
            if (ho.kind == AMOEBA && u > 0.85f) { ring = e.size * 0.9f; alpha = 0.7f * smooth(0.85f, 1f, u) }
        } else if (t < cap + dig) {
            v = (t - cap) / dig
            val f0 = when (ho.kind) { MACRO -> 0.9f; TARDI -> 0.3f; else -> 0.75f }
            digestPath(ho.kind, v, t, mx, my)
            lx = tmpP[0]; ly = tmpP[1]
            ps = e.size * f0 * (1f - 0.9f * v)
            ring = e.size * f0 * 1.25f * (1f - 0.5f * v) + 4f
        } else {
            val w = (t - cap - dig) / EE
            digestPath(ho.kind, 1f, t, mx, my)
            lx = tmpP[0] + (-0.36f - tmpP[0]) * w; ly = tmpP[1] * (1f - w)
            ps = 0f
            ring = e.size * 0.45f * (1f - 0.6f * w) + 3f
            alpha = 0.7f * (1f - w)
            v = 1f
        }
        val x = ho.x + (hc * lx - hs * ly) * sh
        val y = ho.y + (hs * lx + hc * ly) * sh
        if (ring > 0f) put(x, y, 0f, ring, GHOST, alpha, 0f, WHITE)
        if (ps > 2f) {
            // la preda si scurisce man mano che viene digerita
            tmpC[0] = e.color[0] * (1f - 0.5f * v); tmpC[1] = e.color[1] * (1f - 0.5f * v); tmpC[2] = e.color[2] * (1f - 0.5f * v)
            put(x, y, ang, ps, e.kind, st, if (t < cap) 0.3f else 0f, tmpC)
        }
    }

    private fun drawEgg(g: Egg) {
        val u = (g.t / 9f).coerceIn(0f, 1f)
        val hatch = smooth(0.88f, 1f, u)
        put(g.x, g.y, 0f, 34f, GHOST, 0.8f * (1f - hatch), 0f, WHITE)
        val wig = 1.5f * u * sin(g.t * 8f)
        put(g.x + wig, g.y, 0f, 11f + 10f * u, FOOD, 0f, 0f, g.color)
    }

    private fun drawFusion(fu: Fusion) {
        val a = fu.a; val b = fu.b
        val u = (fu.t / 3f).coerceIn(0f, 1f)
        val mx = (a.x + b.x) / 2f; val my = (a.y + b.y) / 2f
        // onda concentrica: segnala che i due individui si stanno riproducendo
        put(mx, my, 0f, 30f + 90f * u, GHOST, 0.45f * (1f - u), 0f, WHITE)
        if (fu.conj) {
            // ponte citoplasmatico e scambio dei micronuclei migratori
            for (i in 0 until 5) {
                val f = (i + 0.5f) / 5f
                put(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, 0f, 8f, FOOD, 0f, 0f, WHITE)
            }
            val s = smooth(0.25f, 0.75f, u)
            put(a.x + (b.x - a.x) * s, a.y + (b.y - a.y) * s, 0f, 12f, FOOD, 0f, 0f, a.color)
            put(b.x + (a.x - b.x) * s, b.y + (a.y - b.y) * s, 0f, 12f, FOOD, 0f, 0f, b.color)
        } else if (u < 0.55f) {
            // i gameti escono dai genitori e si incontrano a meta' strada
            val s = smooth(0.1f, 0.55f, u)
            put(a.x + (mx - a.x) * s, a.y + (my - a.y) * s, 0f, 12f, FOOD, 0f, 0f, a.color)
            put(b.x + (mx - b.x) * s, b.y + (my - b.y) * s, 0f, 12f, FOOD, 0f, 0f, b.color)
        } else {
            // zigote: si forma la membrana e comincia a crescere
            val g = (u - 0.55f) / 0.45f
            put(mx, my, 0f, 16f + 10f * g, GHOST, 0.8f, 0f, WHITE)
            put(mx, my, 0f, 10f + 4f * g, FOOD, 0f, 0f, a.color)
        }
    }

    private fun put(x: Float, y: Float, a: Float, sz: Float, kind: Int, state: Float, flash: Float, c: FloatArray, sp: Float = 0f, ph: Float = 0f, dv: Float = 0f, fd: Float = 0f, fa: Float = 0f, cv: Float = 0f) {
        if ((n + 1) * 16 > buf.capacity()) return
        buf.put(x); buf.put(y); buf.put(a)
        buf.put(sz); buf.put(kind.toFloat()); buf.put(state); buf.put(flash)
        buf.put(c[0]); buf.put(c[1]); buf.put(c[2])
        buf.put(sp); buf.put(ph); buf.put(cv)
        buf.put(dv); buf.put(fd); buf.put(fa)
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
        for (f in foods) put(f.x, f.y, f.phase, 15f, FOOD, 0f, 0f, WHITE)
        for (g in ghosts) put(g.x, g.y, g.angle, g.size, GHOST, 1f - g.t / 14f, 0f, WHITE)
        for (o in orgs) {
            val st = when (o.kind) {
                MICROBE -> if (o.state == 3) -1f else o.energy.coerceIn(0f, 1f)
                TARDI, MACRO -> if (o.state == 2) 1f else 0f
                PARAM -> if (o.state == 1) 1f else 0f
                else -> 0f
            }
            val c = o.color
            val fl = if (o.kind == PLANKTON) o.flash + (1f - light) * 0.4f else o.flash
            val gr = if (o.kind == VIRUS) 1f else o.sc // i nati crescono
            var sz = (if (o.kind == MICROBE && o.state == 3) o.size * 0.55f else o.size) * gr
            if (o.kind == MICROBE && o.state == 1 && o.timer < 3f) sz *= 1f + 0.25f * (1f - o.timer / 3f) // la cellula si gonfia prima della lisi
            val sp = (o.speed / (SPEEDK * 1.6f * o.size * px * SPD55[o.kind] * TROPH[o.kind] + 0.001f)).coerceIn(0f, 1f)
            val szD = sz * (if (o.kind == AMOEBA) 1.25f else 1f)
            if (o.divT > 0f && o.kind == YEAST) {
                // gemmazione: la gemma cresce da una cicatrice del lievito
                val p = (o.divT / DIVT[YEAST]).coerceAtMost(1f)
                put(o.x, o.y, -o.heading, sz, o.kind, st, fl, c, sp, o.ph)
                val d = sz * px * (0.30f + 0.25f * p)
                put(o.x + cos(o.heading) * d, o.y + sin(o.heading) * d, -o.heading, sz * (0.25f + 0.45f * p), o.kind, st, fl, c, sp, o.ph)
            } else if (o.divT > 0f) {
                // mitosi: la cellula si allunga e si strozza (vedi lo shader); lo sprite cresce con l'allungamento
                val u = smooth(0f, 1f, o.divT / DIVT[o.kind])
                put(o.x, o.y, -o.heading, szD * (1f + 0.8f * u), o.kind, st, fl, c, sp, o.ph, u, o.feed, o.feedAng, o.curv)
            } else put(o.x, o.y, -o.heading, szD, o.kind, st, fl, c, sp, o.ph, 0f, o.feed, o.feedAng, o.curv)
        }
        for (g in eggs) drawEgg(g)
        for (fu in fusions) drawFusion(fu)
        for (e in engulfs) drawEngulf(e)

        GLES20.glUseProgram(pSprite)
        GLES20.glUniform4f(GLES20.glGetUniformLocation(pSprite, "uTrans"), tx, ty, tz, tw)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(pSprite, "time"), time)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(pSprite, "uPx"), px)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(pSprite, "uMax"), maxPoint)
        val names = arrayOf("aPos", "aInfo", "aColor", "aExtra", "aEx2")
        val sizes = intArrayOf(3, 4, 3, 3, 3)
        val offs = intArrayOf(0, 3, 7, 10, 13)
        val locs = IntArray(5)
        for (i in 0..4) {
            locs[i] = GLES20.glGetAttribLocation(pSprite, names[i])
            buf.position(offs[i])
            GLES20.glEnableVertexAttribArray(locs[i])
            GLES20.glVertexAttribPointer(locs[i], sizes[i], GLES20.GL_FLOAT, false, 64, buf)
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
  c *= 1.0 - 0.8 * v * v;
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
attribute vec3 aExtra;
varying float vCurv;
attribute vec3 aEx2;
varying float vDiv;
varying float vFeed;
varying float vFAng;
varying float vSpd;
varying float vPh;
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
  vColor = aColor; vT = time; vSpd = aExtra.x; vPh = aExtra.y; vCurv = aExtra.z; vDiv = aEx2.x; vFeed = aEx2.y; vFAng = aEx2.z;
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
varying float vSpd;
varying float vPh;
varying float vDiv;
varying float vFeed;
varying float vFAng;
varying float vCurv;
float sq(float x) { return x * x; }
float band(float d, float w) { return clamp(1.0 - sq(d / w), 0.0, 1.0); }
float blob(vec2 v, float rad) { return clamp(1.0 - length(v) / rad, 0.0, 1.0); }
float sdSeg(vec2 v, vec2 a, vec2 b) { vec2 pa = v - a; vec2 ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }
float sdEll(vec2 v, vec2 ab) { return (length(v / ab) - 1.0) * min(ab.x, ab.y); }
float skin(float d) { return 0.55 * band(d, 0.045) + 0.16 * clamp(-d * 6.0, 0.0, 1.0); }
vec2 ax(float x, float y) { return vec2(x, y + vCurv * 1.5 * x * x); }
float yc(float x) { return vCurv * 1.5 * x * x; }
float legD(vec2 v, float x, float ph) { return min(sdSeg(v, vec2(x, 0.11), vec2(x - 0.03 + ph, 0.19)), sdSeg(v, vec2(x, -0.11), vec2(x - 0.03 - ph, -0.19))); }
void main() {
  vec2 p = gl_PointCoord - 0.5;
  float r = length(p) * 2.0;
  vec2 q = vec2(p.x * vH.x - p.y * vH.y, p.x * vH.y + p.y * vH.x);
  float ang = atan(p.y, p.x);
  vec3 col = vColor;
  float a = 0.0;
  float k = vKind;
  float sp = clamp(vSpd, 0.0, 1.0);
  float r0 = r;
  if (vDiv > 0.0) {
    // mitosi: la cellula si allunga e si strozza al centro (solco di divisione)
    float S = 1.0 + 0.8 * vDiv;
    float pinch = smoothstep(0.3, 1.0, vDiv);
    q = vec2(q.x, q.y * S * (1.0 + 3.0 * pinch * exp(-sq(q.x / 0.11))));
    p = vec2(q.x * vH.x + q.y * vH.y, -q.x * vH.y + q.y * vH.x);
    r = length(p) * 2.0;
    ang = atan(p.y, p.x);
  }
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
    // rotifero bdelloideo: corpo trasparente a fuso con anelli, stomaco, mastax che mastica, due dischi trocali di ciglia che ruotano,
    // macchia oculare rossa e piede con le dita. Da fermo avanza "a bruco" (tronco e piede si accorciano/allungano); nuotando e' rigido.
    float Lx = 1.0 + 0.12 * sin(vT * 2.2 + vPh) * (1.0 - sp);
    float Lf = 1.0 + 0.25 * sin(vT * 2.2 + vPh + 3.14) * (1.0 - sp);
    vec2 b = vec2(q.x, q.y - yc(q.x));
    float hw = mix(0.12, 0.07, smoothstep(0.0, 0.2 * Lx, b.x)) / Lx;
    float d = sdEll(b - vec2(-0.02 * Lx, 0.0), vec2(0.22 * Lx, hw));
    float ann = clamp(-d * 8.0, 0.0, 1.0) * (0.5 + 0.5 * sin(b.x * 60.0)) * 0.10;
    float stom = band(sdEll(b - vec2(-0.06 * Lx, 0.0), vec2(0.10 * Lx, hw * 0.55)), 0.03) * 0.35;
    float jw = 0.004 + 0.012 * (0.5 + 0.5 * sin(vT * 10.0 + vPh)) + 0.012 * vFeed * sin(vT * 30.0);
    float mastax = blob(q - ax(0.07 * Lx, jw), 0.02) + blob(q - ax(0.07 * Lx, -jw), 0.02);
    vec2 hc = ax(0.28 * Lx, 0.0);
    vec2 c1 = q - hc - vec2(0.0, 0.085);
    vec2 c2 = q - hc + vec2(0.0, 0.085);
    float cil = 0.0;
    for (int i = 0; i < 8; i++) {
      float an = float(i) * 0.785398 + vT * 7.0;
      vec2 o1 = 0.06 * vec2(cos(an), sin(an));
      cil += blob(c1 - o1, 0.022) + blob(c2 - vec2(o1.x, -o1.y), 0.022);
    }
    float discs = (band(length(c1) - 0.06, 0.03) + band(length(c2) - 0.06, 0.03)) * 0.25;
    float neck = band(sdSeg(q, ax(0.17 * Lx, 0.0), ax(0.26 * Lx, 0.0)), 0.03) * 0.35;
    vec2 fe = ax(-0.40 * Lf, 0.0);
    float foot = band(sdSeg(q, ax(-0.22 * Lx, 0.0), fe), 0.03) * 0.5;
    float toes = band(min(sdSeg(q, fe, fe + vec2(-0.06, 0.04)), sdSeg(q, fe, fe + vec2(-0.06, -0.04))), 0.02) * 0.5;
    float eye = blob(q - ax(0.20 * Lx, 0.0), 0.025);
    a = skin(d) + ann + stom + mastax * 0.8 + cil * 0.7 + discs + neck + foot + toes + eye;
    col = vColor * 1.1 + mastax * 0.2 + vFlash * 0.5;
    col = mix(col, vec3(1.0, 0.25, 0.2), eye * 0.8);
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
    // batteriofago: testa icosaedrica, guaina, piastra basale, zampe. vFeed = fase di iniezione: la guaina si contrae, il DNA scorre nella coda
    float inj = vFeed;
    float cx = 0.08 * smoothstep(0.1, 0.5, inj);
    vec2 hp = abs(q - vec2(0.15, 0.0));
    float hexd = max(hp.x * 0.866 + hp.y * 0.5, hp.y) - 0.11;
    float tail = band(sdSeg(q, vec2(0.04, 0.0), vec2(-0.22 + cx, 0.0)), 0.03) * (0.55 + 0.25 * sin(q.x * 60.0 * (1.0 + 1.2 * smoothstep(0.1, 0.5, inj))));
    float base = band(sdSeg(q, vec2(-0.24 + cx, 0.05), vec2(-0.24 + cx, -0.05)), 0.025) * 0.6;
    float legs = band(min(sdSeg(q, vec2(-0.24 + cx, 0.0), vec2(-0.36 + cx, 0.11)), min(sdSeg(q, vec2(-0.24 + cx, 0.0), vec2(-0.36 + cx, -0.11)), sdSeg(q, vec2(-0.24 + cx, 0.0), vec2(-0.40 + cx, 0.0)))), 0.02) * 0.5;
    float dnaFull = 1.0 - smoothstep(0.1, 0.9, inj);
    float dn = blob(q - vec2(0.10 - 0.34 * smoothstep(0.25, 1.0, inj), 0.0), 0.035) * step(0.25, inj) * (1.0 - smoothstep(0.9, 1.0, inj));
    a = skin(hexd) + blob(q - vec2(0.15, 0.0), 0.09) * 0.35 * dnaFull + tail + base + legs + dn * 0.9;
    col = vColor * 1.1 + (sin(vT * 18.0) * 0.3 + 0.3) * 0.3 + vFlash * 0.5;
  } else if (k < 6.5) {
    if (vState > 0.5) {
      float tr = clamp(1.0 - sq((r - 0.5) * 16.0), 0.0, 0.95);
      float tc = clamp((0.3 - r) * 10.0, 0.0, 0.6);
      col = vec3(0.98, 0.82, 0.25) + tc * 0.3;
      a = tr + tc;
    } else {
      vec2 b = q - vec2(-0.05, 0.0);
      float d = sdEll(b, vec2(0.21 + 0.10 * vFeed, 0.17 + 0.09 * vFeed));
      float m1 = clamp(1.0 - sq(q.y / 0.17), 0.0, 1.0);
      float g1 = band(q.x + 0.10 + 0.5 * q.y * q.y, 0.03) * m1 * (0.6 + 0.4 * sin(q.y * 40.0 - vT * (6.0 + 16.0 * sp)));
      float g2 = band(q.x - 0.02 + 0.5 * q.y * q.y, 0.03) * m1 * (0.6 + 0.4 * sin(q.y * 40.0 + vT * (6.0 + 16.0 * sp)));
      float probo = band(sdSeg(q, vec2(0.14, 0.0), vec2(0.31, 0.0)), 0.045 + 0.03 * vFeed) * 0.6;
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
      // il corpo molle si flette in curva e pulsa con il passo; zampe, artigli, testa e bocca sono rigidi: traslano, non si deformano
      float gait = sp * sin(vT * 4.0 + vPh);
      vec2 b = vec2(q.x, q.y - yc(q.x));
      float d = sdEll(b, vec2(0.27, 0.14 * (1.0 + 0.07 * gait)));
      float w1 = 0.022 * sp * sin(vT * 4.0 + vPh);
      float w2 = 0.022 * sp * sin(vT * 4.0 + vPh + 1.57);
      float w3 = 0.022 * sp * sin(vT * 4.0 + vPh + 3.14);
      float w4 = 0.022 * sp * sin(vT * 4.0 + vPh + 4.71);
      float lg = min(min(legD(q - vec2(0.0, yc(-0.17)), -0.17, w1), legD(q - vec2(0.0, yc(-0.06)), -0.06, w2)),
                     min(legD(q - vec2(0.0, yc(0.05)), 0.05, w3), legD(q - vec2(0.0, yc(0.16)), 0.16, w4)));
      float seg = (0.5 + 0.5 * sin(b.x * 34.0)) * 0.10 * clamp(-d * 8.0, 0.0, 1.0);
      vec2 hd = ax(0.26, 0.0);
      float head = band(sdEll(q - hd, vec2(0.06, 0.075)), 0.04) * 0.4;
      float mouth = blob(q - ax(0.29, 0.0), 0.03);
      float sty = band(sdSeg(q, ax(0.28, 0.0), ax(0.28 + 0.09 * vFeed, 0.0)), 0.015) * vFeed;
      a = skin(d) + band(lg, 0.05) * 0.5 + seg + head + mouth * 0.5 + sty;
      col = vColor * 1.1 + vFlash * 0.5;
    }
  } else if (k < 8.5) {
    float fa = atan(q.y, q.x);
    float rad = 0.27 + 0.03 * sin(ang * 3.0 + vT * 1.3) + 0.02 * sin(ang * 5.0 - vT * 2.1) + 0.01 * sin(ang * 7.0 + vT * 2.9) + (0.02 + 0.07 * sp) * exp(-fa * fa * 1.5) * (0.6 + 0.4 * sin(vT * 2.0 + vPh));
    float arm = 0.8 * (1.0 - 0.5 * vFeed);
    float da1 = atan(sin(fa - vFAng - arm), cos(fa - vFAng - arm));
    float da2 = atan(sin(fa - vFAng + arm), cos(fa - vFAng + arm));
    float dc = atan(sin(fa - vFAng), cos(fa - vFAng));
    rad += vFeed * 0.20 * (exp(-sq(da1) * 12.0) + exp(-sq(da2) * 12.0)) - vFeed * 0.07 * (1.0 - vFeed) * exp(-sq(dc) * 8.0);
    vec2 pp = p * 1.25;
    float d = length(pp) - rad;
    vec2 np = pp - vec2(0.03 * cos(vT * 0.6), 0.03 * sin(vT * 0.6));
    float sepN = 0.15 * smoothstep(0.25, 0.8, vDiv);
    vec2 qn = q * 1.25 - vec2(0.03 * cos(vT * 0.6), 0.03 * sin(vT * 0.6));
    vec2 nA = qn - vec2(sepN, 0.0);
    vec2 nB = qn + vec2(sepN, 0.0);
    float nuc = max(band(length(nA) - 0.07, 0.035) * 0.8 + blob(nA, 0.025), band(length(nB) - 0.07, 0.035) * 0.8 + blob(nB, 0.025));
    nuc += band(qn.y, 0.012) * step(abs(qn.x), sepN) * 0.4 * step(0.2, vDiv) * (1.0 - smoothstep(0.7, 0.95, vDiv));
    float cv = blob(pp - vec2(-0.13, 0.08), 0.045 * (0.55 + 0.45 * sin(vT * 1.6)) + 0.01);
    float fv = band(length(pp - vec2(0.11, -0.10)) - 0.03, 0.02) * 0.5;
    a = skin(d) + clamp(-d * 4.0, 0.0, 0.2) + band(d + 0.05, 0.03) * 0.15 + nuc * 0.5 + cv * 0.5 + fv;
    col = vColor * 1.1 + nuc * 0.15 + vFlash * 0.5;
  } else if (k < 9.5) {
    // corpo rigido: avanza con l'onda metacronale delle ciglia e ruota attorno al proprio asse (il solco orale scorre da un lato all'altro)
    float hw = mix(0.075, 0.115, smoothstep(-0.2, 0.15, q.x));
    float d = sdEll(q, vec2(0.28, hw));
    float ins = clamp(-d * 20.0, 0.0, 1.0);
    float roll = sin(vT * 5.0 + vPh) * sp;
    float cil = band(d - 0.02, 0.03) * (0.4 + 0.3 * sin(q.x * 55.0 - vT * (6.0 + 22.0 * sp))) * 0.5;
    float groove = band(sdSeg(q, vec2(0.15, 0.06 * (0.4 + 0.6 * roll)), vec2(0.0, -0.005 + 0.03 * roll)), 0.02) * (0.45 + 0.4 * vFeed) * ins;
    float sepM = 0.13 * smoothstep(0.25, 0.8, vDiv);
    float mac = max(blob(vec2(q.x - sepM, (q.y + 0.01) * 1.8), 0.075), blob(vec2(q.x + sepM, (q.y + 0.01) * 1.8), 0.075));
    float mic = max(blob(q - vec2(0.03 + sepM, 0.04), 0.02), blob(q - vec2(0.03 - sepM, 0.04), 0.02));
    float v1 = band(length(q - vec2(0.20, 0.0)) - 0.03 * (0.5 + 0.5 * sin(vT * 1.2)), 0.02) * 0.5;
    float v2 = band(length(q - vec2(-0.19, 0.0)) - 0.03 * (0.5 + 0.5 * sin(vT * 1.2 + 3.14)), 0.02) * 0.5;
    float sym = step(0.5, vState) * ins * smoothstep(0.45, 0.8, sin(q.x * 40.0) * sin(q.y * 70.0 + 1.0));
    a = skin(d) + cil + groove + mac * 0.5 + mic * 0.6 + v1 + v2 + sym * 0.5;
    col = vColor * 1.1 + mac * 0.15 + vFlash * 0.5;
    if (sym > 0.0) col = mix(col, vec3(0.3, 0.95, 0.4), 0.7);
  } else if (k < 10.5) {
    // da ferma: metabolia euglenoide (il corpo si accorcia e si ingrossa a turno); nuotando e' un fuso rigido che ruota (la macchia oculare gira).
    // Flagello, macchia oculare, cloroplasti e nucleo non si deformano: seguono l'asse del corpo.
    float m = 1.0 + 0.18 * sin(vT * 2.6 + vPh) * (1.0 - sp);
    vec2 b = vec2(q.x, q.y - yc(q.x));
    float d = sdEll(b - vec2(-0.02, 0.0), vec2(0.24 * m, 0.065 / m));
    float fx = -0.02 + 0.24 * m;
    float fl = band(q.y - yc(fx) - 0.05 * sin(q.x * 30.0 - vT * 16.0), 0.025) * 0.55 * step(fx - 0.03, q.x) * step(q.x, fx + 0.22);
    float tl = band(sdSeg(q, ax(-0.02 - 0.24 * m, 0.0), ax(-0.02 - 0.24 * m - 0.07, 0.0)), 0.03) * 0.4;
    float eye = blob(q - ax(-0.02 + 0.17 * m, 0.035 * cos(vT * 5.0 + vPh) * sp), 0.03);
    float chl = (blob(q - ax(-0.12 * m, 0.0), 0.05) + blob(q - ax(-0.02 * m, -0.01), 0.05) + blob(q - ax(0.07 * m, 0.01), 0.04)) * 0.4;
    float sepE = 0.12 * smoothstep(0.25, 0.8, vDiv);
    float nuc = max(blob(q - ax(-0.07 * m + sepE, 0.0), 0.035), blob(q - ax(-0.07 * m - sepE, 0.0), 0.035));
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
  if (vDiv > 0.0) a += 0.4 * smoothstep(0.3, 1.0, vDiv) * band(q.x, 0.04) * clamp(1.0 - abs(q.y) * 3.0, 0.0, 1.0) * clamp(a * 4.0, 0.0, 1.0);
  if (vDiv > 0.0) a *= 1.0 - sq(smoothstep(0.75, 1.0, vDiv)) * exp(-sq(q.x / 0.03));
  float mask = 1.0 - smoothstep(0.9, 1.0, r0);
  float alpha = clamp(a, 0.0, 0.95) * mask;
  if (alpha < 0.003) discard;
  gl_FragColor = vec4(col, alpha);
}"""
    }
}
