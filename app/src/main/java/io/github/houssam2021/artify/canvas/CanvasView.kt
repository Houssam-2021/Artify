package io.github.houssam2021.artify.canvas

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.view.MotionEvent
import android.view.View
import androidx.input.motionprediction.MotionEventPredictor
import io.github.houssam2021.artify.editor.CanvasHost
import io.github.houssam2021.artify.editor.EditorController
import io.github.houssam2021.artify.editor.Tool
import io.github.houssam2021.artify.engine.LayerDraw
import io.github.houssam2021.artify.engine.RenderCache
import io.github.houssam2021.artify.engine.SceneRenderer
import io.github.houssam2021.artify.engine.ShapeDetector
import io.github.houssam2021.artify.engine.StrokePainter
import io.github.houssam2021.artify.model.Camera
import io.github.houssam2021.artify.model.Stroke
import io.github.houssam2021.artify.model.SymmetryMode
import io.github.houssam2021.artify.model.ViewTransform
import io.github.houssam2021.artify.model.WorldTransforms
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The drawing surface.
 *
 * Rendering: committed artwork is rendered on a background thread into
 * bitmaps split around the active layer (below / active / above). While the
 * user pans or zooms those bitmaps are transformed on the GPU, and a sharp
 * re-render is requested once the camera settles. The stroke being drawn is
 * rendered live on the hardware canvas every frame, inside the active layer,
 * with motion prediction to hide input latency.
 */
@SuppressLint("ViewConstructor")
class CanvasView(context: Context, private val ctl: EditorController) : View(context), CanvasHost {

    var camera: Camera = ctl.camera
        private set

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "artify-render").apply { isDaemon = true } }
    private val gen = AtomicInteger()
    private var renderInFlight = false
    private var cache: RenderCache? = null
    /** Strokes committed but not yet present in [cache], with the doc version that added them. */
    private val pending = ArrayList<Pair<Long, Stroke>>()

    private val painter = StrokePainter()
    private val workerPainter = StrokePainter()
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val activePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val cacheMatrix = Matrix()
    private val clip = RectF()
    private val hugeClip = RectF(-1e6f, -1e6f, 1e6f, 1e6f)
    private val dp = resources.displayMetrics.density

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.5f * resources.displayMetrics.density
        pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
    }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f * resources.displayMetrics.density }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 14f * resources.displayMetrics.density; textAlign = Paint.Align.CENTER
    }
    private val hudBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC1C1C1E.toInt() }

    private val predictor: MotionEventPredictor? = try { MotionEventPredictor.newInstance(this) } catch (e: Throwable) { null }

    // ------------------------------------------------------------- gestures
    private enum class Mode { NONE, DRAW, NAV, LASSO, TRANSFORM, EYEDROP, IGNORE }
    private var mode = Mode.NONE
    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f
    private var maxPointers = 0
    private var tapCandidate = false

    private var live: LiveStroke? = null
    private var liveN = 0
    private var drawPointer = -1
    private var drawStylus = false
    private var drawVt: ViewTransform? = null
    private var lastX = 0f
    private var lastY = 0f
    private var shapeAnchorX = 0f
    private var shapeAnchorY = 0f

    private var navCam = camera
    private var navCx = 0f
    private var navCy = 0f
    private var navSpan = 0f
    private var navAngle = 0f
    private var navCount = 0
    private var navMoved = 0f

    private val lassoPts = ArrayList<Float>()

    // selection transform gesture (screen space)
    private var tBaseCx = 0f
    private var tBaseCy = 0f
    private var tBaseSpan = 0f
    private var tBaseAngle = 0f
    private var tCount = 0
    private var tScale = 1f
    private var tAngle = 0f
    private var tDx = 0f
    private var tDy = 0f
    private val selMatrix = Matrix()

    private var eyedropColor = 0
    private var eyedropX = 0f
    private var eyedropY = 0f

    private var hovering = false
    private var hoverX = 0f
    private var hoverY = 0f

    private val longPress = Runnable {
        val ls = live
        if (mode == Mode.DRAW && !drawStylus && ls != null && ls.length * camera.scale < 12 * dp) {
            live = null
            mode = Mode.EYEDROP
            eyedrop(lastX, lastY)
        }
    }

    private val quickShape = Runnable {
        val ls = live ?: return@Runnable
        if (mode != Mode.DRAW || !ctl.settings.quickShape || ls.snapped) return@Runnable
        val shape = ShapeDetector.detect(ls.xs, ls.ys, ls.ps, ls.n) ?: return@Runnable
        ls.replaceWith(shape.xs, shape.ys, shape.ps)
        liveN = ls.n
        performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
        ctl.toast(shape.kind)
        invalidate()
    }

    private val renderRunnable = Runnable { startRender() }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    // --------------------------------------------------------- CanvasHost

    override fun requestRender() = scheduleRender(0)
    override fun invalidateCanvas() = invalidate()
    override fun viewSize(): IntArray = intArrayOf(max(1, width), max(1, height))
    override fun setCamera(camera: Camera) = applyCamera(camera, immediate = true)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ctl.host = this
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (ctl.host === this) ctl.host = null
        gen.incrementAndGet()
        removeCallbacks(renderRunnable)
        executor.shutdown()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        scheduleRender(0)
    }

    // ---------------------------------------------------------- rendering

    private fun scheduleRender(delayMs: Long) {
        removeCallbacks(renderRunnable)
        postDelayed(renderRunnable, delayMs)
    }

    private fun startRender() {
        if (width <= 0 || height <= 0 || executor.isShutdown) return
        val g = gen.incrementAndGet()
        renderInFlight = true
        val plan = ctl.buildPlan()
        val vt = ViewTransform(camera, width, height)
        val version = ctl.docVersion
        executor.execute {
            if (gen.get() != g) return@execute
            val result = try {
                SceneRenderer.renderCache(plan, vt, version, workerPainter) { gen.get() != g }
            } catch (e: OutOfMemoryError) {
                null
            }
            post {
                if (gen.get() == g) {
                    renderInFlight = false
                    if (result != null) {
                        cache = result
                        pending.removeAll { it.first <= result.version }
                        invalidate()
                    }
                }
            }
        }
    }

    private fun applyCamera(c: Camera, immediate: Boolean) {
        camera = c.sanitized()
        ctl.onCameraChanged(camera)
        invalidate()
        val cc = cache
        val ratio = if (cc != null) camera.scale / cc.transform.camera.scale else 1.0
        if (immediate || (!renderInFlight && (ratio > 2.5 || ratio < 0.4))) scheduleRender(0) else scheduleRender(140)
    }

    /** Matrix mapping cache-screen coordinates to current-screen coordinates. */
    private fun cacheToScreen(from: ViewTransform, to: ViewTransform, m: Matrix) {
        val c0 = from.camera; val c1 = to.camera
        val k = (c1.scale / c0.scale).toFloat()
        val dRot = Math.toDegrees(c1.rotation - c0.rotation).toFloat()
        val dx = c0.cx - c1.cx; val dy = c0.cy - c1.cy
        val tx = (to.a * dx - to.b * dy).toFloat()
        val ty = (to.b * dx + to.a * dy).toFloat()
        m.setTranslate(-from.width / 2f, -from.height / 2f)
        m.postRotate(dRot)
        m.postScale(k, k)
        m.postTranslate(to.width / 2f + tx, to.height / 2f + ty)
    }

    override fun onDraw(c: Canvas) {
        val doc = ctl.document
        val vt = ViewTransform(camera, width, height)
        clip.set(0f, 0f, width.toFloat(), height.toFloat())

        if (ctl.playing) {
            c.drawColor(doc.background)
            val frames = ctl.frames()
            if (frames.isNotEmpty()) SceneRenderer.drawLayer(c, LayerDraw(frames[ctl.playFrame % frames.size]), vt, clip, painter)
            drawHud(c, "Frame ${ctl.playFrame % max(1, frames.size) + 1} / ${frames.size} — tap to stop")
            return
        }

        c.drawColor(doc.background)
        val cc = cache
        if (cc != null) {
            cacheToScreen(cc.transform, vt, cacheMatrix)
            val useFull = cc.full != null && !cc.fullStale && live == null && pending.isEmpty()
            if (useFull) {
                c.drawBitmap(cc.full!!, cacheMatrix, bmpPaint)
            } else {
                c.drawBitmap(cc.below, cacheMatrix, bmpPaint)
                val cachedActive = cc.plan.active
                val al = doc.activeLayer?.takeIf { it.id == cachedActive?.id } ?: cachedActive
                if (al != null && al.visible) {
                    SceneRenderer.layerPaintFor(al, p = activePaint)
                    c.saveLayer(null, activePaint)
                    c.drawBitmap(cc.active, cacheMatrix, bmpPaint)
                    for ((_, s) in pending) painter.drawStroke(c, vt, clip, s, al.alphaLock)
                    drawLive(c, vt, al.alphaLock)
                    c.restore()
                }
                cc.above?.let { c.drawBitmap(it, cacheMatrix, bmpPaint) }
            }
        } else {
            // First frame(s) before the background render lands.
            SceneRenderer.drawPlan(c, ctl.buildPlan(), vt, clip, painter)
            drawLive(c, vt, doc.activeLayer?.alphaLock ?: false)
        }

        if (ctl.settings.showGrid) drawGrid(c, vt)
        drawSymmetryGuides(c, vt)
        drawSelection(c, vt)
        drawLasso(c)
        drawEyedrop(c)
        drawCursor(c, vt)
        if (mode == Mode.NAV) {
            val pct = camera.scale * 100
            val zoom = if (pct >= 10) "%,.0f%%".format(pct) else "%.3g%%".format(pct)
            val rot = Math.toDegrees(camera.rotation).roundToInt().let { ((it % 360) + 360) % 360 }
            drawHud(c, if (rot != 0) "$zoom  ·  $rot°" else zoom)
        }
    }

    private fun symmetryMatrices(vt: ViewTransform): List<Matrix> {
        val doc = ctl.document
        if (doc.symmetry == SymmetryMode.OFF) return emptyList()
        val px = vt.sx(doc.symX, doc.symY).toFloat(); val py = vt.sy(doc.symX, doc.symY).toFloat()
        val axisDeg = vt.rotationDeg + 90f
        return WorldTransforms.symmetryOps(doc.symmetry).map { (flip, ang) ->
            Matrix().apply {
                setTranslate(-px, -py)
                if (flip) { postRotate(-axisDeg); postScale(1f, -1f); postRotate(axisDeg) }
                postRotate(Math.toDegrees(ang).toFloat())
                postTranslate(px, py)
            }
        }
    }

    private fun drawLive(c: Canvas, vt: ViewTransform, alphaLock: Boolean) {
        val ls = live ?: return
        val n = liveN.coerceAtMost(ls.xs.size)
        if (n <= 0) return
        val mg = ls.margin()
        fun one(cl: RectF) = painter.draw(
            c, vt, cl, ls.brush, ls.color, ls.size, ls.opacity, ls.eraser, ls.seed, ls.ox, ls.oy,
            ls.xs, ls.ys, ls.ps, n, alphaLock, ls.minX - mg, ls.minY - mg, ls.maxX + mg, ls.maxY + mg,
        )
        one(clip)
        for (m in symmetryMatrices(vt)) {
            c.save(); c.concat(m); one(hugeClip); c.restore()
        }
    }

    private fun drawGrid(c: Canvas, vt: ViewTransform) {
        val bg = ctl.document.background
        val lum = (Color.red(bg) * 0.299 + Color.green(bg) * 0.587 + Color.blue(bg) * 0.114) / 255
        val base = if (lum > 0.5) Color.BLACK else Color.WHITE
        val minPx = 40.0 * dp
        val sp = 10.0.pow(ceil(log10(minPx / vt.scale)))
        val screenSp = sp * vt.scale
        val wb = vt.worldBounds(0.0, 0.0, width.toDouble(), height.toDouble())
        val i0 = floor(wb[0] / sp); val i1 = ceil(wb[2] / sp)
        val j0 = floor(wb[1] / sp); val j1 = ceil(wb[3] / sp)
        if ((i1 - i0 + 1) * (j1 - j0 + 1) > 6000) return
        val fade = ((screenSp - minPx) / (minPx * 3)).coerceIn(0.0, 1.0)
        val pts = FloatArray(((i1 - i0 + 1) * (j1 - j0 + 1) * 2).toInt())
        val major = FloatArray(pts.size)
        var k = 0; var km = 0
        var i = i0
        while (i <= i1) {
            var j = j0
            while (j <= j1) {
                val wx = i * sp; val wy = j * sp
                val x = vt.sx(wx, wy).toFloat(); val y = vt.sy(wx, wy).toFloat()
                val isMajor = (abs(i) % 10 == 0.0) && (abs(j) % 10 == 0.0)
                if (isMajor) { major[km++] = x; major[km++] = y } else { pts[k++] = x; pts[k++] = y }
                j += 1.0
            }
            i += 1.0
        }
        gridPaint.color = base
        gridPaint.alpha = (24 + 26 * fade).toInt()
        gridPaint.strokeWidth = 2.2f * dp
        if (k > 0) c.drawPoints(pts, 0, k, gridPaint)
        gridPaint.alpha = 70
        gridPaint.strokeWidth = 3.2f * dp
        if (km > 0) c.drawPoints(major, 0, km, gridPaint)
    }

    private fun drawSymmetryGuides(c: Canvas, vt: ViewTransform) {
        val doc = ctl.document
        if (doc.symmetry == SymmetryMode.OFF) return
        val px = vt.sx(doc.symX, doc.symY).toFloat(); val py = vt.sy(doc.symX, doc.symY).toFloat()
        val len = hypot(width.toFloat(), height.toFloat()) * 2
        guidePaint.color = 0xCC3D7BFF.toInt()
        val angles: List<Float> = when (doc.symmetry) {
            SymmetryMode.VERTICAL -> listOf(90f)
            SymmetryMode.HORIZONTAL -> listOf(0f)
            SymmetryMode.QUADRANT -> listOf(0f, 90f)
            SymmetryMode.RADIAL -> listOf(90f, 30f, 150f)
            SymmetryMode.OFF -> emptyList()
        }
        for (a in angles) {
            val r = Math.toRadians((a + vt.rotationDeg).toDouble())
            val dx = (cos(r) * len).toFloat(); val dy = (sin(r) * len).toFloat()
            c.drawLine(px - dx, py - dy, px + dx, py + dy, guidePaint)
        }
        fillPaint.color = 0xFF3D7BFF.toInt()
        c.drawCircle(px, py, 5 * dp, fillPaint)
    }

    private fun drawSelection(c: Canvas, vt: ViewTransform) {
        if (!ctl.selectionActive) return
        val layer = ctl.document.activeLayer
        c.save()
        c.concat(selMatrix)
        if (layer != null && layer.visible) {
            SceneRenderer.layerPaintFor(layer, p = activePaint)
            c.saveLayer(null, activePaint)
            for (s in ctl.floating) painter.drawStroke(c, vt, hugeClip, s, false)
            c.restore()
        }
        ctl.floatingBounds()?.let { b ->
            val p = Path()
            val corners = arrayOf(doubleArrayOf(b[0], b[1]), doubleArrayOf(b[2], b[1]), doubleArrayOf(b[2], b[3]), doubleArrayOf(b[0], b[3]))
            corners.forEachIndexed { i, w ->
                val x = vt.sx(w[0], w[1]).toFloat(); val y = vt.sy(w[0], w[1]).toFloat()
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            guidePaint.color = 0xFF3D7BFF.toInt()
            c.drawPath(p, guidePaint)
        }
        c.restore()
    }

    private fun drawLasso(c: Canvas) {
        if (mode != Mode.LASSO || lassoPts.size < 4) return
        val p = Path()
        p.moveTo(lassoPts[0], lassoPts[1])
        var i = 2
        while (i + 1 < lassoPts.size) { p.lineTo(lassoPts[i], lassoPts[i + 1]); i += 2 }
        p.close()
        fillPaint.color = 0x223D7BFF
        c.drawPath(p, fillPaint)
        guidePaint.color = 0xFF3D7BFF.toInt()
        c.drawPath(p, guidePaint)
    }

    private fun drawEyedrop(c: Canvas) {
        if (mode != Mode.EYEDROP) return
        val r = 44 * dp
        val y = eyedropY - r - 24 * dp
        fillPaint.color = eyedropColor
        c.drawCircle(eyedropX, y, r, fillPaint)
        cursorPaint.color = Color.WHITE; cursorPaint.strokeWidth = 4 * dp
        c.drawCircle(eyedropX, y, r, cursorPaint)
        cursorPaint.color = 0x55000000; cursorPaint.strokeWidth = 1.2f * dp
        c.drawCircle(eyedropX, y, r + 2.5f * dp, cursorPaint)
    }

    private fun drawCursor(c: Canvas, vt: ViewTransform) {
        if (!hovering || mode != Mode.NONE || ctl.tool == Tool.LASSO) return
        val r = ctl.brushSize / 2f
        cursorPaint.strokeWidth = 1.2f * dp
        cursorPaint.color = 0x99FFFFFF.toInt(); c.drawCircle(hoverX, hoverY, r + 1f, cursorPaint)
        cursorPaint.color = 0x99000000.toInt(); c.drawCircle(hoverX, hoverY, max(1f, r), cursorPaint)
        if (vt.width == 0) return
    }

    private fun drawHud(c: Canvas, text: String) {
        val w = hudPaint.measureText(text) + 28 * dp
        val h = 32 * dp
        val top = 76 * dp
        c.drawRoundRect(width / 2f - w / 2, top, width / 2f + w / 2, top + h, h / 2, h / 2, hudBg)
        c.drawText(text, width / 2f, top + h / 2 + hudPaint.textSize * 0.35f, hudPaint)
    }

    // ------------------------------------------------------------- touch

    override fun onHoverEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> { hovering = true; hoverX = e.x; hoverY = e.y }
            MotionEvent.ACTION_HOVER_EXIT -> hovering = false
        }
        invalidate()
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (ctl.playing) {
            if (e.actionMasked == MotionEvent.ACTION_UP) ctl.stopPlayback()
            return true
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(e)
            MotionEvent.ACTION_POINTER_DOWN -> onPointerDown(e)
            MotionEvent.ACTION_MOVE -> onMove(e)
            MotionEvent.ACTION_POINTER_UP -> onPointerUp(e)
            MotionEvent.ACTION_UP -> onUp(e)
            MotionEvent.ACTION_CANCEL -> onCancel()
        }
        return true
    }

    private fun isStylus(e: MotionEvent, i: Int): Boolean {
        val t = e.getToolType(i)
        return t == MotionEvent.TOOL_TYPE_STYLUS || t == MotionEvent.TOOL_TYPE_ERASER
    }

    private fun onDown(e: MotionEvent) {
        hovering = false
        downTime = e.eventTime; downX = e.x; downY = e.y
        maxPointers = 1; tapCandidate = true
        lastX = e.x; lastY = e.y
        val stylus = isStylus(e, 0)

        if (ctl.selectionActive) { mode = Mode.TRANSFORM; beginTransform(e, -1); return }
        if (ctl.settings.penOnly && !stylus && e.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER) {
            mode = Mode.NAV; beginNav(e, -1); return
        }
        if (ctl.tool == Tool.LASSO) {
            mode = Mode.LASSO
            lassoPts.clear(); lassoPts += e.x; lassoPts += e.y
            return
        }
        if (stylus && (e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0) {
            mode = Mode.EYEDROP; eyedrop(e.x, e.y); return
        }
        val reason = ctl.drawBlockReason()
        if (reason != null) { ctl.toast(reason); mode = Mode.IGNORE; return }

        mode = Mode.DRAW
        drawStylus = stylus
        drawPointer = e.getPointerId(0)
        if (stylus && Build.VERSION.SDK_INT >= 30) requestUnbufferedDispatch(e)
        val eraser = ctl.tool == Tool.ERASER || e.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
        startStroke(e, eraser)
        predictor?.record(e)
        if (!stylus && ctl.settings.fingerEyedropper) postDelayed(longPress, 480)
        shapeAnchorX = e.x; shapeAnchorY = e.y
        postDelayed(quickShape, 600)
    }

    private fun startStroke(e: MotionEvent, eraser: Boolean) {
        val vt = ViewTransform(camera, width, height)
        drawVt = vt
        val brush = if (eraser && ctl.tool != Tool.ERASER) ctl.eraseBrush else ctl.currentBrush
        val sizePx = if (eraser && ctl.tool != Tool.ERASER) brush.defaultSize else ctl.brushSize
        val opacity = if (eraser && ctl.tool != Tool.ERASER) 1f else ctl.brushOpacity
        val ox = vt.wx(e.x.toDouble(), e.y.toDouble()); val oy = vt.wy(e.x.toDouble(), e.y.toDouble())
        val ls = LiveStroke(
            brush = brush, color = if (eraser) Color.BLACK else ctl.color,
            size = (sizePx / camera.scale).toFloat(), opacity = opacity, eraser = eraser,
            seed = Random.nextInt(), ox = ox, oy = oy, streamline = brush.streamline,
            minDist = (0.35 / camera.scale).toFloat(),
        )
        live = ls
        addSamples(e)
    }

    private fun pressureOf(e: MotionEvent, i: Int, h: Int): Float {
        if (!isStylus(e, i)) return 1f
        val p = (if (h < 0) e.getPressure(i) else e.getHistoricalPressure(i, h)).coerceIn(0f, 1f)
        val g = ctl.settings.pressureGamma
        return if (g == 1f) p else p.toDouble().pow(g.toDouble()).toFloat()
    }

    private fun addSamples(e: MotionEvent) {
        val ls = live ?: return
        val vt = drawVt ?: return
        val i = e.findPointerIndex(drawPointer).takeIf { it >= 0 } ?: return
        fun add(x: Float, y: Float, p: Float) {
            val lx = (vt.wx(x.toDouble(), y.toDouble()) - ls.ox).toFloat()
            val ly = (vt.wy(x.toDouble(), y.toDouble()) - ls.oy).toFloat()
            ls.add(lx, ly, p)
        }
        for (h in 0 until e.historySize) add(e.getHistoricalX(i, h), e.getHistoricalY(i, h), pressureOf(e, i, h))
        add(e.getX(i), e.getY(i), pressureOf(e, i, -1))
        lastX = e.getX(i); lastY = e.getY(i)
        liveN = ls.n
    }

    private fun updatePrediction() {
        val ls = live ?: return
        val vt = drawVt ?: return
        val pe = try { predictor?.predict() } catch (e: Throwable) { null } ?: return
        try {
            val i = pe.findPointerIndex(drawPointer)
            if (i >= 0) {
                val lx = (vt.wx(pe.getX(i).toDouble(), pe.getY(i).toDouble()) - ls.ox).toFloat()
                val ly = (vt.wy(pe.getX(i).toDouble(), pe.getY(i).toDouble()) - ls.oy).toFloat()
                liveN = ls.setPrediction(lx, ly)
            }
        } finally {
            pe.recycle()
        }
    }

    private fun onPointerDown(e: MotionEvent) {
        maxPointers = max(maxPointers, e.pointerCount)
        when (mode) {
            Mode.DRAW -> {
                if (drawStylus) return // palm rejection: ignore touches while the pen is down
                removeCallbacks(longPress); removeCallbacks(quickShape)
                val ls = live
                val young = e.eventTime - downTime < 250
                if (ls != null && young && ls.length * camera.scale < 40 * dp) live = null else finishStroke()
                mode = Mode.NAV; beginNav(e, -1)
            }
            Mode.TRANSFORM -> { bakeTransform(); beginTransform(e, -1) }
            Mode.NAV -> beginNav(e, -1)
            else -> { lassoPts.clear(); mode = Mode.NAV; beginNav(e, -1) }
        }
        invalidate()
    }

    private fun onMove(e: MotionEvent) {
        when (mode) {
            Mode.DRAW -> {
                addSamples(e)
                predictor?.record(e)
                updatePrediction()
                if (hypot(lastX - downX, lastY - downY) > 10 * dp) removeCallbacks(longPress)
                if (hypot(lastX - shapeAnchorX, lastY - shapeAnchorY) > 4 * dp) {
                    shapeAnchorX = lastX; shapeAnchorY = lastY
                    removeCallbacks(quickShape); postDelayed(quickShape, 550)
                }
                invalidate()
            }
            Mode.NAV -> updateNav(e)
            Mode.LASSO -> {
                for (h in 0 until e.historySize) { lassoPts += e.getHistoricalX(0, h); lassoPts += e.getHistoricalY(0, h) }
                lassoPts += e.x; lassoPts += e.y
                invalidate()
            }
            Mode.TRANSFORM -> updateTransform(e)
            Mode.EYEDROP -> eyedrop(e.x, e.y)
            else -> {}
        }
    }

    private fun onPointerUp(e: MotionEvent) {
        val idx = e.actionIndex
        when (mode) {
            Mode.NAV -> beginNav(e, idx)
            Mode.TRANSFORM -> { bakeTransform(); beginTransform(e, idx) }
            Mode.DRAW -> if (e.getPointerId(idx) == drawPointer) {
                cancelledByFlag(e)
                finishStroke(); mode = Mode.IGNORE
            }
            else -> {}
        }
    }

    private fun cancelledByFlag(e: MotionEvent) {
        if (Build.VERSION.SDK_INT >= 33 && (e.flags and MotionEvent.FLAG_CANCELED) != 0) live = null
    }

    private fun onUp(e: MotionEvent) {
        removeCallbacks(longPress); removeCallbacks(quickShape)
        when (mode) {
            Mode.DRAW -> {
                cancelledByFlag(e)
                if (e.getPointerId(0) == drawPointer) addSamples(e)
                live?.finish()
                finishStroke()
            }
            Mode.NAV -> {
                if (tapCandidate && maxPointers >= 2 && e.eventTime - downTime < 320) {
                    when (maxPointers) {
                        2 -> ctl.undo()
                        3 -> ctl.redo()
                        else -> ctl.uiHidden = !ctl.uiHidden
                    }
                }
                scheduleRender(0)
            }
            Mode.LASSO -> finishLasso()
            Mode.TRANSFORM -> bakeTransform()
            Mode.EYEDROP -> {}
            else -> {}
        }
        mode = Mode.NONE
        invalidate()
    }

    private fun onCancel() {
        removeCallbacks(longPress); removeCallbacks(quickShape)
        live = null; lassoPts.clear()
        if (mode == Mode.TRANSFORM) { tScale = 1f; tAngle = 0f; tDx = 0f; tDy = 0f; selMatrix.reset() }
        mode = Mode.NONE
        invalidate()
    }

    private fun finishStroke() {
        val ls = live ?: return
        live = null
        val base = ls.toStroke() ?: return
        val doc = ctl.document
        val drafts = ArrayList<Stroke>()
        drafts += base
        for ((flip, ang) in WorldTransforms.symmetryOps(doc.symmetry)) {
            drafts += base.transformed(doc.symX, doc.symY, 1.0, ang, flip, newSeed = base.seed)
        }
        val before = ctl.docVersion
        val v = ctl.commitStrokes(drafts)
        if (v < 0) { invalidate(); return }
        val layer = ctl.document.activeLayer ?: return
        val committed = layer.strokes.takeLast(drafts.size)
        val cc = cache
        if (cc != null && !renderInFlight && cc.version == before && cc.transform.camera == camera &&
            cc.transform.width == width && cc.transform.height == height && cc.plan.active?.id == layer.id
        ) {
            val canvas = Canvas(cc.active)
            val full = RectF(0f, 0f, width.toFloat(), height.toFloat())
            for (s in committed) painter.drawStroke(canvas, cc.transform, full, s, layer.alphaLock)
            cc.version = v
            if (cc.full != null) { cc.fullStale = true; scheduleRender(0) }
        } else {
            for (s in committed) pending += v to s
            scheduleRender(0)
        }
        invalidate()
    }

    // navigation -----------------------------------------------------------

    private fun pointerStats(e: MotionEvent, skip: Int): FloatArray {
        var sx = 0f; var sy = 0f; var n = 0
        for (i in 0 until e.pointerCount) { if (i == skip) continue; sx += e.getX(i); sy += e.getY(i); n++ }
        if (n == 0) return floatArrayOf(0f, 0f, 0f, 0f, 0f)
        val cx = sx / n; val cy = sy / n
        var span = 0f
        for (i in 0 until e.pointerCount) { if (i == skip) continue; span += hypot(e.getX(i) - cx, e.getY(i) - cy) }
        span /= n
        var angle = 0f
        val idx = (0 until e.pointerCount).filter { it != skip }
        if (idx.size >= 2) angle = atan2(e.getY(idx[1]) - e.getY(idx[0]), e.getX(idx[1]) - e.getX(idx[0]))
        return floatArrayOf(cx, cy, span, angle, n.toFloat())
    }

    private fun beginNav(e: MotionEvent, skip: Int) {
        val s = pointerStats(e, skip)
        navCam = camera; navCx = s[0]; navCy = s[1]; navSpan = s[2]; navAngle = s[3]; navCount = s[4].toInt()
        navMoved = 0f
    }

    private fun updateNav(e: MotionEvent) {
        val s = pointerStats(e, -1)
        if (s[4].toInt() != navCount) { beginNav(e, -1); return }
        val two = navCount >= 2 && navSpan > 4f
        val k = if (two) (s[2] / navSpan).toDouble() else 1.0
        var dTheta = if (two) (s[3] - navAngle).toDouble() else 0.0
        while (dTheta > Math.PI) dTheta -= 2 * Math.PI
        while (dTheta < -Math.PI) dTheta += 2 * Math.PI
        navMoved = max(navMoved, max(hypot(s[0] - navCx, s[1] - navCy), abs(s[2] - navSpan)))
        if (navMoved > 18 * dp) tapCandidate = false

        val vt0 = ViewTransform(navCam, width, height)
        val wx = vt0.wx(navCx.toDouble(), navCy.toDouble()); val wy = vt0.wy(navCx.toDouble(), navCy.toDouble())
        val scale = (navCam.scale * k).coerceIn(Camera.MIN_SCALE, Camera.MAX_SCALE)
        var rot = navCam.rotation + dTheta
        val deg = Math.toDegrees(rot)
        val snapped = (deg / 90.0).roundToInt() * 90.0
        if (abs(deg - snapped) < 4.0) rot = Math.toRadians(snapped)
        val dx = s[0] - width / 2.0; val dy = s[1] - height / 2.0
        val cs = cos(rot); val sn = sin(rot)
        val cam = Camera(wx - (cs * dx + sn * dy) / scale, wy - (-sn * dx + cs * dy) / scale, scale, rot)
        applyCamera(cam, immediate = false)
    }

    // selection transform --------------------------------------------------

    private fun beginTransform(e: MotionEvent, skip: Int) {
        val s = pointerStats(e, skip)
        tBaseCx = s[0]; tBaseCy = s[1]; tBaseSpan = s[2]; tBaseAngle = s[3]; tCount = s[4].toInt()
        tScale = 1f; tAngle = 0f; tDx = 0f; tDy = 0f
        selMatrix.reset()
    }

    private fun updateTransform(e: MotionEvent) {
        val s = pointerStats(e, -1)
        if (s[4].toInt() != tCount) { bakeTransform(); beginTransform(e, -1); return }
        val two = tCount >= 2 && tBaseSpan > 4f
        tScale = if (two) (s[2] / tBaseSpan).coerceIn(0.01f, 100f) else 1f
        var a = if (two) s[3] - tBaseAngle else 0f
        while (a > Math.PI) a -= (2 * Math.PI).toFloat()
        while (a < -Math.PI) a += (2 * Math.PI).toFloat()
        tAngle = a
        tDx = s[0] - tBaseCx; tDy = s[1] - tBaseCy
        selMatrix.setTranslate(-tBaseCx, -tBaseCy)
        selMatrix.postRotate(Math.toDegrees(tAngle.toDouble()).toFloat())
        selMatrix.postScale(tScale, tScale)
        selMatrix.postTranslate(tBaseCx + tDx, tBaseCy + tDy)
        invalidate()
    }

    /** Converts the screen-space gesture into a world-space transform and applies it. */
    private fun bakeTransform() {
        if (tScale == 1f && tAngle == 0f && tDx == 0f && tDy == 0f) return
        val vt = ViewTransform(camera, width, height)
        val px = vt.wx(tBaseCx.toDouble(), tBaseCy.toDouble()); val py = vt.wy(tBaseCx.toDouble(), tBaseCy.toDouble())
        val cs = cos(camera.rotation); val sn = sin(camera.rotation)
        val twx = (cs * tDx + sn * tDy) / camera.scale
        val twy = (-sn * tDx + cs * tDy) / camera.scale
        ctl.transformFloating(px, py, tScale.toDouble(), tAngle.toDouble(), false, twx, twy)
        tScale = 1f; tAngle = 0f; tDx = 0f; tDy = 0f
        selMatrix.reset()
    }

    // lasso / eyedropper ---------------------------------------------------

    private fun finishLasso() {
        if (lassoPts.size >= 6) {
            val vt = ViewTransform(camera, width, height)
            val poly = DoubleArray(lassoPts.size)
            var i = 0
            while (i + 1 < lassoPts.size) {
                poly[i] = vt.wx(lassoPts[i].toDouble(), lassoPts[i + 1].toDouble())
                poly[i + 1] = vt.wy(lassoPts[i].toDouble(), lassoPts[i + 1].toDouble())
                i += 2
            }
            ctl.lassoSelect(poly)
        }
        lassoPts.clear()
    }

    private fun eyedrop(x: Float, y: Float) {
        eyedropX = x; eyedropY = y
        val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(-x, -y)
        val vt = ViewTransform(camera, width, height)
        SceneRenderer.drawPlan(c, ctl.buildPlan(), vt, RectF(x - 1, y - 1, x + 1, y + 1), painter)
        val color = bmp.getPixel(0, 0) or (0xFF shl 24)
        bmp.recycle()
        eyedropColor = color
        ctl.pickColor(color)
        invalidate()
    }
}
