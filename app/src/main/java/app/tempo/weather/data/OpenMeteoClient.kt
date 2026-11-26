package app.tempo.weather.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Falha de rede/HTTP (recuperável) ou resposta inválida da API. */
class WeatherApiException(message: String, cause: Throwable? = null) : IOException(message, cause)

data class CityResult(
    val name: String,
    val admin1: String?,
    val country: String?,
    val countryCode: String?,
    val latitude: Double,
    val longitude: Double,
) {
    val region: String
        get() = listOfNotNull(admin1?.takeIf { it.isNotBlank() && it != name }, countryCode).joinToString(", ")

    fun toPlace() = Place(name, region, latitude, longitude)
}

/**
 * Cliente mínimo para a API pública da Open-Meteo (https://open-meteo.com/en/docs).
 * Sem dependências externas: HttpURLConnection + org.json, que já vêm no Android.
 */
object OpenMeteoClient {

    private const val FORECAST = "https://api.open-meteo.com/v1/forecast"
    private const val GEOCODING = "https://geocoding-api.open-meteo.com/v1/search"

    private val CURRENT_VARS = listOf(
        "temperature_2m", "apparent_temperature", "relative_humidity_2m", "weather_code",
        "wind_speed_10m", "wind_direction_10m", "is_day", "precipitation_probability", "uv_index",
    ).joinToString(",")

    private val HOURLY_VARS = listOf(
        "temperature_2m", "weather_code", "is_day", "precipitation_probability", "wind_speed_10m",
    ).joinToString(",")

    private const val AIR = "https://air-quality-api.open-meteo.com/v1/air-quality"

    private val DAILY_VARS = listOf(
        "weather_code", "temperature_2m_max", "temperature_2m_min",
        "precipitation_probability_max", "uv_index_max", "sunrise", "sunset",
    ).joinToString(",")

    /**
     * Modelo principal: ECMWF IFS 0,25° — o modelo global mais preciso, aberto e gratuito.
     * O padrão da Open-Meteo no Brasil (best_match, baseado no GFS) costuma perder chuva fraca:
     * ex.: garoa real às 05:30 aparecia como "nublado". O best_match continua como complemento
     * (índice UV, que o ECMWF não fornece) e como reserva se o ECMWF falhar.
     */
    const val PRIMARY_MODEL = "ecmwf_ifs025"

    fun buildForecastUrl(lat: Double, lon: Double, model: String? = PRIMARY_MODEL): String =
        "$FORECAST?latitude=${fmt(lat)}&longitude=${fmt(lon)}" +
            "&current=$CURRENT_VARS&daily=$DAILY_VARS&hourly=$HOURLY_VARS" +
            "&temperature_unit=celsius&wind_speed_unit=kmh" +
            "&timezone=auto&forecast_days=6&forecast_hours=24" +
            (model?.let { "&models=$it" } ?: "")

    fun fetchForecast(place: Place, now: Long = System.currentTimeMillis()): WeatherSnapshot {
        val primary = runCatching {
            parseForecast(get(buildForecastUrl(place.latitude, place.longitude)), place, now)
        }
        val fallback = runCatching {
            parseForecast(get(buildForecastUrl(place.latitude, place.longitude, model = null)), place, now)
        }
        val p = primary.getOrNull() ?: return fallback.getOrThrow()
        return fallback.getOrNull()?.let { merge(p, it) } ?: p
    }

    /** Mantém tudo do modelo principal e só preenche o que ele não fornece. */
    fun merge(primary: WeatherSnapshot, secondary: WeatherSnapshot): WeatherSnapshot {
        val secDays = secondary.daily.associateBy { it.date }
        val secHours = secondary.hourly.associateBy { it.time }
        val c = primary.current
        val sc = secondary.current
        return primary.copy(
            current = c.copy(
                uvIndex = c.uvIndex ?: sc.uvIndex,
                precipitationProbability = c.precipitationProbability ?: sc.precipitationProbability,
                windDirection = c.windDirection ?: sc.windDirection,
                humidity = if (c.humidity >= 0) c.humidity else sc.humidity,
            ),
            daily = primary.daily.map { d ->
                val s = secDays[d.date] ?: return@map d
                d.copy(
                    uvIndexMax = d.uvIndexMax ?: s.uvIndexMax,
                    precipitationProbability = d.precipitationProbability ?: s.precipitationProbability,
                    sunrise = d.sunrise ?: s.sunrise,
                    sunset = d.sunset ?: s.sunset,
                )
            },
            hourly = primary.hourly.ifEmpty { secondary.hourly }.map { h ->
                val s = secHours[h.time] ?: return@map h
                h.copy(
                    precipitationProbability = h.precipitationProbability ?: s.precipitationProbability,
                    windSpeedKmh = h.windSpeedKmh ?: s.windSpeedKmh,
                )
            },
        )
    }

    /** Qualidade do ar atual (índice US AQI). Opcional: falhas não impedem a previsão. */
    fun fetchAirQuality(place: Place): AirQuality? {
        val url = "$AIR?latitude=${fmt(place.latitude)}&longitude=${fmt(place.longitude)}" +
            "&current=us_aqi,pm2_5,pm10&timezone=auto"
        return parseAirQuality(get(url))
    }

    fun parseAirQuality(body: String): AirQuality? {
        val c = runCatching { JSONObject(body).optJSONObject("current") }.getOrNull() ?: return null
        val aqi = c.intOrNull("us_aqi") ?: return null
        return AirQuality(aqi, c.doubleOrNull("pm2_5"), c.doubleOrNull("pm10"), c.optString("time"))
    }

    fun searchCity(query: String, language: String = Locale.getDefault().language): List<CityResult> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val url = "$GEOCODING?name=${URLEncoder.encode(q, "UTF-8")}&count=8&language=$language&format=json"
        return parseGeocoding(get(url))
    }

    // --- Parsing (público para testes) -----------------------------------------------------

    fun parseForecast(body: String, place: Place, fetchedAt: Long): WeatherSnapshot {
        val root = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw WeatherApiException("Resposta inválida da Open-Meteo", e)
        }
        if (root.optBoolean("error", false)) {
            throw WeatherApiException(root.optString("reason", "Erro da Open-Meteo"))
        }
        val c = root.optJSONObject("current") ?: throw WeatherApiException("Resposta sem dados atuais")
        val temperature = c.doubleOrNull("temperature_2m")
            ?: throw WeatherApiException("Temperatura atual ausente")
        val code = c.intOrNull("weather_code") ?: throw WeatherApiException("Condição atual ausente")

        val current = CurrentWeather(
            temperature = temperature,
            apparentTemperature = c.doubleOrNull("apparent_temperature") ?: temperature,
            humidity = c.intOrNull("relative_humidity_2m") ?: -1,
            windSpeedKmh = c.doubleOrNull("wind_speed_10m") ?: -1.0,
            weatherCode = code,
            isDay = (c.intOrNull("is_day") ?: 1) == 1,
            precipitationProbability = c.intOrNull("precipitation_probability"),
            uvIndex = c.doubleOrNull("uv_index"),
            time = c.optString("time"),
            windDirection = c.intOrNull("wind_direction_10m"),
        )

        val hourly = root.optJSONObject("hourly")?.let { h ->
            val times = h.optJSONArray("time") ?: JSONArray()
            val temps = h.optJSONArray("temperature_2m")
            val hcodes = h.optJSONArray("weather_code")
            val days = h.optJSONArray("is_day")
            val hpp = h.optJSONArray("precipitation_probability")
            val wind = h.optJSONArray("wind_speed_10m")
            (0 until times.length()).mapNotNull { i ->
                HourlyForecast(
                    time = times.getString(i),
                    temperature = temps.doubleAt(i) ?: return@mapNotNull null,
                    weatherCode = hcodes.intAt(i) ?: return@mapNotNull null,
                    isDay = (days.intAt(i) ?: 1) == 1,
                    precipitationProbability = hpp.intAt(i),
                    windSpeedKmh = wind.doubleAt(i),
                )
            }
        }.orEmpty()

        val d = root.optJSONObject("daily") ?: throw WeatherApiException("Resposta sem previsão diária")
        val dates = d.optJSONArray("time") ?: JSONArray()
        val codes = d.optJSONArray("weather_code")
        val maxs = d.optJSONArray("temperature_2m_max")
        val mins = d.optJSONArray("temperature_2m_min")
        val pps = d.optJSONArray("precipitation_probability_max")
        val uvs = d.optJSONArray("uv_index_max")
        val srs = d.optJSONArray("sunrise")
        val sss = d.optJSONArray("sunset")

        val days = (0 until dates.length()).mapNotNull { i ->
            val dCode = codes.intAt(i) ?: return@mapNotNull null
            val max = maxs.doubleAt(i) ?: return@mapNotNull null
            val min = mins.doubleAt(i) ?: return@mapNotNull null
            DailyForecast(
                date = dates.getString(i),
                weatherCode = dCode,
                max = max,
                min = min,
                precipitationProbability = pps.intAt(i),
                uvIndexMax = uvs.doubleAt(i),
                sunrise = srs.stringAt(i),
                sunset = sss.stringAt(i),
            )
        }
        if (days.isEmpty()) throw WeatherApiException("Previsão diária vazia")

        return WeatherSnapshot(
            place = place,
            current = current,
            daily = days,
            timezone = root.optString("timezone", "UTC"),
            utcOffsetSeconds = root.optInt("utc_offset_seconds", 0),
            fetchedAt = fetchedAt,
            hourly = hourly,
        )
    }

    fun parseGeocoding(body: String): List<CityResult> {
        val root = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw WeatherApiException("Resposta inválida da busca de cidades", e)
        }
        val arr = root.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CityResult(
                name = o.getString("name"),
                admin1 = o.optString("admin1").ifEmpty { null },
                country = o.optString("country").ifEmpty { null },
                countryCode = o.optString("country_code").ifEmpty { null },
                latitude = o.getDouble("latitude"),
                longitude = o.getDouble("longitude"),
            )
        }
    }

    // --- HTTP ------------------------------------------------------------------------------

    internal fun get(url: String, userAgent: String = "TempoWidget/1.0 (Android)"): String {
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw WeatherApiException("URL inválida", e)
        }
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", userAgent)
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val reason = runCatching { JSONObject(text).optString("reason") }.getOrNull()
                throw WeatherApiException("HTTP $status${reason?.let { ": $it" } ?: ""}")
            }
            return text
        } catch (e: WeatherApiException) {
            throw e
        } catch (e: IOException) {
            throw WeatherApiException("Sem conexão com o serviço meteorológico", e)
        } finally {
            conn.disconnect()
        }
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.4f", v)

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }

    private fun JSONObject.intOrNull(key: String): Int? = doubleOrNull(key)?.let { Math.round(it).toInt() }

    private fun JSONArray?.doubleAt(i: Int): Double? =
        if (this == null || i >= length() || isNull(i)) null else optDouble(i).takeUnless { it.isNaN() }

    private fun JSONArray?.intAt(i: Int): Int? = doubleAt(i)?.let { Math.round(it).toInt() }

    private fun JSONArray?.stringAt(i: Int): String? =
        if (this == null || i >= length() || isNull(i)) null else optString(i).ifEmpty { null }
}
