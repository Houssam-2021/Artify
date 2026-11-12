package io.github.houssam2021.artify.editor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.houssam2021.artify.engine.LayerDraw
import io.github.houssam2021.artify.engine.RenderPlan
import io.github.houssam2021.artify.engine.SceneRenderer
import io.github.houssam2021.artify.engine.StrokePainter
import io.github.houssam2021.artify.io.DocumentRepository
import io.github.houssam2021.artify.io.Exporter
import io.github.houssam2021.artify.io.VideoEncoder
import io.github.houssam2021.artify.model.AnimSettings
import io.github.houssam2021.artify.model.ArtDocument
import io.github.houssam2021.artify.model.BrushSpec
import io.github.houssam2021.artify.model.BrushLibrary
import io.github.houssam2021.artify.model.Camera
import io.github.houssam2021.artify.model.Layer
import io.github.houssam2021.artify.model.LayerBlend
import io.github.houssam2021.artify.model.Stroke
import io.github.houssam2021.artify.model.SymmetryMode
import io.github.houssam2021.artify.model.ViewTransform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

enum class Tool { BRUSH, ERASER, LASSO }
enum class Panel { BRUSHES, COLORS, LAYERS, ACTIONS, SETTINGS }

/** Implemented by the canvas view. */
interface CanvasHost {
    fun requestRender()
    fun invalidateCanvas()
    fun setCamera(camera: Camera)
    fun viewSize(): IntArray
}

/**
 * All editor state and operations. The document is immutable; every edit
 * produces a new ArtDocument, which makes undo a simple stack of snapshots
 * (strokes are shared between snapshots, so this is cheap).
 */
class EditorController(
    initial: ArtDocument,
    private val repo: DocumentRepository,
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
    private val appContext: Context,
) {
    var document by mutableStateOf(initial)
        private set
    /** Bumped on every content change; the canvas uses it to validate its caches. */
    var docVersion = 0L
        private set

    private val undoStack = ArrayDeque<ArtDocument>()
    private val redoStack = ArrayDeque<ArtDocument>()
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set

    var tool by mutableStateOf(Tool.BRUSH)
    var paintBrush by mutableStateOf(BrushLibrary.defaultPaint); private set
    var eraseBrush by mutableStateOf(BrushLibrary.defaultEraser); private set
    private val editedBrushes = mutableStateMapOf<String, BrushSpec>()
    private val sizes = mutableStateMapOf<String, Float>()
    private val opacities = mutableStateMapOf<String, Float>()

    var color by mutableIntStateOf(0xFF1B1B1F.toInt())
    val recentColors = mutableStateListOf<Int>()
    val palette = mutableStateListOf<Int>()

    var settings by mutableStateOf(settingsStore.load()); private set
    var openPanel by mutableStateOf<Panel?>(null)
    var message by mutableStateOf<String?>(null)
    var pendingIntent by mutableStateOf<Intent?>(null)
    var busy by mutableStateOf<String?>(null)
    var uiHidden by mutableStateOf(false)

    var playing by mutableStateOf(false); private set
    var playFrame by mutableIntStateOf(0); private set
    private var playJob: Job? = null

    var selectionActive by mutableStateOf(false); private set
    var floating: List<Stroke> = emptyList(); private set
    private var selectionBase: ArtDocument? = null

    var host: CanvasHost? = null
    var camera: Camera = initial.camera.sanitized()
        private set

    private var editBase: ArtDocument? = null
    private var saveJob: Job? = null
    private val saveMutex = Mutex()
    @Volatile private var dirty = false

    init {
        palette.addAll(settingsStore.loadPalette().ifEmpty { DEFAULT_PALETTE })
    }

    // ---------------------------------------------------------------- brushes

    val currentBrush: BrushSpec get() = if (tool == Tool.ERASER) eraseBrush else paintBrush
    private fun key() = (if (tool == Tool.ERASER) "e:" else "p:") + currentBrush.id

    var brushSize: Float
        get() = sizes[key()] ?: currentBrush.defaultSize
        set(v) { sizes[key()] = v.coerceIn(currentBrush.minSize, currentBrush.maxSize) }

    var brushOpacity: Float
        get() = opacities[key()] ?: if (tool == Tool.ERASER) 1f else currentBrush.defaultOpacity
        set(v) { opacities[key()] = v.coerceIn(0.01f, 1f) }

    fun brushFor(spec: BrushSpec): BrushSpec = editedBrushes[spec.id] ?: spec

    fun selectBrush(spec: BrushSpec) {
        val b = brushFor(spec)
        if (tool == Tool.ERASER) eraseBrush = b else { paintBrush = b; if (tool == Tool.LASSO) tool = Tool.BRUSH }
    }

    fun editBrush(f: (BrushSpec) -> BrushSpec) {
        val nb = f(currentBrush).sanitized()
        editedBrushes[nb.id] = nb
        if (tool == Tool.ERASER) eraseBrush = nb else paintBrush = nb
    }

    fun resetBrush() {
        val orig = BrushLibrary.byId(currentBrush.id) ?: return
        editedBrushes.remove(orig.id)
        if (tool == Tool.ERASER) eraseBrush = orig else paintBrush = orig
    }

    // ----------------------------------------------------------------- colors

    fun pickColor(c: Int) { color = c or (0xFF shl 24) }

    private fun rememberColor(c: Int) {
        recentColors.remove(c)
        recentColors.add(0, c)
        while (recentColors.size > 12) recentColors.removeAt(recentColors.lastIndex)
    }

    fun addToPalette(c: Int) {
        if (palette.contains(c) || palette.size >= 60) return
        palette.add(c); settingsStore.savePalette(palette)
    }

    fun removeFromPalette(c: Int) { palette.remove(c); settingsStore.savePalette(palette) }

    // --------------------------------------------------------------- settings

    fun updateSettings(f: (AppSettings) -> AppSettings) {
        settings = f(settings)
        settingsStore.save(settings)
        host?.invalidateCanvas()
    }

    // ------------------------------------------------------------ doc changes

    private fun pushUndo(doc: ArtDocument) {
        undoStack.addLast(doc)
        while (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()
        refreshUndo()
    }

    private fun refreshUndo() { canUndo = undoStack.isNotEmpty() || selectionActive; canRedo = redoStack.isNotEmpty() }

    private fun setDoc(doc: ArtDocument, render: Boolean = true) {
        document = doc.copy(modified = System.currentTimeMillis())
        docVersion++
        scheduleSave()
        if (render) host?.requestRender()
    }

    private fun commit(doc: ArtDocument, undoable: Boolean = true) {
        if (undoable) pushUndo(document)
        setDoc(doc)
    }

    /** Begin a continuous edit (slider drag): one undo step for the whole gesture. */
    fun beginEdit() { if (editBase == null) editBase = document }
    fun endEdit() {
        val base = editBase ?: return
        editBase = null
        if (base !== document) pushUndo(base)
    }
    private fun preview(doc: ArtDocument) {
        if (editBase == null) commit(doc) else setDoc(doc)
    }

    fun drawBlockReason(): String? {
        val l = document.activeLayer ?: return "No layer selected"
        return when {
            playing -> "Stop playback to draw"
            !l.visible -> "This layer is hidden"
            l.locked -> "This layer is locked"
            else -> null
        }
    }

    /** Adds finished strokes to the active layer. Returns the new version, or -1. */
    fun commitStrokes(drafts: List<Stroke>): Long {
        val layer = document.activeLayer ?: return -1
        if (drafts.isEmpty()) return -1
        var seq = document.nextSeq
        val strokes = drafts.map { it.withSeq(seq++) }
        pushUndo(document)
        setDoc(
            document.updateLayer(layer.id) { it.copy(strokes = it.strokes + strokes) }.copy(nextSeq = seq),
            render = false,
        )
        if (!drafts[0].eraser) rememberColor(color)
        return docVersion
    }

    fun undo() {
        if (selectionActive) { cancelSelection(); return }
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(document)
        document = prev; docVersion++
        refreshUndo(); scheduleSave(); host?.requestRender()
    }

    fun redo() {
        if (selectionActive) return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(document)
        document = next; docVersion++
        refreshUndo(); scheduleSave(); host?.requestRender()
    }

    fun toast(msg: String) { message = msg }

    // ----------------------------------------------------------------- layers

    fun addLayer(name: String? = null) {
        val d = document
        if (d.layers.size >= ArtDocument.MAX_LAYERS) { toast("Layer limit reached (${ArtDocument.MAX_LAYERS})"); return }
        val id = d.nextLayerId
        val idx = (d.activeIndex + 1).coerceIn(0, d.layers.size)
        val label = name ?: if (d.anim.enabled) "Frame ${d.layers.size + 1}" else "Layer ${id}"
        val layers = d.layers.toMutableList().apply { add(idx, Layer(id, label)) }
        commit(d.copy(layers = layers, activeLayerId = id, nextLayerId = id + 1))
    }

    fun selectLayer(id: Long) {
        if (selectionActive) commitSelection()
        if (document.activeLayerId != id) commit(document.copy(activeLayerId = id), undoable = false)
    }

    fun toggleVisible(id: Long) = commit(document.updateLayer(id) { it.copy(visible = !it.visible) })
    fun toggleLocked(id: Long) = commit(document.updateLayer(id) { it.copy(locked = !it.locked) })
    fun toggleAlphaLock(id: Long) = commit(document.updateLayer(id) { it.copy(alphaLock = !it.alphaLock) })
    fun setLayerOpacity(id: Long, v: Float) = preview(document.updateLayer(id) { it.copy(opacity = v.coerceIn(0f, 1f)) })
    fun setLayerBlend(id: Long, b: LayerBlend) = commit(document.updateLayer(id) { it.copy(blend = b) })
    fun renameLayer(id: Long, name: String) {
        val clean = name.filter { it >= ' ' }.take(40).ifBlank { return }
        commit(document.updateLayer(id) { it.copy(name = clean) })
    }

    fun clearLayer(id: Long) = commit(document.updateLayer(id) { it.copy(strokes = emptyList()) })

    fun duplicateLayer(id: Long) {
        val d = document
        if (d.layers.size >= ArtDocument.MAX_LAYERS) { toast("Layer limit reached"); return }
        val idx = d.layers.indexOfFirst { it.id == id }
        if (idx < 0) return
        val src = d.layers[idx]
        var seq = d.nextSeq
        val copy = src.copy(id = d.nextLayerId, name = (src.name + " copy").take(40), strokes = src.strokes.map { it.withSeq(seq++) })
        val layers = d.layers.toMutableList().apply { add(idx + 1, copy) }
        commit(d.copy(layers = layers, activeLayerId = copy.id, nextLayerId = d.nextLayerId + 1, nextSeq = seq))
    }

    fun deleteLayer(id: Long) {
        val d = document
        val idx = d.layers.indexOfFirst { it.id == id }
        if (idx < 0) return
        if (d.layers.size == 1) { clearLayer(id); return }
        val layers = d.layers.filterNot { it.id == id }
        val active = if (d.activeLayerId == id) layers[max(0, idx - 1)].id else d.activeLayerId
        commit(d.copy(layers = layers, activeLayerId = active))
    }

    fun moveLayer(id: Long, delta: Int) {
        val d = document
        val idx = d.layers.indexOfFirst { it.id == id }
        val to = idx + delta
        if (idx < 0 || to !in d.layers.indices) return
        val layers = d.layers.toMutableList().apply { add(to, removeAt(idx)) }
        commit(d.copy(layers = layers))
    }

    /**
     * Merges a layer into the one below. Layer opacity is folded into stroke
     * opacity; blend modes and alpha lock of the upper layer are flattened to Normal.
     */
    fun mergeDown(id: Long) {
        val d = document
        val idx = d.layers.indexOfFirst { it.id == id }
        if (idx <= 0) { toast("Nothing below to merge into"); return }
        val upper = d.layers[idx]; val lower = d.layers[idx - 1]
        if (upper.blend != LayerBlend.NORMAL) toast("Blend mode flattened to Normal")
        val folded = if (upper.opacity < 0.999f) upper.strokes.map {
            Stroke(it.brush, it.color, it.size, it.opacity * upper.opacity, it.eraser, it.seed, it.seq, it.ox, it.oy, it.xs, it.ys, it.ps)
        } else upper.strokes
        val merged = lower.copy(strokes = lower.strokes + if (upper.visible) folded else emptyList())
        val layers = d.layers.toMutableList().apply { set(idx - 1, merged); removeAt(idx) }
        commit(d.copy(layers = layers, activeLayerId = merged.id))
    }

    fun setBackground(c: Int) = commit(document.copy(background = c or (0xFF shl 24)))

    // -------------------------------------------------------------- symmetry

    fun setSymmetry(mode: SymmetryMode) {
        commit(document.copy(symmetry = mode, symX = camera.cx, symY = camera.cy), undoable = false)
        host?.invalidateCanvas()
    }

    // ------------------------------------------------------------- selection

    fun lassoSelect(poly: DoubleArray) {
        val layer = document.activeLayer ?: return
        if (layer.locked) { toast("This layer is locked"); return }
        val (sel, rest) = layer.strokes.partition { it.mostlyInside(poly) }
        if (sel.isEmpty()) { toast("Nothing selected"); return }
        startFloating(layer, sel, rest)
    }

    fun selectAllOnLayer() {
        val layer = document.activeLayer ?: return
        if (layer.locked) { toast("This layer is locked"); return }
        if (layer.strokes.isEmpty()) { toast("Layer is empty"); return }
        startFloating(layer, layer.strokes, emptyList())
    }

    private fun startFloating(layer: Layer, sel: List<Stroke>, rest: List<Stroke>) {
        selectionBase = document
        floating = sel
        selectionActive = true
        setDoc(document.updateLayer(layer.id) { it.copy(strokes = rest) })
        refreshUndo()
    }

    fun transformFloating(px: Double, py: Double, scale: Double, angle: Double, flipX: Boolean, tx: Double, ty: Double) {
        floating = floating.map { it.transformed(px, py, scale, angle, flipX, tx, ty) }
        host?.invalidateCanvas()
    }

    fun floatingBounds(): DoubleArray? {
        if (floating.isEmpty()) return null
        var a = Double.MAX_VALUE; var b = Double.MAX_VALUE; var c = -Double.MAX_VALUE; var e = -Double.MAX_VALUE
        for (s in floating) {
            a = min(a, s.ox + s.minX); b = min(b, s.oy + s.minY)
            c = max(c, s.ox + s.maxX); e = max(e, s.oy + s.maxY)
        }
        return doubleArrayOf(a, b, c, e)
    }

    fun flipSelection(horizontal: Boolean) {
        val bb = floatingBounds() ?: return
        val cx = (bb[0] + bb[2]) / 2; val cy = (bb[1] + bb[3]) / 2
        transformFloating(cx, cy, 1.0, if (horizontal) 0.0 else Math.PI, true, 0.0, 0.0)
    }

    fun duplicateSelection() {
        val layer = document.activeLayer ?: return
        var seq = document.nextSeq
        val stamped = floating.map { it.withSeq(seq++) }
        setDoc(document.updateLayer(layer.id) { it.copy(strokes = it.strokes + stamped) }.copy(nextSeq = seq))
        toast("Duplicated — drag to move the copy")
    }

    fun commitSelection() {
        val base = selectionBase ?: return
        val layer = document.activeLayer
        var seq = document.nextSeq
        val placed = floating.map { it.withSeq(seq++) }
        val doc = if (layer != null) document.updateLayer(layer.id) { it.copy(strokes = it.strokes + placed) }.copy(nextSeq = seq) else document
        clearSelection()
        pushUndo(base)
        setDoc(doc)
    }

    fun deleteSelection() {
        val base = selectionBase ?: return
        clearSelection()
        pushUndo(base)
        setDoc(document)
    }

    fun cancelSelection() {
        val base = selectionBase ?: return
        clearSelection()
        setDoc(base)
    }

    private fun clearSelection() {
        selectionBase = null; floating = emptyList(); selectionActive = false
        refreshUndo()
    }

    // -------------------------------------------------------------- animation

    fun toggleAnimation() {
        stopPlayback()
        val on = !document.anim.enabled
        commit(document.copy(anim = document.anim.copy(enabled = on)), undoable = false)
        toast(if (on) "Animation Assist on — each layer is a frame" else "Animation Assist off")
    }

    fun setAnim(a: AnimSettings) = commit(document.copy(anim = a.copy(fps = a.fps.coerceIn(1, 60), onionFrames = a.onionFrames.coerceIn(0, 5))), undoable = false)

    fun frames(): List<Layer> = document.layers.filter { it.visible }

    fun togglePlay() { if (playing) stopPlayback() else startPlayback() }

    private fun startPlayback() {
        val frames = frames()
        if (frames.size < 2) { toast("Add at least two visible frames"); return }
        playing = true
        playJob = scope.launch {
            var i = 0
            while (isActive) {
                val count = frames().size
                if (count == 0) break
                playFrame = i % count
                host?.invalidateCanvas()
                delay(1000L / document.anim.fps.coerceIn(1, 60))
                i++
            }
        }
    }

    fun stopPlayback() {
        playJob?.cancel(); playJob = null
        if (playing) { playing = false; host?.requestRender() }
    }

    // ------------------------------------------------------------- rendering

    fun onCameraChanged(c: Camera) { camera = c; dirty = true }

    fun fitToContent() {
        val host = host ?: return
        val size = host.viewSize()
        val b = document.contentBounds() ?: run { host.setCamera(Camera(rotation = 0.0)); return }
        val vt = Exporter.framing(b, min(size[0], size[1]), padding = 0.08)
        val scale = min(size[0] / ((b[2] - b[0]) * 1.16), size[1] / ((b[3] - b[1]) * 1.16))
        host.setCamera(vt.camera.copy(scale = scale.coerceIn(Camera.MIN_SCALE, Camera.MAX_SCALE)))
    }

    fun resetRotation() { host?.setCamera(camera.copy(rotation = 0.0)) }

    /** What the canvas should draw, split around the active layer (or the current frame). */
    fun buildPlan(doc: ArtDocument = document): RenderPlan {
        val idx = doc.activeIndex
        if (doc.anim.enabled) {
            val below = ArrayList<LayerDraw>()
            val n = doc.anim.onionFrames
            for (k in n downTo 1) {
                val fade = doc.anim.onionOpacity * (1f - (k - 1f) / (n + 1f))
                doc.layers.getOrNull(idx - k)?.let { if (it.visible) below += LayerDraw(it, PREV_TINT, fade) }
                doc.layers.getOrNull(idx + k)?.let { if (it.visible) below += LayerDraw(it, NEXT_TINT, fade) }
            }
            return RenderPlan(doc.background, below, doc.layers.getOrNull(idx), emptyList())
        }
        return RenderPlan(
            doc.background,
            doc.layers.take(max(0, idx)).map { LayerDraw(it) },
            doc.layers.getOrNull(idx),
            doc.layers.drop(idx + 1).map { LayerDraw(it) },
        )
    }

    /** Plan containing every visible layer (or a single frame), for export. */
    private fun exportPlan(doc: ArtDocument, frame: Layer? = null, maxSeq: Long = Long.MAX_VALUE): RenderPlan =
        if (frame != null) RenderPlan(doc.background, listOf(LayerDraw(frame)), null, emptyList(), maxSeq)
        else RenderPlan(doc.background, doc.layers.map { LayerDraw(it) }, null, emptyList(), maxSeq)

    private fun docWithFloating(): ArtDocument {
        val layer = document.activeLayer
        return if (selectionActive && layer != null) document.updateLayer(layer.id) { it.copy(strokes = it.strokes + floating) } else document
    }

    fun renderThumbnail(doc: ArtDocument, maxSide: Int = 512): Bitmap {
        val frame = if (doc.anim.enabled) doc.activeLayer else null
        val b = (if (frame != null) doc.copy(layers = listOf(frame)) else doc).contentBounds()
        if (b == null) {
            val bmp = Bitmap.createBitmap(maxSide, maxSide * 3 / 4, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(doc.background)
            return bmp
        }
        val vt = Exporter.framing(b, maxSide, padding = 0.08)
        return SceneRenderer.renderToBitmap(exportPlan(doc, frame), vt, transparent = false)
    }

    // ----------------------------------------------------------------- export

    enum class ImageExport { PNG, JPEG, PNG_TRANSPARENT, SHARE, VIEW_2X }

    fun exportImage(kind: ImageExport) {
        if (busy != null) return
        val doc = docWithFloating()
        val viewSize = host?.viewSize()
        val cam = camera
        busy = "Exporting image…"
        scope.launch(Dispatchers.Default) {
            try {
                val frame = if (doc.anim.enabled) doc.activeLayer else null
                val bmp: Bitmap = if (kind == ImageExport.VIEW_2X) {
                    val w = (viewSize?.get(0) ?: 1080) * 2; val h = (viewSize?.get(1) ?: 1920) * 2
                    SceneRenderer.renderToBitmap(exportPlan(doc, frame), ViewTransform(cam.copy(scale = cam.scale * 2), w, h), false)
                } else {
                    val bounds = (if (frame != null) doc.copy(layers = listOf(frame)) else doc).contentBounds()
                        ?: run { withContext(Dispatchers.Main) { toast("Nothing to export yet") }; return@launch }
                    val vt = Exporter.framing(bounds, settings.exportSize)
                    SceneRenderer.renderToBitmap(exportPlan(doc, frame), vt, kind == ImageExport.PNG_TRANSPARENT)
                }
                if (kind == ImageExport.SHARE) {
                    val f = Exporter.writeTemp(appContext, doc.name, "png") { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    val intent = Exporter.shareIntent(appContext, f, "image/png")
                    withContext(Dispatchers.Main) { pendingIntent = intent }
                } else {
                    Exporter.saveImageToGallery(appContext, bmp, doc.name, kind != ImageExport.JPEG)
                    withContext(Dispatchers.Main) { toast("Saved to Pictures/Artify") }
                }
                bmp.recycle()
            } catch (e: OutOfMemoryError) {
                withContext(Dispatchers.Main) { toast("Not enough memory — try a smaller export size") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast("Export failed: ${e.message ?: "unknown error"}") }
            } finally {
                withContext(Dispatchers.Main) { busy = null }
            }
        }
    }

    /** Timelapse replay of every stroke in drawing order, as an MP4. */
    fun exportTimelapse() = exportVideo(timelapse = true)

    /** Animation Assist frames (visible layers) as a looping MP4. */
    fun exportAnimation() = exportVideo(timelapse = false)

    private fun exportVideo(timelapse: Boolean) {
        if (busy != null) return
        val doc = docWithFloating()
        val frames = doc.layers.filter { it.visible }
        if (frames.isEmpty() || doc.strokeCount() == 0) { toast("Nothing to export yet"); return }
        busy = "Encoding video…"
        scope.launch(Dispatchers.Default) {
            try {
                val bounds = doc.contentBounds() ?: return@launch
                val vt = Exporter.framing(bounds, 1080, even = true)
                val clip = RectF(0f, 0f, vt.width.toFloat(), vt.height.toFloat())
                val painter = StrokePainter()
                val out = Exporter.tempExportFile(appContext, doc.name + if (timelapse) " timelapse" else " animation", "mp4")
                if (timelapse) {
                    val seqs = doc.layers.filter { it.visible }.flatMap { l -> l.strokes.map { it.seq } }.sorted()
                    val fps = 30
                    val steps = seqs.size.coerceIn(30, fps * 20)
                    val hold = fps * 2
                    VideoEncoder.encode(out, vt.width, vt.height, fps, steps + hold, { p -> busy = "Encoding timelapse ${(p * 100).toInt()}%" }) { c: Canvas, i: Int ->
                        val k = min(steps - 1, i)
                        val maxSeq = seqs[((k + 1L) * seqs.size / steps - 1).toInt().coerceIn(0, seqs.size - 1)]
                        SceneRenderer.drawPlan(c, exportPlan(doc, maxSeq = maxSeq), vt, clip, painter)
                    }
                } else {
                    val fps = doc.anim.fps
                    val loops = max(1, (fps * 3 + frames.size - 1) / frames.size)
                    VideoEncoder.encode(out, vt.width, vt.height, fps, frames.size * loops, { p -> busy = "Encoding animation ${(p * 100).toInt()}%" }) { c: Canvas, i: Int ->
                        SceneRenderer.drawPlan(c, exportPlan(doc, frames[i % frames.size]), vt, clip, painter)
                    }
                }
                Exporter.saveVideoToGallery(appContext, out, out.nameWithoutExtension)
                val intent = Exporter.shareIntent(appContext, out, "video/mp4")
                withContext(Dispatchers.Main) { toast("Saved to Movies/Artify"); pendingIntent = intent }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast("Video export failed: ${e.message ?: "unknown error"}") }
            } finally {
                withContext(Dispatchers.Main) { busy = null }
            }
        }
    }

    /** Shares the native .artify file (layers and strokes intact). */
    fun shareProjectFile() {
        if (busy != null) return
        busy = "Preparing file…"
        scope.launch {
            try {
                saveNow(force = true)
                val f = withContext(Dispatchers.IO) {
                    val src = repo.fileFor(document.id)
                    val dst = Exporter.tempExportFile(appContext, document.name, "artify")
                    src.copyTo(dst, overwrite = true)
                }
                pendingIntent = Exporter.shareIntent(appContext, f, "application/octet-stream")
            } catch (e: Exception) {
                toast("Could not share: ${e.message ?: "unknown error"}")
            } finally {
                busy = null
            }
        }
    }

    // ------------------------------------------------------------------- save

    fun renameDocument(name: String) {
        val clean = name.filter { it >= ' ' }.take(60).ifBlank { return }
        commit(document.copy(name = clean), undoable = false)
    }

    fun scheduleSave() {
        dirty = true
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1500)
            saveNow()
        }
    }

    suspend fun saveNow(force: Boolean = false) {
        if (!dirty && !force) return
        val doc = docWithFloating().copy(camera = camera)
        dirty = false
        try {
            withContext(Dispatchers.IO) {
                saveMutex.withLock {
                    val thumb = try { renderThumbnail(doc) } catch (e: OutOfMemoryError) { null }
                    repo.save(doc, thumb)
                    thumb?.recycle()
                }
            }
        } catch (e: Exception) {
            dirty = true
            withContext(Dispatchers.Main) { toast("Autosave failed: ${e.message ?: "unknown error"}") }
        }
    }

    suspend fun close() {
        stopPlayback()
        if (selectionActive) commitSelection()
        saveJob?.cancel()
        saveNow()
    }

    companion object {
        const val MAX_UNDO = 250
        val PREV_TINT = 0xFFE5484D.toInt()
        val NEXT_TINT = 0xFF2F9E6E.toInt()
        val DEFAULT_PALETTE: List<Int> = listOf(
            0xFF000000, 0xFF3A3A3C, 0xFF8E8E93, 0xFFD1D1D6, 0xFFFFFFFF,
            0xFF7A1F1F, 0xFFD32F2F, 0xFFFF7043, 0xFFFFB300, 0xFFFFEB3B,
            0xFF33691E, 0xFF43A047, 0xFF26A69A, 0xFF0288D1, 0xFF1A237E,
            0xFF5E35B1, 0xFFAB47BC, 0xFFEC407A, 0xFF8D6E63, 0xFFF5DEB3,
        ).map { it.toInt() }
    }
}
