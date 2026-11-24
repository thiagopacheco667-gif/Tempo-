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

    suspend fun refresh(): RefreshResult = withContext(Dispatchers.IO) {
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
            val snap = OpenMeteoClient.fetchForecast(place)
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
