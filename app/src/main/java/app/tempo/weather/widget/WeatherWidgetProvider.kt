package app.tempo.weather.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import app.tempo.weather.data.WeatherStore
import app.tempo.weather.work.UpdateScheduler

class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        UpdateScheduler.schedulePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        UpdateScheduler.cancelAll(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
        UpdateScheduler.schedulePeriodic(context)
        // Se os dados guardados estiverem velhos (ex.: após reiniciar), busca já.
        val store = WeatherStore(context)
        val snap = store.snapshot
        val age = snap?.let { System.currentTimeMillis() - it.fetchedAt } ?: Long.MAX_VALUE
        if (age > store.updateIntervalMinutes * 60_000L && !store.isRefreshing()) {
            UpdateScheduler.refreshNow(context)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        render(context, manager, intArrayOf(id))
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val store = WeatherStore(context)
        ids.forEach(store::forgetWidget)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val store = WeatherStore(context)
            if (!store.isRefreshing()) {
                store.refreshingSince = System.currentTimeMillis()
                renderAll(context)
                UpdateScheduler.refreshNow(context)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "app.tempo.weather.action.REFRESH"

        fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, WeatherWidgetProvider::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun renderAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val model = RenderModel.from(context)
            ids.forEach { id ->
                val opts = manager.getAppWidgetOptions(id)
                // Retrato: largura mínima e altura máxima, como recomenda a documentação.
                val w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).toFloat()
                val h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).toFloat()
                val fallback = if (w > 0 && h > 0) WidgetSize.forSize(w, h) else WidgetSize.EXPANDED
                manager.updateAppWidget(id, WidgetRenderer.buildResponsive(context, id, model, fallback, commit = true))
            }
        }
    }
}
