package app.cumulus.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

fun meanOf(values: List<Double>): Double? = if (values.isEmpty()) null else values.sum() / values.size

fun effectiveMean(day: DayObservation): Double? {
    day.tempMeanC?.let { return it }
    val max = day.tempMaxC ?: return null
    val min = day.tempMinC ?: return null
    return (max + min) / 2.0
}

fun summarize(days: List<DayObservation>): PeriodStats {
    val means = days.mapNotNull { effectiveMean(it) }
    val maxes = days.mapNotNull { it.tempMaxC }
    val mins = days.mapNotNull { it.tempMinC }
    val precip = days.mapNotNull { it.precipMm }
    val highest = days.filter { it.tempMaxC != null }.maxByOrNull { it.tempMaxC!! }
    val lowest = days.filter { it.tempMinC != null }.minByOrNull { it.tempMinC!! }
    return PeriodStats(
        dayCount = days.size,
        averageMean = meanOf(means),
        averageMax = meanOf(maxes),
        averageMin = meanOf(mins),
        highest = highest?.tempMaxC,
        highestDate = highest?.date,
        lowest = lowest?.tempMinC,
        lowestDate = lowest?.date,
        totalPrecipitationMm = if (precip.isEmpty()) null else precip.sum(),
    )
}

fun LocalDate.monthIndex(): Int = month.ordinal + 1

fun climateNormals(days: List<DayObservation>, fromYear: Int, toYear: Int): ClimateNormals {
    val byDate = days.groupBy { it.date.monthIndex() to it.date.day }
    val normalDays = byDate.map { (key, samples) ->
        NormalDay(
            month = key.first,
            day = key.second,
            avgMax = meanOf(samples.mapNotNull { it.tempMaxC }) ?: 0.0,
            avgMin = meanOf(samples.mapNotNull { it.tempMinC }) ?: 0.0,
            avgMean = meanOf(samples.mapNotNull { effectiveMean(it) }) ?: 0.0,
            samples = samples.size,
        )
    }.sortedWith(compareBy({ it.month }, { it.day }))

    val normalMonths = (1..12).mapNotNull { month ->
        val samples = days.filter { it.date.monthIndex() == month }
        if (samples.isEmpty()) return@mapNotNull null
        NormalMonth(
            month = month,
            avgMax = meanOf(samples.mapNotNull { it.tempMaxC }) ?: return@mapNotNull null,
            avgMin = meanOf(samples.mapNotNull { it.tempMinC }) ?: return@mapNotNull null,
            avgMean = meanOf(samples.mapNotNull { effectiveMean(it) }) ?: return@mapNotNull null,
            samples = samples.size,
        )
    }

    val overall = NormalOverall(
        avgMax = meanOf(days.mapNotNull { it.tempMaxC }) ?: 0.0,
        avgMin = meanOf(days.mapNotNull { it.tempMinC }) ?: 0.0,
        avgMean = meanOf(days.mapNotNull { effectiveMean(it) }) ?: 0.0,
    )
    return ClimateNormals(fromYear, toYear, normalDays, normalMonths, overall)
}

fun mergeDays(primary: List<DayObservation>, fallback: List<DayObservation>): List<DayObservation> {
    val byDate = LinkedHashMap<LocalDate, DayObservation>()
    fallback.forEach { byDate[it.date] = it }
    primary.forEach { byDate[it.date] = it }
    return byDate.values.sortedBy { it.date }
}

fun monthRange(anchor: LocalDate): Pair<LocalDate, LocalDate> {
    val start = LocalDate(anchor.year, anchor.month, 1)
    val end = start.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
    return start to end
}

fun yearRange(year: Int): Pair<LocalDate, LocalDate> =
    LocalDate(year, 1, 1) to LocalDate(year, 12, 31)

fun LocalDate.clamp(min: LocalDate, max: LocalDate): LocalDate = when {
    this < min -> min
    this > max -> max
    else -> this
}

fun shiftAnchor(anchor: LocalDate, span: HistorySpan, delta: Int): LocalDate = when (span) {
    HistorySpan.Day -> anchor.plus(DatePeriod(days = delta))
    HistorySpan.Month -> anchor.plus(DatePeriod(months = delta))
    HistorySpan.Year -> anchor.plus(DatePeriod(years = delta))
}
