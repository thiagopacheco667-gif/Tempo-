package app.tempo.weather.data

import android.graphics.BitmapFactory
import org.json.JSONObject
import kotlin.math.asinh
import kotlin.math.tan

/** Intensidade de precipitação observada pelo radar no local. */
enum class RadarRain(val weatherCode: Int) {
    LIGHT(61), MODERATE(63), HEAVY(65);
}

/**
 * Observação de radar meteorológico em tempo real (mosaico gratuito do RainViewer, atualizado a
 * cada ~10 min). Os modelos numéricos são previsões e frequentemente perdem pancadas e garoas
 * locais; o radar mostra a chuva que está caindo agora.
 */
object RadarClient {

    private const val MAPS = "https://api.rainviewer.com/public/weather-maps.json"
    private const val ZOOM = 7 // ~1,1 km por pixel no Brasil; zoom máximo do plano gratuito
    private const val MAX_AGE_S = 30 * 60

    /** null = sem eco de chuva (ou radar indisponível/antigo). */
    fun rainAt(lat: Double, lon: Double, nowEpochS: Long = System.currentTimeMillis() / 1000): RadarRain? {
        val maps = JSONObject(OpenMeteoClient.get(MAPS))
        val host = maps.getString("host")
        val past = maps.getJSONObject("radar").getJSONArray("past")
        if (past.length() == 0) return null
        val frame = past.getJSONObject(past.length() - 1)
        if (nowEpochS - frame.getLong("time") > MAX_AGE_S) return null

        val n = 1 shl ZOOM
        val xt = (lon + 180.0) / 360.0 * n
        val yt = (1.0 - asinh(tan(Math.toRadians(lat))) / Math.PI) / 2.0 * n
        val tx = xt.toInt()
        val ty = yt.toInt()
        val px = ((xt - tx) * 256).toInt().coerceIn(0, 255)
        val py = ((yt - ty) * 256).toInt().coerceIn(0, 255)
        val url = "$host${frame.getString("path")}/256/$ZOOM/$tx/$ty/0/0_0.png"
        val bytes = OpenMeteoClient.getBytes(url)
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        // Vizinhança de 3x3 px (~3 km): a chuva mais forte em volta do ponto.
        var best: RadarRain? = null
        for (dx in -1..1) for (dy in -1..1) {
            val c = bmp.getPixel((px + dx).coerceIn(0, 255), (py + dy).coerceIn(0, 255))
            val r = classify(c)
            if (r != null && (best == null || r.ordinal > best.ordinal)) best = r
        }
        bmp.recycle()
        return best
    }

    /**
     * Classifica a cor do mosaico (paleta padrão do RainViewer): azuis claros = chuva fraca,
     * azuis escuros = moderada, amarelo/laranja/vermelho = forte. Ecos translúcidos (garoa
     * muito fraca ou ruído de solo) são ignorados para evitar falsos positivos.
     */
    fun classify(argb: Int): RadarRain? {
        val a = argb ushr 24 and 0xFF
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        if (a < 250) return null
        return when {
            r > 180 && b < 120 -> RadarRain.HEAVY          // amarelo, laranja, vermelho
            r > 150 && b > 150 && g < 120 -> RadarRain.HEAVY // magenta (muito forte)
            b > r && g >= 150 -> RadarRain.LIGHT           // azul claro / ciano
            b > r -> RadarRain.MODERATE                    // azul médio / escuro
            else -> null
        }
    }
}
