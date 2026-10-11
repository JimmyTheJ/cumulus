package app.cumulus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.cumulus.CumulusApp
import app.cumulus.model.AppUiState
import app.cumulus.model.ClimateNormals
import app.cumulus.model.DayObservation
import app.cumulus.model.HistorySpan
import app.cumulus.model.HourPoint
import app.cumulus.model.PeriodStats
import app.cumulus.model.Screen
import app.cumulus.model.UnitPrefs
import app.cumulus.model.earliestHistory
import app.cumulus.model.formatClock
import app.cumulus.model.formatFullDate
import app.cumulus.model.formatMonthYear
import app.cumulus.model.formatPrecip
import app.cumulus.model.formatTemperature
import app.cumulus.model.formatTemperatureDelta
import app.cumulus.model.formatWind
import app.cumulus.model.monthLength
import app.cumulus.model.monthLong
import app.cumulus.model.monthShort
import app.cumulus.model.shownDayNumbers
import app.cumulus.model.shownHourMarks
import app.cumulus.model.summarize
import app.cumulus.model.weatherLabel
import androidx.compose.foundation.lazy.LazyColumn
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
    val zone = runCatching { TimeZone.of(place.timezone) }.getOrDefault(TimeZone.UTC)
    val today = Clock.System.now().toLocalDateTime(zone).date
    var choosingDate by remember { mutableStateOf(false) }
    if (choosingDate) {
        HistoryJumpDialog(
            span = history.span,
            anchor = history.anchor ?: today,
            today = today,
            sky = sky,
            onDismiss = { choosingDate = false },
            onConfirm = { date ->
                choosingDate = false
                app.jumpHistory(date)
            },
        )
    }
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
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).clickable { choosingDate = true }) {
                    Text(anchorLabel(history.span, history.anchor), color = sky.onSky, fontSize = 18.sp, textAlign = TextAlign.Center)
                    Text("Choose a date", color = sky.accent, fontSize = 12.sp)
                }
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
                if (history.span == HistorySpan.Day) {
                    HourlyHistoryCard(history.hours, state.units, sky)
                } else {
                    GlassCard(sky) {
                        SectionLabel(if (history.span == HistorySpan.Year) "Each month" else "Each day", sky)
                        Spacer(Modifier.height(8.dp))
                        RangeChart(rangePoints(history.span, history.days, state.units), sky, state.units)
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

@Composable
private fun HourlyHistoryCard(hours: List<HourPoint>, units: UnitPrefs, sky: Sky) {
    GlassCard(sky) {
        SectionLabel("Hourly", sky)
        Spacer(Modifier.height(8.dp))
        if (hours.isEmpty()) {
            Text("Hourly temperatures aren't in the archive for this day.", color = sky.muted, fontSize = 13.sp)
        } else {
            ValueChart(hourPoints(hours, units), sky, units)
        }
    }
}

private fun rangePoints(span: HistorySpan, days: List<DayObservation>, units: UnitPrefs): List<RangePoint> = when (span) {
    HistorySpan.Year -> days.groupBy { it.date.month }.toSortedMap().map { (month, samples) ->
        val stats = summarize(samples)
        RangePoint(
            label = monthShort(month),
            showLabel = true,
            low = stats.lowest,
            high = stats.highest,
            detail = "${monthLong(month)} · highest ${formatTemperature(stats.highest, units)} · lowest ${formatTemperature(stats.lowest, units)}",
        )
    }
    else -> {
        val marks = shownDayNumbers(days.map { it.date.day })
        days.map { day ->
            RangePoint(
                label = day.date.day.toString(),
                showLabel = day.date.day in marks,
                low = day.tempMinC,
                high = day.tempMaxC,
                detail = "${formatFullDate(day.date)} · high ${formatTemperature(day.tempMaxC, units)} · low ${formatTemperature(day.tempMinC, units)}",
            )
        }
    }
}

private fun hourPoints(hours: List<HourPoint>, units: UnitPrefs): List<ValuePoint> {
    val marks = shownHourMarks(hours.map { it.time.hour })
    return hours.map { hour ->
        ValuePoint(
            label = formatClock(hour.time, units),
            showLabel = hour.time.minute == 0 && hour.time.hour in marks,
            value = hour.temperatureC,
            detail = "${formatClock(hour.time, units)} · ${formatTemperature(hour.temperatureC, units)}",
        )
    }
}

@Composable
private fun HistoryJumpDialog(
    span: HistorySpan,
    anchor: LocalDate,
    today: LocalDate,
    sky: Sky,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    var yearText by remember(anchor) { mutableStateOf(anchor.year.toString()) }
    var month by remember(anchor) { mutableStateOf(anchor.month) }
    var day by remember(anchor) { mutableStateOf(anchor.day) }
    var message by remember(anchor) { mutableStateOf<String?>(null) }
    val surface = if (sky.light) Color.White else Color(0xFF1B2836)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(surface)
                .border(1.dp, sky.border, RoundedCornerShape(22.dp))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                when (span) {
                    HistorySpan.Day -> "Go to a day"
                    HistorySpan.Month -> "Go to a month"
                    HistorySpan.Year -> "Go to a year"
                },
                color = sky.onSky,
                style = TitleStyle,
            )
            Text(
                when (span) {
                    HistorySpan.Day -> "Type the year, then pick the month and day."
                    HistorySpan.Month -> "Type the year, then pick the month."
                    HistorySpan.Year -> "Type any year back to ${earliestHistory.year}."
                },
                color = sky.muted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = yearText,
                onValueChange = { yearText = it.filter(Char::isDigit).take(4) },
                label = { Text("Year") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(140.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = sky.onSky,
                    unfocusedTextColor = sky.onSky,
                    focusedBorderColor = sky.accent,
                    unfocusedBorderColor = sky.muted,
                    focusedLabelColor = sky.accent,
                    unfocusedLabelColor = sky.muted,
                    cursorColor = sky.accent,
                ),
            )
            if (span != HistorySpan.Year) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Month.entries.forEach { entry ->
                        JumpChip(monthShort(entry), entry == month, sky) { month = entry }
                    }
                }
            }
            if (span == HistorySpan.Day) {
                val length = monthLength(yearText.toIntOrNull() ?: anchor.year, month)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val selectedDay = day.coerceIn(1, length)
                    for (number in 1..length) {
                        JumpChip(number.toString(), number == selectedDay, sky) { day = number }
                    }
                }
            }
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = sky.accent, fontSize = 13.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = sky.muted) }
                TextButton(
                    onClick = {
                        val year = yearText.toIntOrNull()
                        if (year == null || year !in earliestHistory.year..today.year) {
                            message = "Enter a year from ${earliestHistory.year} to ${today.year}."
                            return@TextButton
                        }
                        val picked = when (span) {
                            HistorySpan.Year -> LocalDate(year, 1, 1)
                            HistorySpan.Month -> LocalDate(year, month, 1)
                            HistorySpan.Day -> LocalDate(year, month, day.coerceIn(1, monthLength(year, month)))
                        }
                        if (picked > today) {
                            message = "That date is still ahead."
                            return@TextButton
                        }
                        onConfirm(picked)
                    },
                ) { Text("Show", color = sky.accent) }
            }
        }
    }
}

@Composable
private fun JumpChip(label: String, active: Boolean, sky: Sky, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) sky.accent else sky.onSky.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) Color(0xFF1B1408) else sky.onSky, fontSize = 13.sp)
    }
}

private fun anchorLabel(span: HistorySpan, anchor: kotlinx.datetime.LocalDate?): String {
    val date = anchor ?: return "History"
    return when (span) {
        HistorySpan.Day -> formatFullDate(date)
        HistorySpan.Month -> formatMonthYear(date)
        HistorySpan.Year -> date.year.toString()
    }
}
