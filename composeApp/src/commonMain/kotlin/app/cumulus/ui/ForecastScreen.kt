package app.cumulus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cumulus.CumulusApp
import app.cumulus.model.AirSnapshot
import app.cumulus.model.AppUiState
import app.cumulus.model.DayObservation
import app.cumulus.model.Screen
import app.cumulus.model.WeatherResource
import app.cumulus.model.HourPoint
import app.cumulus.model.PlaceWeather
import app.cumulus.model.SavedPlace
import app.cumulus.model.UnitPrefs
import app.cumulus.model.aqiReading
import app.cumulus.model.bugReport
import app.cumulus.model.feelReport
import app.cumulus.model.HourStripEntry
import app.cumulus.model.formatClock
import app.cumulus.model.hourStrip
import app.cumulus.model.formatDayHeading
import app.cumulus.model.formatFixed
import app.cumulus.model.formatPercent
import app.cumulus.model.formatPrecip
import app.cumulus.model.formatPressure
import app.cumulus.model.formatTemperature
import app.cumulus.model.formatUv
import app.cumulus.model.healthSourceNote
import app.cumulus.model.liveUv
import app.cumulus.model.formatVisibility
import app.cumulus.model.formatWind
import app.cumulus.model.formatWindWithDirection
import app.cumulus.model.pollenReport
import app.cumulus.model.relativeUpdated
import app.cumulus.model.uvReading
import app.cumulus.model.weatherLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun ForecastPager(app: CumulusApp, state: AppUiState, sky: Sky, modifier: Modifier = Modifier) {
    val places = state.places
    if (places.isEmpty()) {
        EmptyForecast(sky, onAdd = { app.root(Screen.Places) })
        return
    }
    val pager = rememberPagerState(initialPage = state.selectedIndex.coerceIn(0, places.lastIndex)) { places.size }
    LaunchedEffect(state.selectedIndex, places.size) {
        val index = state.selectedIndex.coerceIn(0, places.lastIndex)
        if (pager.currentPage != index) pager.scrollToPage(index)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { app.selectIndex(it) }
    }
    LaunchedEffect(state.selectedIndex) {
        while (true) {
            app.refreshIfStale()
            delay(60_000)
        }
    }
    HorizontalPager(
        state = pager,
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = places.size > 1,
    ) { page ->
        val place = places[page]
        ForecastPage(app, place, state.weather[place.id], state.units, sky)
    }
}

@Composable
private fun EmptyForecast(sky: Sky, onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Cumulus", color = sky.onSky, style = TitleStyle, fontSize = 36.sp)
        Spacer(Modifier.height(8.dp))
        Text("Forecasts, history, and no ads.", color = sky.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onAdd) { Text("Add a place", color = sky.accent) }
    }
}

@Composable
private fun ForecastPage(
    app: CumulusApp,
    place: SavedPlace,
    resource: WeatherResource?,
    units: UnitPrefs,
    sky: Sky,
) {
    val weather = resource?.value
    if (weather == null && resource?.loading != false) {
        LoadingMark(sky, "Loading ${place.name}…")
        return
    }
    if (weather == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(resource.error ?: "Weather is unavailable", color = sky.onSky)
            TextButton(onClick = app::refresh) { Text("Try again", color = sky.accent) }
        }
        return
    }
    val zone = runCatching { TimeZone.of(weather.timezone) }.getOrDefault(TimeZone.UTC)
    val today = Clock.System.now().toLocalDateTime(zone).date
    val upcoming = weather.hourly.filter { hour ->
        hour.time.date > today || (hour.time.date == today && hour.time.hour >= weather.current.time.hour)
    }.ifEmpty { weather.hourly }
    val days = weather.daily
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item { Hero(place, weather, units, sky) }
        if (resource.error != null) {
            item {
                Text(resource.error, color = sky.accent, fontSize = 13.sp)
                TextButton(onClick = app::refresh) { Text("Retry", color = sky.accent) }
            }
        }
        item {
            GlassCard(sky) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Hourly", sky)
                    TextButton(onClick = app::openHourly) { Text("14 days", color = sky.accent) }
                }
                LazyRow {
                    items(
                        hourStrip(upcoming, today),
                        key = { entry ->
                            when (entry) {
                                is HourStripEntry.DayMark -> "day-${entry.date}"
                                is HourStripEntry.Hour -> entry.point.time.toString()
                            }
                        },
                    ) { entry ->
                        when (entry) {
                            is HourStripEntry.DayMark -> HourDayBreak(entry.label, sky)
                            is HourStripEntry.Hour -> HourChip(entry.point, units, sky)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
        item {
            GlassCard(sky) {
                SectionLabel("Daily", sky)
                Spacer(Modifier.height(8.dp))
                val scaleMin = days.mapNotNull { it.tempMinC }.minOrNull() ?: 0.0
                val scaleMax = days.mapNotNull { it.tempMaxC }.maxOrNull() ?: 1.0
                days.forEach { day ->
                    DayRow(day, today, units, sky, scaleMin, scaleMax) { app.openDay(day.date.toString()) }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
        item { HealthCard(weather, units, sky) }
        item { Spacer(Modifier.height(12.dp)) }
        item { DetailsCard(weather, units, sky) }
        item {
            Text(
                "Weather data by Open-Meteo. No ads, no tracking.",
                Modifier.padding(vertical = 18.dp),
                color = sky.muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Hero(place: SavedPlace, weather: PlaceWeather, units: UnitPrefs, sky: Sky) {
    val current = weather.current
    val today = weather.daily.firstOrNull { it.date == current.time.date }
    val feel = feelReport(current.temperatureC, current.apparentC, current.windKmh, current.dewPointC)
    Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(place.name, color = sky.onSky, style = TitleStyle, textAlign = TextAlign.Center)
        if (place.subtitle.isNotBlank()) {
            Text(place.subtitle, color = sky.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(14.dp))
        Text(weatherLabel(current.weatherCode), color = sky.onSky, fontSize = 18.sp)
        Text(formatTemperature(current.temperatureC, units), color = sky.onSky, style = TempStyle, textAlign = TextAlign.Center)
        Text("Feels like ${formatTemperature(feel.apparentC, units)}", color = sky.muted, fontSize = 16.sp)
        if (feel.windChillC != null) {
            Text("Wind chill ${formatTemperature(feel.windChillC, units)}", color = sky.onSky, fontSize = 16.sp)
        }
        if (feel.humidexC != null) {
            Text("Humidex ${formatFixed(feel.humidexC, 0)}", color = sky.onSky, fontSize = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("High ${formatTemperature(today?.tempMaxC, units)}", color = sky.high, style = NumberStyle)
            Text("Low ${formatTemperature(today?.tempMinC, units)}", color = sky.low, style = NumberStyle)
        }
        Spacer(Modifier.height(8.dp))
        Text(relativeUpdated(weather.fetchedAtEpochMs, Clock.System.now().toEpochMilliseconds()), color = sky.muted, fontSize = 12.sp)
    }
}

@Composable
private fun HourDayBreak(label: String, sky: Sky) {
    Row(Modifier.padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(1.dp).height(88.dp).background(sky.onSky.copy(alpha = 0.35f)))
        Text(
            label,
            Modifier.padding(start = 10.dp).widthIn(max = 76.dp),
            color = sky.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun HourChip(hour: HourPoint, units: UnitPrefs, sky: Sky) {
    Column(Modifier.width(62.dp).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(formatClock(hour.time, units), color = sky.muted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        WeatherGlyph(hour.weatherCode, hour.isDay != false, sky.onSky, Modifier.size(28.dp))
        Spacer(Modifier.height(6.dp))
        Text(formatTemperature(hour.temperatureC, units), color = sky.onSky, style = NumberStyle, fontSize = 15.sp)
        val pop = hour.precipProbability ?: 0.0
        Text(
            if (pop >= 15) "${formatFixed(pop, 0)}%" else " ",
            color = sky.precip,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun DayRow(
    day: DayObservation,
    today: kotlinx.datetime.LocalDate,
    units: UnitPrefs,
    sky: Sky,
    scaleMin: Double,
    scaleMax: Double,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            formatDayHeading(day.date, today),
            Modifier.width(78.dp),
            color = sky.onSky,
            fontSize = 14.sp,
        )
        WeatherGlyph(day.weatherCode, true, sky.onSky, Modifier.size(26.dp))
        Text(
            "${formatFixed(day.precipProbability ?: 0.0, 0)}%",
            Modifier.width(42.dp),
            color = if ((day.precipProbability ?: 0.0) >= 30) sky.precip else sky.muted,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.width(8.dp))
        TempRangeBar(
            day.tempMinC ?: scaleMin,
            day.tempMaxC ?: scaleMax,
            scaleMin,
            scaleMax,
            sky,
            Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(formatTemperature(day.tempMaxC, units), Modifier.width(42.dp), color = sky.high, style = NumberStyle, textAlign = TextAlign.End, fontSize = 14.sp)
        Text(formatTemperature(day.tempMinC, units), Modifier.width(42.dp), color = sky.low, style = NumberStyle, textAlign = TextAlign.End, fontSize = 14.sp)
    }
}

@Composable
private fun HealthCard(weather: PlaceWeather, units: UnitPrefs, sky: Sky) {
    val air = weather.air
    val aqi = aqiReading(air)
    val live = liveUv(weather)
    val uv = uvReading(live.index)
    val uvDetail = buildString {
        if (live.index == null) append("No current reading") else append(uv.detail)
        if (live.peak != null) append(" · peak ${formatUv(live.peak)}")
    }
    val pollen = pollenReport(air?.pollen.orEmpty())
    val bugs = bugReport(weather.current.temperatureC, weather.current.humidity, weather.current.windKmh)
    val zone = runCatching { TimeZone.of(weather.timezone) }.getOrDefault(TimeZone.UTC)
    GlassCard(sky) {
        SectionLabel("Health", sky)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Metric("Air quality", aqi?.detail ?: "—", sky, aqi?.let { "${it.scale} ${it.value}" }, Modifier.weight(1f))
            Metric("UV", if (live.index == null) "—" else uv.value, sky, uvDetail, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            Metric("Pollen", if (pollen.available) pollen.value else "Unavailable", sky, pollen.detail, Modifier.weight(1f))
            Metric("Bugs", bugs?.label ?: "—", sky, bugs?.let { "Estimate ${formatFixed(it.score, 0)}/10" } ?: "Need temp and wind", Modifier.weight(1f))
        }
        PollutantLine(air, sky)
        Text(
            healthSourceNote(weather, zone, units, Clock.System.now().toEpochMilliseconds()),
            color = sky.muted,
            fontSize = 12.sp,
        )
        Text(
            "Bug activity is estimated from temperature, humidity, and wind. Pollen covers Europe. Not medical advice.",
            color = sky.muted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun PollutantLine(air: AirSnapshot?, sky: Sky) {
    if (air?.pm25 == null && air?.ozone == null) return
    val parts = listOfNotNull(
        air.pm25?.let { "PM2.5 ${formatFixed(it, 1)}" },
        air.pm10?.let { "PM10 ${formatFixed(it, 1)}" },
        air.ozone?.let { "Ozone ${formatFixed(it, 0)}" },
    )
    Text(parts.joinToString("  ·  "), color = sky.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun DetailsCard(weather: PlaceWeather, units: UnitPrefs, sky: Sky) {
    val current = weather.current
    val today = weather.daily.firstOrNull { it.date == current.time.date }
    GlassCard(sky) {
        SectionLabel("Details", sky)
        Row(Modifier.fillMaxWidth()) {
            Metric("Wind", formatWindWithDirection(current.windKmh, current.windDirectionDeg, units), sky, modifier = Modifier.weight(1f))
            Metric("Gusts", formatWind(current.windGustKmh, units), sky, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            Metric("Humidity", formatPercent(current.humidity), sky, modifier = Modifier.weight(1f))
            Metric("Dew point", formatTemperature(current.dewPointC, units), sky, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            Metric("Pressure", formatPressure(current.pressureHpa, units), sky, modifier = Modifier.weight(1f))
            Metric("Visibility", formatVisibility(current.visibilityM, units), sky, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            Metric("Precipitation", formatPrecip(today?.precipMm, units), sky, modifier = Modifier.weight(1f))
            Metric("Cloud cover", formatPercent(current.cloudCover), sky, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            Metric("Sunrise", today?.sunrise?.let { formatClock(it, units) } ?: "—", sky, modifier = Modifier.weight(1f))
            Metric("Sunset", today?.sunset?.let { formatClock(it, units) } ?: "—", sky, modifier = Modifier.weight(1f))
        }
    }
}
