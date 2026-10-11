package app.cumulus

import app.cumulus.data.parseAir
import app.cumulus.data.parseForecast
import app.cumulus.data.parseHistoryBody
import app.cumulus.data.parseSearch
import app.cumulus.model.DayObservation
import app.cumulus.model.PrecipUnit
import app.cumulus.model.TemperatureUnit
import app.cumulus.model.UnitPrefs
import app.cumulus.model.WindUnit
import app.cumulus.model.aqiReading
import app.cumulus.model.bugReport
import app.cumulus.model.climateNormals
import app.cumulus.model.compass
import app.cumulus.model.formatFixed
import app.cumulus.model.formatPrecip
import app.cumulus.model.formatTemperature
import app.cumulus.model.formatTemperatureDelta
import app.cumulus.model.healthSourceNote
import app.cumulus.model.humidex
import app.cumulus.model.liveUv
import app.cumulus.model.mergeDays
import app.cumulus.model.modelFreshness
import app.cumulus.model.monthLength
import app.cumulus.model.monthRange
import app.cumulus.model.niceTicks
import app.cumulus.model.shownDayNumbers
import app.cumulus.model.shownHourMarks
import app.cumulus.model.pollenReport
import app.cumulus.model.summarize
import app.cumulus.model.weatherLabel
import app.cumulus.model.windChillCelsius
import app.cumulus.model.windFromKmh
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class WeatherLogicTest {
    @Test
    fun windChillMatchesEnvironmentCanadaFormula() {
        assertClose(-32.57, windChillCelsius(-20.0, 30.0))
        assertClose(-17.86, windChillCelsius(-10.0, 20.0))
        assertClose(1.75, windChillCelsius(5.0, 15.0))
        assertNull(windChillCelsius(18.0, 20.0))
        assertNull(windChillCelsius(-5.0, 2.0))
    }

    @Test
    fun humidexMatchesPublishedExamples() {
        assertClose(33.97, humidex(30.0, 15.0))
        assertClose(32.57, humidex(25.0, 20.0))
        assertTrue(humidex(20.0, 10.0) < 25.0)
    }

    @Test
    fun bugActivityRespondsToWindAndCold() {
        val calm = bugReport(24.0, 80.0, 4.0)!!
        val windy = bugReport(24.0, 80.0, 30.0)!!
        val cold = bugReport(2.0, 80.0, 4.0)!!
        assertEquals("High", calm.label)
        assertTrue(windy.score < calm.score)
        assertEquals("Quiet", cold.label)
        assertNull(bugReport(null, 50.0, 5.0))
    }

    @Test
    fun periodStatsAndNormals() {
        val days = listOf(
            day("2020-07-01", 30.0, 18.0, 24.0, 2.0),
            day("2020-07-02", 28.0, 16.0, 22.0, 0.0),
            day("2021-07-01", 32.0, 20.0, 26.0, 5.0),
        )
        val stats = summarize(days)
        assertEquals(3, stats.dayCount)
        assertClose(24.0, stats.averageMean)
        assertClose(30.0, stats.averageMax)
        assertEquals(LocalDate.parse("2021-07-01"), stats.highestDate)
        assertEquals(LocalDate.parse("2020-07-02"), stats.lowestDate)
        assertClose(7.0, stats.totalPrecipitationMm)

        val normals = climateNormals(days, 2020, 2021)
        val julyFirst = normals.day(7, 1)!!
        assertClose(31.0, julyFirst.avgMax)
        assertEquals(2, julyFirst.samples)
        assertClose(30.0, normals.month(7)!!.avgMax)
    }

    @Test
    fun mergePrefersPrimaryObservation() {
        val archive = listOf(day("2024-01-01", 1.0, -5.0, -2.0, 0.0))
        val newer = listOf(day("2024-01-01", 3.0, -4.0, 0.0, 1.0), day("2024-01-02", 4.0, -1.0, 1.0, 0.0))
        val merged = mergeDays(newer, archive)
        assertEquals(2, merged.size)
        assertClose(3.0, merged.first().tempMaxC)
    }

    @Test
    fun historyScaleAndDayLabelsStayReadable() {
        assertEquals(listOf(5.0, 10.0, 15.0, 20.0, 25.0), niceTicks(6.0, 25.0))
        assertEquals(listOf(-40.0, -20.0, 0.0, 20.0, 40.0), niceTicks(-25.0, 37.0))
        assertEquals(setOf(1, 10, 20, 30), shownDayNumbers((1..31).toList()))
        assertEquals(setOf(1, 10, 20), shownDayNumbers((1..28).toList()))
        assertEquals(setOf(1, 10), shownDayNumbers((1..12).toList()))
        assertEquals(setOf(5, 8), shownDayNumbers(listOf(5, 6, 7, 8)))
        assertEquals(setOf(0, 6, 12, 18), shownHourMarks((0..23).toList()))
        assertEquals(29, monthLength(2024, Month.FEBRUARY))
        assertEquals(28, monthLength(2023, Month.FEBRUARY))
    }

    @Test
    fun monthRangeCoversTheWholeMonth() {
        val (start, end) = monthRange(LocalDate.parse("2024-02-10"))
        assertEquals(LocalDate.parse("2024-02-01"), start)
        assertEquals(LocalDate.parse("2024-02-29"), end)
    }

    @Test
    fun unitsAndCompass() {
        val metric = UnitPrefs()
        assertEquals("0°", formatTemperature(0.0, metric))
        assertEquals("32°F", formatTemperature(0.0, metric.copy(temperature = TemperatureUnit.Fahrenheit)))
        assertEquals("−40°", formatTemperatureDelta(-40.0, metric))
        assertClose(10.0, windFromKmh(36.0, WindUnit.MetresPerSecond))
        assertEquals("1.00 in", formatPrecip(25.4, metric.copy(precipitation = PrecipUnit.Inches)))
        assertEquals("N", compass(0.0))
        assertEquals("E", compass(90.0))
        assertEquals("N", compass(350.0))
        assertEquals("1.3", formatFixed(1.26, 1))
        assertEquals("-1.3", formatFixed(-1.26, 1))
    }

    @Test
    fun labelsAndPollenNullVersusZero() {
        assertEquals("Clear", weatherLabel(0))
        assertEquals("Thunderstorm with hail", weatherLabel(99))
        assertEquals(false, pollenReport(emptyList()).available)
        val quiet = pollenReport(listOf(app.cumulus.model.PollenReading("Grass", 0.0)))
        assertEquals(true, quiet.available)
        assertEquals("Low", quiet.value)
    }

    @Test
    fun liveUvUsesTheCurrentHourNotTheDailyPeak() {
        val base = sampleForecast()
        val fromHour = liveUv(base)
        assertClose(3.5, fromHour.index)
        assertEquals(LocalDateTime.parse("2026-10-10T13:00"), fromHour.observedAt)
        assertClose(3.75, fromHour.peak)

        val fromCurrent = liveUv(base.copy(current = base.current.copy(uvIndex = 2.1)))
        assertClose(2.1, fromCurrent.index)
        assertEquals(LocalDateTime.parse("2026-10-10T13:45"), fromCurrent.observedAt)
        assertClose(3.75, fromCurrent.peak)

        val zero = liveUv(base.copy(current = base.current.copy(uvIndex = 0.0)))
        assertClose(0.0, zero.index)

        val distant = base.copy(
            hourly = base.hourly.map { it.copy(time = LocalDateTime.parse("2026-10-10T10:00")) },
        )
        val noLive = liveUv(distant)
        assertNull(noLive.index)
        assertClose(3.75, noLive.peak)

        val air = parseAir("""{"current":{"time":"2026-10-10T12:00","us_aqi":28,"uv_index":1.2}}""")
        assertEquals(LocalDateTime.parse("2026-10-10T12:00"), air.time)
        val fromAir = liveUv(distant.copy(air = air))
        assertClose(1.2, fromAir.index)
        assertEquals(air.time, fromAir.observedAt)

        val parsed = parseForecast(
            forecastJson.replace(""""visibility":21200}""", """"visibility":21200,"uv_index":2.1}"""),
            1000L,
        )
        assertClose(2.1, parsed.current.uvIndex)
        assertClose(2.1, liveUv(parsed).index)

        val zone = TimeZone.of("America/Toronto")
        val observed = LocalDateTime.parse("2026-10-10T13:00")
        val observedMs = observed.toInstant(zone).toEpochMilliseconds()
        assertEquals(
            "This hour, 1pm · fetched 5 min ago",
            modelFreshness(observed, zone, UnitPrefs(), observedMs - 5 * 60_000, observedMs),
        )
        assertEquals(
            "As of 1pm · fetched 3h ago",
            modelFreshness(observed, zone, UnitPrefs(), observedMs, observedMs + 3 * 60 * 60_000),
        )
        val note = healthSourceNote(
            base.copy(current = base.current.copy(uvIndex = 2.1), air = air),
            zone,
            UnitPrefs(),
            observedMs,
        )
        assertTrue(note.contains("latest model hour, not a live sensor"))
        assertTrue(note.contains("Air as of 12pm"))
    }

    @Test
    fun parsesForecastAirAndSearch() {
        val forecast = sampleForecast()
        assertEquals("America/Toronto", forecast.timezone)
        assertClose(15.7, forecast.current.temperatureC)
        assertEquals(true, forecast.current.isDay)
        assertEquals(1, forecast.hourly.size)
        assertEquals(LocalDateTime.parse("2026-10-10T07:25"), forecast.daily.first().sunrise)
        assertEquals("Moderate", aqiReading(parseAir("""{"current":{"us_aqi":72,"pm2_5":12.5,"grass_pollen":null,"birch_pollen":0}}"""))!!.detail)

        val places = parseSearch(
            """{"results":[{"id":6167865,"name":"Toronto","latitude":43.7,"longitude":-79.4,"timezone":"America/Toronto","country":"Canada","admin1":"Ontario"}]}""",
        )
        assertEquals("geo-6167865", places.single().id)
        assertEquals("Ontario", places.single().region)

        val history = parseHistoryBody(
            """{"daily":{"time":["2024-07-15"],"temperature_2m_max":[25.7],"temperature_2m_min":[19.9],"temperature_2m_mean":[22.9],"precipitation_sum":[6.4]}}""",
        )
        assertClose(22.9, history.first.single().tempMeanC)
    }

    private fun sampleForecast() = parseForecast(forecastJson, 1000L)

    private val forecastJson =
        """
        {"timezone":"America/Toronto","current":{"time":"2026-10-10T13:45","temperature_2m":15.7,"apparent_temperature":12.6,"relative_humidity_2m":60,"dew_point_2m":8.0,"is_day":1,"precipitation":0,"weather_code":3,"cloud_cover":100,"pressure_msl":1027.7,"wind_speed_10m":20.1,"wind_direction_10m":91,"wind_gusts_10m":28.8,"visibility":21200},"hourly":{"time":["2026-10-10T13:00"],"temperature_2m":[15.2],"apparent_temperature":[12.0],"precipitation_probability":[10],"precipitation":[0],"weather_code":[3],"relative_humidity_2m":[60],"dew_point_2m":[8],"wind_speed_10m":[20],"wind_direction_10m":[90],"uv_index":[3.5],"is_day":[1],"visibility":[20000],"pressure_msl":[1027]},"daily":{"time":["2026-10-10"],"weather_code":[3],"temperature_2m_max":[16.0],"temperature_2m_min":[5.8],"apparent_temperature_max":[12.9],"apparent_temperature_min":[3.0],"sunrise":["2026-10-10T07:25"],"sunset":["2026-10-10T18:42"],"uv_index_max":[3.75],"precipitation_sum":[0],"precipitation_probability_max":[0],"wind_speed_10m_max":[20.6],"wind_gusts_10m_max":[36],"wind_direction_10m_dominant":[68]}}
        """.trimIndent()

    private fun day(date: String, max: Double, min: Double, mean: Double, precip: Double) = DayObservation(
        date = LocalDate.parse(date),
        weatherCode = 1,
        tempMaxC = max,
        tempMinC = min,
        tempMeanC = mean,
        apparentMaxC = null,
        apparentMinC = null,
        sunrise = null,
        sunset = null,
        uvIndexMax = null,
        precipMm = precip,
        precipProbability = null,
        windMaxKmh = null,
        windGustKmh = null,
        windDirectionDeg = null,
        humidityMean = null,
    )

    private fun assertClose(expected: Double, actual: Double?) {
        assertTrue(actual != null && abs(expected - actual) < 0.02, "expected $expected but was $actual")
    }
}
