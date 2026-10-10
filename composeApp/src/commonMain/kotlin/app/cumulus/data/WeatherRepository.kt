package app.cumulus.data

import app.cumulus.model.ClimateNormals
import app.cumulus.model.DayObservation
import app.cumulus.model.HourPoint
import app.cumulus.model.PlaceWeather
import app.cumulus.model.SavedPlace
import app.cumulus.model.climateNormals
import app.cumulus.model.formatFixed
import app.cumulus.model.mergeDays
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class WeatherRepository(
    private val api: OpenMeteoApi,
    private val disk: DiskStore,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    suspend fun search(query: String): List<SavedPlace> {
        val body = api.get(
            "https://geocoding-api.open-meteo.com/v1/search",
            mapOf(
                "name" to query,
                "count" to "8",
                "language" to "en",
                "format" to "json",
            ),
        )
        return parseSearch(body)
    }

    suspend fun loadWeather(place: SavedPlace, force: Boolean = false): PlaceWeather {
        val now = clock()
        val forecastBody = cached("forecast-${placeKey(place)}", FORECAST_TTL_MS, force, now) {
            api.get(
                "https://api.open-meteo.com/v1/forecast",
                params(
                    place,
                    mapOf(
                        "timezone" to "auto",
                        "forecast_days" to "14",
                        "current" to CURRENT,
                        "hourly" to HOURLY,
                        "daily" to DAILY,
                    ),
                ),
            )
        }
        val forecast = parseForecast(forecastBody, now)
        val air = runCatching {
            val airBody = cached("air-${placeKey(place)}", AIR_TTL_MS, force, now) {
                api.get(
                    "https://air-quality-api.open-meteo.com/v1/air-quality",
                    params(place, mapOf("timezone" to "auto", "current" to AIR)),
                )
            }
            parseAir(airBody)
        }.getOrNull()
        return forecast.copy(air = air)
    }

    suspend fun history(
        place: SavedPlace,
        start: String,
        end: String,
        includeHours: Boolean,
    ): Pair<List<DayObservation>, List<HourPoint>> {
        val now = clock()
        val age = if (isSettled(end)) HISTORY_TTL_MS else RECENT_TTL_MS
        val dailyBody = cached("daily-${placeKey(place)}-$start-$end", age, force = false, now) {
            api.get(
                "https://archive-api.open-meteo.com/v1/archive",
                params(
                    place,
                    mapOf(
                        "start_date" to start,
                        "end_date" to end,
                        "timezone" to "auto",
                        "daily" to HISTORY_DAILY,
                    ),
                ),
            )
        }
        val archiveDays = parseHistoryBody(dailyBody).first
        val hours = if (!includeHours) {
            emptyList()
        } else {
            val hourlyBody = cached("hourly-${placeKey(place)}-$start-$end", age, force = false, now) {
                api.get(
                    "https://archive-api.open-meteo.com/v1/archive",
                    params(
                        place,
                        mapOf(
                            "start_date" to start,
                            "end_date" to end,
                            "timezone" to "auto",
                            "hourly" to HISTORY_HOURLY,
                        ),
                    ),
                )
            }
            parseHistoryBody(hourlyBody).second
        }
        val last = archiveDays.lastOrNull()?.date?.toString()
        if (last != null && last >= end) return archiveDays to hours
        val tailStart = last?.let(::dayAfter) ?: start
        if (tailStart > end) return archiveDays to hours
        val tail = runCatching {
            val extra = buildMap {
                put("start_date", tailStart)
                put("end_date", end)
                put("timezone", "auto")
                put("daily", "$DAILY,temperature_2m_mean")
                if (includeHours) put("hourly", HOURLY)
            }
            parseHistoryBody(api.get("https://api.open-meteo.com/v1/forecast", params(place, extra)))
        }.getOrDefault(emptyList<DayObservation>() to emptyList())
        return mergeDays(tail.first, archiveDays) to (hours + tail.second)
    }

    suspend fun normals(place: SavedPlace, onProgress: (String) -> Unit): ClimateNormals {
        val key = placeKey(place)
        disk.readNormals(key)?.let { return it }
        val chunks = listOf(1991 to 2000, 2001 to 2010, 2011 to 2020)
        val days = mutableListOf<DayObservation>()
        for ((from, to) in chunks) {
            onProgress("Downloading $from–$to")
            val body = api.get(
                "https://archive-api.open-meteo.com/v1/archive",
                params(
                    place,
                    mapOf(
                        "start_date" to "$from-01-01",
                        "end_date" to "$to-12-31",
                        "timezone" to "auto",
                        "daily" to "temperature_2m_max,temperature_2m_min,temperature_2m_mean",
                    ),
                ),
            )
            days += parseHistoryBody(body).first
        }
        onProgress("Crunching 1991–2020")
        val computed = climateNormals(days, 1991, 2020)
        disk.writeNormals(key, computed)
        return computed
    }

    suspend fun cachedNormals(place: SavedPlace): ClimateNormals? = disk.readNormals(placeKey(place))

    private suspend fun cached(
        name: String,
        maxAgeMs: Long,
        force: Boolean,
        now: Long,
        fetch: suspend () -> String,
    ): String {
        if (!force) {
            disk.readCache(safeName(name), maxAgeMs, now)?.let { return it }
        }
        val body = fetch()
        disk.writeCache(safeName(name), body, now)
        return body
    }

    private fun isSettled(end: String): Boolean = runCatching {
        val endDate = LocalDate.parse(end)
        val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        endDate < today.minus(DatePeriod(days = 3))
    }.getOrDefault(false)

    private fun params(place: SavedPlace, extra: Map<String, String>): Map<String, String> =
        mapOf(
            "latitude" to formatFixed(place.latitude, 4),
            "longitude" to formatFixed(place.longitude, 4),
        ) + extra

    private fun placeKey(place: SavedPlace): String =
        "${formatFixed(place.latitude, 2)}_${formatFixed(place.longitude, 2)}"

    private fun safeName(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun dayAfter(iso: String): String =
        LocalDate.parse(iso).plus(DatePeriod(days = 1)).toString()

    private companion object {
        const val FORECAST_TTL_MS = 20 * 60 * 1000L
        const val AIR_TTL_MS = 30 * 60 * 1000L
        const val RECENT_TTL_MS = 30 * 60 * 1000L
        const val HISTORY_TTL_MS = 7 * 24 * 60 * 60 * 1000L
        const val CURRENT =
            "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m,dew_point_2m,visibility"
        const val HOURLY =
            "temperature_2m,apparent_temperature,precipitation_probability,precipitation,weather_code,relative_humidity_2m,dew_point_2m,wind_speed_10m,wind_direction_10m,uv_index,is_day,visibility,pressure_msl"
        const val DAILY =
            "weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min,sunrise,sunset,uv_index_max,precipitation_sum,precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant"
        const val AIR =
            "us_aqi,european_aqi,pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone,uv_index,alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen"
        const val HISTORY_DAILY =
            "weather_code,temperature_2m_max,temperature_2m_min,temperature_2m_mean,apparent_temperature_max,apparent_temperature_min,precipitation_sum,wind_speed_10m_max,relative_humidity_2m_mean,sunrise,sunset"
        const val HISTORY_HOURLY =
            "temperature_2m,apparent_temperature,precipitation,weather_code,relative_humidity_2m,wind_speed_10m,wind_direction_10m,dew_point_2m"
    }
}
