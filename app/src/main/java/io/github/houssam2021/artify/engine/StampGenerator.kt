package io.github.houssam2021.artify.engine

import io.github.houssam2021.artify.model.BrushSpec
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Receives one stamp, in stroke-local world coordinates. */
fun interface StampSink {
    fun stamp(x: Float, y: Float, diameter: Float, alpha: Float, angleDeg: Float)
}

/**
 * Turns recorded input samples into evenly spaced brush stamps.
 * Deterministic: identical inputs and seed always produce identical stamps,
 * which is what allows strokes to be re-rendered at any zoom.
 */
object StampGenerator {
    /** Hard cap so a pathological stroke can never hang rendering. */
    const val MAX_STAMPS = 200_000

    fun generate(
        brush: BrushSpec, size: Float, seed: Int,
        xs: FloatArray, ys: FloatArray, ps: FloatArray, n: Int,
        minStep: Double, sink: StampSink,
    ) {
        if (n <= 0 || size <= 0f) return
        var state = seed.toLong() * -0x61c8864680b583ebL + 0x632BE59BD9B4E019L
        fun rnd(): Float {
            state += -0x61c8864680b583ebL
            var z = state
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            z = z xor (z ushr 31)
            return ((z ushr 40).toFloat()) / (1L shl 24).toFloat()
        }

        var total = 0.0
        for (i in 1 until n) {
            val dx = (xs[i] - xs[i - 1]).toDouble(); val dy = (ys[i] - ys[i - 1]).toDouble()
            total += sqrt(dx * dx + dy * dy)
        }
        val taperLen = if (brush.taper > 0f) min(brush.taper * size.toDouble(), total * 0.45) else 0.0
        var emitted = 0

        fun emitAt(x: Float, y: Float, p: Float, dist: Double, dirRad: Double): Double {
            var sf = 1f - brush.pressureSize * (1f - p)
            val ff = 1f - brush.pressureFlow * (1f - p)
            if (taperLen > 0.0) {
                val t = min(1.0, min(dist / taperLen, (total - dist) / taperLen)).coerceIn(0.0, 1.0)
                val sm = t * t * (3 - 2 * t)
                sf *= (0.12 + 0.88 * sm).toFloat()
            }
            val r1 = rnd(); val r2 = rnd(); val r3 = rnd(); val r4 = rnd(); val r5 = rnd()
            val base = size * sf
            val d = max(0f, base * (1f + brush.sizeJitter * (r1 * 2f - 1f)))
            val alpha = brush.flow * ff * (1f - brush.flowJitter * r2)
            val dirDeg = Math.toDegrees(dirRad).toFloat()
            val angle = brush.angleDeg + (if (brush.followStroke) dirDeg else 0f) + brush.rotationJitter * 360f * r3
            var sx = x; var sy = y
            if (brush.scatter > 0f) {
                val perp = (r4 * 2f - 1f) * brush.scatter * base
                val along = (r5 * 2f - 1f) * brush.scatter * base * 0.5f
                val c = cos(dirRad).toFloat(); val s = sin(dirRad).toFloat()
                sx += c * along - s * perp
                sy += s * along + c * perp
            }
            if (d > 0f && alpha > 0f) {
                sink.stamp(sx, sy, d, alpha, angle)
                emitted++
            }
            return max(base * brush.spacing.toDouble(), minStep)
        }

        if (n == 1 || total <= 0.0) {
            emitAt(xs[0], ys[0], ps[0], 0.0, 0.0)
            return
        }

        var travelled = 0.0
        var next = 0.0
        for (i in 1 until n) {
            val x0 = xs[i - 1]; val y0 = ys[i - 1]
            val dx = (xs[i] - x0).toDouble(); val dy = (ys[i] - y0).toDouble()
            val len = sqrt(dx * dx + dy * dy)
            if (len <= 0.0) continue
            val dir = atan2(dy, dx)
            while (next <= travelled + len) {
                if (emitted >= MAX_STAMPS) return
                val t = (next - travelled) / len
                val x = (x0 + dx * t).toFloat()
                val y = (y0 + dy * t).toFloat()
                val p = ps[i - 1] + (ps[i] - ps[i - 1]) * t.toFloat()
                val step = emitAt(x, y, p, next, dir)
                next += if (step > 0.0) step else minStep.coerceAtLeast(1e-12)
            }
            travelled += len
        }
    }
}
