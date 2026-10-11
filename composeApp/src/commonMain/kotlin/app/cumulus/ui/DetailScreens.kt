package app.cumulus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cumulus.model.ClimateNormals
import app.cumulus.model.HourPoint
import app.cumulus.model.PlaceWeather
import app.cumulus.model.UnitPrefs
import app.cumulus.model.formatClock
import app.cumulus.model.formatDayHeading
import app.cumulus.model.formatFixed
import app.cumulus.model.formatFullDate
import app.cumulus.model.formatPrecip
import app.cumulus.model.formatTemperature
import app.cumulus.model.formatUv
import app.cumulus.model.formatWind
import app.cumulus.model.lowWindChill
import app.cumulus.model.peakHumidex
import app.cumulus.model.weatherLabel
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun HourlyScreen(weather: PlaceWeather?, units: UnitPrefs, sky: Sky, modifier: Modifier = Modifier) {
    if (weather == null) {
        Text("Hourly forecast isn't loaded yet.", Modifier.padding(20.dp), color = sky.onSky)
        return
    }
    val zone = runCatching { TimeZone.of(weather.timezone) }.getOrDefault(TimeZone.UTC)
    val today = Clock.System.now().toLocalDateTime(zone).date
    val grouped = weather.hourly.groupBy { it.time.date }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("Hourly", color = sky.onSky, style = TitleStyle)
            Text("Temperatures through the next 14 days.", color = sky.muted, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
        }
        grouped.entries.forEachIndexed { index, (date, hours) ->
            if (index > 0) {
                item(key = "h-$date") {
                    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(sky.onSky.copy(alpha = 0.28f)))
                        Text(
                            formatDayHeading(date, today),
                            Modifier.padding(top = 10.dp),
                            color = sky.accent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            items(hours, key = { "${it.time}" }) { hour -> HourLine(hour, units, sky) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HourLine(hour: HourPoint, units: UnitPrefs, sky: Sky) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(formatClock(hour.time, units), Modifier.weight(0.8f), color = sky.muted, fontSize = 13.sp)
        WeatherGlyph(hour.weatherCode, hour.isDay != false, sky.onSky, Modifier.size(22.dp))
        Text(formatTemperature(hour.temperatureC, units), Modifier.weight(0.7f), color = sky.onSky, style = NumberStyle)
        Text(formatTemperature(hour.apparentC, units), Modifier.weight(0.7f), color = sky.muted, style = NumberStyle, fontSize = 14.sp)
        Text(
            hour.precipProbability?.takeIf { it >= 15 }?.let { "${formatFixed(it, 0)}%" } ?: "",
            Modifier.weight(0.5f),
            color = sky.precip,
            fontSize = 13.sp,
        )
        Text(formatWind(hour.windKmh, units), Modifier.weight(1f), color = sky.muted, fontSize = 12.sp)
    }
}

@Composable
fun DayScreen(
    date: String,
    weather: PlaceWeather?,
    normals: ClimateNormals?,
    units: UnitPrefs,
    sky: Sky,
    modifier: Modifier = Modifier,
) {
    val day = weather?.daily?.find { it.date.toString() == date }
    if (weather == null || day == null) {
        Text("That day isn't in the current forecast.", Modifier.padding(20.dp), color = sky.onSky)
        return
    }
    val hours = weather.hourly.filter { it.time.date.toString() == date }
    val chill = lowWindChill(hours)
    val humidex = peakHumidex(hours)
    val normal = normals?.day(day.date.month.ordinal + 1, day.date.day)
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            Text(formatFullDate(day.date), color = sky.onSky, style = TitleStyle)
            Text(weatherLabel(day.weatherCode), color = sky.muted, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("High ${formatTemperature(day.tempMaxC, units)}", color = sky.high, fontSize = 28.sp, style = NumberStyle)
                Spacer(Modifier.weight(1f))
                Text("Low ${formatTemperature(day.tempMinC, units)}", color = sky.low, fontSize = 28.sp, style = NumberStyle)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Feels like ${formatTemperature(day.apparentMaxC, units)} / ${formatTemperature(day.apparentMinC, units)}",
                color = sky.onSky,
            )
            if (chill != null) Text("Wind chill as low as ${formatTemperature(chill, units)}", color = sky.onSky)
            if (humidex != null) Text("Humidex up to ${formatFixed(humidex, 0)}", color = sky.onSky)
            if (normal != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Typical high ${formatTemperature(normal.avgMax, units)}, low ${formatTemperature(normal.avgMin, units)} (1991–2020)",
                    color = sky.muted,
                    fontSize = 13.sp,
                )
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            GlassCard(sky) {
                Row(Modifier.fillMaxWidth()) {
                    Metric("Precip", formatPrecip(day.precipMm, units), sky, modifier = Modifier.weight(1f))
                    Metric("Chance", day.precipProbability?.let { "${formatFixed(it, 0)}%" } ?: "—", sky, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    Metric("Wind", formatWind(day.windMaxKmh, units), sky, modifier = Modifier.weight(1f))
                    Metric("Gusts", formatWind(day.windGustKmh, units), sky, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    Metric("Peak UV", formatUv(day.uvIndexMax), sky, modifier = Modifier.weight(1f))
                    Metric("Sunrise", day.sunrise?.let { formatClock(it, units) } ?: "—", sky, modifier = Modifier.weight(1f))
                }
            }
        }
        if (hours.isNotEmpty()) {
            item {
                Spacer(Modifier.height(12.dp))
                SectionLabel("Hourly", sky)
                LineChart(hours.map { it.temperatureC }, sky)
            }
            items(hours) { hour -> HourLine(hour, units, sky) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
