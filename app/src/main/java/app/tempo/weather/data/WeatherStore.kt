package app.tempo.weather.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONObject

enum class LocationMode { AUTO, MANUAL }

/**
 * Persistência leve (SharedPreferences) das preferências e do último conjunto de dados válido.
 * Leituras são síncronas e baratas, o que é importante porque o widget renderiza a partir daqui.
 */
class WeatherStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("tempo_weather", Context.MODE_PRIVATE)

    // --- Preferências -----------------------------------------------------------------------

    var locationMode: LocationMode
        get() = runCatching { LocationMode.valueOf(prefs.getString(K_MODE, null)!!) }
            .getOrDefault(LocationMode.AUTO)
        set(value) = prefs.edit { putString(K_MODE, value.name) }

    var manualPlace: Place?
        get() = readPlace(K_MANUAL_PLACE)
        set(value) = writePlace(K_MANUAL_PLACE, value)

    /** Última posição obtida pelo GPS/rede; usada quando o sistema não entrega localização em segundo plano. */
    var lastAutoPlace: Place?
        get() = readPlace(K_AUTO_PLACE)
        set(value) = writePlace(K_AUTO_PLACE, value)

    var updateIntervalMinutes: Int
        get() = prefs.getInt(K_INTERVAL, 60)
        set(value) = prefs.edit { putInt(K_INTERVAL, value) }

    var animationsEnabled: Boolean
        get() = prefs.getBoolean(K_ANIM, true)
        set(value) = prefs.edit { putBoolean(K_ANIM, value) }

    // --- Dados ------------------------------------------------------------------------------

    /** Último conjunto de dados VÁLIDO vindo da API. Nunca é sobrescrito por uma falha. */
    var snapshot: WeatherSnapshot?
        get() = prefs.getString(K_SNAPSHOT, null)?.let { raw ->
            runCatching { WeatherSnapshot.fromJson(JSONObject(raw)) }.getOrNull()
        }
        set(value) = prefs.edit {
            if (value == null) remove(K_SNAPSHOT) else putString(K_SNAPSHOT, value.toJson().toString())
        }

    /** Mensagem da última falha de atualização; null quando a última tentativa deu certo. */
    var lastError: String?
        get() = prefs.getString(K_ERROR, null)
        set(value) = prefs.edit { if (value == null) remove(K_ERROR) else putString(K_ERROR, value) }

    var lastAttemptAt: Long
        get() = prefs.getLong(K_ATTEMPT, 0L)
        set(value) = prefs.edit { putLong(K_ATTEMPT, value) }

    /** Marca de "atualizando" com carimbo de tempo, para que um processo morto não deixe o spinner preso. */
    var refreshingSince: Long
        get() = prefs.getLong(K_REFRESHING, 0L)
        set(value) = prefs.edit { putLong(K_REFRESHING, value) }

    fun isRefreshing(now: Long = System.currentTimeMillis()): Boolean {
        val since = refreshingSince
        return since > 0 && now - since < REFRESH_TIMEOUT_MS
    }

    // --- Estado por widget (transições suaves) ---------------------------------------------

    fun widgetSlot(widgetId: Int, key: String): Int = prefs.getInt("w${widgetId}_${key}_slot", 0)
    fun widgetValue(widgetId: Int, key: String): String? = prefs.getString("w${widgetId}_${key}_val", null)
    fun saveWidgetValue(widgetId: Int, key: String, slot: Int, value: String) = prefs.edit {
        putInt("w${widgetId}_${key}_slot", slot)
        putString("w${widgetId}_${key}_val", value)
    }

    fun forgetWidget(widgetId: Int) {
        val prefix = "w${widgetId}_"
        val keys = prefs.all.keys.filter { it.startsWith(prefix) }
        prefs.edit { keys.forEach { remove(it) } }
    }

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(l)

    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(l)

    private fun readPlace(key: String): Place? = prefs.getString(key, null)?.let { raw ->
        runCatching { Place.fromJson(JSONObject(raw)) }.getOrNull()
    }

    private fun writePlace(key: String, place: Place?) = prefs.edit {
        if (place == null) remove(key) else putString(key, place.toJson().toString())
    }

    companion object {
        private const val K_MODE = "location_mode"
        private const val K_MANUAL_PLACE = "manual_place"
        private const val K_AUTO_PLACE = "auto_place"
        private const val K_INTERVAL = "interval_min"
        private const val K_ANIM = "animations"
        private const val K_SNAPSHOT = "snapshot"
        private const val K_ERROR = "last_error"
        private const val K_ATTEMPT = "last_attempt"
        private const val K_REFRESHING = "refreshing_since"
        private const val REFRESH_TIMEOUT_MS = 90_000L
    }
}
