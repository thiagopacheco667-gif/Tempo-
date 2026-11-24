package app.tempo.weather

import app.tempo.weather.data.AlertLevel
import app.tempo.weather.data.InmetClient
import app.tempo.weather.data.OpenMeteoClient
import app.tempo.weather.data.Place
import app.tempo.weather.data.WeatherSnapshot
import app.tempo.weather.widget.WidgetRenderer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlertsAndAirTest {

    private fun fixture(name: String) = javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()
    private val platina = Place("Santo Antônio da Platina", "PR, BR", -23.2928, -50.0794)
    private val now = LocalDateTime.parse("2026-11-24T03:00")

    @Test
    fun inmetAlertsMatchByOfficialPolygon() {
        // Mesmo resultado do app do sistema na gravação: alerta laranja hoje e amarelo amanhã.
        val alerts = InmetClient.parse(fixture("inmet_avisos.json"), platina.latitude, platina.longitude, now)
        assertEquals(listOf("Tempestade", "Tempestade", "Tempestade"), alerts.map { it.event })
        assertEquals(AlertLevel.DANGER, alerts.first().level)
        assertTrue(alerts.any { it.level == AlertLevel.POTENTIAL && it.start.startsWith("2026-11-25") })
        assertEquals("Alerta laranja para tempestade", alerts.first().title)
        assertTrue(alerts.first().risks.isNotEmpty())
    }

    @Test
    fun noAlertsOutsideAffectedAreas() {
        val curitibaCoast = InmetClient.parse(fixture("inmet_avisos.json"), -25.52, -48.51, now)
        val lisbon = Place("Lisboa", "PT", 38.72, -9.14)
        assertTrue(curitibaCoast.none { it.event == "Baixa Umidade" })
        assertEquals(false, InmetClient.covers(lisbon))
    }

    @Test
    fun expiredAlertsAreDropped() {
        val later = LocalDateTime.parse("2026-11-27T12:00")
        assertTrue(InmetClient.parse(fixture("inmet_avisos.json"), platina.latitude, platina.longitude, later).isEmpty())
    }

    @Test
    fun pyListParsing() {
        assertEquals(listOf("a b", "c"), InmetClient.pyList("['a b', 'c']"))
    }

    @Test
    fun parsesAirQualityAndHourly() {
        val aq = OpenMeteoClient.parseAirQuality(fixture("air_platina.json"))!!
        assertEquals(76, aq.usAqi)
        assertEquals("Moderada", aq.category)
        val snap = OpenMeteoClient.parseForecast(fixture("forecast_platina.json"), platina, 1L)
        assertEquals(24, snap.hourly.size)
        assertEquals(106, snap.current.windDirection)
        assertEquals("L", WidgetRenderer.compass(106))
        assertEquals("N", WidgetRenderer.compass(350))
        assertEquals("SO", WidgetRenderer.compass(225))
    }

    @Test
    fun extendedSnapshotRoundTrip() {
        val alerts = InmetClient.parse(fixture("inmet_avisos.json"), platina.latitude, platina.longitude, now)
        val snap = OpenMeteoClient.parseForecast(fixture("forecast_platina.json"), platina, 5L)
            .copy(airQuality = OpenMeteoClient.parseAirQuality(fixture("air_platina.json")), alerts = alerts, alertsCheckedAt = 9L)
        assertEquals(snap, WeatherSnapshot.fromJson(JSONObject(snap.toJson().toString())))
    }

    @Test
    fun readsVersion1Cache() {
        // Cache gravado pela versão anterior (sem 24 h, IQA nem alertas) continua válido.
        val v1 = OpenMeteoClient.parseForecast(fixture("forecast_sao_paulo.json"), platina, 1L).toJson()
        v1.remove("hourly"); v1.remove("alerts"); v1.remove("alertsAt"); v1.put("v", 1)
        val back = WeatherSnapshot.fromJson(v1)
        assertTrue(back.hourly.isEmpty() && back.alerts.isEmpty() && back.airQuality == null)
    }
}
