package app.tempo.weather

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ApplicationProvider
import app.tempo.weather.data.InmetClient
import app.tempo.weather.data.OpenMeteoClient
import app.tempo.weather.data.Place
import app.tempo.weather.data.WeatherStore
import app.tempo.weather.ui.DetailActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime

/** Abre a previsão detalhada com dados reais gravados e salva uma captura de tela longa. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class DetailScreenTest {

    @Test
    fun rendersDetailScreenWithRealData() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val f = { n: String -> javaClass.classLoader!!.getResource("fixtures/$n")!!.readText() }
        val place = Place("Santo Antônio da Platina", "PR, BR", -23.2928, -50.0794)
        val snap = OpenMeteoClient.parseForecast(f("forecast_platina.json"), place, System.currentTimeMillis()).copy(
            airQuality = OpenMeteoClient.parseAirQuality(f("air_platina.json")),
            // Avisos válidos "agora" para o teste: reinterpreta a fixture com o relógio da gravação.
            alerts = InmetClient.parse(f("inmet_avisos.json"), place.latitude, place.longitude, LocalDateTime.parse("2026-11-24T03:00"))
                .map { it.copy(end = "2099-01-01 00:00") },
        )
        WeatherStore(ctx).snapshot = snap

        val controller = Robolectric.buildActivity(DetailActivity::class.java).setup()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val root: View = controller.get().window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File("build/widget-renders").mkdirs()
        File("build/widget-renders/detail_screen.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(root.width > 0 && root.height > 0)
    }
}
