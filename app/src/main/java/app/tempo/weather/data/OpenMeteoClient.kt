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
        "wind_speed_10m", "is_day", "precipitation_probability", "uv_index",
    ).joinToString(",")

    private val DAILY_VARS = listOf(
        "weather_code", "temperature_2m_max", "temperature_2m_min",
        "precipitation_probability_max", "uv_index_max", "sunrise", "sunset",
    ).joinToString(",")

    fun buildForecastUrl(lat: Double, lon: Double): String =
        "$FORECAST?latitude=${fmt(lat)}&longitude=${fmt(lon)}" +
            "&current=$CURRENT_VARS&daily=$DAILY_VARS" +
            "&temperature_unit=celsius&wind_speed_unit=kmh" +
            "&timezone=auto&forecast_days=6"

    fun fetchForecast(place: Place, now: Long = System.currentTimeMillis()): WeatherSnapshot {
        val body = get(buildForecastUrl(place.latitude, place.longitude))
        return parseForecast(body, place, now)
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
        )

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

    private fun get(url: String): String {
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw WeatherApiException("URL inválida", e)
        }
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "TempoWidget/1.0 (Android)")
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
