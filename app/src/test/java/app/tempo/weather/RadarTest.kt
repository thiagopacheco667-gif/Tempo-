package app.tempo.weather

import android.graphics.BitmapFactory
import app.tempo.weather.data.CurrentWeather
import app.tempo.weather.data.RadarClient
import app.tempo.weather.data.RadarRain
import app.tempo.weather.data.mergeRadar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RadarTest {

    @Test
    fun classifiesPalette() {
        assertEquals(RadarRain.LIGHT, RadarClient.classify(0xFF1BAEE2.toInt()))     // (27,174,226)
        assertEquals(RadarRain.LIGHT, RadarClient.classify(0xFF6CD1EB.toInt()))     // (108,209,235)
        assertEquals(RadarRain.MODERATE, RadarClient.classify(0xFF0088BF.toInt()))  // (0,136,191)
        assertEquals(RadarRain.MODERATE, RadarClient.classify(0xFF006295.toInt()))  // (0,98,149)
        assertEquals(RadarRain.HEAVY, RadarClient.classify(0xFFFFD000.toInt()))
        assertNull(RadarClient.classify(0x00000000))
        assertNull(RadarClient.classify(0xB4DACC93.toInt()))                        // eco translúcido
    }

    @Test
    fun realTileShowsRainOverTheCity() {
        // Mosaico real gravado em 26/11 05:40, quando havia chuva leve e os modelos diziam nublado.
        val bytes = javaClass.classLoader!!.getResource("fixtures/radar_platina_z7.png")!!.readBytes()
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val n = 128.0
        val xt = (-50.0794 + 180) / 360 * n
        val yt = (1 - kotlin.math.asinh(kotlin.math.tan(Math.toRadians(-23.2928))) / Math.PI) / 2 * n
        val px = ((xt - xt.toInt()) * 256).toInt(); val py = ((yt - yt.toInt()) * 256).toInt()
        assertEquals(RadarRain.LIGHT, RadarClient.classify(bmp.getPixel(px, py)))
    }

    private fun cur(code: Int) = CurrentWeather(21.0, 23.0, 92, 13.0, code, true, 59, 0.0, "t")

    @Test
    fun radarOverridesOnlyWhenModelMissesRain() {
        assertEquals(61, mergeRadar(cur(3), RadarRain.LIGHT).weatherCode)   // nublado -> chuva fraca
        assertEquals(65, mergeRadar(cur(61), RadarRain.HEAVY).weatherCode)  // fraca -> forte
        assertEquals(63, mergeRadar(cur(63), RadarRain.LIGHT).weatherCode)  // mantém a maior
        assertEquals(95, mergeRadar(cur(95), RadarRain.HEAVY).weatherCode)  // tempestade mantida
        assertEquals(73, mergeRadar(cur(73), RadarRain.LIGHT).weatherCode)  // neve mantida
    }
}
