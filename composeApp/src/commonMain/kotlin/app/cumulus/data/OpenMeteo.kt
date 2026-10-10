package app.cumulus.data

import app.cumulus.model.AirSnapshot
import app.cumulus.model.CurrentConditions
import app.cumulus.model.DayObservation
import app.cumulus.model.HourPoint
import app.cumulus.model.PlaceWeather
import app.cumulus.model.PollenReading
import app.cumulus.model.SavedPlace
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class WeatherException(message: String) : Exception(message)

private val json = Json { ignoreUnknownKeys = true }

class OpenMeteoApi(private val client: HttpClient) {
    suspend fun get(url: String, params: Map<String, String>): String {
        val response = client.get(url) {
            params.forEach { (key, value) -> parameter(key, value) }
        }
        val body = response.bodyAsText()
        if (response.status.value >= 400) {
            throw WeatherException(errorReason(body) ?: "Weather service returned ${response.status.value}")
        }
        return body
    }
}

fun errorReason(body: String): String? = runCatching {
    val obj = json.parseToJsonElement(body).jsonObject
    val flag = obj["error"]
    if (flag is JsonPrimitive && flag.booleanOrNull == true) {
        obj["reason"]?.jsonPrimitive?.contentOrNull
    } else {
        null
    }
}.getOrNull()

private fun parseObject(body: String): JsonObject {
    val element = json.parseToJsonElement(body)
    val obj = element as? JsonObject ?: throw WeatherException("Unexpected response from the weather service")
    val reason = obj["error"]
    if (reason is JsonPrimitive && reason.booleanOrNull == true) {
        throw WeatherException(obj["reason"]?.jsonPrimitive?.contentOrNull ?: "Weather service error")
    }
    return obj
}

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonArray.numberAt(index: Int): Double? {
    val element = getOrNull(index) ?: return null
    val primitive = element as? JsonPrimitive ?: return null
    if (primitive.contentOrNull == "null") return null
    return primitive.doubleOrNull ?: primitive.intOrNull?.toDouble()
}

private fun JsonArray.intAt(index: Int): Int? = numberAt(index)?.toInt()

private fun parseDateTime(value: String?): LocalDateTime? {
    if (value.isNullOrBlank()) return null
    return runCatching { LocalDateTime.parse(value) }.getOrNull()
}

private fun parseDate(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(value.substringBefore('T')) }.getOrNull()
}

fun parseHours(section: JsonObject?): List<HourPoint> {
    if (section == null) return emptyList()
    val times = section.array("time") ?: return emptyList()
    val temperature = section.array("temperature_2m")
    val apparent = section.array("apparent_temperature")
    val probability = section.array("precipitation_probability")
    val precip = section.array("precipitation")
    val code = section.array("weather_code")
    val humidity = section.array("relative_humidity_2m")
    val dew = section.array("dew_point_2m")
    val wind = section.array("wind_speed_10m")
    val direction = section.array("wind_direction_10m")
    val uv = section.array("uv_index")
    val day = section.array("is_day")
    val visibility = section.array("visibility")
    val pressure = section.array("pressure_msl")
    return buildList {
        for (index in times.indices) {
            val stamp = (times[index] as? JsonPrimitive)?.contentOrNull ?: continue
            val time = parseDateTime(stamp) ?: continue
            add(
                HourPoint(
                    time = time,
                    temperatureC = temperature?.numberAt(index),
                    apparentC = apparent?.numberAt(index),
                    precipProbability = probability?.numberAt(index),
                    precipMm = precip?.numberAt(index),
                    weatherCode = code?.intAt(index),
                    humidity = humidity?.numberAt(index),
                    dewPointC = dew?.numberAt(index),
                    windKmh = wind?.numberAt(index),
                    windDirectionDeg = direction?.numberAt(index),
                    uvIndex = uv?.numberAt(index),
                    isDay = day?.intAt(index)?.let { it == 1 },
                    visibilityM = visibility?.numberAt(index),
                    pressureHpa = pressure?.numberAt(index),
                ),
            )
        }
    }
}

fun parseDays(section: JsonObject?): List<DayObservation> {
    if (section == null) return emptyList()
    val times = section.array("time") ?: return emptyList()
    val code = section.array("weather_code")
    val max = section.array("temperature_2m_max")
    val min = section.array("temperature_2m_min")
    val mean = section.array("temperature_2m_mean")
    val apparentMax = section.array("apparent_temperature_max")
    val apparentMin = section.array("apparent_temperature_min")
    val sunrise = section.array("sunrise")
    val sunset = section.array("sunset")
    val uv = section.array("uv_index_max")
    val precip = section.array("precipitation_sum")
    val probability = section.array("precipitation_probability_max")
    val wind = section.array("wind_speed_10m_max")
    val gust = section.array("wind_gusts_10m_max")
    val direction = section.array("wind_direction_10m_dominant")
    val humidity = section.array("relative_humidity_2m_mean")
    return buildList {
        for (index in times.indices) {
            val stamp = (times[index] as? JsonPrimitive)?.contentOrNull ?: continue
            val date = parseDate(stamp) ?: continue
            add(
                DayObservation(
                    date = date,
                    weatherCode = code?.intAt(index),
                    tempMaxC = max?.numberAt(index),
                    tempMinC = min?.numberAt(index),
                    tempMeanC = mean?.numberAt(index),
                    apparentMaxC = apparentMax?.numberAt(index),
                    apparentMinC = apparentMin?.numberAt(index),
                    sunrise = sunrise?.let { (it.getOrNull(index) as? JsonPrimitive)?.contentOrNull }?.let(::parseDateTime),
                    sunset = sunset?.let { (it.getOrNull(index) as? JsonPrimitive)?.contentOrNull }?.let(::parseDateTime),
                    uvIndexMax = uv?.numberAt(index),
                    precipMm = precip?.numberAt(index),
                    precipProbability = probability?.numberAt(index),
                    windMaxKmh = wind?.numberAt(index),
                    windGustKmh = gust?.numberAt(index),
                    windDirectionDeg = direction?.numberAt(index),
                    humidityMean = humidity?.numberAt(index),
                ),
            )
        }
    }
}

fun parseForecast(body: String, fetchedAtEpochMs: Long): PlaceWeather {
    val root = parseObject(body)
    val timezone = root["timezone"]?.jsonPrimitive?.contentOrNull ?: "UTC"
    val currentObj = root["current"] as? JsonObject ?: throw WeatherException("Current conditions were missing")
    val time = parseDateTime(currentObj["time"]?.jsonPrimitive?.contentOrNull)
        ?: throw WeatherException("Current time was missing")
    fun JsonObject.num(name: String): Double? = (this[name] as? JsonPrimitive)?.doubleOrNull
        ?: (this[name] as? JsonPrimitive)?.intOrNull?.toDouble()
    val current = CurrentConditions(
        time = time,
        temperatureC = currentObj.num("temperature_2m"),
        apparentC = currentObj.num("apparent_temperature"),
        humidity = currentObj.num("relative_humidity_2m"),
        dewPointC = currentObj.num("dew_point_2m"),
        weatherCode = currentObj.num("weather_code")?.toInt(),
        isDay = currentObj.num("is_day")?.toInt() != 0,
        precipMm = currentObj.num("precipitation"),
        cloudCover = currentObj.num("cloud_cover"),
        pressureHpa = currentObj.num("pressure_msl"),
        windKmh = currentObj.num("wind_speed_10m"),
        windDirectionDeg = currentObj.num("wind_direction_10m"),
        windGustKmh = currentObj.num("wind_gusts_10m"),
        visibilityM = currentObj.num("visibility"),
        uvIndex = currentObj.num("uv_index"),
    )
    return PlaceWeather(
        fetchedAtEpochMs = fetchedAtEpochMs,
        timezone = timezone,
        current = current,
        hourly = parseHours(root["hourly"] as? JsonObject),
        daily = parseDays(root["daily"] as? JsonObject),
        air = null,
    )
}

fun parseAir(body: String): AirSnapshot {
    val root = parseObject(body)
    val current = root["current"] as? JsonObject ?: throw WeatherException("Air quality was missing")
    fun num(name: String): Double? {
        val primitive = current[name] as? JsonPrimitive ?: return null
        if (primitive.contentOrNull == "null") return null
        return primitive.doubleOrNull ?: primitive.intOrNull?.toDouble()
    }
    val pollenNames = listOf(
        "alder_pollen" to "Alder",
        "birch_pollen" to "Birch",
        "grass_pollen" to "Grass",
        "mugwort_pollen" to "Mugwort",
        "olive_pollen" to "Olive",
        "ragweed_pollen" to "Ragweed",
    )
    val pollen = pollenNames.mapNotNull { (key, label) ->
        val amount = num(key) ?: return@mapNotNull null
        PollenReading(label, amount)
    }
    return AirSnapshot(
        usAqi = num("us_aqi"),
        europeanAqi = num("european_aqi"),
        pm25 = num("pm2_5"),
        pm10 = num("pm10"),
        ozone = num("ozone"),
        nitrogenDioxide = num("nitrogen_dioxide"),
        sulphurDioxide = num("sulphur_dioxide"),
        carbonMonoxide = num("carbon_monoxide"),
        uvIndex = num("uv_index"),
        pollen = pollen,
        time = parseDateTime(current["time"]?.jsonPrimitive?.contentOrNull),
    )
}

fun parseSearch(body: String): List<SavedPlace> {
    val root = parseObject(body)
    val results = root["results"] as? JsonArray ?: return emptyList()
    return results.mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        fun text(name: String): String = item[name]?.jsonPrimitive?.contentOrNull.orEmpty()
        fun num(name: String): Double? = item[name]?.jsonPrimitive?.doubleOrNull
            ?: item[name]?.jsonPrimitive?.intOrNull?.toDouble()
        val id = item["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val latitude = num("latitude") ?: return@mapNotNull null
        val longitude = num("longitude") ?: return@mapNotNull null
        SavedPlace(
            id = "geo-$id",
            name = text("name").ifBlank { "Unknown place" },
            region = text("admin1"),
            country = text("country"),
            latitude = latitude,
            longitude = longitude,
            timezone = text("timezone").ifBlank { "UTC" },
        )
    }
}

fun parseHistoryBody(body: String): Pair<List<DayObservation>, List<HourPoint>> {
    val root = parseObject(body)
    return parseDays(root["daily"] as? JsonObject) to parseHours(root["hourly"] as? JsonObject)
}
