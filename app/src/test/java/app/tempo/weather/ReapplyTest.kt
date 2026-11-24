package app.tempo.weather

import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import app.tempo.weather.data.InmetClient
import app.tempo.weather.data.OpenMeteoClient
import app.tempo.weather.data.Place
import app.tempo.weather.widget.RenderModel
import app.tempo.weather.widget.WidgetRenderer
import app.tempo.weather.widget.WidgetSize
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDateTime

/**
 * O launcher reaproveita a árvore de views e aplica só as ações novas (reapply). Cada estado
 * precisa redefinir tudo o que outro estado possa ter escondido.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReapplyTest {

    @Test
    fun emptyThenDataRestoresEveryElement() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val f = { n: String -> javaClass.classLoader!!.getResource("fixtures/$n")!!.readText() }
        val place = Place("Santo Antônio da Platina", "PR, BR", -23.2928, -50.0794)
        val now = Instant.parse("2026-11-24T07:10:00Z")
        val snap = OpenMeteoClient.parseForecast(f("forecast_platina.json"), place, now.toEpochMilli()).copy(
            alerts = InmetClient.parse(f("inmet_avisos.json"), place.latitude, place.longitude, LocalDateTime.parse("2026-11-24T03:00")),
        )
        val empty = RenderModel(null, false, "Sem conexão", 0, 60, null, now)
        val full = RenderModel(snap, false, null, 0, 60, null, now)

        for (size in WidgetSize.entries) {
            val view = WidgetRenderer.buildSingle(ctx, size, empty).apply(ctx, FrameLayout(ctx))
            WidgetRenderer.buildSingle(ctx, size, full).reapply(ctx, view)
            val fresh = WidgetRenderer.buildSingle(ctx, size, full).apply(ctx, FrameLayout(ctx))
            // Depois do reapply, a visibilidade de cada view deve ser igual à de uma inflação nova.
            fun walk(a: View, b: View) {
                if (a.id != View.NO_ID) assertEquals("$size: view ${ctx.resources.getResourceEntryName(a.id)}", b.visibility, a.visibility)
                if (a is android.view.ViewGroup && b is android.view.ViewGroup) {
                    for (i in 0 until minOf(a.childCount, b.childCount)) walk(a.getChildAt(i), b.getChildAt(i))
                }
            }
            walk(view, fresh)
        }
    }
}
