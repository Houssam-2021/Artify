package io.github.houssam2021.artify.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.houssam2021.artify.editor.EditorController
import io.github.houssam2021.artify.editor.Tool
import io.github.houssam2021.artify.engine.StrokePainter
import io.github.houssam2021.artify.io.Exporter
import io.github.houssam2021.artify.model.BrushLibrary
import io.github.houssam2021.artify.model.Layer
import io.github.houssam2021.artify.model.LayerBlend
import io.github.houssam2021.artify.model.SymmetryMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ======================================================================= Brushes

@Composable
fun BrushPanel(ctl: EditorController) {
    val current = ctl.currentBrush
    var category by remember { mutableStateOf(current.category) }
    var showSettings by remember { mutableStateOf(false) }
    Text(if (ctl.tool == Tool.ERASER) "Eraser brush" else "Brush library", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        BrushLibrary.categories.forEach { c -> Chip(c, c == category) { category = c } }
    }
    Spacer(Modifier.height(6.dp))
    BrushLibrary.brushes.filter { it.category == category }.forEach { base ->
        val b = ctl.brushFor(base)
        val selected = current.id == b.id
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
                .background(if (selected) Accent.copy(alpha = 0.25f) else Color(0x14FFFFFF))
                .clickable { ctl.selectBrush(base) }.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(b.name, fontSize = 14.sp, modifier = Modifier.width(110.dp), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            val preview by produceState<Bitmap?>(null, b) {
                value = withContext(Dispatchers.Default) { BrushPreviews.get(b, 420, 84) }
            }
            preview?.let { Image(it.asImageBitmap(), null, Modifier.weight(1f).height(42.dp), contentScale = ContentScale.FillBounds) }
                ?: Spacer(Modifier.weight(1f).height(42.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = { showSettings = !showSettings }) {
        Text(if (showSettings) "Hide brush settings" else "Brush settings…")
    }
    if (showSettings) {
        val b = ctl.currentBrush
        LabeledSlider("Streamline", b.streamline) { v -> ctl.editBrush { it.copy(streamline = v) } }
        LabeledSlider("Spacing", b.spacing, 0.01f..2f, { "%.2f".format(it) }) { v -> ctl.editBrush { it.copy(spacing = v) } }
        LabeledSlider("Flow", b.flow, 0.01f..1f) { v -> ctl.editBrush { it.copy(flow = v) } }
        LabeledSlider("Pressure → size", b.pressureSize) { v -> ctl.editBrush { it.copy(pressureSize = v) } }
        LabeledSlider("Pressure → opacity", b.pressureFlow) { v -> ctl.editBrush { it.copy(pressureFlow = v) } }
        LabeledSlider("Taper", b.taper, 0f..10f, { "%.1f".format(it) }) { v -> ctl.editBrush { it.copy(taper = v) } }
        LabeledSlider("Size jitter", b.sizeJitter) { v -> ctl.editBrush { it.copy(sizeJitter = v) } }
        LabeledSlider("Scatter", b.scatter, 0f..3f, { "%.2f".format(it) }) { v -> ctl.editBrush { it.copy(scatter = v) } }
        LabeledSlider("Rotation jitter", b.rotationJitter) { v -> ctl.editBrush { it.copy(rotationJitter = v) } }
        LabeledSlider("Roundness", b.roundness, 0.05f..1f) { v -> ctl.editBrush { it.copy(roundness = v) } }
        LabeledSlider("Angle", b.angleDeg, 0f..180f, { "${it.toInt()}°" }) { v -> ctl.editBrush { it.copy(angleDeg = v) } }
        SwitchRow("Follow stroke direction", b.followStroke) { v -> ctl.editBrush { it.copy(followStroke = v) } }
        TextButton(onClick = { ctl.resetBrush() }) { Text("Reset brush") }
    }
}

// ======================================================================== Colors

private fun toHsv(c: Int): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV(c, it) }
private fun fromHsv(h: FloatArray): Int = android.graphics.Color.HSVToColor(h)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ColorPanel(ctl: EditorController) {
    var hsv by remember { mutableStateOf(toHsv(ctl.color)) }
    LaunchedEffect(ctl.color) { if (fromHsv(hsv) != ctl.color) hsv = toHsv(ctl.color) }
    fun set(h: Float = hsv[0], s: Float = hsv[1], v: Float = hsv[2]) {
        hsv = floatArrayOf(h, s, v); ctl.pickColor(fromHsv(hsv))
    }
    val hueColor = Color(fromHsv(floatArrayOf(hsv[0], 1f, 1f)))

    Text("Color", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
    // Saturation / value square
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                fun upd(o: Offset) {
                    set(s = (o.x / size.width).coerceIn(0f, 1f), v = 1f - (o.y / size.height).coerceIn(0f, 1f))
                }
                detectTapGestures { upd(it) }
            }
            .pointerInput("drag") {
                detectDragGestures(onDragStart = { o ->
                    set(s = (o.x / size.width).coerceIn(0f, 1f), v = 1f - (o.y / size.height).coerceIn(0f, 1f))
                }) { ch, _ ->
                    ch.consume()
                    val o = ch.position
                    set(s = (o.x / size.width).coerceIn(0f, 1f), v = 1f - (o.y / size.height).coerceIn(0f, 1f))
                }
            }
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val p = Offset(hsv[1] * size.width, (1 - hsv[2]) * size.height)
            drawCircle(Color.White, 11.dp.toPx(), p, style = DrawStroke(3.dp.toPx()))
            drawCircle(Color.Black.copy(alpha = 0.4f), 13.dp.toPx(), p, style = DrawStroke(1.dp.toPx()))
        }
    }
    Spacer(Modifier.height(12.dp))
    // Hue bar
    val hues = remember { (0..12).map { Color(fromHsv(floatArrayOf(it * 30f, 1f, 1f))) } }
    Box(
        Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp))
            .pointerInput(Unit) { detectTapGestures { set(h = (it.x / size.width).coerceIn(0f, 1f) * 359.9f) } }
            .pointerInput("drag") {
                detectDragGestures(onDragStart = { set(h = (it.x / size.width).coerceIn(0f, 1f) * 359.9f) }) { ch, _ ->
                    ch.consume(); set(h = (ch.position.x / size.width).coerceIn(0f, 1f) * 359.9f)
                }
            }
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.horizontalGradient(hues))
            val x = hsv[0] / 360f * size.width
            drawCircle(Color.White, size.height / 2 - 2.dp.toPx(), Offset(x, size.height / 2), style = DrawStroke(3.dp.toPx()))
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(ctl.color)).border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(12.dp))
        var hex by remember(ctl.color) { mutableStateOf("%06X".format(ctl.color and 0xFFFFFF)) }
        OutlinedTextField(
            value = hex,
            onValueChange = { v ->
                val clean = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                hex = clean
                if (clean.length == 6) ctl.pickColor(clean.toInt(16) or (0xFF shl 24))
            },
            prefix = { Text("#") }, singleLine = true, modifier = Modifier.width(150.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        )
    }
    if (ctl.recentColors.isNotEmpty()) {
        SectionTitle("Recent")
        SwatchRows(ctl.recentColors.toList(), ctl.color, onPick = { ctl.pickColor(it) })
    }
    SectionTitle("Palette  ·  long-press to remove")
    SwatchRows(ctl.palette.toList(), ctl.color, onPick = { ctl.pickColor(it) }, onLong = { ctl.removeFromPalette(it) },
        trailing = { ctl.addToPalette(ctl.color) })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwatchRows(colors: List<Int>, current: Int, onPick: (Int) -> Unit, onLong: ((Int) -> Unit)? = null, trailing: (() -> Unit)? = null) {
    val items: List<Int?> = colors + if (trailing != null) listOf(null) else emptyList()
    items.chunked(8).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { c ->
                Box(
                    Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                        .background(if (c == null) Color(0x22FFFFFF) else Color(c))
                        .border(if (c == current) 2.dp else 1.dp, if (c == current) Color.White else Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onClick = { if (c == null) trailing?.invoke() else onPick(c) },
                            onLongClick = { if (c != null) onLong?.invoke(c) },
                        ),
                    contentAlignment = Alignment.Center,
                ) { if (c == null) Icon(Icons.Rounded.Add, "Add current color", tint = ChromeFg) }
            }
            repeat(8 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

// ======================================================================== Layers

@Composable
fun LayersPanel(ctl: EditorController) {
    val doc = ctl.document
    var renaming by remember { mutableStateOf<Layer?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (doc.anim.enabled) "Frames" else "Layers", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("${doc.layers.size}/${io.github.houssam2021.artify.model.ArtDocument.MAX_LAYERS}", color = Muted, fontSize = 12.sp)
        ToolButton(Icons.Rounded.Add, "Add layer") { ctl.addLayer() }
    }
    Spacer(Modifier.height(6.dp))
    doc.layers.asReversed().forEach { layer ->
        val active = layer.id == doc.activeLayerId
        LayerRow(ctl, layer, active)
        if (active) LayerOptions(ctl, layer, onRename = { renaming = layer })
    }
    SectionTitle("Background")
    val papers = listOf(0xFFFFFFFF.toInt(), 0xFFF4EBD9.toInt(), 0xFFE8E8E8.toInt(), 0xFF808080.toInt(), 0xFF2B2B2B.toInt(), 0xFF0E0E10.toInt())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        papers.forEach { c ->
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                .border(if (doc.background == c) 3.dp else 1.dp, if (doc.background == c) Accent else Color(0x44FFFFFF), CircleShape)
                .clickable { ctl.setBackground(c) })
        }
        TextButton(onClick = { ctl.setBackground(ctl.color) }) { Text("Use color") }
    }
    renaming?.let { l ->
        TextInputDialog("Rename layer", l.name, onDismiss = { renaming = null }) { renaming = null; ctl.renameLayer(l.id, it) }
    }
}

@Composable
private fun LayerRow(ctl: EditorController, layer: Layer, active: Boolean) {
    val doc = ctl.document
    val thumb by produceState<Bitmap?>(null, layer, doc.background) {
        value = withContext(Dispatchers.Default) { layerThumb(ctl, layer) }
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
            .background(if (active) Accent.copy(alpha = 0.3f) else Color(0x14FFFFFF))
            .clickable { ctl.selectLayer(layer.id) }.padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 54.dp, height = 42.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF3A3A40))) {
            thumb?.let { Image(it.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Fit) }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(layer.name, fontSize = 14.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
            val info = buildList {
                if (layer.blend != LayerBlend.NORMAL) add(layer.blend.label)
                if (layer.opacity < 0.999f) add("${(layer.opacity * 100).toInt()}%")
                if (layer.alphaLock) add("α-lock")
                if (layer.locked) add("locked")
                add("${layer.strokes.size} strokes")
            }.joinToString(" · ")
            Text(info, fontSize = 11.sp, color = Muted, maxLines = 1)
        }
        ToolButton(if (layer.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, "Visibility", size = 38.dp) {
            ctl.toggleVisible(layer.id)
        }
    }
}

private fun layerThumb(ctl: EditorController, layer: Layer): Bitmap? {
    val doc = ctl.document
    val bounds = doc.copy(layers = listOf(layer.copy(visible = true))).contentBounds() ?: return null
    val vt = Exporter.framing(bounds, 120, padding = 0.08)
    val bmp = Bitmap.createBitmap(vt.width, vt.height, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(doc.background)
    io.github.houssam2021.artify.engine.SceneRenderer.drawLayer(
        android.graphics.Canvas(bmp), io.github.houssam2021.artify.engine.LayerDraw(layer.copy(visible = true, opacity = 1f, blend = LayerBlend.NORMAL)),
        vt, android.graphics.RectF(0f, 0f, vt.width.toFloat(), vt.height.toFloat()), StrokePainter(),
    )
    return bmp
}

@Composable
private fun LayerOptions(ctl: EditorController, layer: Layer, onRename: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, bottom = 8.dp)) {
        LabeledSlider("Opacity", layer.opacity, onStart = { ctl.beginEdit() }, onEnd = { ctl.endEdit() }) { ctl.setLayerOpacity(layer.id, it) }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            LayerBlend.entries.forEach { b -> Chip(b.label, layer.blend == b) { ctl.setLayerBlend(layer.id, b) } }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            ToolButton(Icons.Rounded.Opacity, "Alpha lock", selected = layer.alphaLock, size = 40.dp) { ctl.toggleAlphaLock(layer.id) }
            ToolButton(if (layer.locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen, "Lock", selected = layer.locked, size = 40.dp) { ctl.toggleLocked(layer.id) }
            ToolButton(Icons.Rounded.Edit, "Rename", size = 40.dp, onClick = onRename)
            ToolButton(Icons.Rounded.ContentCopy, "Duplicate", size = 40.dp) { ctl.duplicateLayer(layer.id) }
            ToolButton(Icons.Rounded.MergeType, "Merge down", size = 40.dp) { ctl.mergeDown(layer.id) }
            ToolButton(Icons.Rounded.KeyboardArrowUp, "Move up", size = 40.dp) { ctl.moveLayer(layer.id, 1) }
            ToolButton(Icons.Rounded.KeyboardArrowDown, "Move down", size = 40.dp) { ctl.moveLayer(layer.id, -1) }
            ToolButton(Icons.Rounded.CleaningServices, "Clear", size = 40.dp) { ctl.clearLayer(layer.id) }
            ToolButton(Icons.Rounded.Delete, "Delete", size = 40.dp) { ctl.deleteLayer(layer.id) }
        }
        Text("Alpha lock · Lock · Rename · Duplicate · Merge · Up · Down · Clear · Delete", fontSize = 10.sp, color = Muted)
    }
}

// ======================================================================= Actions

@Composable
fun ActionsPanel(ctl: EditorController) {
    val doc = ctl.document
    val s = ctl.settings
    var renaming by remember { mutableStateOf(false) }

    Text("Actions", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

    SectionTitle("Export")
    ActionItem("Save PNG to Gallery") { ctl.exportImage(EditorController.ImageExport.PNG) }
    ActionItem("Save JPEG to Gallery") { ctl.exportImage(EditorController.ImageExport.JPEG) }
    ActionItem("Save transparent PNG") { ctl.exportImage(EditorController.ImageExport.PNG_TRANSPARENT) }
    ActionItem("Save current view (2×)") { ctl.exportImage(EditorController.ImageExport.VIEW_2X) }
    ActionItem("Share image…") { ctl.exportImage(EditorController.ImageExport.SHARE) }
    ActionItem("Timelapse replay video") { ctl.exportTimelapse() }
    if (doc.anim.enabled) ActionItem("Export animation video") { ctl.exportAnimation() }
    ActionItem("Share .artify project file") { ctl.shareProjectFile() }
    Text("Image size (longest side)", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
    Row(Modifier.padding(top = 4.dp)) {
        listOf(1024, 2048, 4096, 8192).forEach { px -> Chip("$px", s.exportSize == px) { ctl.updateSettings { it.copy(exportSize = px) } } }
    }

    SectionTitle("Canvas")
    ActionItem("Rename artwork") { renaming = true }
    ActionItem("Fit artwork to screen") { ctl.fitToContent(); ctl.openPanel = null }
    ActionItem("Reset rotation") { ctl.resetRotation() }
    SwitchRow("Show dot grid", s.showGrid) { v -> ctl.updateSettings { it.copy(showGrid = v) } }
    Text("Symmetry (centered on current view)", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp)) {
        SymmetryMode.entries.forEach { m -> Chip(m.label, doc.symmetry == m) { ctl.setSymmetry(m) } }
    }

    SectionTitle("Animation")
    SwitchRow("Animation Assist (layers are frames)", doc.anim.enabled) { ctl.toggleAnimation() }
    if (doc.anim.enabled) {
        LabeledSlider("Frames per second", doc.anim.fps.toFloat(), 1f..30f, { "${it.toInt()}" }) { v ->
            ctl.setAnim(doc.anim.copy(fps = v.toInt()))
        }
        LabeledSlider("Onion skin frames", doc.anim.onionFrames.toFloat(), 0f..5f, { "${it.toInt()}" }) { v ->
            ctl.setAnim(doc.anim.copy(onionFrames = v.toInt()))
        }
        LabeledSlider("Onion skin opacity", doc.anim.onionOpacity) { v -> ctl.setAnim(doc.anim.copy(onionOpacity = v)) }
    }

    SectionTitle("Preferences")
    SwitchRow("Pen-only drawing (fingers navigate)", s.penOnly) { v -> ctl.updateSettings { it.copy(penOnly = v) } }
    LabeledSlider("Pressure curve", s.pressureGamma, 0.4f..2.5f, {
        when { it < 0.85f -> "Soft"; it > 1.15f -> "Firm"; else -> "Linear" }
    }) { v -> ctl.updateSettings { it.copy(pressureGamma = v) } }
    SwitchRow("QuickShape (draw and hold)", s.quickShape) { v -> ctl.updateSettings { it.copy(quickShape = v) } }
    SwitchRow("Touch and hold for eyedropper", s.fingerEyedropper) { v -> ctl.updateSettings { it.copy(fingerEyedropper = v) } }
    SwitchRow("Sidebar on right (left-handed)", s.leftHanded) { v -> ctl.updateSettings { it.copy(leftHanded = v) } }

    SectionTitle("Gestures")
    Text(
        "• Pinch to zoom & rotate, drag with two fingers to pan — the canvas is infinite\n" +
            "• Two-finger tap: undo · Three-finger tap: redo · Four-finger tap: hide UI\n" +
            "• Draw and hold: snap to line / circle / ellipse\n" +
            "• Touch and hold (finger) or stylus button: eyedropper\n" +
            "• Stylus eraser end erases automatically\n" +
            "• Keyboard: Ctrl+Z / Ctrl+Shift+Z, B / E / S tools, [ ] size, 0 fit, Tab hide UI",
        fontSize = 13.sp, color = ChromeFg, lineHeight = 20.sp,
    )
    Spacer(Modifier.height(8.dp))
    Text("${doc.strokeCount()} strokes · ${doc.layers.size} layers · zoom ${"%.4g".format(ctl.camera.scale * 100)}%", fontSize = 11.sp, color = Muted)

    if (renaming) TextInputDialog("Rename artwork", doc.name, onDismiss = { renaming = false }) { renaming = false; ctl.renameDocument(it) }
}

@Composable
private fun ActionItem(text: String, onClick: () -> Unit) {
    Text(
        text, fontSize = 15.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 10.dp),
    )
}
