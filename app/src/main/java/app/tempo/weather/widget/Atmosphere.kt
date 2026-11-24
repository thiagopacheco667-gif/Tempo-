package app.tempo.weather.widget

import androidx.annotation.DrawableRes
import app.tempo.weather.R
import app.tempo.weather.data.Condition
import app.tempo.weather.data.WeatherCodes
import app.tempo.weather.data.WeatherSnapshot
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Cada atmosfera mantém o DNA azul-marinho da identidade e muda apenas luz, temperatura de cor
 * e a camada estática de detalhe (estrelas, chuva, névoa, luz).
 */
enum class Atmosphere(
    @DrawableRes val background: Int,
    /** Camada estática de detalhe (sem animação contínua). */
    @DrawableRes val deco: Int,
    /** Cor do símbolo de grau, um pequeno acento de luz que acompanha o clima. */
    val accent: Int,
    /** Gradiente da tela detalhada (topo → base), coerente com o widget. */
    val screen: IntArray,
) {
    CLEAR_DAY(R.drawable.bg_clear_day, R.drawable.deco_sun, 0xFFFFD08A.toInt(), intArrayOf(0xFF12306E.toInt(), 0xFF0B2154.toInt(), 0xFF07132F.toInt())),
    CLOUDY(R.drawable.bg_cloudy, R.drawable.deco_mist, 0xFFC9D6EE.toInt(), intArrayOf(0xFF2E3E5C.toInt(), 0xFF1F2C47.toInt(), 0xFF121B2F.toInt())),
    RAIN(R.drawable.bg_rain, R.drawable.deco_rain, 0xFF9CCBFF.toInt(), intArrayOf(0xFF11284D.toInt(), 0xFF0A1A37.toInt(), 0xFF050E21.toInt())),
    STORM(R.drawable.bg_storm, R.drawable.deco_storm, 0xFFB8AEFF.toInt(), intArrayOf(0xFF161C38.toInt(), 0xFF0B0F23.toInt(), 0xFF04060E.toInt())),
    NIGHT(R.drawable.bg_night, R.drawable.deco_stars, 0xFFA9C2FF.toInt(), intArrayOf(0xFF0B1433.toInt(), 0xFF060C21.toInt(), 0xFF02040D.toInt())),
    SUNSET(R.drawable.bg_sunset, R.drawable.deco_sun, 0xFFFFB07A.toInt(), intArrayOf(0xFF142A60.toInt(), 0xFF2B2C66.toInt(), 0xFF4E3159.toInt()));

    companion object {

        fun resolve(snapshot: WeatherSnapshot, now: Instant = Instant.now()): Atmosphere {
            val condition = WeatherCodes.condition(snapshot.current.weatherCode)
            val sky = SkyClock.of(snapshot, now)
            return when (condition) {
                Condition.STORM -> STORM
                Condition.RAIN, Condition.DRIZZLE -> RAIN
                else -> when {
                    sky.golden && condition in setOf(Condition.CLEAR, Condition.PARTLY_CLOUDY) -> SUNSET
                    !sky.isDay -> NIGHT
                    condition == Condition.CLEAR || condition == Condition.PARTLY_CLOUDY -> CLEAR_DAY
                    else -> CLOUDY
                }
            }
        }
    }
}

/** Dia/noite e "hora dourada" calculados no fuso do local consultado. */
data class SkyClock(val isDay: Boolean, val golden: Boolean) {
    companion object {
        fun of(snapshot: WeatherSnapshot, now: Instant): SkyClock {
            val local = LocalDateTime.ofInstant(now, ZoneOffset.ofTotalSeconds(snapshot.utcOffsetSeconds))
            val today = snapshot.daily.firstOrNull { it.date == local.toLocalDate().toString() }
            val sunrise = today?.sunrise?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            val sunset = today?.sunset?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            if (sunrise == null || sunset == null) return SkyClock(snapshot.current.isDay, false)
            val isDay = local.isAfter(sunrise) && local.isBefore(sunset)
            val toSunset = ChronoUnit.MINUTES.between(local, sunset)
            val fromSunrise = ChronoUnit.MINUTES.between(sunrise, local)
            val golden = toSunset in -25..60 || fromSunrise in -20..45
            return SkyClock(isDay, golden)
        }
    }
}
