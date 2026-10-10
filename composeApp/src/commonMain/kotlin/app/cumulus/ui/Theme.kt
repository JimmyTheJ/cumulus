package app.cumulus.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class Sky(
    val top: Color,
    val bottom: Color,
    val onSky: Color,
    val muted: Color,
    val card: Color,
    val border: Color,
    val accent: Color,
    val high: Color,
    val low: Color,
    val precip: Color,
    val light: Boolean,
)

fun skyFor(code: Int?, isDay: Boolean): Sky {
    val kind = when (code) {
        0 -> "clear"
        1, 2 -> "partly"
        45, 48 -> "fog"
        71, 73, 75, 77, 85, 86 -> "snow"
        95, 96, 99 -> "storm"
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "rain"
        else -> "cloud"
    }
    return when (kind) {
        "clear" -> if (isDay) dark(0xFF0E5A96, 0xFFE0A15A, 0xFFFFD56A) else dark(0xFF070B16, 0xFF24345C, 0xFFE7E2C8)
        "partly" -> if (isDay) dark(0xFF1A6798, 0xFFD9894A, 0xFFFFD56A) else dark(0xFF0C1428, 0xFF2C3E68, 0xFFE7E2C8)
        "rain" -> dark(0xFF142636, 0xFF3E5870, 0xFF9ECAFF)
        "storm" -> dark(0xFF140E22, 0xFF3A2E55, 0xFFF0C96A)
        "snow" -> if (isDay) light(0xFFD5E3F0, 0xFFF7FAFC) else dark(0xFF1A2430, 0xFF4C5E72, 0xFFE7EEF5)
        "fog" -> if (isDay) light(0xFFD5DDE3, 0xFFEEF2F5) else dark(0xFF2A333A, 0xFF4E5961, 0xFFE6EAED)
        else -> if (isDay) dark(0xFF2A3A48, 0xFF6D8294, 0xFFFFD56A) else dark(0xFF121A24, 0xFF2C3A48, 0xFFE7E2C8)
    }
}

private fun dark(top: Long, bottom: Long, accent: Long): Sky {
    val on = Color(0xFFF7F4EF)
    return Sky(
        top = Color(top),
        bottom = Color(bottom),
        onSky = on,
        muted = on.copy(alpha = 0.68f),
        card = Color(0xFF101820).copy(alpha = 0.78f),
        border = Color.White.copy(alpha = 0.14f),
        accent = Color(accent),
        high = Color(0xFFFFD7A8),
        low = Color(0xFFB9D6FF),
        precip = Color(0xFF9ECAFF),
        light = false,
    )
}

private fun light(top: Long, bottom: Long): Sky {
    val on = Color(0xFF1B2C3D)
    return Sky(
        top = Color(top),
        bottom = Color(bottom),
        onSky = on,
        muted = on.copy(alpha = 0.64f),
        card = Color.White.copy(alpha = 0.82f),
        border = on.copy(alpha = 0.08f),
        accent = Color(0xFFB86E00),
        high = Color(0xFFB45309),
        low = Color(0xFF1D4E89),
        precip = Color(0xFF1D4E89),
        light = true,
    )
}

val TempStyle = TextStyle(
    fontSize = 92.sp,
    lineHeight = 96.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = (-2).sp,
    fontFeatureSettings = "tnum",
)

val TitleStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
val SectionStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
val NumberStyle = TextStyle(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
