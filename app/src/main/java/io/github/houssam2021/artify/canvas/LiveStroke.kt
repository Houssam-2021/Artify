package io.github.houssam2021.artify.canvas

import io.github.houssam2021.artify.model.BrushSpec
import io.github.houssam2021.artify.model.Stroke

/** The stroke currently being drawn, in stroke-local world coordinates. */
class LiveStroke(
    val brush: BrushSpec,
    val color: Int,
    val size: Float,
    val opacity: Float,
    val eraser: Boolean,
    val seed: Int,
    val ox: Double,
    val oy: Double,
    private val streamline: Float,
    /** Minimum distance between stored points, in world units. */
    private val minDist: Float,
) {
    var xs = FloatArray(256); private set
    var ys = FloatArray(256); private set
    var ps = FloatArray(256); private set
    var n = 0; private set
    var minX = 0f; private set
    var minY = 0f; private set
    var maxX = 0f; private set
    var maxY = 0f; private set
    /** Total path length in world units. */
    var length = 0.0; private set
    var snapped = false; private set

    private var sx = 0f; private var sy = 0f; private var sp = 0f
    private var has = false
    private var lastRawX = 0f; private var lastRawY = 0f; private var lastRawP = 0f

    fun add(x: Float, y: Float, p: Float) {
        if (snapped) return
        lastRawX = x; lastRawY = y; lastRawP = p
        if (!has) {
            sx = x; sy = y; sp = p; has = true
            push(x, y, p)
            return
        }
        val k = 1f - streamline * 0.9f
        sx += (x - sx) * k; sy += (y - sy) * k
        sp += (p - sp) * 0.6f
        val dx = sx - xs[n - 1]; val dy = sy - ys[n - 1]
        if (dx * dx + dy * dy < minDist * minDist) return
        if (n >= MAX_POINTS) return
        push(sx, sy, sp)
    }

    /** Lets the smoothed path catch up to where the pen actually lifted. */
    fun finish() {
        if (snapped || !has || n == 0) return
        val dx = lastRawX - xs[n - 1]; val dy = lastRawY - ys[n - 1]
        if (dx * dx + dy * dy > minDist * minDist) push(lastRawX, lastRawY, sp)
    }

    fun replaceWith(nx: FloatArray, ny: FloatArray, np: FloatArray) {
        n = 0; length = 0.0
        for (i in nx.indices) push(nx[i], ny[i], np[i])
        snapped = true
    }

    /** Writes a temporary predicted point after the last real one (not counted in [n]). */
    fun setPrediction(x: Float, y: Float): Int {
        if (n == 0 || snapped) return n
        ensure(n + 1)
        xs[n] = x; ys[n] = y; ps[n] = ps[n - 1]
        return n + 1
    }

    private fun push(x: Float, y: Float, p: Float) {
        ensure(n + 2)
        if (n == 0) { minX = x; maxX = x; minY = y; maxY = y } else {
            val dx = (x - xs[n - 1]).toDouble(); val dy = (y - ys[n - 1]).toDouble()
            length += kotlin.math.sqrt(dx * dx + dy * dy)
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
        }
        xs[n] = x; ys[n] = y; ps[n] = p.coerceIn(0f, 1f)
        n++
    }

    private fun ensure(cap: Int) {
        if (cap <= xs.size) return
        val c = maxOf(cap, xs.size * 2)
        xs = xs.copyOf(c); ys = ys.copyOf(c); ps = ps.copyOf(c)
    }

    fun margin(): Float = size * (0.5f + brush.scatter + brush.sizeJitter) + size * 0.05f

    fun toStroke(): Stroke? {
        if (n == 0) return null
        return Stroke(brush, color, size, opacity, eraser, seed, 0, ox, oy, xs.copyOf(n), ys.copyOf(n), ps.copyOf(n))
    }

    companion object {
        const val MAX_POINTS = 100_000
    }

    val full: Boolean get() = n >= MAX_POINTS
}
