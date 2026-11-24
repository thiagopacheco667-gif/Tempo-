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

    /** Mesmo lugar (tolerância de ~1 km), usado para decidir se dados auxiliares ainda valem. */
    fun sameSpot(other: Place?): Boolean = other != null &&
        Math.abs(latitude - other.latitude) < 0.01 && Math.abs(longitude - other.longitude) < 0.01

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
    /** Direção de onde o vento sopra, em graus (0 = norte). */
    val windDirection: Int? = null,
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

/** Uma hora da previsão de 24 horas. */
data class HourlyForecast(
    /** Horário local ISO, ex.: "2026-11-24T15:00". */
    val time: String,
    val temperature: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val precipitationProbability: Int?,
    val windSpeedKmh: Double?,
)

/** Qualidade do ar (Open-Meteo Air Quality, índice US AQI). */
data class AirQuality(
    val usAqi: Int,
    val pm25: Double?,
    val pm10: Double?,
    val time: String,
) {
    val category: String
        get() = when {
            usAqi <= 50 -> "Boa"
            usAqi <= 100 -> "Moderada"
            usAqi <= 150 -> "Ruim para sensíveis"
            usAqi <= 200 -> "Ruim"
            usAqi <= 300 -> "Muito ruim"
            else -> "Perigosa"
        }

    /** Cor de referência da escala EPA, suavizada para o fundo escuro. */
    val color: Int
        get() = when {
            usAqi <= 50 -> 0xFF7CE0A0.toInt()
            usAqi <= 100 -> 0xFFF5D76E.toInt()
            usAqi <= 150 -> 0xFFFFAA5C.toInt()
            usAqi <= 200 -> 0xFFFF6B6B.toInt()
            usAqi <= 300 -> 0xFFC28CFF.toInt()
            else -> 0xFFB0536E.toInt()
        }

    fun toJson(): JSONObject = JSONObject().put("aqi", usAqi).put("time", time).also { o ->
        pm25?.let { o.put("pm25", it) }
        pm10?.let { o.put("pm10", it) }
    }

    companion object {
        fun fromJson(o: JSONObject) = AirQuality(
            usAqi = o.getInt("aqi"),
            pm25 = if (o.has("pm25")) o.getDouble("pm25") else null,
            pm10 = if (o.has("pm10")) o.getDouble("pm10") else null,
            time = o.optString("time"),
        )
    }
}

enum class AlertLevel(val label: String, val colorName: String, val color: Int) {
    POTENTIAL("Perigo potencial", "amarelo", 0xFFFFD84D.toInt()),
    DANGER("Perigo", "laranja", 0xFFFF9442.toInt()),
    GREAT_DANGER("Grande perigo", "vermelho", 0xFFFF5A52.toInt());
}

/** Aviso meteorológico oficial do INMET que cobre o local consultado. */
data class WeatherAlert(
    val id: String,
    /** Evento, ex.: "Tempestade", "Baixa Umidade". */
    val event: String,
    val level: AlertLevel,
    /** Início e fim no horário de Brasília, ex.: "2026-11-24 00:00". */
    val start: String,
    val end: String,
    val risks: List<String>,
    val instructions: List<String>,
) {
    val title: String get() = "Alerta ${level.colorName} para ${event.lowercase()}"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("event", event).put("level", level.name)
        .put("start", start).put("end", end)
        .put("risks", JSONArray(risks)).put("instr", JSONArray(instructions))

    companion object {
        fun fromJson(o: JSONObject) = WeatherAlert(
            id = o.getString("id"),
            event = o.getString("event"),
            level = AlertLevel.valueOf(o.getString("level")),
            start = o.getString("start"),
            end = o.getString("end"),
            risks = o.optJSONArray("risks").strings(),
            instructions = o.optJSONArray("instr").strings(),
        )
    }
}

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
    /** Próximas 24 horas a partir da hora atual. */
    val hourly: List<HourlyForecast> = emptyList(),
    /** Qualidade do ar; null se o serviço não respondeu e não havia dado válido do mesmo local. */
    val airQuality: AirQuality? = null,
    /** Avisos do INMET ativos ou futuros para o local (vazio fora do Brasil ou sem avisos). */
    val alerts: List<WeatherAlert> = emptyList(),
    /** Quando os avisos foram consultados com sucesso pela última vez (0 = nunca). */
    val alertsCheckedAt: Long = 0L,
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
        current.windDirection?.let { c.put("wd", it) }

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
        val h = JSONArray()
        hourly.forEach { hr ->
            val o = JSONObject().put("time", hr.time).put("t", hr.temperature).put("code", hr.weatherCode).put("day", hr.isDay)
            hr.precipitationProbability?.let { o.put("pp", it) }
            hr.windSpeedKmh?.let { o.put("w", it) }
            h.put(o)
        }
        return JSONObject()
            .put("v", 2)
            .put("place", place.toJson())
            .put("current", c)
            .put("daily", d)
            .put("hourly", h)
            .put("tz", timezone)
            .put("off", utcOffsetSeconds)
            .put("fetchedAt", fetchedAt)
            .put("alerts", JSONArray().also { arr -> alerts.forEach { arr.put(it.toJson()) } })
            .put("alertsAt", alertsCheckedAt)
            .also { o -> airQuality?.let { o.put("aq", it.toJson()) } }
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
            val h = o.optJSONArray("hourly") ?: JSONArray()
            val hours = (0 until h.length()).map { i ->
                val hr = h.getJSONObject(i)
                HourlyForecast(
                    time = hr.getString("time"),
                    temperature = hr.getDouble("t"),
                    weatherCode = hr.getInt("code"),
                    isDay = hr.optBoolean("day", true),
                    precipitationProbability = if (hr.has("pp")) hr.getInt("pp") else null,
                    windSpeedKmh = if (hr.has("w")) hr.getDouble("w") else null,
                )
            }
            val al = o.optJSONArray("alerts") ?: JSONArray()
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
                    windDirection = if (c.has("wd")) c.getInt("wd") else null,
                ),
                daily = days,
                timezone = o.optString("tz", "UTC"),
                utcOffsetSeconds = o.optInt("off", 0),
                fetchedAt = o.getLong("fetchedAt"),
                hourly = hours,
                airQuality = o.optJSONObject("aq")?.let { runCatching { AirQuality.fromJson(it) }.getOrNull() },
                alerts = (0 until al.length()).mapNotNull { runCatching { WeatherAlert.fromJson(al.getJSONObject(it)) }.getOrNull() },
                alertsCheckedAt = o.optLong("alertsAt", 0L),
            )
        }
    }
}

internal fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
