package app.tempo.weather.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.tempo.weather.data.RefreshResult
import app.tempo.weather.data.WeatherRepository
import app.tempo.weather.data.WeatherStore
import app.tempo.weather.widget.WeatherWidgetProvider
import java.util.concurrent.TimeUnit

class WeatherUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = WeatherStore(applicationContext)
        store.refreshingSince = System.currentTimeMillis()
        WeatherWidgetProvider.renderAll(applicationContext)
        val result = try {
            WeatherRepository(applicationContext).refresh()
        } finally {
            store.refreshingSince = 0L
        }
        WeatherWidgetProvider.renderAll(applicationContext)
        return when (result) {
            is RefreshResult.Success -> Result.success()
            is RefreshResult.Failure ->
                if (result.recoverable && runAttemptCount < 2) Result.retry() else Result.success()
        }
    }
}

object UpdateScheduler {
    private const val PERIODIC = "tempo-periodic"
    private const val NOW = "tempo-now"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Atualização automática. Intervalo mínimo do sistema é 15 min; usamos 30 min ou mais. */
    fun schedulePeriodic(context: Context, replace: Boolean = false) {
        val minutes = WeatherStore(context).updateIntervalMinutes.coerceAtLeast(30).toLong()
        val req = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(minutes, TimeUnit.MINUTES, 10, TimeUnit.MINUTES)
            .setConstraints(network)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            req,
        )
    }

    /** Atualização manual imediata. Sem restrição de rede para que a falha seja exibida na hora. */
    fun refreshNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<WeatherUpdateWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }
}
