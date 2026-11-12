package io.github.houssam2021.artify.model

/** Shape of a single brush stamp. ROUND is drawn analytically and stays crisp at any zoom. */
enum class BrushTip { ROUND, SOFT, PENCIL, CHARCOAL, BRISTLE, SQUARE, SPLATTER, WATERCOLOR }

/**
 * Complete, self-contained description of how a brush lays down stamps.
 * A copy is stored with every stroke so documents render identically even if
 * the built-in library changes later.
 */
data class BrushSpec(
    val id: String,
    val name: String,
    val category: String,
    val tip: BrushTip,
    /** Distance between stamps as a fraction of the stamp diameter. */
    val spacing: Float = 0.08f,
    /** Alpha of each individual stamp (0..1). */
    val flow: Float = 1f,
    /** 0 = pressure does not affect size, 1 = zero pressure gives zero size. */
    val pressureSize: Float = 0.5f,
    /** 0 = pressure does not affect flow, 1 = zero pressure gives zero flow. */
    val pressureFlow: Float = 0f,
    val sizeJitter: Float = 0f,
    val flowJitter: Float = 0f,
    /** Random sideways offset, in stamp diameters. */
    val scatter: Float = 0f,
    /** Random stamp rotation, as a fraction of a full turn. */
    val rotationJitter: Float = 0f,
    /** Rotate the tip to follow the stroke direction. */
    val followStroke: Boolean = false,
    val angleDeg: Float = 0f,
    /** 1 = round stamp; smaller values squash it (calligraphy nibs). */
    val roundness: Float = 1f,
    /** Length of the start/end taper, in stamp diameters. */
    val taper: Float = 0f,
    val minSize: Float = 1f,
    val maxSize: Float = 100f,
    val defaultSize: Float = 10f,
    val defaultOpacity: Float = 1f,
    /** Stroke stabilisation 0..1. */
    val streamline: Float = 0.3f,
) {
    fun sanitized(): BrushSpec = copy(
        name = name.take(MAX_NAME),
        category = category.take(MAX_NAME),
        id = id.take(MAX_NAME),
        spacing = spacing.finiteOr(0.08f).coerceIn(0.01f, 4f),
        flow = flow.finiteOr(1f).coerceIn(0.005f, 1f),
        pressureSize = pressureSize.finiteOr(0f).coerceIn(0f, 1f),
        pressureFlow = pressureFlow.finiteOr(0f).coerceIn(0f, 1f),
        sizeJitter = sizeJitter.finiteOr(0f).coerceIn(0f, 1f),
        flowJitter = flowJitter.finiteOr(0f).coerceIn(0f, 1f),
        scatter = scatter.finiteOr(0f).coerceIn(0f, 5f),
        rotationJitter = rotationJitter.finiteOr(0f).coerceIn(0f, 1f),
        angleDeg = angleDeg.finiteOr(0f).coerceIn(-360f, 360f),
        roundness = roundness.finiteOr(1f).coerceIn(0.05f, 1f),
        taper = taper.finiteOr(0f).coerceIn(0f, 50f),
        minSize = minSize.finiteOr(1f).coerceIn(0.1f, 2000f),
        maxSize = maxSize.finiteOr(100f).coerceIn(0.1f, 2000f),
        defaultSize = defaultSize.finiteOr(10f).coerceIn(0.1f, 2000f),
        defaultOpacity = defaultOpacity.finiteOr(1f).coerceIn(0f, 1f),
        streamline = streamline.finiteOr(0f).coerceIn(0f, 1f),
    )

    companion object {
        const val MAX_NAME = 64
    }
}

internal fun Float.finiteOr(fallback: Float) = if (isFinite()) this else fallback

object BrushLibrary {
    val brushes: List<BrushSpec> = listOf(
        // Sketching
        BrushSpec("hb_pencil", "HB Pencil", "Sketching", BrushTip.PENCIL, spacing = 0.12f, flow = 0.55f,
            pressureSize = 0.35f, pressureFlow = 0.8f, rotationJitter = 1f, minSize = 1f, maxSize = 30f,
            defaultSize = 5f, streamline = 0.15f),
        BrushSpec("6b_pencil", "6B Pencil", "Sketching", BrushTip.PENCIL, spacing = 0.1f, flow = 0.75f,
            pressureSize = 0.4f, pressureFlow = 0.7f, rotationJitter = 1f, minSize = 2f, maxSize = 60f,
            defaultSize = 12f, streamline = 0.15f),
        BrushSpec("fine_liner", "Fine Liner", "Sketching", BrushTip.ROUND, spacing = 0.06f, flow = 1f,
            pressureSize = 0.15f, minSize = 0.5f, maxSize = 20f, defaultSize = 3f, streamline = 0.35f),
        BrushSpec("charcoal_stick", "Charcoal Stick", "Sketching", BrushTip.CHARCOAL, spacing = 0.12f,
            flow = 0.7f, pressureSize = 0.3f, pressureFlow = 0.6f, rotationJitter = 1f, minSize = 4f,
            maxSize = 120f, defaultSize = 24f, streamline = 0.1f),

        // Inking
        BrushSpec("studio_pen", "Studio Pen", "Inking", BrushTip.ROUND, spacing = 0.05f, pressureSize = 0.85f,
            taper = 3f, minSize = 1f, maxSize = 60f, defaultSize = 10f, streamline = 0.55f),
        BrushSpec("technical_pen", "Technical Pen", "Inking", BrushTip.ROUND, spacing = 0.05f,
            pressureSize = 0.05f, minSize = 0.5f, maxSize = 40f, defaultSize = 5f, streamline = 0.45f),
        BrushSpec("brush_pen", "Brush Pen", "Inking", BrushTip.ROUND, spacing = 0.05f, pressureSize = 1f,
            taper = 6f, minSize = 2f, maxSize = 120f, defaultSize = 18f, streamline = 0.5f),
        BrushSpec("dry_ink", "Dry Ink", "Inking", BrushTip.CHARCOAL, spacing = 0.06f, flow = 0.95f,
            pressureSize = 0.7f, rotationJitter = 1f, taper = 2f, minSize = 1f, maxSize = 80f,
            defaultSize = 9f, streamline = 0.4f),

        // Painting
        BrushSpec("gouache", "Gouache", "Painting", BrushTip.BRISTLE, spacing = 0.06f, flow = 0.55f,
            pressureSize = 0.4f, pressureFlow = 0.5f, followStroke = true, minSize = 4f, maxSize = 200f,
            defaultSize = 40f, streamline = 0.2f),
        BrushSpec("oil_bristle", "Oil Bristle", "Painting", BrushTip.BRISTLE, spacing = 0.04f, flow = 0.85f,
            pressureSize = 0.3f, pressureFlow = 0.3f, followStroke = true, minSize = 4f, maxSize = 200f,
            defaultSize = 36f, streamline = 0.2f),
        BrushSpec("flat_brush", "Flat Brush", "Painting", BrushTip.ROUND, spacing = 0.04f, flow = 0.9f,
            pressureSize = 0.3f, followStroke = true, angleDeg = 90f, roundness = 0.3f, minSize = 4f,
            maxSize = 200f, defaultSize = 30f, streamline = 0.25f),
        BrushSpec("watercolor", "Watercolor", "Painting", BrushTip.WATERCOLOR, spacing = 0.1f, flow = 0.16f,
            pressureSize = 0.3f, pressureFlow = 0.6f, rotationJitter = 1f, minSize = 8f, maxSize = 400f,
            defaultSize = 60f, defaultOpacity = 0.9f, streamline = 0.3f),
        BrushSpec("wet_wash", "Wet Wash", "Painting", BrushTip.SOFT, spacing = 0.08f, flow = 0.08f,
            pressureFlow = 0.8f, pressureSize = 0.2f, minSize = 10f, maxSize = 600f, defaultSize = 120f,
            streamline = 0.3f),

        // Airbrushing
        BrushSpec("soft_air", "Soft Airbrush", "Airbrushing", BrushTip.SOFT, spacing = 0.06f, flow = 0.05f,
            pressureSize = 0f, pressureFlow = 1f, minSize = 10f, maxSize = 800f, defaultSize = 140f,
            streamline = 0.2f),
        BrushSpec("medium_air", "Medium Airbrush", "Airbrushing", BrushTip.SOFT, spacing = 0.06f, flow = 0.12f,
            pressureSize = 0f, pressureFlow = 1f, minSize = 4f, maxSize = 400f, defaultSize = 60f,
            streamline = 0.2f),
        BrushSpec("hard_air", "Hard Airbrush", "Airbrushing", BrushTip.ROUND, spacing = 0.05f, flow = 0.12f,
            pressureSize = 0f, pressureFlow = 1f, minSize = 2f, maxSize = 400f, defaultSize = 40f,
            streamline = 0.2f),

        // Artistic / texture
        BrushSpec("chalk", "Chalk", "Artistic", BrushTip.CHARCOAL, spacing = 0.15f, flow = 0.6f,
            pressureSize = 0.25f, pressureFlow = 0.5f, rotationJitter = 1f, sizeJitter = 0.2f, minSize = 4f,
            maxSize = 150f, defaultSize = 28f, streamline = 0.1f),
        BrushSpec("marker", "Marker", "Artistic", BrushTip.SQUARE, spacing = 0.05f, flow = 1f,
            pressureSize = 0.1f, angleDeg = 45f, roundness = 0.55f, minSize = 2f, maxSize = 120f,
            defaultSize = 20f, defaultOpacity = 0.7f, streamline = 0.35f),
        BrushSpec("splatter", "Splatter", "Artistic", BrushTip.SPLATTER, spacing = 0.9f, flow = 1f,
            pressureSize = 0.4f, sizeJitter = 0.6f, scatter = 1.2f, rotationJitter = 1f, minSize = 10f,
            maxSize = 400f, defaultSize = 60f, streamline = 0f),
        BrushSpec("stipple", "Stipple", "Artistic", BrushTip.ROUND, spacing = 1.4f, flow = 1f,
            pressureSize = 0.5f, sizeJitter = 0.8f, scatter = 2f, minSize = 1f, maxSize = 60f,
            defaultSize = 6f, streamline = 0f),

        // Calligraphy
        BrushSpec("calligraphy", "Calligraphy", "Calligraphy", BrushTip.ROUND, spacing = 0.03f,
            pressureSize = 0.4f, angleDeg = 45f, roundness = 0.18f, taper = 1f, minSize = 4f, maxSize = 120f,
            defaultSize = 24f, streamline = 0.55f),
        BrushSpec("monoline", "Monoline", "Calligraphy", BrushTip.ROUND, spacing = 0.04f, pressureSize = 0f,
            minSize = 1f, maxSize = 60f, defaultSize = 8f, streamline = 0.75f),
        BrushSpec("script", "Script", "Calligraphy", BrushTip.ROUND, spacing = 0.04f, pressureSize = 0.9f,
            taper = 4f, minSize = 2f, maxSize = 80f, defaultSize = 14f, streamline = 0.7f),

        // Erasers (any brush may also be used as an eraser)
        BrushSpec("hard_eraser", "Hard Eraser", "Erasers", BrushTip.ROUND, spacing = 0.05f, pressureSize = 0f,
            minSize = 1f, maxSize = 400f, defaultSize = 30f, streamline = 0.2f),
        BrushSpec("soft_eraser", "Soft Eraser", "Erasers", BrushTip.SOFT, spacing = 0.06f, flow = 0.25f,
            pressureSize = 0f, pressureFlow = 0.6f, minSize = 4f, maxSize = 600f, defaultSize = 100f,
            streamline = 0.2f),
    )

    val categories: List<String> = brushes.map { it.category }.distinct()

    val defaultPaint: BrushSpec = brushes.first { it.id == "studio_pen" }
    val defaultEraser: BrushSpec = brushes.first { it.id == "hard_eraser" }

    fun byId(id: String): BrushSpec? = brushes.firstOrNull { it.id == id }
}
