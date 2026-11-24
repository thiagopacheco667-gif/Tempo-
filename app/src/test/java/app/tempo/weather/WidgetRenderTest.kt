package app.tempo.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import app.tempo.weather.data.InmetClient
import app.tempo.weather.data.OpenMeteoClient
import app.tempo.weather.data.Place
import app.tempo.weather.data.WeatherSnapshot
import app.tempo.weather.widget.Atmosphere
import app.tempo.weather.widget.RenderModel
import app.tempo.weather.widget.WidgetRenderer
import app.tempo.weather.widget.WidgetSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

/**
 * Renderiza os layouts REAIS do widget (RemoteViews -> View) em vários tamanhos e atmosferas e
 * grava PNGs em app/build/widget-renders para inspeção visual. Também verifica que nada estoura
 * os limites do widget (texto cortado/sobreposto).
 *
 * Observação: as variações de atmosfera alteram o código WMO de uma resposta real gravada apenas
 * para exercitar os temas visuais — isso existe somente no teste, nunca no app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class WidgetRenderTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val out = File("build/widget-renders").apply { mkdirs() }

    private val base: WeatherSnapshot by lazy {
        val body = javaClass.classLoader!!.getResource("fixtures/forecast_sao_paulo.json")!!.readText()
        OpenMeteoClient.parseForecast(body, Place("São Paulo", "SP, BR", -23.5475, -46.6361),
            Instant.parse("2026-11-24T04:46:00Z").toEpochMilli())
    }

    private fun variant(code: Int, localTime: String, isDay: Boolean, temp: Double? = null): Pair<WeatherSnapshot, Instant> {
        val now = Instant.parse("2026-11-24T${localTime}:00-03:00".let { java.time.OffsetDateTime.parse(it).toInstant().toString() })
        val snap = base.copy(
            current = base.current.copy(weatherCode = code, isDay = isDay, temperature = temp ?: base.current.temperature),
            fetchedAt = now.minusSeconds(600).toEpochMilli(),
        )
        return snap to now
    }

    private val platina: WeatherSnapshot by lazy {
        val place = Place("Santo Antônio da Platina", "PR, BR", -23.2928, -50.0794)
        val f = { n: String -> javaClass.classLoader!!.getResource("fixtures/$n")!!.readText() }
        OpenMeteoClient.parseForecast(f("forecast_platina.json"), place, Instant.parse("2026-11-24T06:00:00Z").toEpochMilli()).copy(
            airQuality = OpenMeteoClient.parseAirQuality(f("air_platina.json")),
            alerts = InmetClient.parse(f("inmet_avisos.json"), place.latitude, place.longitude, java.time.LocalDateTime.parse("2026-11-24T03:00")),
        )
    }

    private val scenarios: List<Triple<String, WeatherSnapshot, Instant>> by lazy {
        listOf(
            Triple("real_platina_alert", platina, Instant.parse("2026-11-24T06:05:00Z")),
            Triple("real_night_cloudy", base, Instant.parse("2026-11-24T04:50:00Z")),
            variant(0, "12:30", true, 29.4).let { Triple("clear_day", it.first, it.second) },
            variant(1, "18:05", true, 26.2).let { Triple("sunset", it.first, it.second) },
            variant(3, "11:00", true, 22.0).let { Triple("cloudy", it.first, it.second) },
            variant(63, "15:00", true, 19.0).let { Triple("rain", it.first, it.second) },
            variant(95, "21:00", false, 21.0).let { Triple("storm", it.first, it.second) },
            variant(0, "23:30", false, -2.6).let { Triple("clear_night_negative", it.first, it.second) },
        )
    }

    private val sizes = listOf(
        "2x2" to (150 to 150),
        "2x3" to (150 to 250),
        "4x1" to (320 to 110),
        "3x2" to (250 to 170),
        "4x2" to (320 to 170),
        "5x2" to (400 to 175),
        "4x3" to (320 to 265),
        "5x3" to (400 to 285),
        "4x4" to (330 to 380),
        "4x3tall" to (330 to 300),
        "3x3" to (250 to 250),
    )

    private fun model(snap: WeatherSnapshot?, now: Instant, refreshing: Boolean = false, error: String? = null, attempt: Long = 0) =
        RenderModel(snap, refreshing, error, attempt, intervalMinutes = 60, pendingPlaceName = null, now = now)

    private fun render(name: String, w: Int, h: Int, model: RenderModel): View {
        val size = WidgetSize.forSize(w.toFloat(), h.toFloat())
        val rv = WidgetRenderer.buildSingle(ctx, size, model)
        val host = FrameLayout(ctx)
        val view = rv.apply(ctx, host)
        val d = ctx.resources.displayMetrics.density
        val wPx = (w * d).toInt()
        val hPx = (h * d).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hPx, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, wPx, hPx)

        val pad = (16 * d).toInt()
        val bmp = Bitmap.createBitmap(wPx + pad * 2, hPx + pad * 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        // "papel de parede" neutro para avaliar contraste e bordas
        c.drawPaint(Paint().apply {
            shader = LinearGradient(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat(), Color.rgb(54, 64, 84), Color.rgb(24, 28, 38), Shader.TileMode.CLAMP)
        })
        c.translate(pad.toFloat(), pad.toFloat())
        view.draw(c)
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return view
    }

    /** Todo TextView visível precisa caber inteiro dentro do widget. */
    private fun assertInside(root: View, label: String) {
        val rw = root.width
        val rh = root.height
        fun walk(v: View) {
            if (v.visibility != View.VISIBLE) return
            if (v is android.widget.TextView && v.text.isNotEmpty() && v.width > 0) {
                val loc = IntArray(2)
                var x = 0; var y = 0; var p: View? = v
                while (p != null && p !== root) { x += p.left; y += p.top; p = p.parent as? View }
                assertTrue("$label: '${v.text}' fora dos limites ($x,$y ${v.width}x${v.height} em ${rw}x$rh)",
                    x >= 0 && y >= 0 && x + v.width <= rw + 1 && y + v.height <= rh + 1)
                assertTrue("$label: '${v.text}' com altura insuficiente", v.height >= v.lineHeight * 0.8 || v.maxLines == 0)
                // Também não pode ser cortado pelo contêiner pai (clipChildren).
                val parent = v.parent as? View
                if (parent != null && parent !== root) {
                    assertTrue("$label: '${v.text}' cortado pelo contêiner (${v.bottom} > ${parent.height})",
                        v.bottom <= parent.height - parent.paddingBottom + 2 && v.right <= parent.width + 2)
                }
            }
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
    }

    @Test
    fun rendersAllSizesAndAtmospheres() {
        for ((sName, snap, now) in scenarios) {
            for ((label, wh) in sizes) {
                val v = render("${sName}_$label", wh.first, wh.second, model(snap, now))
                assertInside(v, "${sName}_$label")
            }
        }
    }

    @Test
    fun rendersStates() {
        val now = Instant.parse("2026-11-24T04:50:00Z")
        for ((label, wh) in listOf("2x2" to (150 to 150), "4x2" to (320 to 170), "4x3" to (320 to 265))) {
            render("state_empty_$label", wh.first, wh.second, model(null, now))
            render("state_loading_$label", wh.first, wh.second, model(null, now, refreshing = true))
            render("state_refreshing_$label", wh.first, wh.second, model(base, now, refreshing = true))
            render("state_offline_$label", wh.first, wh.second,
                model(base, now.plusSeconds(5 * 3600), error = "Sem conexão", attempt = now.plusSeconds(5 * 3600).toEpochMilli()))
        }
    }

    @Test
    fun atmosphereSelection() {
        val byName = scenarios.associate { it.first to Atmosphere.resolve(it.second, it.third) }
        assertEquals(Atmosphere.NIGHT, byName["real_night_cloudy"])
        assertEquals(Atmosphere.CLEAR_DAY, byName["clear_day"])
        assertEquals(Atmosphere.SUNSET, byName["sunset"])
        assertEquals(Atmosphere.CLOUDY, byName["cloudy"])
        assertEquals(Atmosphere.RAIN, byName["rain"])
        assertEquals(Atmosphere.STORM, byName["storm"])
    }
}
