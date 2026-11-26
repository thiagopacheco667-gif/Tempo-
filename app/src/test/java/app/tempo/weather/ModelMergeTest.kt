package app.tempo.weather

import app.tempo.weather.data.OpenMeteoClient
import app.tempo.weather.data.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModelMergeTest {
    private val place = Place("Santo Antônio da Platina", "PR, BR", -23.29, -50.08)

    @Test
    fun ecmwfConditionWinsAndUvIsFilledFromDefaultModel() {
        val f = { n: String -> javaClass.classLoader!!.getResource("fixtures/$n")!!.readText() }
        val ecmwf = OpenMeteoClient.parseForecast(f("forecast_platina_ecmwf.json"), place, 0L)
        val best = OpenMeteoClient.parseForecast(f("forecast_platina_best.json"), place, 0L)
        // Momento real gravado: garoa observada; ECMWF = garoa, padrão = nublado.
        assertEquals(53, ecmwf.current.weatherCode)
        assertEquals(3, best.current.weatherCode)
        val m = OpenMeteoClient.merge(ecmwf, best)
        assertEquals(53, m.current.weatherCode)
        assertEquals(ecmwf.current.temperature, m.current.temperature, 0.0)
        assertTrue(ecmwf.daily.all { it.uvIndexMax == null })
        assertTrue(m.daily.all { it.uvIndexMax != null })
    }

    @Test
    fun urlSelectsModel() {
        assertTrue(OpenMeteoClient.buildForecastUrl(1.0, 2.0).contains("&models=ecmwf_ifs025"))
        assertTrue(!OpenMeteoClient.buildForecastUrl(1.0, 2.0, null).contains("models="))
    }
}
