package io.github.houssam2021.artify.model

import kotlin.math.cos
import kotlin.math.sin

/**
 * Camera over the infinite canvas. World coordinates are doubles; `scale` is
 * screen pixels per world unit. Zoom is clamped to 14 orders of magnitude.
 */
data class Camera(
    val cx: Double = 0.0,
    val cy: Double = 0.0,
    val scale: Double = 1.0,
    val rotation: Double = 0.0,
) {
    fun sanitized(): Camera = Camera(
        cx = if (cx.isFinite()) cx.coerceIn(-MAX_COORD, MAX_COORD) else 0.0,
        cy = if (cy.isFinite()) cy.coerceIn(-MAX_COORD, MAX_COORD) else 0.0,
        scale = if (scale.isFinite()) scale.coerceIn(MIN_SCALE, MAX_SCALE) else 1.0,
        rotation = if (rotation.isFinite()) rotation % (2 * Math.PI) else 0.0,
    )

    companion object {
        const val MIN_SCALE = 1e-6
        const val MAX_SCALE = 1e8
        const val MAX_COORD = 1e12
    }
}

/** Precomputed world <-> screen transform for a camera and viewport. */
class ViewTransform(val camera: Camera, val width: Int, val height: Int) {
    val scale = camera.scale
    val a = scale * cos(camera.rotation)
    val b = scale * sin(camera.rotation)
    private val hw = width / 2.0
    private val hh = height / 2.0
    private val invScale2 = 1.0 / (scale * scale)
    val rotationDeg = Math.toDegrees(camera.rotation).toFloat()

    fun sx(wx: Double, wy: Double) = hw + a * (wx - camera.cx) - b * (wy - camera.cy)
    fun sy(wx: Double, wy: Double) = hh + b * (wx - camera.cx) + a * (wy - camera.cy)
    fun wx(sx: Double, sy: Double): Double {
        val dx = sx - hw; val dy = sy - hh
        return camera.cx + (a * dx + b * dy) * invScale2
    }
    fun wy(sx: Double, sy: Double): Double {
        val dx = sx - hw; val dy = sy - hh
        return camera.cy + (-b * dx + a * dy) * invScale2
    }

    /** World-space AABB of a screen rectangle: [minX, minY, maxX, maxY]. */
    fun worldBounds(l: Double, t: Double, r: Double, btm: Double): DoubleArray {
        val xs = doubleArrayOf(wx(l, t), wx(r, t), wx(r, btm), wx(l, btm))
        val ys = doubleArrayOf(wy(l, t), wy(r, t), wy(r, btm), wy(l, btm))
        return doubleArrayOf(xs.min(), ys.min(), xs.max(), ys.max())
    }
}

enum class LayerBlend(val label: String) {
    NORMAL("Normal"), MULTIPLY("Multiply"), SCREEN("Screen"), OVERLAY("Overlay"),
    DARKEN("Darken"), LIGHTEN("Lighten"), COLOR_DODGE("Color Dodge"), COLOR_BURN("Color Burn"),
    HARD_LIGHT("Hard Light"), SOFT_LIGHT("Soft Light"), DIFFERENCE("Difference"),
    EXCLUSION("Exclusion"), ADD("Add"), HUE("Hue"), SATURATION("Saturation"),
    COLOR("Color"), LUMINOSITY("Luminosity"),
}

data class Layer(
    val id: Long,
    val name: String,
    val strokes: List<Stroke> = emptyList(),
    val visible: Boolean = true,
    val locked: Boolean = false,
    val alphaLock: Boolean = false,
    val opacity: Float = 1f,
    val blend: LayerBlend = LayerBlend.NORMAL,
)

enum class SymmetryMode(val label: String) {
    OFF("Off"), VERTICAL("Vertical"), HORIZONTAL("Horizontal"), QUADRANT("Quadrant"), RADIAL("Radial")
}

data class AnimSettings(
    val enabled: Boolean = false,
    val fps: Int = 8,
    val onionFrames: Int = 1,
    val onionOpacity: Float = 0.35f,
)

data class ArtDocument(
    val id: String,
    val name: String,
    val created: Long,
    val modified: Long,
    val background: Int,
    val layers: List<Layer>,
    val activeLayerId: Long,
    val camera: Camera,
    val nextSeq: Long,
    val nextLayerId: Long,
    val anim: AnimSettings = AnimSettings(),
    val symmetry: SymmetryMode = SymmetryMode.OFF,
    val symX: Double = 0.0,
    val symY: Double = 0.0,
) {
    val activeLayer: Layer? get() = layers.firstOrNull { it.id == activeLayerId }
    val activeIndex: Int get() = layers.indexOfFirst { it.id == activeLayerId }

    fun updateLayer(id: Long, f: (Layer) -> Layer): ArtDocument =
        copy(layers = layers.map { if (it.id == id) f(it) else it })

    fun strokeCount(): Int = layers.sumOf { it.strokes.size }

    /** World bounds of all visible content, or null if empty: [minX, minY, maxX, maxY]. */
    fun contentBounds(includeHidden: Boolean = false, maxSeq: Long = Long.MAX_VALUE): DoubleArray? {
        var minX = Double.POSITIVE_INFINITY; var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY; var maxY = Double.NEGATIVE_INFINITY
        for (l in layers) {
            if (!l.visible && !includeHidden) continue
            for (s in l.strokes) {
                if (s.eraser || s.seq > maxSeq) continue
                minX = minOf(minX, s.ox + s.minX); minY = minOf(minY, s.oy + s.minY)
                maxX = maxOf(maxX, s.ox + s.maxX); maxY = maxOf(maxY, s.oy + s.maxY)
            }
        }
        return if (minX.isFinite() && maxX > minX && maxY > minY) doubleArrayOf(minX, minY, maxX, maxY) else null
    }

    companion object {
        const val MAX_LAYERS = 200
        const val DEFAULT_BACKGROUND = 0xFFFFFFFF.toInt()

        fun new(id: String, name: String, now: Long, background: Int = DEFAULT_BACKGROUND): ArtDocument =
            ArtDocument(
                id = id, name = name, created = now, modified = now, background = background,
                layers = listOf(Layer(id = 1, name = "Layer 1")), activeLayerId = 1,
                camera = Camera(), nextSeq = 1, nextLayerId = 2,
            )
    }
}

/** Builds rotation/flip transforms in world space (used by symmetry and selection). */
object WorldTransforms {
    /** Mirror copies for the symmetry mode, as (flipX, angleRad) pairs about (symX, symY). */
    fun symmetryOps(mode: SymmetryMode): List<Pair<Boolean, Double>> = when (mode) {
        SymmetryMode.OFF -> emptyList()
        SymmetryMode.VERTICAL -> listOf(true to 0.0)
        SymmetryMode.HORIZONTAL -> listOf(true to Math.PI)
        SymmetryMode.QUADRANT -> listOf(true to 0.0, true to Math.PI, false to Math.PI)
        SymmetryMode.RADIAL -> (1 until 6).map { false to it * Math.PI / 3 }
    }
}
