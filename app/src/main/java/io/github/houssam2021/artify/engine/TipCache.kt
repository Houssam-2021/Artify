package io.github.houssam2021.artify.engine

import android.graphics.Bitmap
import io.github.houssam2021.artify.model.BrushSpec
import io.github.houssam2021.artify.model.BrushTip
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Procedurally generated ALPHA_8 brush tips (drawn tinted with the paint colour). */
object TipCache {
    const val SIZE = 128
    private val cache = HashMap<String, Bitmap>()

    /** Returns null when the stamp should be drawn as an analytic circle. */
    fun bitmapFor(brush: BrushSpec): Bitmap? {
        if (brush.tip == BrushTip.ROUND && brush.roundness >= 0.999f) return null
        val key = if (brush.tip == BrushTip.ROUND) "HARD" else brush.tip.name
        synchronized(cache) {
            return cache.getOrPut(key) { build(key) }
        }
    }

    private fun build(key: String): Bitmap {
        val s = SIZE
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ALPHA_8)
        val stride = bmp.rowBytes
        val data = ByteArray(stride * s)
        val bristles = if (key == "BRISTLE") bristleDots(7) else null
        val splats = if (key == "SPLATTER") splatDots(11) else null
        val half = s / 2f
        for (py in 0 until s) for (px in 0 until s) {
            val x = (px + 0.5f - half) / half
            val y = (py + 0.5f - half) / half
            val r = sqrt(x * x + y * y)
            val edge = (1f - r) * half + 0.5f // pixel-accurate antialiased disc edge
            val disc = edge.coerceIn(0f, 1f)
            val a: Float = when (key) {
                "HARD" -> disc
                "SOFT" -> if (r >= 1f) 0f else {
                    val t = 1f - r
                    (t * t * (3 - 2 * t)).let { it * it }
                }
                "PENCIL" -> disc * (0.18f + 0.82f * smooth(0.35f, 0.75f, noise(px * 0.9f, py * 0.9f, 3)))
                "CHARCOAL" -> {
                    val ang = atan2(y, x)
                    val ragged = 0.82f + 0.18f * noise((ang + 4f) * 6f, 1f, 9)
                    val e = ((ragged - r) * half + 0.5f).coerceIn(0f, 1f)
                    e * smooth(0.38f, 0.7f, noise(px * 0.55f, py * 0.55f, 5) * 0.7f + noise(px * 0.13f, py * 0.13f, 6) * 0.3f)
                }
                "BRISTLE" -> {
                    var m = 0f
                    val d = bristles!!
                    var i = 0
                    while (i < d.size) {
                        val dx = x - d[i]; val dy = y - d[i + 1]
                        val rr = sqrt(dx * dx + dy * dy) / d[i + 2]
                        if (rr < 1f) m = max(m, (1f - rr * rr) * d[i + 3])
                        i += 4
                    }
                    m * if (r < 1f) 1f else 0f
                }
                "SQUARE" -> {
                    val q = max(abs(x), abs(y))
                    ((1f - q) * half + 0.5f).coerceIn(0f, 1f)
                }
                "SPLATTER" -> {
                    var m = 0f
                    val d = splats!!
                    var i = 0
                    while (i < d.size) {
                        val dx = x - d[i]; val dy = y - d[i + 1]
                        val e = ((d[i + 2] - sqrt(dx * dx + dy * dy)) * half + 0.5f).coerceIn(0f, 1f)
                        m = max(m, e)
                        i += 3
                    }
                    m
                }
                "WATERCOLOR" -> {
                    val body = smooth(1f, 0.82f, r)
                    val rim = 0.4f + 0.6f * smooth(0.5f, 0.93f, r)
                    body * rim * (0.55f + 0.45f * noise(px * 0.08f, py * 0.08f, 11))
                }
                else -> disc
            }
            data[py * stride + px] = (a.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
        }
        bmp.copyPixelsFromBuffer(ByteBuffer.wrap(data))
        return bmp
    }

    private fun bristleDots(seed: Int): FloatArray {
        val out = ArrayList<Float>()
        var k = 0
        while (out.size < 4 * 46) {
            val x = hash(k, 1, seed) * 2f - 1f
            val y = hash(k, 2, seed) * 2f - 1f
            k++
            if (x * x + y * y > 0.8f) continue
            out += x; out += y
            out += 0.07f + 0.1f * hash(k, 3, seed)
            out += 0.45f + 0.55f * hash(k, 4, seed)
        }
        return out.toFloatArray()
    }

    private fun splatDots(seed: Int): FloatArray {
        val out = ArrayList<Float>()
        for (k in 0 until 14) {
            val ang = hash(k, 1, seed) * 6.2832f
            val dist = if (k == 0) 0f else 0.25f + 0.6f * hash(k, 2, seed)
            val rad = if (k == 0) 0.32f else 0.04f + 0.13f * hash(k, 3, seed) * (1f - dist * 0.6f)
            out += kotlin.math.cos(ang) * dist; out += kotlin.math.sin(ang) * dist; out += rad
        }
        return out.toFloatArray()
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777215f
    }

    private fun noise(x: Float, y: Float, seed: Int): Float {
        val x0 = floor(x).toInt(); val y0 = floor(y).toInt()
        val fx = x - x0; val fy = y - y0
        val sx = fx * fx * (3 - 2 * fx); val sy = fy * fy * (3 - 2 * fy)
        val a = hash(x0, y0, seed); val b = hash(x0 + 1, y0, seed)
        val c = hash(x0, y0 + 1, seed); val d = hash(x0 + 1, y0 + 1, seed)
        return (a + (b - a) * sx) * (1 - sy) + (c + (d - c) * sx) * sy
    }

    @Suppress("unused")
    private fun gaussian(r: Float) = exp(-r * r * 3.5f).let { min(1f, it) }
}
