package app.tempo.weather.widget

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.DrawableRes
import app.tempo.weather.data.WeatherSnapshot
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Períodos do dia, sempre no fuso horário da localização meteorológica selecionada. */
enum class DayPeriod(val label: String, val startHour: Int) {
    MADRUGADA("Madrugada", 0),
    MANHA("Manhã", 6),
    TARDE("Tarde", 12),
    ENTARDECER("Entardecer", 17),
    NOITE("Noite", 19);

    companion object {
        fun ofHour(hour: Int): DayPeriod = entries.last { hour >= it.startHour }
    }
}

/** Condições com cenário próprio, derivadas estritamente do código WMO devolvido pela API. */
enum class SceneCondition {
    CLEAR, PARTLY, CLOUDY, FOG, RAIN_LIGHT, RAIN_MODERATE, RAIN_HEAVY, STORM, HAIL, SNOW;

    companion object {
        fun of(code: Int): SceneCondition = when (code) {
            0, 1 -> CLEAR
            2 -> PARTLY
            3 -> CLOUDY
            45, 48 -> FOG
            51, 53, 55, 56, 57, 61, 80 -> RAIN_LIGHT
            63, 66, 81 -> RAIN_MODERATE
            65, 67, 82 -> RAIN_HEAVY
            95 -> STORM
            96, 99 -> HAIL
            71, 73, 75, 77, 85, 86 -> SNOW
            else -> CLOUDY
        }
    }
}

/**
 * Cenário fotográfico = período do dia × condição. Várias combinações compartilham a mesma
 * fotografia quando a diferença não seria perceptível (ex.: neve de madrugada e de noite).
 */
data class Scene(val condition: SceneCondition, val period: DayPeriod, val photo: String) {
    /** Chave estável usada para decidir se o fundo mudou (sem troca desnecessária). */
    val key: String get() = photo
}

object Scenery {

    private val NIGHTLIKE = setOf(DayPeriod.MADRUGADA, DayPeriod.NOITE)

    /** Nome do recurso (drawable `scene_*`) para cada combinação. */
    fun photoName(c: SceneCondition, p: DayPeriod): String {
        val night = p in NIGHTLIKE
        return "scene_" + when (c) {
            SceneCondition.CLEAR -> when (p) {
                DayPeriod.MADRUGADA -> "clear_madrugada"
                DayPeriod.MANHA -> "clear_manha"
                DayPeriod.TARDE -> "clear_tarde"
                DayPeriod.ENTARDECER -> "clear_entardecer"
                DayPeriod.NOITE -> "clear_noite"
            }
            SceneCondition.PARTLY -> when {
                night -> "partly_noite"
                p == DayPeriod.MANHA -> "partly_manha"
                p == DayPeriod.TARDE -> "partly_tarde"
                else -> "partly_entardecer"
            }
            SceneCondition.CLOUDY -> when {
                night -> "cloudy_noite"
                p == DayPeriod.ENTARDECER -> "cloudy_entardecer"
                else -> "cloudy_dia"
            }
            SceneCondition.FOG -> when {
                night -> "fog_noite"
                p == DayPeriod.MANHA -> "fog_manha"
                else -> "fog_dia"
            }
            SceneCondition.RAIN_LIGHT, SceneCondition.RAIN_MODERATE, SceneCondition.RAIN_HEAVY -> {
                val intensity = when (c) {
                    SceneCondition.RAIN_LIGHT -> "light"
                    SceneCondition.RAIN_MODERATE -> "moderate"
                    else -> "heavy"
                }
                when {
                    p == DayPeriod.ENTARDECER -> "rain_entardecer"
                    night -> "rain_${intensity}_noite"
                    else -> "rain_${intensity}_dia"
                }
            }
            SceneCondition.STORM -> when {
                night -> "storm_noite"
                p == DayPeriod.ENTARDECER -> "storm_entardecer"
                else -> "storm_dia"
            }
            SceneCondition.HAIL -> if (night) "hail_noite" else "hail_dia"
            SceneCondition.SNOW -> if (night) "snow_noite" else "snow_dia"
        }
    }

    /** Todas as fotografias distintas do sistema (para conferência e testes). */
    val ALL_PHOTOS: Set<String> by lazy {
        SceneCondition.entries.flatMap { c -> DayPeriod.entries.map { p -> photoName(c, p) } }.toSet()
    }

    fun localTime(snapshot: WeatherSnapshot, now: Instant): LocalDateTime =
        LocalDateTime.ofInstant(now, ZoneOffset.ofTotalSeconds(snapshot.utcOffsetSeconds))

    fun sceneFor(snapshot: WeatherSnapshot, now: Instant = Instant.now()): Scene {
        val c = SceneCondition.of(snapshot.current.weatherCode)
        val p = DayPeriod.ofHour(localTime(snapshot, now).hour)
        return Scene(c, p, photoName(c, p))
    }

    /**
     * Recurso da fotografia, ou 0 se ela não estiver no app (fallback: degradê da atmosfera).
     * Os recursos são mantidos pelo R8 via res/raw/keep_scenes.xml.
     */
    @SuppressLint("DiscouragedApi")
    @DrawableRes
    fun photoRes(context: Context, name: String): Int =
        context.resources.getIdentifier(name, "drawable", context.packageName)

    /** Próximo instante (UTC) em que o período do dia muda no fuso da localização. */
    fun nextBoundary(snapshot: WeatherSnapshot, now: Instant = Instant.now()): Instant {
        val local = localTime(snapshot, now)
        val next = DayPeriod.entries.map { it.startHour }.firstOrNull { it > local.hour }
        val boundary = if (next != null) {
            local.toLocalDate().atTime(next, 0)
        } else {
            local.toLocalDate().plusDays(1).atStartOfDay()
        }
        return boundary.toInstant(ZoneOffset.ofTotalSeconds(snapshot.utcOffsetSeconds)).plusSeconds(5)
    }
}
