package io.github.houssam2021.artify.engine

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** "Draw and hold" shape snapping: turns a rough stroke into a clean line, circle or ellipse. */
object ShapeDetector {
    class Shape(val xs: FloatArray, val ys: FloatArray, val ps: FloatArray, val kind: String)

    fun detect(xs: FloatArray, ys: FloatArray, ps: FloatArray, n: Int): Shape? {
        if (n < 4) return null
        var length = 0.0
        for (i in 1 until n) length += hypot((xs[i] - xs[i - 1]).toDouble(), (ys[i] - ys[i - 1]).toDouble())
        if (length <= 0.0) return null
        val x0 = xs[0]; val y0 = ys[0]; val x1 = xs[n - 1]; val y1 = ys[n - 1]
        val chord = hypot((x1 - x0).toDouble(), (y1 - y0).toDouble())
        var pAvg = 0f
        for (i in 0 until n) pAvg += ps[i]
        pAvg /= n

        if (chord / length > 0.8) {
            // Line, if no point strays too far from the chord.
            var maxDev = 0.0
            val dx = (x1 - x0) / chord; val dy = (y1 - y0) / chord
            for (i in 0 until n) {
                val dev = abs((xs[i] - x0) * dy - (ys[i] - y0) * dx)
                if (dev > maxDev) maxDev = dev
            }
            if (maxDev > chord * 0.12) return null
            val k = 48
            return Shape(
                FloatArray(k) { x0 + (x1 - x0) * it / (k - 1f) },
                FloatArray(k) { y0 + (y1 - y0) * it / (k - 1f) },
                FloatArray(k) { pAvg }, "Line",
            )
        }
        if (chord < length * 0.25) {
            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (i in 0 until n) {
                minX = minOf(minX, xs[i]); maxX = maxOf(maxX, xs[i])
                minY = minOf(minY, ys[i]); maxY = maxOf(maxY, ys[i])
            }
            val cx = (minX + maxX) / 2.0; val cy = (minY + maxY) / 2.0
            var rx = (maxX - minX) / 2.0; var ry = (maxY - minY) / 2.0
            if (rx <= 0 || ry <= 0) return null
            // Reject shapes that are far from elliptical (e.g. scribbles).
            var err = 0.0
            for (i in 0 until n) {
                val ex = (xs[i] - cx) / rx; val ey = (ys[i] - cy) / ry
                err += abs(hypot(ex, ey) - 1.0)
            }
            if (err / n > 0.22) return null
            var kind = "Ellipse"
            if (abs(rx - ry) / max(rx, ry) < 0.15) { val r = (rx + ry) / 2; rx = r; ry = r; kind = "Circle" }
            val start = atan2((y0 - cy) / ry, (x0 - cx) / rx)
            val k = 120
            return Shape(
                FloatArray(k + 1) { (cx + rx * cos(start + 2 * Math.PI * it / k)).toFloat() },
                FloatArray(k + 1) { (cy + ry * sin(start + 2 * Math.PI * it / k)).toFloat() },
                FloatArray(k + 1) { pAvg }, kind,
            )
        }
        return null
    }
}
