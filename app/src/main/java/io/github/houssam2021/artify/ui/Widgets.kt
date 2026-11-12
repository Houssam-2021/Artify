package io.github.houssam2021.artify.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.houssam2021.artify.engine.StrokePainter
import io.github.houssam2021.artify.model.BrushSpec
import io.github.houssam2021.artify.model.Camera
import io.github.houssam2021.artify.model.Stroke
import io.github.houssam2021.artify.model.ViewTransform
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun ToolButton(
    icon: ImageVector, desc: String, selected: Boolean = false, enabled: Boolean = true,
    size: Dp = 44.dp, onClick: () -> Unit,
) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (selected) Accent.copy(alpha = 0.85f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = if (!enabled) Muted.copy(alpha = 0.4f) else if (selected) Color.White else ChromeFg, modifier = Modifier.size(24.dp))
    }
}

/** Relative-drag vertical slider (drag up to increase), Procreate style. */
@Composable
fun VerticalSlider(
    value: Float, onChange: (Float) -> Unit, onStart: () -> Unit = {}, onEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    Box(
        modifier.width(34.dp).height(150.dp).clip(RoundedCornerShape(17.dp)).background(Color(0x33FFFFFF))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { onStart() },
                    onDragEnd = { onEnd() },
                    onDragCancel = { onEnd() },
                ) { ch, dy ->
                    ch.consume()
                    change((current - dy / size.height).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(value.coerceIn(0.04f, 1f)).clip(RoundedCornerShape(17.dp)).background(Color(0x99FFFFFF)))
    }
}

@Composable
fun LabeledSlider(
    label: String, value: Float, range: ClosedFloatingPointRange<Float> = 0f..1f,
    display: (Float) -> String = { "${(it * 100).toInt()}%" },
    onStart: () -> Unit = {}, onEnd: () -> Unit = {}, onChange: (Float) -> Unit,
) {
    val started = androidx.compose.runtime.remember { BooleanArray(1) }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = ChromeFg, modifier = Modifier.weight(1f))
            Text(display(value), fontSize = 12.sp, color = Muted)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive), valueRange = range,
            onValueChange = { if (!started[0]) { started[0] = true; onStart() }; onChange(it) },
            onValueChangeFinished = { started[0] = false; onEnd() },
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Accent),
            modifier = Modifier.height(30.dp),
        )
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = Muted,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(end = 6.dp, bottom = 6.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) Accent else Color(0x22FFFFFF))
            .border(1.dp, if (selected) Accent else Color(0x22FFFFFF), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(text, fontSize = 13.sp, color = if (selected) Color.White else ChromeFg) }
}

/** Renders brush preview strokes for the brush library (cached per brush value). */
object BrushPreviews {
    private val cache = HashMap<BrushSpec, Bitmap>()

    fun get(brush: BrushSpec, w: Int, h: Int): Bitmap = synchronized(cache) {
        cache.getOrPut(brush) {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val n = 90
            val xs = FloatArray(n); val ys = FloatArray(n); val ps = FloatArray(n)
            for (i in 0 until n) {
                val t = i / (n - 1f)
                xs[i] = w * (0.08f + 0.84f * t)
                ys[i] = h * (0.5f + 0.28f * sin(t * 2 * PI).toFloat())
                ps[i] = sin(t * PI).toFloat().coerceIn(0.05f, 1f)
            }
            val size = (h * 0.28f).coerceAtMost(brush.maxSize * 2f).coerceAtLeast(2f)
            val stroke = Stroke(brush, 0xFFFFFFFF.toInt(), size, 1f, false, 42, 0, 0.0, 0.0, xs, ys, ps)
            val vt = ViewTransform(Camera(w / 2.0, h / 2.0, 1.0, 0.0), w, h)
            StrokePainter().drawStroke(Canvas(bmp), vt, RectF(0f, 0f, w.toFloat(), h.toFloat()), stroke, false)
            bmp
        }
    }
}

object ArtIcons {
    val Eraser: ImageVector by lazy {
        ImageVector.Builder("Eraser", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(7f, 21f); lineTo(3.6f, 17.6f)
                curveTo(2.8f, 16.8f, 2.8f, 15.6f, 3.6f, 14.8f)
                lineTo(13.8f, 4.6f)
                curveTo(14.6f, 3.8f, 15.8f, 3.8f, 16.6f, 4.6f)
                lineTo(20.4f, 8.4f)
                curveTo(21.2f, 9.2f, 21.2f, 10.4f, 20.4f, 11.2f)
                lineTo(10.6f, 21f)
                close()
                moveTo(7f, 21f); lineTo(21f, 21f)
                moveTo(9f, 9.4f); lineTo(15.6f, 16f)
            }
        }.build()
    }
}
