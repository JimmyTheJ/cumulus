package app.cumulus.model

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

fun formatFixed(value: Double, digits: Int): String {
    if (digits < 0) return value.toString()
    if (value.isNaN() || value.isInfinite()) return "—"
    val factor = 10.0.pow(digits.toDouble())
    val scaled = round(abs(value) * factor).toLong()
    val negative = value < 0 && scaled != 0L
    val sign = if (negative) "-" else ""
    if (digits == 0) return sign + scaled.toString()
    val divisor = factor.toLong()
    val whole = scaled / divisor
    val fraction = (scaled % divisor).toString().padStart(digits, '0')
    return "$sign$whole.$fraction"
}

fun celsiusTo(value: Double, unit: TemperatureUnit): Double = when (unit) {
    TemperatureUnit.Celsius -> value
    TemperatureUnit.Fahrenheit -> value * 9.0 / 5.0 + 32.0
}

fun formatTemperature(celsius: Double?, units: UnitPrefs, digits: Int = 0): String {
    if (celsius == null) return "—"
    val symbol = if (units.temperature == TemperatureUnit.Fahrenheit) "°F" else "°"
    val shown = if (digits == 0 && units.temperature == TemperatureUnit.Celsius) "°" else symbol
    val number = formatFixed(celsiusTo(celsius, units.temperature), digits)
    return if (units.temperature == TemperatureUnit.Celsius && digits == 0) "$number°" else "$number$shown"
}

fun formatTemperatureDelta(celsiusDelta: Double, units: UnitPrefs): String {
    val converted = when (units.temperature) {
        TemperatureUnit.Celsius -> celsiusDelta
        TemperatureUnit.Fahrenheit -> celsiusDelta * 9.0 / 5.0
    }
    val sign = when {
        converted > 0.0 -> "+"
        converted < 0.0 -> "−"
        else -> ""
    }
    val suffix = if (units.temperature == TemperatureUnit.Fahrenheit) "°F" else "°"
    return sign + formatFixed(abs(converted), 0) + suffix
}

fun windFromKmh(kmh: Double, unit: WindUnit): Double = when (unit) {
    WindUnit.KilometresPerHour -> kmh
    WindUnit.MilesPerHour -> kmh * 0.621371
    WindUnit.MetresPerSecond -> kmh / 3.6
}

fun formatWind(kmh: Double?, units: UnitPrefs): String {
    if (kmh == null) return "—"
    val unit = when (units.wind) {
        WindUnit.KilometresPerHour -> "km/h"
        WindUnit.MilesPerHour -> "mph"
        WindUnit.MetresPerSecond -> "m/s"
    }
    return "${formatFixed(windFromKmh(kmh, units.wind), 0)} $unit"
}

fun formatPrecip(mm: Double?, units: UnitPrefs): String {
    if (mm == null) return "—"
    return when (units.precipitation) {
        PrecipUnit.Millimetres -> "${formatFixed(mm, if (mm < 10) 1 else 0)} mm"
        PrecipUnit.Inches -> "${formatFixed(mm / 25.4, 2)} in"
    }
}

fun formatPressure(hpa: Double?, units: UnitPrefs): String {
    if (hpa == null) return "—"
    return when (units.pressure) {
        PressureUnit.Hectopascals -> "${formatFixed(hpa, 0)} hPa"
        PressureUnit.InchesOfMercury -> "${formatFixed(hpa * 0.02953, 2)} inHg"
    }
}

fun formatPercent(value: Double?): String {
    if (value == null) return "—"
    return "${formatFixed(value, 0)}%"
}

fun formatVisibility(meters: Double?, units: UnitPrefs): String {
    if (meters == null) return "—"
    val imperial = units.wind == WindUnit.MilesPerHour
    return if (imperial) {
        "${formatFixed(meters / 1609.344, 1)} mi"
    } else {
        "${formatFixed(meters / 1000.0, if (meters >= 10000) 0 else 1)} km"
    }
}

fun formatUv(value: Double?): String {
    if (value == null) return "—"
    return formatFixed(value, if (value < 10) 1 else 0)
}

fun compass(degrees: Double): String {
    val dirs = listOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
    )
    val normalized = ((degrees % 360.0) + 360.0) % 360.0
    val index = ((normalized + 11.25) / 22.5).toInt() % 16
    return dirs[index]
}

fun formatWindWithDirection(kmh: Double?, degrees: Double?, units: UnitPrefs): String {
    val speed = formatWind(kmh, units)
    if (degrees == null || kmh == null) return speed
    return "$speed ${compass(degrees)}"
}

private val shortDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val shortMonths = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val longMonths = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

fun dayShort(day: DayOfWeek): String = shortDays[day.ordinal]
fun monthShort(month: Month): String = shortMonths[month.ordinal]
fun monthLong(month: Month): String = longMonths[month.ordinal]

fun formatClock(time: LocalDateTime, units: UnitPrefs): String {
    if (units.time == TimeFormat.TwentyFourHour) {
        return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
    }
    val hour12 = when (val h = time.hour % 12) {
        0 -> 12
        else -> h
    }
    val suffix = if (time.hour < 12) "am" else "pm"
    val minutes = if (time.minute == 0) "" else ":${time.minute.toString().padStart(2, '0')}"
    return "$hour12$minutes$suffix"
}

fun formatDayHeading(date: LocalDate, today: LocalDate): String = when {
    date == today -> "Today"
    date.toEpochDays() == today.toEpochDays() + 1 -> "Tomorrow"
    else -> "${dayShort(date.dayOfWeek)} ${date.day}"
}

sealed interface HourStripEntry {
    data class DayMark(val date: LocalDate, val label: String) : HourStripEntry
    data class Hour(val point: HourPoint) : HourStripEntry
}

/** Hours in order, with a day label before every day after the first. */
fun hourStrip(hours: List<HourPoint>, today: LocalDate): List<HourStripEntry> {
    var seen = false
    var previous: LocalDate? = null
    return buildList {
        for (hour in hours) {
            val date = hour.time.date
            if (seen && date != previous) add(HourStripEntry.DayMark(date, formatDayHeading(date, today)))
            seen = true
            previous = date
            add(HourStripEntry.Hour(hour))
        }
    }
}

fun formatFullDate(date: LocalDate): String =
    "${dayShort(date.dayOfWeek)} ${date.day} ${monthLong(date.month)} ${date.year}"

fun formatMonthYear(date: LocalDate): String = "${monthLong(date.month)} ${date.year}"

fun formatCoordinates(latitude: Double, longitude: Double): String {
    fun hem(value: Double, positive: String, negative: String): String {
        val suffix = if (value >= 0) positive else negative
        return "${formatFixed(abs(value), 2)}°$suffix"
    }
    return "${hem(latitude, "N", "S")}  ${hem(longitude, "E", "W")}"
}

fun modelFreshness(
    observed: LocalDateTime?,
    zone: TimeZone,
    units: UnitPrefs,
    fetchedAtMs: Long,
    nowMs: Long,
): String {
    val fetchedMinutes = (nowMs - fetchedAtMs) / 60_000
    val fetched = when {
        fetchedMinutes < 1 -> "fetched just now"
        fetchedMinutes < 60 -> "fetched $fetchedMinutes min ago"
        fetchedMinutes < 24 * 60 -> "fetched ${fetchedMinutes / 60}h ago"
        else -> "fetched ${fetchedMinutes / (24 * 60)}d ago"
    }
    if (observed == null) return "Model time unknown · $fetched"
    val clock = formatClock(observed, units)
    val ageMin = (nowMs - observed.toInstant(zone).toEpochMilliseconds()) / 60_000
    val whenText = if (ageMin in 0..74) "This hour, $clock" else "As of $clock"
    return "$whenText · $fetched"
}

fun relativeUpdated(epochMs: Long, nowMs: Long): String {
    val minutes = (nowMs - epochMs) / 60_000
    return when {
        minutes < 1 -> "Updated just now"
        minutes < 60 -> "Updated ${minutes} min ago"
        else -> {
            val hours = minutes / 60
            if (hours < 24) "Updated ${hours}h ago" else "Updated ${hours / 24}d ago"
        }
    }
}
