package app.tempo.weather

import app.tempo.weather.data.OpenMeteoClient
import app.tempo.weather.data.Place
import app.tempo.weather.data.WeatherApiException
import app.tempo.weather.data.WeatherCodes
import app.tempo.weather.data.WeatherSnapshot
import app.tempo.weather.widget.WidgetRenderer
import app.tempo.weather.widget.WidgetSize
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OpenMeteoParsingTest {

    private fun fixture(name: String) =
        javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()

    private val place = Place("São Paulo", "SP, BR", -23.5475, -46.6361)

    @Test
    fun parsesRealForecastResponse() {
        val snap = OpenMeteoClient.parseForecast(fixture("forecast_sao_paulo.json"), place, 1L)
        assertEquals(20.6, snap.current.temperature, 0.001)
        assertEquals(23.5, snap.current.apparentTemperature, 0.001)
        assertEquals(97, snap.current.humidity)
        assertEquals(3, snap.current.weatherCode)
        assertEquals(false, snap.current.isDay)
        assertEquals(22, snap.current.precipitationProbability)
        assertEquals(6, snap.daily.size)
        assertEquals(5, snap.nextDays.size)
        assertEquals("2026-11-25", snap.nextDays.first().date)
        assertEquals(-10800, snap.utcOffsetSeconds)
    }

    @Test
    fun snapshotSurvivesPersistenceRoundTrip() {
        val snap = OpenMeteoClient.parseForecast(fixture("forecast_sao_paulo.json"), place, 42L)
        val back = WeatherSnapshot.fromJson(JSONObject(snap.toJson().toString()))
        assertEquals(snap, back)
    }

    @Test
    fun nullValuesAreSkippedNotInvented() {
        val body = """
            {"utc_offset_seconds":0,"timezone":"UTC",
             "current":{"time":"2026-01-01T12:00","temperature_2m":10.0,"weather_code":2,"relative_humidity_2m":null,"is_day":1},
             "daily":{"time":["2026-01-01","2026-01-02"],"weather_code":[2,null],"temperature_2m_max":[12,13],"temperature_2m_min":[5,6]}}
        """.trimIndent()
        val snap = OpenMeteoClient.parseForecast(body, place, 0L)
        assertEquals(-1, snap.current.humidity)
        assertNull(snap.current.uvIndex)
        assertEquals(1, snap.daily.size)
    }

    @Test(expected = WeatherApiException::class)
    fun apiErrorIsReported() {
        OpenMeteoClient.parseForecast("""{"error":true,"reason":"Latitude must be in range"}""", place, 0L)
    }

    @Test
    fun parsesGeocoding() {
        val r = OpenMeteoClient.parseGeocoding(fixture("geocoding_recife.json"))
        assertTrue(r.isNotEmpty())
        assertEquals("Recife", r.first().name)
        assertEquals("BR", r.first().countryCode)
    }

    @Test
    fun formatting() {
        assertEquals("0°", WidgetRenderer.deg(-0.4))
        assertEquals("−3°", WidgetRenderer.deg(-2.6))
        assertEquals("21°", WidgetRenderer.deg(20.6))
        assertEquals("Qua", WidgetRenderer.dayName("2026-11-25"))
        assertEquals("Trovoadas com granizo", WeatherCodes.label(96))
    }

    @Test
    fun responsiveBuckets() {
        assertEquals(WidgetSize.COMPACT, WidgetSize.forSize(150f, 150f))
        assertEquals(WidgetSize.COMPACT, WidgetSize.forSize(150f, 400f))
        assertEquals(WidgetSize.MEDIUM_SHORT, WidgetSize.forSize(320f, 120f))
        assertEquals(WidgetSize.MEDIUM, WidgetSize.forSize(320f, 180f))
        assertEquals(WidgetSize.MEDIUM_NARROW, WidgetSize.forSize(250f, 180f))
        assertEquals(WidgetSize.EXPANDED_NARROW, WidgetSize.forSize(250f, 250f))
        assertEquals(WidgetSize.EXPANDED, WidgetSize.forSize(320f, 270f))
        assertEquals(WidgetSize.LARGE, WidgetSize.forSize(360f, 400f))
    }
}
