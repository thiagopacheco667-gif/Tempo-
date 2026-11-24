package app.tempo.weather

import androidx.test.core.app.ApplicationProvider
import app.tempo.weather.data.OpenMeteoClient
import app.tempo.weather.data.Place
import app.tempo.weather.widget.Atmosphere
import app.tempo.weather.widget.DayPeriod
import app.tempo.weather.widget.SceneCondition
import app.tempo.weather.widget.Scenery
import app.tempo.weather.widget.WidgetRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SceneryTest {

    private val snap by lazy {
        val body = javaClass.classLoader!!.getResource("fixtures/forecast_platina.json")!!.readText()
        OpenMeteoClient.parseForecast(body, Place("Santo Antônio da Platina", "PR, BR", -23.29, -50.08), 0L)
    }

    /** Horário local (UTC−3) → instante. */
    private fun at(hhmm: String) = Instant.parse("2026-11-24T${hhmm}:00-03:00".let { java.time.OffsetDateTime.parse(it).toInstant().toString() })

    @Test
    fun periodsFollowTheSpecifiedHours() {
        assertEquals(DayPeriod.MADRUGADA, DayPeriod.ofHour(0))
        assertEquals(DayPeriod.MADRUGADA, DayPeriod.ofHour(5))
        assertEquals(DayPeriod.MANHA, DayPeriod.ofHour(6))
        assertEquals(DayPeriod.MANHA, DayPeriod.ofHour(11))
        assertEquals(DayPeriod.TARDE, DayPeriod.ofHour(12))
        assertEquals(DayPeriod.TARDE, DayPeriod.ofHour(16))
        assertEquals(DayPeriod.ENTARDECER, DayPeriod.ofHour(17))
        assertEquals(DayPeriod.ENTARDECER, DayPeriod.ofHour(18))
        assertEquals(DayPeriod.NOITE, DayPeriod.ofHour(19))
        assertEquals(DayPeriod.NOITE, DayPeriod.ofHour(23))
    }

    @Test
    fun conditionsComeOnlyFromWmoCodes() {
        assertEquals(SceneCondition.CLEAR, SceneCondition.of(0))
        assertEquals(SceneCondition.PARTLY, SceneCondition.of(2))
        assertEquals(SceneCondition.CLOUDY, SceneCondition.of(3))
        assertEquals(SceneCondition.FOG, SceneCondition.of(45))
        assertEquals(SceneCondition.RAIN_LIGHT, SceneCondition.of(61))
        assertEquals(SceneCondition.RAIN_MODERATE, SceneCondition.of(63))
        assertEquals(SceneCondition.RAIN_HEAVY, SceneCondition.of(82))
        assertEquals(SceneCondition.STORM, SceneCondition.of(95))
        assertEquals(SceneCondition.HAIL, SceneCondition.of(99))
        assertEquals(SceneCondition.SNOW, SceneCondition.of(73))
    }

    @Test
    fun usesLocationTimezoneAndCombinesBothVariables() {
        val fog = snap.copy(current = snap.current.copy(weatherCode = 45))
        assertEquals("scene_fog_manha", Scenery.sceneFor(fog, at("07:30")).photo)
        assertEquals("scene_fog_noite", Scenery.sceneFor(fog, at("21:00")).photo)
        val rain = snap.copy(current = snap.current.copy(weatherCode = 65))
        assertEquals("scene_rain_entardecer", Scenery.sceneFor(rain, at("17:40")).photo)
        assertEquals("scene_rain_heavy_dia", Scenery.sceneFor(rain, at("13:00")).photo)
        val storm = snap.copy(current = snap.current.copy(weatherCode = 95))
        assertEquals("scene_storm_noite", Scenery.sceneFor(storm, at("22:00")).photo)
        assertEquals(29, Scenery.ALL_PHOTOS.size)
    }

    @Test
    fun nextBoundaryIsTheNextPeriodStart() {
        assertEquals(at("06:00").plusSeconds(5), Scenery.nextBoundary(snap, at("03:20")))
        assertEquals(at("19:00").plusSeconds(5), Scenery.nextBoundary(snap, at("17:01")))
        assertEquals(Instant.parse("2026-11-25T03:00:05Z"), Scenery.nextBoundary(snap, at("23:10")))
    }

    @Test
    fun photoWhenBundledGradientOtherwiseAndNoChurn() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fog = snap.copy(current = snap.current.copy(weatherCode = 45))
        val k1 = WidgetRenderer.backgroundKey(ctx, fog, at("07:00"), Atmosphere.CLOUDY)
        val k2 = WidgetRenderer.backgroundKey(ctx, fog, at("11:50"), Atmosphere.CLOUDY)
        assertEquals("photo:scene_fog_manha", k1)
        assertEquals("mesmo cenário = mesma chave (sem troca desnecessária)", k1, k2)
        assertNotEquals(k1, WidgetRenderer.backgroundKey(ctx, fog, at("12:10"), Atmosphere.CLOUDY))
        // Todos os 29 cenários têm fotografia no app.
        val missing = Scenery.ALL_PHOTOS.filter { Scenery.photoRes(ctx, it) == 0 }
        assertTrue("sem foto: $missing", missing.isEmpty())
        val hail = snap.copy(current = snap.current.copy(weatherCode = 99))
        assertEquals("photo:scene_hail_dia", WidgetRenderer.backgroundKey(ctx, hail, at("14:00"), Atmosphere.STORM))
    }
}
