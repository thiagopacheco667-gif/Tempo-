package app.tempo.weather.data

import android.content.Context
import app.tempo.weather.location.DeviceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface RefreshResult {
    data class Success(val snapshot: WeatherSnapshot) : RefreshResult
    /** Falhou, mas o último dado válido (se houver) continua disponível para uso offline. */
    data class Failure(val message: String, val recoverable: Boolean) : RefreshResult
}

private const val ALERTS_INTERVAL_MS = 2 * 60 * 60_000L

/** Descarta avisos já encerrados (o horário do INMET é o de Brasília). */
fun WeatherAlert.isActiveOrUpcoming(now: java.time.LocalDateTime = java.time.LocalDateTime.now(java.time.ZoneId.of("America/Sao_Paulo"))): Boolean =
    runCatching { !java.time.LocalDateTime.parse(end, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")).isBefore(now) }
        .getOrDefault(true)

/** Aviso em vigor agora (já começou e ainda não terminou). */
fun WeatherAlert.isActiveNow(now: java.time.LocalDateTime = java.time.LocalDateTime.now(java.time.ZoneId.of("America/Sao_Paulo"))): Boolean =
    runCatching {
        val f = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        !java.time.LocalDateTime.parse(start, f).isAfter(now) && !java.time.LocalDateTime.parse(end, f).isBefore(now)
    }.getOrDefault(false)

/**
 * Se o radar observa chuva agora e o modelo não indica precipitação, a condição atual passa a
 * refletir a observação (chuva fraca/moderada/forte). Tempestade, neve e granizo do modelo são
 * mantidos. Sem eco no radar, prevalece o modelo (o radar tem lacunas de cobertura).
 */
fun applyRadar(snap: WeatherSnapshot, place: Place): WeatherSnapshot {
    val rain = runCatching { RadarClient.rainAt(place.latitude, place.longitude) }.getOrNull() ?: return snap
    return snap.copy(current = mergeRadar(snap.current, rain))
}

fun mergeRadar(current: CurrentWeather, rain: RadarRain): CurrentWeather {
    val c = WeatherCodes.condition(current.weatherCode)
    val keep = c == Condition.STORM || c == Condition.SNOW
    if (keep) return current
    val modelIntensity = when (current.weatherCode) {
        65, 67, 82 -> 2; 63, 66, 81 -> 1; 61, 80, 51, 53, 55, 56, 57 -> 0; else -> -1
    }
    // Usa a intensidade maior entre radar e modelo.
    return if (rain.ordinal > modelIntensity) current.copy(weatherCode = rain.weatherCode) else current
}

class WeatherRepository(private val context: Context) {

    private val store = WeatherStore(context)
    private val locator = DeviceLocator(context)

    /** Resolve o local a ser consultado de acordo com a preferência do usuário. */
    suspend fun resolvePlace(): Place? = when (store.locationMode) {
        LocationMode.MANUAL -> store.manualPlace
        LocationMode.AUTO -> {
            val loc = locator.locate()
            if (loc != null) {
                val previous = store.lastAutoPlace
                val place = locator.describe(loc, previous)
                store.lastAutoPlace = place
                place
            } else {
                // Sem posição nova (ex.: sem permissão em segundo plano): usa a última conhecida.
                store.lastAutoPlace
            }
        }
    }

    /**
     * Previsão é obrigatória; qualidade do ar e avisos do INMET são complementares: se falharem,
     * reaproveitam o último valor válido do mesmo local (nunca de outro lugar).
     * [manual] força a consulta de avisos mesmo dentro do intervalo de economia.
     */
    suspend fun refresh(manual: Boolean = false): RefreshResult = withContext(Dispatchers.IO) {
        store.lastAttemptAt = System.currentTimeMillis()
        val place = try {
            resolvePlace()
        } catch (e: Exception) {
            null
        }
        if (place == null) {
            val msg = when {
                store.locationMode == LocationMode.MANUAL -> "Escolha uma cidade"
                !locator.hasPermission() -> "Permita a localização ou escolha uma cidade"
                !locator.isLocationEnabled() -> "Ative a localização do aparelho ou escolha uma cidade"
                else -> "Não foi possível obter sua posição. Escolha uma cidade"
            }
            store.lastError = msg
            return@withContext RefreshResult.Failure(msg, recoverable = false)
        }
        try {
            val previous = store.snapshot?.takeIf { it.place.sameSpot(place) }
            val base = applyRadar(OpenMeteoClient.fetchForecast(place), place)
            val air = runCatching { OpenMeteoClient.fetchAirQuality(place) }.getOrNull() ?: previous?.airQuality

            // O feed nacional do INMET é grande (~400 KB): consulta no máximo a cada 2 h,
            // salvo atualização manual ou troca de local.
            val alertsDue = manual || previous == null ||
                base.fetchedAt - previous.alertsCheckedAt > ALERTS_INTERVAL_MS
            val (alerts, alertsAt) = if (alertsDue && InmetClient.covers(place)) {
                runCatching { InmetClient.fetchFor(place) }.fold(
                    onSuccess = { it to base.fetchedAt },
                    onFailure = { (previous?.alerts.orEmpty()) to (previous?.alertsCheckedAt ?: 0L) },
                )
            } else if (!InmetClient.covers(place)) {
                emptyList<WeatherAlert>() to base.fetchedAt
            } else {
                previous!!.alerts to previous.alertsCheckedAt
            }
            val snap = base.copy(airQuality = air, alerts = alerts.filter { it.isActiveOrUpcoming() }, alertsCheckedAt = alertsAt)
            store.snapshot = snap
            store.lastError = null
            RefreshResult.Success(snap)
        } catch (e: WeatherApiException) {
            val msg = if (e.cause is java.io.IOException) "Sem conexão" else (e.message ?: "Falha ao atualizar")
            store.lastError = msg
            RefreshResult.Failure(msg, recoverable = true)
        } catch (e: Exception) {
            store.lastError = "Falha ao atualizar"
            RefreshResult.Failure("Falha ao atualizar", recoverable = true)
        }
    }
}
