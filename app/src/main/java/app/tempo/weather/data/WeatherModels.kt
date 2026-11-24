package app.tempo.weather.data

import org.json.JSONArray
import org.json.JSONObject

/** Local escolhido (automático ou manual). */
data class Place(
    val name: String,
    val region: String,
    val latitude: Double,
    val longitude: Double,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("region", region)
        .put("lat", latitude)
        .put("lon", longitude)

    companion object {
        fun fromJson(o: JSONObject) = Place(
            name = o.getString("name"),
            region = o.optString("region", ""),
            latitude = o.getDouble("lat"),
            longitude = o.getDouble("lon"),
        )
    }
}

data class CurrentWeather(
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val windSpeedKmh: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val precipitationProbability: Int?,
    val uvIndex: Double?,
    /** Horário local da observação, ex.: "2026-11-24T14:15". */
    val time: String,
)

data class DailyForecast(
    /** Data local ISO, ex.: "2026-11-25". */
    val date: String,
    val weatherCode: Int,
    val max: Double,
    val min: Double,
    val precipitationProbability: Int?,
    val uvIndexMax: Double?,
    /** Horários locais ISO, ex.: "2026-11-25T05:12". */
    val sunrise: String?,
    val sunset: String?,
)

/**
 * Um instantâneo completo e válido retornado pela Open-Meteo.
 * Só é persistido quando a resposta foi interpretada com sucesso.
 */
data class WeatherSnapshot(
    val place: Place,
    val current: CurrentWeather,
    /** daily[0] é hoje; daily[1..5] são os próximos cinco dias. */
    val daily: List<DailyForecast>,
    val timezone: String,
    val utcOffsetSeconds: Int,
    /** Momento (epoch ms) em que os dados foram obtidos com sucesso. */
    val fetchedAt: Long,
) {
    val today: DailyForecast? get() = daily.firstOrNull()
    val nextDays: List<DailyForecast> get() = daily.drop(1).take(5)

    fun toJson(): JSONObject {
        val c = JSONObject()
            .put("t", current.temperature)
            .put("at", current.apparentTemperature)
            .put("h", current.humidity)
            .put("w", current.windSpeedKmh)
            .put("code", current.weatherCode)
            .put("day", current.isDay)
            .put("time", current.time)
        current.precipitationProbability?.let { c.put("pp", it) }
        current.uvIndex?.let { c.put("uv", it) }

        val d = JSONArray()
        daily.forEach { day ->
            val o = JSONObject()
                .put("date", day.date)
                .put("code", day.weatherCode)
                .put("max", day.max)
                .put("min", day.min)
            day.precipitationProbability?.let { o.put("pp", it) }
            day.uvIndexMax?.let { o.put("uv", it) }
            day.sunrise?.let { o.put("sr", it) }
            day.sunset?.let { o.put("ss", it) }
            d.put(o)
        }
        return JSONObject()
            .put("v", 1)
            .put("place", place.toJson())
            .put("current", c)
            .put("daily", d)
            .put("tz", timezone)
            .put("off", utcOffsetSeconds)
            .put("fetchedAt", fetchedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): WeatherSnapshot {
            val c = o.getJSONObject("current")
            val d = o.getJSONArray("daily")
            val days = (0 until d.length()).map { i ->
                val day = d.getJSONObject(i)
                DailyForecast(
                    date = day.getString("date"),
                    weatherCode = day.getInt("code"),
                    max = day.getDouble("max"),
                    min = day.getDouble("min"),
                    precipitationProbability = if (day.has("pp")) day.getInt("pp") else null,
                    uvIndexMax = if (day.has("uv")) day.getDouble("uv") else null,
                    sunrise = day.optString("sr").ifEmpty { null },
                    sunset = day.optString("ss").ifEmpty { null },
                )
            }
            return WeatherSnapshot(
                place = Place.fromJson(o.getJSONObject("place")),
                current = CurrentWeather(
                    temperature = c.getDouble("t"),
                    apparentTemperature = c.getDouble("at"),
                    humidity = c.getInt("h"),
                    windSpeedKmh = c.getDouble("w"),
                    weatherCode = c.getInt("code"),
                    isDay = c.getBoolean("day"),
                    precipitationProbability = if (c.has("pp")) c.getInt("pp") else null,
                    uvIndex = if (c.has("uv")) c.getDouble("uv") else null,
                    time = c.getString("time"),
                ),
                daily = days,
                timezone = o.optString("tz", "UTC"),
                utcOffsetSeconds = o.optInt("off", 0),
                fetchedAt = o.getLong("fetchedAt"),
            )
        }
    }
}
