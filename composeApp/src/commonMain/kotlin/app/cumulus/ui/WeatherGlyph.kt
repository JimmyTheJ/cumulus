package app.cumulus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.cumulus.model.weatherLabel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WeatherGlyph(code: Int?, isDay: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .semantics { contentDescription = weatherLabel(code) },
    ) {
        drawWeather(code ?: 3, isDay, tint)
    }
}

private fun DrawScope.drawWeather(code: Int, isDay: Boolean, tint: Color) {
    val showers = code in 80..86
    val rain = code in 51..67 || code in 80..82
    val snow = code in 71..77 || code in 85..86
    val thunder = code in 95..99
    val fog = code in 45..48
    val clear = code == 0
    val partly = code in 1..2 || showers
    if (clear || partly) {
        if (isDay) sun(tint) else moon(tint)
    }
    if (!clear) cloud(tint)
    if (fog) fogBands(tint)
    if (rain) drops(tint, freezing = code in 56..57 || code in 66..67)
    if (snow) flakes(tint)
    if (thunder) bolt()
}

private fun DrawScope.sun(tint: Color) {
    val center = Offset(size.width * 0.34f, size.height * 0.34f)
    val radius = size.minDimension * 0.13f
    drawCircle(tint, radius, center)
    val stroke = size.minDimension * 0.035f
    for (index in 0 until 8) {
        val angle = index * PI / 4.0
        val inner = radius * 1.55f
        val outer = radius * 2.25f
        drawLine(
            tint,
            center + Offset((cos(angle) * inner).toFloat(), (sin(angle) * inner).toFloat()),
            center + Offset((cos(angle) * outer).toFloat(), (sin(angle) * outer).toFloat()),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.moon(tint: Color) {
    val center = Offset(size.width * 0.36f, size.height * 0.36f)
    val radius = size.minDimension * 0.16f
    drawCircle(tint, radius, center)
    drawCircle(Color.Black, radius * 0.82f, center + Offset(radius * 0.55f, -radius * 0.25f), blendMode = BlendMode.Clear)
}

private fun DrawScope.cloud(tint: Color) {
    val w = size.width
    val h = size.height
    drawCircle(tint, w * 0.15f, Offset(w * 0.40f, h * 0.50f))
    drawCircle(tint, w * 0.20f, Offset(w * 0.56f, h * 0.44f))
    drawCircle(tint, w * 0.13f, Offset(w * 0.70f, h * 0.52f))
    drawRoundRect(
        tint,
        topLeft = Offset(w * 0.30f, h * 0.50f),
        size = Size(w * 0.46f, h * 0.18f),
        cornerRadius = CornerRadius(h * 0.1f, h * 0.1f),
    )
}

private fun DrawScope.drops(tint: Color, freezing: Boolean) {
    val color = if (freezing) Color(0xFFB9D6FF) else tint.copy(alpha = 0.9f)
    val w = size.width
    val h = size.height
    val stroke = size.minDimension * 0.035f
    listOf(0.40f to 0.74f, 0.55f to 0.80f, 0.68f to 0.74f).forEach { (x, y) ->
        drawLine(color, Offset(w * x, h * y), Offset(w * x - w * 0.04f, h * y + h * 0.12f), stroke, cap = StrokeCap.Round)
    }
}

private fun DrawScope.flakes(tint: Color) {
    val w = size.width
    val h = size.height
    listOf(0.40f to 0.78f, 0.55f to 0.86f, 0.68f to 0.76f).forEach { (x, y) ->
        drawCircle(tint, w * 0.028f, Offset(w * x, h * y))
    }
}

private fun DrawScope.fogBands(tint: Color) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = size.minDimension * 0.045f, cap = StrokeCap.Round)
    listOf(0.72f, 0.84f).forEach { y ->
        drawLine(tint.copy(alpha = 0.85f), Offset(w * 0.28f, h * y), Offset(w * 0.78f, h * y), stroke.width, cap = StrokeCap.Round)
    }
}

private fun DrawScope.bolt() {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.58f, h * 0.46f)
        lineTo(w * 0.44f, h * 0.68f)
        lineTo(w * 0.54f, h * 0.68f)
        lineTo(w * 0.42f, h * 0.92f)
        lineTo(w * 0.70f, h * 0.60f)
        lineTo(w * 0.58f, h * 0.60f)
        close()
    }
    drawPath(path, Color(0xFFF0C96A))
}
