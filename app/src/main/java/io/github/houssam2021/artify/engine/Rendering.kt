package io.github.houssam2021.artify.engine

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import io.github.houssam2021.artify.model.BrushSpec
import io.github.houssam2021.artify.model.Layer
import io.github.houssam2021.artify.model.LayerBlend
import io.github.houssam2021.artify.model.Stroke
import io.github.houssam2021.artify.model.ViewTransform
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun LayerBlend.toBlendMode(): BlendMode = when (this) {
    LayerBlend.NORMAL -> BlendMode.SRC_OVER
    LayerBlend.MULTIPLY -> BlendMode.MULTIPLY
    LayerBlend.SCREEN -> BlendMode.SCREEN
    LayerBlend.OVERLAY -> BlendMode.OVERLAY
    LayerBlend.DARKEN -> BlendMode.DARKEN
    LayerBlend.LIGHTEN -> BlendMode.LIGHTEN
    LayerBlend.COLOR_DODGE -> BlendMode.COLOR_DODGE
    LayerBlend.COLOR_BURN -> BlendMode.COLOR_BURN
    LayerBlend.HARD_LIGHT -> BlendMode.HARD_LIGHT
    LayerBlend.SOFT_LIGHT -> BlendMode.SOFT_LIGHT
    LayerBlend.DIFFERENCE -> BlendMode.DIFFERENCE
    LayerBlend.EXCLUSION -> BlendMode.EXCLUSION
    LayerBlend.ADD -> BlendMode.PLUS
    LayerBlend.HUE -> BlendMode.HUE
    LayerBlend.SATURATION -> BlendMode.SATURATION
    LayerBlend.COLOR -> BlendMode.COLOR
    LayerBlend.LUMINOSITY -> BlendMode.LUMINOSITY
}

/**
 * Draws strokes onto a Canvas for a given view transform. Not thread-safe:
 * use one instance per thread.
 */
class StrokePainter : StampSink {
    private val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val groupPaint = Paint()
    private val m = Matrix()
    private val bounds = RectF()

    // per-stroke context for the stamp callback
    private var canvas: Canvas? = null
    private var tip: Bitmap? = null
    private var baseX = 0.0
    private var baseY = 0.0
    private var a = 0.0
    private var b = 0.0
    private var scale = 1.0
    private var rotDeg = 0f
    private var roundness = 1f
    private var clipL = 0f; private var clipT = 0f; private var clipR = 0f; private var clipB = 0f

    fun drawStroke(c: Canvas, vt: ViewTransform, clip: RectF, s: Stroke, alphaLock: Boolean) {
        draw(c, vt, clip, s.brush, s.color, s.size, s.opacity, s.eraser, s.seed, s.ox, s.oy, s.xs, s.ys, s.ps, s.count,
            alphaLock, s.minX, s.minY, s.maxX, s.maxY)
    }

    fun draw(
        c: Canvas, vt: ViewTransform, clip: RectF,
        brush: BrushSpec, color: Int, size: Float, opacity: Float, eraser: Boolean, seed: Int,
        ox: Double, oy: Double, xs: FloatArray, ys: FloatArray, ps: FloatArray, n: Int,
        alphaLock: Boolean,
        minX: Float, minY: Float, maxX: Float, maxY: Float,
    ) {
        if (n <= 0 || opacity <= 0f) return
        a = vt.a; b = vt.b; scale = vt.scale; rotDeg = vt.rotationDeg
        baseX = vt.sx(ox, oy); baseY = vt.sy(ox, oy)
        // Screen bounds of the stroke for culling and layer allocation.
        var l = Double.POSITIVE_INFINITY; var t = Double.POSITIVE_INFINITY
        var r = Double.NEGATIVE_INFINITY; var btm = Double.NEGATIVE_INFINITY
        for (k in 0 until 4) {
            val lx = if (k == 0 || k == 3) minX.toDouble() else maxX.toDouble()
            val ly = if (k < 2) minY.toDouble() else maxY.toDouble()
            val sx = baseX + a * lx - b * ly; val sy = baseY + b * lx + a * ly
            l = min(l, sx); r = max(r, sx); t = min(t, sy); btm = max(btm, sy)
        }
        if (r < clip.left || l > clip.right || btm < clip.top || t > clip.bottom) return
        if (r - l < 0.3 && btm - t < 0.3) return // sub-pixel: invisible at this zoom
        bounds.set(
            max(l, clip.left.toDouble()).toFloat() - 1f, max(t, clip.top.toDouble()).toFloat() - 1f,
            min(r, clip.right.toDouble()).toFloat() + 1f, min(btm, clip.bottom.toDouble()).toFloat() + 1f,
        )
        clipL = clip.left; clipT = clip.top; clipR = clip.right; clipB = clip.bottom

        val mode = when {
            eraser -> BlendMode.DST_OUT
            alphaLock -> BlendMode.SRC_ATOP
            else -> BlendMode.SRC_OVER
        }
        val direct = opacity >= 0.999f
        stampPaint.color = if (eraser) Color.BLACK else (color or (0xFF shl 24))
        stampPaint.blendMode = if (direct) mode else BlendMode.SRC_OVER
        if (!direct) {
            groupPaint.alpha = (opacity * 255f).roundToInt().coerceIn(0, 255)
            groupPaint.blendMode = mode
            c.saveLayer(bounds, groupPaint)
        }
        canvas = c
        tip = TipCache.bitmapFor(brush)
        roundness = brush.roundness
        StampGenerator.generate(brush, size, seed, xs, ys, ps, n, 0.6 / scale, this)
        if (!direct) c.restore()
        canvas = null
    }

    override fun stamp(x: Float, y: Float, diameter: Float, alpha: Float, angleDeg: Float) {
        val sx = (baseX + a * x - b * y).toFloat()
        val sy = (baseY + b * x + a * y).toFloat()
        var rad = (diameter * scale * 0.5).toFloat()
        if (sx + rad < clipL || sx - rad > clipR || sy + rad < clipT || sy - rad > clipB) return
        var al = alpha
        if (rad < 0.5f) {
            val k = rad / 0.5f
            al *= k * k
            rad = 0.5f
        }
        val ai = (al * 255f + 0.5f).toInt()
        if (ai <= 0) return
        stampPaint.alpha = min(255, ai)
        val c = canvas ?: return
        val bmp = tip
        if (bmp == null) {
            c.drawCircle(sx, sy, rad, stampPaint)
        } else {
            val half = TipCache.SIZE / 2f
            val k = 2f * rad / TipCache.SIZE
            m.setTranslate(-half, -half)
            m.postScale(k, k * roundness)
            m.postRotate(angleDeg + rotDeg)
            m.postTranslate(sx, sy)
            c.drawBitmap(bmp, m, stampPaint)
        }
    }
}

/** One layer to composite, optionally tinted (onion skins). */
class LayerDraw(val layer: Layer, val tint: Int = 0, val opacityMul: Float = 1f)

/**
 * What to draw, split around the active layer so live strokes can be shown
 * in the correct place in the stack.
 */
class RenderPlan(
    val background: Int,
    val below: List<LayerDraw>,
    val active: Layer?,
    val above: List<LayerDraw>,
    val maxSeq: Long = Long.MAX_VALUE,
) {
    /** Above layers with non-normal blending can't be pre-flattened exactly. */
    val needsFull: Boolean = active != null && above.any { it.layer.blend != LayerBlend.NORMAL }
}

class RenderCache(
    val transform: ViewTransform,
    /** Document version this cache reflects; bumped when strokes are drawn in incrementally. */
    var version: Long,
    val plan: RenderPlan,
    val below: Bitmap,
    val active: Bitmap,
    val above: Bitmap?,
    val full: Bitmap?,
) {
    var fullStale = false
}

object SceneRenderer {
    private val layerPaint = ThreadLocal.withInitial { Paint(Paint.FILTER_BITMAP_FLAG) }

    fun layerPaintFor(layer: Layer, mul: Float = 1f, tint: Int = 0, p: Paint = Paint(Paint.FILTER_BITMAP_FLAG)): Paint {
        p.alpha = (layer.opacity * mul * 255f).roundToInt().coerceIn(0, 255)
        p.blendMode = layer.blend.toBlendMode()
        p.colorFilter = if (tint != 0) PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_ATOP) else null
        return p
    }

    /** Draws a layer's strokes (no layer opacity/blend) directly onto [c]. */
    fun drawLayerContent(
        c: Canvas, layer: Layer, vt: ViewTransform, clip: RectF, painter: StrokePainter,
        maxSeq: Long = Long.MAX_VALUE, cancelled: () -> Boolean = { false },
    ): Boolean {
        for (s in layer.strokes) {
            if (s.seq > maxSeq) continue
            if (cancelled()) return false
            painter.drawStroke(c, vt, clip, s, layer.alphaLock)
        }
        return true
    }

    /** Composites a full layer (isolated, with opacity / blend / tint). */
    fun drawLayer(
        c: Canvas, ld: LayerDraw, vt: ViewTransform, clip: RectF, painter: StrokePainter,
        maxSeq: Long = Long.MAX_VALUE, cancelled: () -> Boolean = { false },
    ): Boolean {
        val layer = ld.layer
        if (!layer.visible || layer.opacity <= 0f || layer.strokes.isEmpty()) return true
        val p = layerPaintFor(layer, ld.opacityMul, ld.tint, layerPaint.get()!!)
        c.saveLayer(clip, p)
        val ok = drawLayerContent(c, layer, vt, clip, painter, maxSeq, cancelled)
        c.restore()
        return ok
    }

    /** Renders an entire plan (background + all layers) onto [c]. */
    fun drawPlan(
        c: Canvas, plan: RenderPlan, vt: ViewTransform, clip: RectF, painter: StrokePainter,
        transparent: Boolean = false, cancelled: () -> Boolean = { false },
    ): Boolean {
        if (!transparent) c.drawColor(plan.background) else c.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR)
        for (ld in plan.below) if (!drawLayer(c, ld, vt, clip, painter, plan.maxSeq, cancelled)) return false
        plan.active?.let { if (!drawLayer(c, LayerDraw(it), vt, clip, painter, plan.maxSeq, cancelled)) return false }
        for (ld in plan.above) if (!drawLayer(c, ld, vt, clip, painter, plan.maxSeq, cancelled)) return false
        return true
    }

    /** Builds the cached bitmaps for the canvas view. Returns null if cancelled. */
    fun renderCache(
        plan: RenderPlan, vt: ViewTransform, version: Long, painter: StrokePainter, cancelled: () -> Boolean,
    ): RenderCache? {
        val w = vt.width; val h = vt.height
        val clip = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val below = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val bc = Canvas(below)
        bc.drawColor(plan.background)
        for (ld in plan.below) if (!drawLayer(bc, ld, vt, clip, painter, plan.maxSeq, cancelled)) return null
        val active = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        plan.active?.let { if (!drawLayerContent(Canvas(active), it, vt, clip, painter, plan.maxSeq, cancelled)) return null }
        var above: Bitmap? = null
        if (plan.above.any { it.layer.visible && it.layer.strokes.isNotEmpty() }) {
            above = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val ac = Canvas(above)
            for (ld in plan.above) if (!drawLayer(ac, ld, vt, clip, painter, plan.maxSeq, cancelled)) return null
        }
        var full: Bitmap? = null
        if (plan.needsFull) {
            full = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val fc = Canvas(full)
            fc.drawBitmap(below, 0f, 0f, null)
            plan.active?.let { if (it.visible) fc.drawBitmap(active, 0f, 0f, layerPaintFor(it)) }
            for (ld in plan.above) if (!drawLayer(fc, ld, vt, clip, painter, plan.maxSeq, cancelled)) return null
        }
        return RenderCache(vt, version, plan, below, active, above, full)
    }

    /** Off-screen render of a plan at an arbitrary size (export, thumbnails, video). */
    fun renderToBitmap(plan: RenderPlan, vt: ViewTransform, transparent: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(vt.width, vt.height, Bitmap.Config.ARGB_8888)
        drawPlan(Canvas(bmp), plan, vt, RectF(0f, 0f, vt.width.toFloat(), vt.height.toFloat()), StrokePainter(), transparent)
        return bmp
    }
}
