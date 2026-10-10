package app.cumulus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import app.cumulus.model.UnitPrefs
import app.cumulus.model.formatTemperature

@Composable
fun GlassCard(
    sky: Sky,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(sky.card)
            .border(1.dp, sky.border, RoundedCornerShape(22.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, sky: Sky, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, color = sky.muted, style = SectionStyle)
}

@Composable
fun PillRow(
    options: List<String>,
    selected: Int,
    sky: Sky,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(sky.onSky.copy(alpha = 0.08f))
            .padding(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (active) sky.accent else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (active) Color(0xFF1B1408) else sky.onSky,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
fun Metric(label: String, value: String, sky: Sky, detail: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 8.dp)) {
        Text(label.uppercase(), color = sky.muted, style = SectionStyle, fontSize = 11.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = sky.onSky, fontSize = 18.sp, style = NumberStyle)
        if (detail != null) {
            Text(detail, color = sky.muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun TempRangeBar(
    low: Double,
    high: Double,
    scaleMin: Double,
    scaleMax: Double,
    sky: Sky,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.height(8.dp).fillMaxWidth()) {
        val span = (scaleMax - scaleMin).takeIf { it > 0.05 } ?: 1.0
        val start = ((low - scaleMin) / span).toFloat().coerceIn(0f, 1f)
        val end = ((high - scaleMin) / span).toFloat().coerceIn(start, 1f)
        drawRoundRect(sky.onSky.copy(alpha = 0.12f), cornerRadius = CornerRadius(8f, 8f))
        val left = start * size.width
        val width = ((end - start) * size.width).coerceAtLeast(8f)
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(sky.low, sky.high)),
            topLeft = Offset(left, 0f),
            size = Size(width, size.height),
            cornerRadius = CornerRadius(8f, 8f),
        )
    }
}

data class RangePoint(val label: String, val low: Double?, val high: Double?)

@Composable
fun RangeChart(points: List<RangePoint>, sky: Sky, units: UnitPrefs, modifier: Modifier = Modifier) {
    val lows = points.mapNotNull { it.low }
    val highs = points.mapNotNull { it.high }
    if (lows.isEmpty() || highs.isEmpty()) return
    val min = lows.min()
    val max = highs.max()
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTemperature(max, units), color = sky.high, fontSize = 11.sp, style = NumberStyle)
            Text(formatTemperature(min, units), color = sky.low, fontSize = 11.sp, style = NumberStyle)
        }
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(132.dp)) {
            val span = (max - min).takeIf { it > 0.05 } ?: 1.0
            val gap = size.width / points.size.coerceAtLeast(1)
            points.forEachIndexed { index, point ->
                val low = point.low ?: return@forEachIndexed
                val high = point.high ?: return@forEachIndexed
                val x = gap * index + gap / 2f
                fun y(value: Double) = (size.height * (1.0 - (value - min) / span)).toFloat()
                drawLine(
                    brush = Brush.verticalGradient(listOf(sky.high, sky.low)),
                    start = Offset(x, y(high)),
                    end = Offset(x, y(low)),
                    strokeWidth = (gap * 0.42f).coerceIn(3f, 16f),
                    cap = StrokeCap.Round,
                )
            }
        }
        val step = if (points.size <= 12) 1 else ((points.size - 1) / 6).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            points.forEachIndexed { index, point ->
                val show = index == points.lastIndex || index % step == 0
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        if (show) point.label else "",
                        color = sky.muted,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun LineChart(values: List<Double?>, sky: Sky, modifier: Modifier = Modifier) {
    val present = values.mapIndexedNotNull { index, value -> value?.let { index to it } }
    if (present.size < 2) return
    val min = present.minOf { it.second }
    val max = present.maxOf { it.second }
    Canvas(modifier.height(120.dp).fillMaxWidth()) {
        val span = (max - min).takeIf { it > 0.05 } ?: 1.0
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        for (i in 0 until present.lastIndex) {
            val (ia, a) = present[i]
            val (ib, b) = present[i + 1]
            fun y(value: Double) = (size.height * (1.0 - (value - min) / span)).toFloat()
            drawLine(
                sky.accent,
                Offset(step * ia, y(a)),
                Offset(step * ib, y(b)),
                strokeWidth = 3f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun LoadingMark(sky: Sky, label: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = sky.accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(12.dp))
        Text(label, color = sky.muted)
    }
}

@Composable
fun IconButton(sky: Sky, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(sky.onSky.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun Chevron(sky: Sky, left: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        val y = size.height / 2f
        if (left) {
            drawLine(sky.onSky, Offset(size.width * 0.7f, size.height * 0.2f), Offset(size.width * 0.3f, y), 2.4f, StrokeCap.Round)
            drawLine(sky.onSky, Offset(size.width * 0.3f, y), Offset(size.width * 0.7f, size.height * 0.8f), 2.4f, StrokeCap.Round)
        } else {
            drawLine(sky.onSky, Offset(size.width * 0.3f, size.height * 0.2f), Offset(size.width * 0.7f, y), 2.4f, StrokeCap.Round)
            drawLine(sky.onSky, Offset(size.width * 0.7f, y), Offset(size.width * 0.3f, size.height * 0.8f), 2.4f, StrokeCap.Round)
        }
    }
}

@Composable
fun Gear(sky: Sky, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val inner = size.minDimension * 0.28f
        val outer = size.minDimension * 0.48f
        repeat(8) { tooth ->
            val angle = tooth * PI / 4.0
            val c = cos(angle).toFloat()
            val s = sin(angle).toFloat()
            drawLine(
                sky.onSky,
                Offset(center.x + c * inner, center.y + s * inner),
                Offset(center.x + c * outer, center.y + s * outer),
                strokeWidth = size.minDimension * 0.14f,
                cap = StrokeCap.Butt,
            )
        }
        drawCircle(sky.onSky, size.minDimension * 0.24f, style = Stroke(2.2f))
        drawCircle(sky.onSky, size.minDimension * 0.08f, style = Stroke(1.8f))
    }
}

@Composable
fun RefreshMark(sky: Sky, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        val stroke = 2.2f
        val inset = stroke
        drawArc(
            sky.onSky,
            startAngle = 40f,
            sweepAngle = 280f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        val end = (40.0 + 280.0) * PI / 180.0
        val radius = size.minDimension / 2f - inset
        val tip = Offset(
            size.width / 2f + (cos(end) * radius).toFloat(),
            size.height / 2f + (sin(end) * radius).toFloat(),
        )
        val tangent = Offset(-sin(end).toFloat(), cos(end).toFloat())
        val normal = Offset(-tangent.y, tangent.x)
        val length = size.minDimension * 0.22f
        val back = Offset(tip.x - tangent.x * length, tip.y - tangent.y * length)
        drawLine(sky.onSky, tip, Offset(back.x + normal.x * length * 0.7f, back.y + normal.y * length * 0.7f), stroke, StrokeCap.Round)
        drawLine(sky.onSky, tip, Offset(back.x - normal.x * length * 0.7f, back.y - normal.y * length * 0.7f), stroke, StrokeCap.Round)
    }
}
