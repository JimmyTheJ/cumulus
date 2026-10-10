package app.cumulus.model

import kotlin.math.exp
import kotlin.math.pow

fun weatherLabel(code: Int?): String = when (code) {
    null -> "Unknown"
    0 -> "Clear"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    61 -> "Light rain"
    63 -> "Rain"
    65 -> "Heavy rain"
    66, 67 -> "Freezing rain"
    71 -> "Light snow"
    73 -> "Snow"
    75 -> "Heavy snow"
    77 -> "Snow grains"
    80 -> "Light showers"
    81 -> "Showers"
    82 -> "Heavy showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunderstorm with hail"
    else -> "Unknown"
}

/**
 * Environment Canada wind chill, valid at or below 10°C with wind of at least 5 km/h.
 * Returns null when the formula does not apply or the result is not colder than the air.
 */
fun windChillCelsius(tempC: Double, windKmh: Double): Double? {
    if (tempC > 10.0 || windKmh < 5.0) return null
    val windPower = windKmh.pow(0.16)
    val chill = 13.12 + 0.6215 * tempC - 11.37 * windPower + 0.3965 * tempC * windPower
    return if (chill < tempC) chill else null
}

/**
 * Environment Canada humidex. [dewPointC] is the dew point, not relative humidity.
 */
fun humidex(tempC: Double, dewPointC: Double): Double {
    val dewKelvin = 273.15 + dewPointC
    val vapourPressure = 6.11 * exp(5417.7530 * ((1.0 / 273.16) - (1.0 / dewKelvin)))
    return tempC + 0.5555 * (vapourPressure - 10.0)
}

data class FeelReport(
    val apparentC: Double?,
    val windChillC: Double?,
    val humidexC: Double?,
)

fun feelReport(
    tempC: Double?,
    apparentC: Double?,
    windKmh: Double?,
    dewPointC: Double?,
): FeelReport {
    val chill = if (tempC != null && windKmh != null) windChillCelsius(tempC, windKmh) else null
    val humidexValue = if (tempC != null && dewPointC != null) humidex(tempC, dewPointC) else null
    return FeelReport(
        apparentC = apparentC,
        windChillC = chill,
        humidexC = humidexValue?.takeIf { it >= 25.0 },
    )
}

fun lowWindChill(hours: List<HourPoint>): Double? =
    hours.mapNotNull { hour ->
        val temp = hour.temperatureC ?: return@mapNotNull null
        val wind = hour.windKmh ?: return@mapNotNull null
        windChillCelsius(temp, wind)
    }.minOrNull()

fun peakHumidex(hours: List<HourPoint>): Double? =
    hours.mapNotNull { hour ->
        val temp = hour.temperatureC ?: return@mapNotNull null
        val dew = hour.dewPointC ?: return@mapNotNull null
        humidex(temp, dew)
    }.maxOrNull()?.takeIf { it >= 25.0 }

data class IndexReading(val title: String, val value: String, val detail: String)

fun uvReading(uv: Double?): IndexReading {
    if (uv == null) return IndexReading("UV", "—", "Unavailable")
    val label = when {
        uv < 3 -> "Low"
        uv < 6 -> "Moderate"
        uv < 8 -> "High"
        uv < 11 -> "Very high"
        else -> "Extreme"
    }
    return IndexReading("UV", formatFixed(uv, if (uv < 10) 1 else 0), label)
}

data class AqiReading(val title: String, val value: String, val detail: String, val scale: String)

fun aqiReading(air: AirSnapshot?): AqiReading? {
    if (air == null) return null
    val us = air.usAqi
    if (us != null) {
        val label = when {
            us <= 50 -> "Good"
            us <= 100 -> "Moderate"
            us <= 150 -> "Sensitive groups"
            us <= 200 -> "Unhealthy"
            us <= 300 -> "Very unhealthy"
            else -> "Hazardous"
        }
        return AqiReading("Air quality", formatFixed(us, 0), label, "US AQI")
    }
    val european = air.europeanAqi ?: return null
    val label = when {
        european <= 20 -> "Good"
        european <= 40 -> "Fair"
        european <= 60 -> "Moderate"
        european <= 80 -> "Poor"
        european <= 100 -> "Very poor"
        else -> "Extremely poor"
    }
    return AqiReading("Air quality", formatFixed(european, 0), label, "European AQI")
}

data class PollenReport(val available: Boolean, val value: String, val detail: String)

/**
 * Coarse bands for CAMS grains/m³. Pollen is only modelled for Europe; a null reading
 * means the data source has no value, which is different from a measured zero.
 */
fun pollenReport(readings: List<PollenReading>): PollenReport {
    if (readings.isEmpty()) {
        return PollenReport(false, "—", "Unavailable")
    }
    val top = readings.maxBy { it.grainsPerCubicMetre }
    val amount = top.grainsPerCubicMetre
    val band = when {
        amount < 20 -> "Low"
        amount < 50 -> "Moderate"
        amount < 100 -> "High"
        else -> "Very high"
    }
    val detail = if (amount <= 0.0) {
        "None detected"
    } else {
        "$band · ${top.name} ${formatFixed(amount, if (amount < 10) 1 else 0)}"
    }
    return PollenReport(true, band, detail)
}

data class BugReport(val score: Double, val label: String)

/**
 * A rough biting-insect activity estimate from temperature, humidity, and wind.
 * It is not a trap count. Mosquitoes are weak fliers and quiet down in strong wind,
 * and they are uncommon below about 10°C or in very dry air.
 */
fun bugReport(tempC: Double?, humidity: Double?, windKmh: Double?): BugReport? {
    if (tempC == null || humidity == null || windKmh == null) return null
    val temperatureScore = when {
        tempC < 8.0 || tempC > 38.0 -> 0.0
        tempC < 12.0 -> 1.5
        tempC < 18.0 -> 4.0
        tempC < 30.0 -> 8.5
        tempC < 34.0 -> 5.5
        else -> 3.0
    }
    val humidityFactor = ((humidity - 35.0) / 50.0).coerceIn(0.25, 1.0)
    val windFactor = when {
        windKmh < 8.0 -> 1.0
        windKmh < 16.0 -> 0.7
        windKmh < 25.0 -> 0.4
        else -> 0.15
    }
    val score = (temperatureScore * humidityFactor * windFactor).coerceIn(0.0, 10.0)
    val label = when {
        score < 1.5 -> "Quiet"
        score < 4.0 -> "Low"
        score < 7.0 -> "Moderate"
        else -> "High"
    }
    return BugReport(score, label)
}
