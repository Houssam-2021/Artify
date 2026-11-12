package io.github.houssam2021.artify.model

import kotlin.math.cos
import kotlin.math.sin

/**
 * An immutable recorded stroke. Points are stored as float offsets from a
 * double-precision origin so strokes stay precise at any zoom depth.
 * Pixels are never stored: the stroke is re-rendered at whatever zoom is shown,
 * which is what makes the canvas infinitely zoomable.
 */
class Stroke(
    val brush: BrushSpec,
    val color: Int,
    /** Stamp diameter in world units. */
    val size: Float,
    val opacity: Float,
    val eraser: Boolean,
    val seed: Int,
    val seq: Long,
    val ox: Double,
    val oy: Double,
    val xs: FloatArray,
    val ys: FloatArray,
    /** Pressure 0..1 (after the user's pressure curve). */
    val ps: FloatArray,
) {
    val count: Int get() = xs.size
    val minX: Float
    val minY: Float
    val maxX: Float
    val maxY: Float

    init {
        require(xs.isNotEmpty() && xs.size == ys.size && ys.size == ps.size) { "bad stroke arrays" }
        var x0 = Float.POSITIVE_INFINITY; var y0 = Float.POSITIVE_INFINITY
        var x1 = Float.NEGATIVE_INFINITY; var y1 = Float.NEGATIVE_INFINITY
        for (i in xs.indices) {
            val x = xs[i]; val y = ys[i]
            if (x < x0) x0 = x; if (x > x1) x1 = x
            if (y < y0) y0 = y; if (y > y1) y1 = y
        }
        val margin = size * (0.5f + brush.scatter + brush.sizeJitter) + size * 0.05f
        minX = x0 - margin; minY = y0 - margin; maxX = x1 + margin; maxY = y1 + margin
    }

    fun withSeq(newSeq: Long) = Stroke(brush, color, size, opacity, eraser, seed, newSeq, ox, oy, xs, ys, ps)

    /**
     * Similarity transform in world space: p' = pivot + R(angle)·S(scale)·F(p - pivot) + (tx, ty),
     * where F mirrors the x axis when [flipX] is set.
     */
    fun transformed(
        pivotX: Double, pivotY: Double, scale: Double, angle: Double, flipX: Boolean,
        tx: Double = 0.0, ty: Double = 0.0, newSeq: Long = seq, newSeed: Int = seed,
    ): Stroke {
        val c = cos(angle) * scale; val s = sin(angle) * scale
        val f = if (flipX) -1.0 else 1.0
        val dx = (ox - pivotX) * f; val dy = oy - pivotY
        val nox = pivotX + c * dx - s * dy + tx
        val noy = pivotY + s * dx + c * dy + ty
        val n = xs.size
        val nx = FloatArray(n); val ny = FloatArray(n)
        for (i in 0 until n) {
            val lx = xs[i] * f; val ly = ys[i].toDouble()
            nx[i] = (c * lx - s * ly).toFloat()
            ny[i] = (s * lx + c * ly).toFloat()
        }
        return Stroke(brush, color, (size * scale).toFloat(), opacity, eraser, newSeed, newSeq, nox, noy, nx, ny, ps)
    }

    /** True if at least half of the stroke's points lie inside the world-space polygon. */
    fun mostlyInside(poly: DoubleArray): Boolean {
        val step = maxOf(1, count / 64)
        var inside = 0; var total = 0
        var i = 0
        while (i < count) {
            if (Geometry.pointInPolygon(ox + xs[i], oy + ys[i], poly)) inside++
            total++
            i += step
        }
        return inside * 2 >= total
    }
}

object Geometry {
    /** Even-odd test; poly = [x0, y0, x1, y1, ...]. */
    fun pointInPolygon(x: Double, y: Double, poly: DoubleArray): Boolean {
        val n = poly.size / 2
        if (n < 3) return false
        var inside = false
        var j = n - 1
        for (i in 0 until n) {
            val xi = poly[2 * i]; val yi = poly[2 * i + 1]
            val xj = poly[2 * j]; val yj = poly[2 * j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }
}
