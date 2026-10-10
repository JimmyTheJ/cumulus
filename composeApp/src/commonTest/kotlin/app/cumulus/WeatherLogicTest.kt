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
import app.cumulus.model.humidex
import app.cumulus.model.mergeDays
import app.cumulus.model.monthRange
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
    fun parsesForecastAirAndSearch() {
        val forecast = parseForecast(
            """
            {"timezone":"America/Toronto","current":{"time":"2026-10-10T13:45","temperature_2m":15.7,"apparent_temperature":12.6,"relative_humidity_2m":60,"dew_point_2m":8.0,"is_day":1,"precipitation":0,"weather_code":3,"cloud_cover":100,"pressure_msl":1027.7,"wind_speed_10m":20.1,"wind_direction_10m":91,"wind_gusts_10m":28.8,"visibility":21200},"hourly":{"time":["2026-10-10T13:00"],"temperature_2m":[15.2],"apparent_temperature":[12.0],"precipitation_probability":[10],"precipitation":[0],"weather_code":[3],"relative_humidity_2m":[60],"dew_point_2m":[8],"wind_speed_10m":[20],"wind_direction_10m":[90],"uv_index":[3.5],"is_day":[1],"visibility":[20000],"pressure_msl":[1027]},"daily":{"time":["2026-10-10"],"weather_code":[3],"temperature_2m_max":[16.0],"temperature_2m_min":[5.8],"apparent_temperature_max":[12.9],"apparent_temperature_min":[3.0],"sunrise":["2026-10-10T07:25"],"sunset":["2026-10-10T18:42"],"uv_index_max":[3.75],"precipitation_sum":[0],"precipitation_probability_max":[0],"wind_speed_10m_max":[20.6],"wind_gusts_10m_max":[36],"wind_direction_10m_dominant":[68]}}
            """.trimIndent(),
            1000L,
        )
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
