package app.cumulus.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class SavedPlace(
    val id: String,
    val name: String,
    val region: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
) {
    val subtitle: String
        get() = listOf(region, country).filter { it.isNotBlank() }.joinToString(", ")
}

@Serializable
data class UnitPrefs(
    val temperature: TemperatureUnit = TemperatureUnit.Celsius,
    val wind: WindUnit = WindUnit.KilometresPerHour,
    val precipitation: PrecipUnit = PrecipUnit.Millimetres,
    val pressure: PressureUnit = PressureUnit.Hectopascals,
    val time: TimeFormat = TimeFormat.TwelveHour,
)

enum class TemperatureUnit { Celsius, Fahrenheit }
enum class WindUnit { KilometresPerHour, MilesPerHour, MetresPerSecond }
enum class PrecipUnit { Millimetres, Inches }
enum class PressureUnit { Hectopascals, InchesOfMercury }
enum class TimeFormat { TwelveHour, TwentyFourHour }

data class HourPoint(
    val time: LocalDateTime,
    val temperatureC: Double?,
    val apparentC: Double?,
    val precipProbability: Double?,
    val precipMm: Double?,
    val weatherCode: Int?,
    val humidity: Double?,
    val dewPointC: Double?,
    val windKmh: Double?,
    val windDirectionDeg: Double?,
    val uvIndex: Double?,
    val isDay: Boolean?,
    val visibilityM: Double?,
    val pressureHpa: Double?,
)

data class DayObservation(
    val date: LocalDate,
    val weatherCode: Int?,
    val tempMaxC: Double?,
    val tempMinC: Double?,
    val tempMeanC: Double?,
    val apparentMaxC: Double?,
    val apparentMinC: Double?,
    val sunrise: LocalDateTime?,
    val sunset: LocalDateTime?,
    val uvIndexMax: Double?,
    val precipMm: Double?,
    val precipProbability: Double?,
    val windMaxKmh: Double?,
    val windGustKmh: Double?,
    val windDirectionDeg: Double?,
    val humidityMean: Double?,
)

data class CurrentConditions(
    val time: LocalDateTime,
    val temperatureC: Double?,
    val apparentC: Double?,
    val humidity: Double?,
    val dewPointC: Double?,
    val weatherCode: Int?,
    val isDay: Boolean,
    val precipMm: Double?,
    val cloudCover: Double?,
    val pressureHpa: Double?,
    val windKmh: Double?,
    val windDirectionDeg: Double?,
    val windGustKmh: Double?,
    val visibilityM: Double?,
    val uvIndex: Double? = null,
)

data class PollenReading(val name: String, val grainsPerCubicMetre: Double)

data class AirSnapshot(
    val usAqi: Double?,
    val europeanAqi: Double?,
    val pm25: Double?,
    val pm10: Double?,
    val ozone: Double?,
    val nitrogenDioxide: Double?,
    val sulphurDioxide: Double?,
    val carbonMonoxide: Double?,
    val uvIndex: Double?,
    val pollen: List<PollenReading>,
    val time: LocalDateTime? = null,
)

data class PlaceWeather(
    val fetchedAtEpochMs: Long,
    val timezone: String,
    val current: CurrentConditions,
    val hourly: List<HourPoint>,
    val daily: List<DayObservation>,
    val air: AirSnapshot?,
)

data class WeatherResource(
    val value: PlaceWeather? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

@Serializable
data class NormalDay(
    val month: Int,
    val day: Int,
    val avgMax: Double,
    val avgMin: Double,
    val avgMean: Double,
    val samples: Int,
)

@Serializable
data class NormalMonth(
    val month: Int,
    val avgMax: Double,
    val avgMin: Double,
    val avgMean: Double,
    val samples: Int,
)

@Serializable
data class NormalOverall(
    val avgMax: Double,
    val avgMin: Double,
    val avgMean: Double,
)

@Serializable
data class ClimateNormals(
    val fromYear: Int,
    val toYear: Int,
    val days: List<NormalDay>,
    val months: List<NormalMonth>,
    val overall: NormalOverall,
) {
    fun day(month: Int, day: Int): NormalDay? = days.find { it.month == month && it.day == day }
    fun month(month: Int): NormalMonth? = months.find { it.month == month }
}

data class PeriodStats(
    val dayCount: Int,
    val averageMean: Double?,
    val averageMax: Double?,
    val averageMin: Double?,
    val highest: Double?,
    val highestDate: LocalDate?,
    val lowest: Double?,
    val lowestDate: LocalDate?,
    val totalPrecipitationMm: Double?,
)

enum class HistorySpan { Day, Month, Year }

data class HistoryRequest(
    val placeId: String,
    val span: HistorySpan,
    val start: LocalDate,
    val end: LocalDate,
)

data class HistoryState(
    val span: HistorySpan = HistorySpan.Month,
    val anchor: LocalDate? = null,
    val request: HistoryRequest? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val days: List<DayObservation> = emptyList(),
    val hours: List<HourPoint> = emptyList(),
    val normalsStatus: String? = null,
    val normalsError: String? = null,
)

sealed interface Screen {
    data object Forecast : Screen
    data object History : Screen
    data object Places : Screen
    data object Settings : Screen
    data object Hourly : Screen
    data class Day(val date: String) : Screen
}

data class AppUiState(
    val ready: Boolean = false,
    val places: List<SavedPlace> = emptyList(),
    val selectedIndex: Int = 0,
    val units: UnitPrefs = UnitPrefs(),
    val stack: List<Screen> = listOf(Screen.Forecast),
    val weather: Map<String, WeatherResource> = emptyMap(),
    val dailyDays: Int = 7,
    val searchQuery: String = "",
    val searchResults: List<SavedPlace> = emptyList(),
    val searching: Boolean = false,
    val searchError: String? = null,
    val locating: Boolean = false,
    val locationMessage: String? = null,
    val history: HistoryState = HistoryState(),
    val normals: Map<String, ClimateNormals> = emptyMap(),
) {
    val selectedPlace: SavedPlace?
        get() = places.getOrNull(selectedIndex)
}
