package io.github.houssam2021.artify.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.HighlightAlt
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.houssam2021.artify.canvas.CanvasView
import io.github.houssam2021.artify.editor.EditorController
import io.github.houssam2021.artify.editor.Panel
import io.github.houssam2021.artify.editor.Tool
import kotlinx.coroutines.delay
import kotlin.math.ln
import kotlin.math.pow

@Composable
fun EditorScreen(ctl: EditorController, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current

    // Immersive drawing: hide system bars while editing.
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val ic = window?.let { WindowCompat.getInsetsController(it, view) }
        ic?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ic?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { ic?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    LaunchedEffect(ctl.pendingIntent) {
        val intent = ctl.pendingIntent ?: return@LaunchedEffect
        ctl.pendingIntent = null
        try { context.startActivity(intent) } catch (e: ActivityNotFoundException) { ctl.toast("No app available") }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(ctl.document.background))) {
        val compact = maxWidth < 600.dp
        androidx.compose.runtime.key(ctl) {
            AndroidView(factory = { CanvasView(it, ctl) }, modifier = Modifier.fillMaxSize())
        }

        if (!ctl.uiHidden) {
            TopBar(ctl, compact, onClose)
            SideBar(ctl, Modifier.align(if (ctl.settings.leftHanded) Alignment.CenterEnd else Alignment.CenterStart))
            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    ctl.selectionActive -> SelectionBar(ctl)
                    ctl.tool == Tool.LASSO -> LassoHint(ctl)
                }
                if (ctl.document.anim.enabled) {
                    Spacer(Modifier.height(8.dp))
                    AnimationBar(ctl)
                }
            }
        }

        ctl.openPanel?.let { panel ->
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                ) { ctl.openPanel = null }
            )
            val width = if (compact) maxWidth - 16.dp else 380.dp
            Box(
                Modifier.align(if (compact) Alignment.TopCenter else Alignment.TopEnd)
                    .statusBarsPadding().padding(top = 60.dp, end = if (compact) 0.dp else 10.dp)
                    .width(width).heightIn(max = maxHeight - 90.dp)
                    .clip(RoundedCornerShape(18.dp)).background(PanelBg)
                    .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(18.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    when (panel) {
                        Panel.BRUSHES -> BrushPanel(ctl)
                        Panel.COLORS -> ColorPanel(ctl)
                        Panel.LAYERS -> LayersPanel(ctl)
                        Panel.ACTIONS, Panel.SETTINGS -> ActionsPanel(ctl)
                    }
                }
            }
        }

        ctl.busy?.let { text ->
            Row(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Color(0xF02C2C31)).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(text, fontSize = 14.sp)
            }
        }

        ctl.message?.let { msg ->
            LaunchedEffect(msg) { delay(2200); if (ctl.message == msg) ctl.message = null }
            Toast(msg, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (ctl.document.anim.enabled) 120.dp else 72.dp))
        }
    }
}

@Composable
private fun TopBar(ctl: EditorController, compact: Boolean, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(24.dp)).background(ChromeBg).padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(Icons.AutoMirrored.Rounded.ArrowBack, "Gallery", onClick = onClose)
            ToolButton(Icons.Rounded.Tune, "Actions", selected = ctl.openPanel == Panel.ACTIONS) { toggle(ctl, Panel.ACTIONS) }
            if (!compact) {
                Text(
                    ctl.document.name, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp).widthIn(max = 220.dp), maxLines = 1,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.clip(RoundedCornerShape(24.dp)).background(ChromeBg).padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(Icons.Rounded.Brush, "Brush", selected = ctl.tool == Tool.BRUSH) { pickTool(ctl, Tool.BRUSH) }
            ToolButton(ArtIcons.Eraser, "Eraser", selected = ctl.tool == Tool.ERASER) { pickTool(ctl, Tool.ERASER) }
            ToolButton(Icons.Rounded.HighlightAlt, "Select", selected = ctl.tool == Tool.LASSO) {
                if (ctl.selectionActive) ctl.commitSelection()
                ctl.tool = if (ctl.tool == Tool.LASSO) Tool.BRUSH else Tool.LASSO
                ctl.openPanel = null
            }
            ToolButton(Icons.Rounded.Layers, "Layers", selected = ctl.openPanel == Panel.LAYERS) { toggle(ctl, Panel.LAYERS) }
            Box(
                Modifier.padding(6.dp).size(32.dp).clip(CircleShape).background(Color(ctl.color))
                    .border(2.dp, if (ctl.openPanel == Panel.COLORS) Accent else Color.White, CircleShape)
                    .clickable { toggle(ctl, Panel.COLORS) }
            )
        }
    }
}

private fun toggle(ctl: EditorController, p: Panel) {
    ctl.openPanel = if (ctl.openPanel == p) null else p
}

private fun pickTool(ctl: EditorController, t: Tool) {
    if (ctl.selectionActive) ctl.commitSelection()
    if (ctl.tool == t) toggle(ctl, Panel.BRUSHES) else { ctl.tool = t; ctl.openPanel = null }
}

@Composable
private fun SideBar(ctl: EditorController, modifier: Modifier) {
    var showSize by remember { mutableStateOf(false) }
    var showOpacity by remember { mutableStateOf(false) }
    val b = ctl.currentBrush
    val lo = b.minSize; val hi = b.maxSize
    val t = if (hi > lo) (ln(ctl.brushSize / lo) / ln(hi / lo)).coerceIn(0f, 1f) else 0f
    Box(modifier.padding(horizontal = 6.dp)) {
        Column(
            Modifier.clip(RoundedCornerShape(24.dp)).background(ChromeBg).padding(vertical = 10.dp, horizontal = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VerticalSlider(t, onChange = { v -> ctl.brushSize = lo * (hi / lo).pow(v) },
                onStart = { showSize = true }, onEnd = { showSize = false })
            Spacer(Modifier.height(14.dp))
            VerticalSlider(ctl.brushOpacity, onChange = { ctl.brushOpacity = it },
                onStart = { showOpacity = true }, onEnd = { showOpacity = false })
            Spacer(Modifier.height(10.dp))
            ToolButton(Icons.AutoMirrored.Rounded.Undo, "Undo", enabled = ctl.canUndo, size = 40.dp) { ctl.undo() }
            ToolButton(Icons.AutoMirrored.Rounded.Redo, "Redo", enabled = ctl.canRedo, size = 40.dp) { ctl.redo() }
        }
        if (showSize || showOpacity) {
            val label = if (showSize) "%.1f px".format(ctl.brushSize) else "${(ctl.brushOpacity * 100).toInt()}%"
            Box(
                Modifier.align(if (ctl.settings.leftHanded) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 56.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xF02C2C31))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (showSize) {
                    val px = ctl.brushSize.coerceAtMost(120f)
                    Box(Modifier.size((px / 2.5f).coerceAtLeast(2f).dp).clip(CircleShape).background(Color(ctl.color)))
                }
                Text(label, fontSize = 13.sp, modifier = Modifier.padding(top = if (showSize) 0.dp else 0.dp))
            }
        }
    }
}

@Composable
private fun SelectionBar(ctl: EditorController) {
    Row(
        Modifier.clip(RoundedCornerShape(26.dp)).background(ChromeBg).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolButton(Icons.Rounded.Flip, "Flip horizontal") { ctl.flipSelection(true) }
        Box(Modifier.rotate(90f)) { ToolButton(Icons.Rounded.Flip, "Flip vertical") { ctl.flipSelection(false) } }
        ToolButton(Icons.Rounded.ContentCopy, "Duplicate") { ctl.duplicateSelection() }
        ToolButton(Icons.Rounded.Delete, "Delete") { ctl.deleteSelection() }
        ToolButton(Icons.Rounded.Close, "Cancel") { ctl.cancelSelection() }
        ToolButton(Icons.Rounded.Check, "Done", selected = true) { ctl.commitSelection() }
    }
}

@Composable
private fun LassoHint(ctl: EditorController) {
    Row(
        Modifier.clip(RoundedCornerShape(26.dp)).background(ChromeBg).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Draw around strokes to select", fontSize = 13.sp, color = Muted)
        Spacer(Modifier.width(8.dp))
        ToolButton(Icons.Rounded.SelectAll, "Select whole layer") { ctl.selectAllOnLayer() }
    }
}

@Composable
private fun AnimationBar(ctl: EditorController) {
    val doc = ctl.document
    Row(
        Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(22.dp)).background(ChromeBg).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolButton(if (ctl.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play") { ctl.togglePlay() }
        Row(Modifier.widthIn(max = 520.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            doc.layers.forEachIndexed { i, l ->
                val active = l.id == doc.activeLayerId
                val playingHere = ctl.playing && ctl.frames().getOrNull(ctl.playFrame)?.id == l.id
                Box(
                    Modifier.padding(3.dp).size(width = 38.dp, height = 44.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (active || playingHere) Accent else Color(0x2AFFFFFF))
                        .clickable { ctl.selectLayer(l.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${i + 1}", fontSize = 13.sp, color = if (l.visible) Color.White else Muted)
                }
            }
        }
        ToolButton(Icons.Rounded.Add, "Add frame") { ctl.addLayer() }
        Text("${doc.anim.fps} fps", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(horizontal = 8.dp))
    }
}
