package app.tempo.weather.widget

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
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
 * e o elemento ambiente animado.
 */
enum class Atmosphere(
    @DrawableRes val background: Int,
    @IdRes val ambient: Int?,
    @IdRes val glow: Int?,
    /** Cor do símbolo de grau, um pequeno acento de luz que acompanha o clima. */
    val accent: Int,
) {
    CLEAR_DAY(R.drawable.bg_clear_day, null, R.id.amb_glow_sun, 0xFFFFD08A.toInt()),
    CLOUDY(R.drawable.bg_cloudy, R.id.amb_mist, null, 0xFFC9D6EE.toInt()),
    RAIN(R.drawable.bg_rain, R.id.amb_rain, null, 0xFF9CCBFF.toInt()),
    STORM(R.drawable.bg_storm, R.id.amb_storm, null, 0xFFB8AEFF.toInt()),
    NIGHT(R.drawable.bg_night, R.id.amb_stars, R.id.amb_glow_moon, 0xFFA9C2FF.toInt()),
    SUNSET(R.drawable.bg_sunset, null, R.id.amb_glow_sunset, 0xFFFFB07A.toInt());

    companion object {
        val AMBIENT_IDS = intArrayOf(R.id.amb_rain, R.id.amb_storm, R.id.amb_stars, R.id.amb_mist)
        val GLOW_IDS = intArrayOf(R.id.amb_glow_sun, R.id.amb_glow_sunset, R.id.amb_glow_moon)

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
