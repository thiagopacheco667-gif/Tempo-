package app.tempo.weather

import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import app.tempo.weather.data.OpenMeteoClient
import app.tempo.weather.data.Place
import app.tempo.weather.widget.RenderModel
import app.tempo.weather.widget.WidgetRenderer
import app.tempo.weather.widget.WidgetSize
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Android 10: sem RemoteViews responsivos nem setViewLayoutWidth; tudo precisa inflar e medir. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class LegacyRenderTest {
    @Test
    fun inflatesEverySizeOnAndroid10() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val body = javaClass.classLoader!!.getResource("fixtures/forecast_sao_paulo.json")!!.readText()
        val snap = OpenMeteoClient.parseForecast(body, Place("São Paulo", "SP, BR", 0.0, 0.0), 0L)
        val model = RenderModel(snap, false, null, 0, true, 60, null, Instant.parse("2026-11-24T04:50:00Z"))
        WidgetSize.entries.forEach { size ->
            val v = WidgetRenderer.buildSingle(ctx, size, model).apply(ctx, FrameLayout(ctx))
            v.measure(View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            assertTrue(v.measuredWidth > 0)
        }
        // Caminho completo do provider (RemoteViews único no pré-Android 12)
        val rv = WidgetRenderer.buildResponsive(ctx, 7, model, WidgetSize.EXPANDED, commit = true)
        assertTrue(rv.layoutId != 0)
    }
}
