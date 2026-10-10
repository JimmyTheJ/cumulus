package app.cumulus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cumulus.CumulusApp
import app.cumulus.model.AppUiState
import app.cumulus.model.ClimateNormals
import app.cumulus.model.DayObservation
import app.cumulus.model.HistorySpan
import app.cumulus.model.PeriodStats
import app.cumulus.model.Screen
import app.cumulus.model.UnitPrefs
import app.cumulus.model.formatClock
import app.cumulus.model.formatFullDate
import app.cumulus.model.formatMonthYear
import app.cumulus.model.formatPrecip
import app.cumulus.model.formatTemperature
import app.cumulus.model.formatTemperatureDelta
import app.cumulus.model.formatWind
import app.cumulus.model.monthLong
import app.cumulus.model.monthShort
import app.cumulus.model.summarize
import app.cumulus.model.weatherLabel
import kotlinx.datetime.Month

@Composable
fun HistoryScreen(app: CumulusApp, state: AppUiState, sky: Sky, modifier: Modifier = Modifier) {
    val place = state.selectedPlace
    if (place == null) {
        Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Add a place to see its history.", color = sky.onSky)
            TextButton(onClick = { app.root(Screen.Places) }) { Text("Choose a place", color = sky.accent) }
        }
        return
    }
    val history = state.history
    val normals = state.normals[place.id]
    val stats = summarize(history.days)
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            Spacer(Modifier.height(18.dp))
            Text(place.name, color = sky.onSky, style = TitleStyle)
            Text("Observed history", color = sky.muted, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            PillRow(
                options = listOf("Day", "Month", "Year"),
                selected = history.span.ordinal,
                sky = sky,
                onSelect = { app.setHistorySpan(HistorySpan.entries[it]) },
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(sky, onClick = { app.shiftHistory(-1) }) { Chevron(sky, left = true) }
                Text(anchorLabel(history.span, history.anchor), color = sky.onSky, fontSize = 18.sp)
                IconButton(sky, onClick = { app.shiftHistory(1) }) { Chevron(sky, left = false) }
            }
            if (history.loading) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = sky.accent, trackColor = sky.onSky.copy(alpha = 0.12f))
            }
        }
        history.error?.let { message ->
            item { Text(message, Modifier.padding(vertical = 12.dp), color = sky.muted) }
        }
        if (history.days.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                GlassCard(sky) {
                    SectionLabel(if (history.span == HistorySpan.Day) "That day" else "Averages", sky)
                    Spacer(Modifier.height(6.dp))
                    if (history.span == HistorySpan.Day) {
                        DayFacts(history.days.first(), state.units, sky)
                    } else {
                        PeriodFacts(stats, state.units, sky, history.days.size)
                    }
                    history.request?.let { request ->
                        if (request.end == history.days.lastOrNull()?.date && request.span != HistorySpan.Day) {
                            Text(
                                "If this period includes today, today's high and low can still change.",
                                color = sky.muted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                GlassCard(sky) {
                    SectionLabel(if (history.span == HistorySpan.Year) "Monthly range" else "Daily range", sky)
                    Spacer(Modifier.height(8.dp))
                    RangeChart(chartPoints(history.span, history.days), sky, state.units)
                }
            }
            if (history.span == HistorySpan.Day && history.hours.isNotEmpty()) {
                item { Spacer(Modifier.height(12.dp)) }
                item {
                    GlassCard(sky) {
                        SectionLabel("Hourly", sky)
                        LineChart(history.hours.map { it.temperatureC }, sky)
                        LazyRow {
                            items(history.hours) { hour ->
                                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(formatClock(hour.time, state.units), color = sky.muted, fontSize = 11.sp)
                                    Text(formatTemperature(hour.temperatureC, state.units), color = sky.onSky, style = NumberStyle, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
            item { NormalsCard(app, history.span, history.days, normals, history.normalsStatus, history.normalsError, state.units, sky) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DayFacts(day: DayObservation, units: UnitPrefs, sky: Sky) {
    Text(weatherLabel(day.weatherCode), color = sky.onSky, fontSize = 16.sp)
    Row(Modifier.fillMaxWidth()) {
        Metric("High", formatTemperature(day.tempMaxC, units), sky, modifier = Modifier.weight(1f))
        Metric("Low", formatTemperature(day.tempMinC, units), sky, modifier = Modifier.weight(1f))
        Metric("Mean", formatTemperature(day.tempMeanC, units), sky, modifier = Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth()) {
        Metric("Feels high", formatTemperature(day.apparentMaxC, units), sky, modifier = Modifier.weight(1f))
        Metric("Feels low", formatTemperature(day.apparentMinC, units), sky, modifier = Modifier.weight(1f))
        Metric("Precip", formatPrecip(day.precipMm, units), sky, modifier = Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth()) {
        Metric("Wind", formatWind(day.windMaxKmh, units), sky, modifier = Modifier.weight(1f))
        Metric("Humidity", day.humidityMean?.let { "${it.toInt()}%" } ?: "—", sky, modifier = Modifier.weight(1f))
        Metric("Sunrise", day.sunrise?.let { formatClock(it, units) } ?: "—", sky, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PeriodFacts(stats: PeriodStats, units: UnitPrefs, sky: Sky, days: Int) {
    Text("$days days with observations", color = sky.muted, fontSize = 12.sp)
    Row(Modifier.fillMaxWidth()) {
        Metric("Avg high", formatTemperature(stats.averageMax, units), sky, modifier = Modifier.weight(1f))
        Metric("Avg low", formatTemperature(stats.averageMin, units), sky, modifier = Modifier.weight(1f))
        Metric("Mean", formatTemperature(stats.averageMean, units), sky, modifier = Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth()) {
        Metric(
            "Highest",
            formatTemperature(stats.highest, units),
            sky,
            stats.highestDate?.let { "${it.day} ${monthShort(it.month)}" },
            Modifier.weight(1f),
        )
        Metric(
            "Lowest",
            formatTemperature(stats.lowest, units),
            sky,
            stats.lowestDate?.let { "${it.day} ${monthShort(it.month)}" },
            Modifier.weight(1f),
        )
        Metric("Precip", formatPrecip(stats.totalPrecipitationMm, units), sky, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun NormalsCard(
    app: CumulusApp,
    span: HistorySpan,
    days: List<DayObservation>,
    normals: ClimateNormals?,
    status: String?,
    error: String?,
    units: UnitPrefs,
    sky: Sky,
) {
    GlassCard(sky) {
        SectionLabel("1991–2020 normal", sky)
        Spacer(Modifier.height(6.dp))
        when {
            normals == null && status != null -> Text(status, color = sky.muted)
            normals == null -> {
                Text(
                    "The long-term average is downloaded once per place from the ERA5 archive, about thirty years of daily temperatures.",
                    color = sky.muted,
                    fontSize = 13.sp,
                )
                TextButton(onClick = app::loadNormals) { Text("Compare with 1991–2020", color = sky.accent) }
                if (error != null) Text(error, color = sky.accent, fontSize = 13.sp)
            }
            else -> NormalBody(span, days, normals, units, sky)
        }
    }
}

@Composable
private fun NormalBody(span: HistorySpan, days: List<DayObservation>, normals: ClimateNormals, units: UnitPrefs, sky: Sky) {
    val sample = days.firstOrNull()
    when (span) {
        HistorySpan.Day -> {
            val normal = sample?.let { normals.day(it.date.month.ordinal + 1, it.date.day) }
            if (sample == null || normal == null) {
                Text("No normal for this date.", color = sky.muted)
            } else {
                CompareLine("Average high", sample.tempMaxC, normal.avgMax, units, sky)
                CompareLine("Average low", sample.tempMinC, normal.avgMin, units, sky)
                CompareLine("Mean", sample.tempMeanC, normal.avgMean, units, sky)
                Text("${normal.samples} years in the sample", color = sky.muted, fontSize = 12.sp)
            }
        }
        HistorySpan.Month -> {
            val month = sample?.date?.month
            val normal = month?.let { normals.month(it.ordinal + 1) }
            val stats = summarize(days)
            if (month == null || normal == null) {
                Text("No normal for this month.", color = sky.muted)
            } else {
                Text(monthLong(month), color = sky.onSky)
                CompareLine("Average high", stats.averageMax, normal.avgMax, units, sky)
                CompareLine("Average low", stats.averageMin, normal.avgMin, units, sky)
                CompareLine("Mean", stats.averageMean, normal.avgMean, units, sky)
            }
        }
        HistorySpan.Year -> {
            val stats = summarize(days)
            CompareLine("Average high", stats.averageMax, normals.overall.avgMax, units, sky)
            CompareLine("Average low", stats.averageMin, normals.overall.avgMin, units, sky)
            CompareLine("Mean", stats.averageMean, normals.overall.avgMean, units, sky)
        }
    }
}

@Composable
private fun CompareLine(label: String, actual: Double?, typical: Double?, units: UnitPrefs, sky: Sky) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, color = sky.muted, fontSize = 12.sp)
        Text(
            "${formatTemperature(actual, units)}   typical ${formatTemperature(typical, units)}",
            color = sky.onSky,
            style = NumberStyle,
        )
        if (actual != null && typical != null) {
            Text(formatTemperatureDelta(actual - typical, units), color = sky.accent, fontSize = 13.sp)
        }
    }
}

private fun chartPoints(span: HistorySpan, days: List<DayObservation>): List<RangePoint> = when (span) {
    HistorySpan.Year -> days.groupBy { it.date.month }.toSortedMap().map { (month, samples) ->
        val stats = summarize(samples)
        RangePoint(monthShort(month), stats.lowest, stats.highest)
    }
    else -> days.map { RangePoint(it.date.day.toString(), it.tempMinC, it.tempMaxC) }
}

private fun anchorLabel(span: HistorySpan, anchor: kotlinx.datetime.LocalDate?): String {
    val date = anchor ?: return "History"
    return when (span) {
        HistorySpan.Day -> formatFullDate(date)
        HistorySpan.Month -> formatMonthYear(date)
        HistorySpan.Year -> date.year.toString()
    }
}
