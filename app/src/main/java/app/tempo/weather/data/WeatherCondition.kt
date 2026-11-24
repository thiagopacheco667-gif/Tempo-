package app.tempo.weather.data

import androidx.annotation.DrawableRes
import app.tempo.weather.R

/** Agrupamento visual dos códigos WMO usados pela Open-Meteo. */
enum class Condition { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

object WeatherCodes {

    fun condition(code: Int): Condition = when (code) {
        0, 1 -> Condition.CLEAR
        2 -> Condition.PARTLY_CLOUDY
        3 -> Condition.CLOUDY
        45, 48 -> Condition.FOG
        51, 53, 55, 56, 57 -> Condition.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> Condition.RAIN
        71, 73, 75, 77, 85, 86 -> Condition.SNOW
        95, 96, 99 -> Condition.STORM
        else -> Condition.CLOUDY
    }

    /** Descrição em português do Brasil, fiel ao código WMO. */
    fun label(code: Int, isDay: Boolean = true): String = when (code) {
        0 -> if (isDay) "Ensolarado" else "Céu limpo"
        1 -> "Poucas nuvens"
        2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45 -> "Nevoeiro"
        48 -> "Nevoeiro com geada"
        51 -> "Garoa fraca"
        53 -> "Garoa"
        55 -> "Garoa intensa"
        56, 57 -> "Garoa congelante"
        61 -> "Chuva fraca"
        63 -> "Chuva"
        65 -> "Chuva forte"
        66, 67 -> "Chuva congelante"
        71 -> "Neve fraca"
        73 -> "Neve"
        75 -> "Neve intensa"
        77 -> "Grãos de neve"
        80 -> "Pancadas de chuva"
        81 -> "Pancadas moderadas"
        82 -> "Pancadas fortes"
        85, 86 -> "Pancadas de neve"
        95 -> "Trovoadas"
        96, 99 -> "Trovoadas com granizo"
        else -> "Condição indisponível"
    }

    @DrawableRes
    fun icon(code: Int, isDay: Boolean): Int = when (condition(code)) {
        Condition.CLEAR -> if (isDay) R.drawable.wx_clear_day else R.drawable.wx_clear_night
        Condition.PARTLY_CLOUDY -> if (isDay) R.drawable.wx_partly_day else R.drawable.wx_partly_night
        Condition.CLOUDY -> R.drawable.wx_cloudy
        Condition.FOG -> R.drawable.wx_fog
        Condition.DRIZZLE -> R.drawable.wx_drizzle
        Condition.RAIN -> R.drawable.wx_rain
        Condition.SNOW -> R.drawable.wx_snow
        Condition.STORM -> R.drawable.wx_storm
    }

    fun uvLabel(uv: Double): String = when {
        uv < 3 -> "Baixo"
        uv < 6 -> "Moderado"
        uv < 8 -> "Alto"
        uv < 11 -> "Muito alto"
        else -> "Extremo"
    }
}
