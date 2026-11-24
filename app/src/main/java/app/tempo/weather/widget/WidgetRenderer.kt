package app.tempo.weather.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.text.SpannableString
import android.text.Spanned
import android.text.format.DateFormat
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import app.tempo.weather.R
import app.tempo.weather.data.WeatherCodes
import app.tempo.weather.data.WeatherSnapshot
import app.tempo.weather.data.WeatherStore
import app.tempo.weather.ui.MainActivity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Variações responsivas. Várias compartilham o mesmo XML e mudam apenas escala e densidade de
 * informação. Os valores padrão do XML são os mais compactos (seguros também abaixo do Android 12).
 */
enum class WidgetSize(
    @LayoutRes val layout: Int,
    val minWidthDp: Float,
    val minHeightDp: Float,
    val tempSp: Float,
    /** Tamanho do ícone e da área de brilho (aplicado no Android 12+). null = valor do XML. */
    val iconDp: Float? = null,
    val showFeels: Boolean = true,
    val longHiLo: Boolean = false,
    val showMetrics: Boolean = true,
    val metricLabels: Boolean = false,
    val forecast: Boolean = false,
) {
    COMPACT(R.layout.widget_compact, 0f, 0f, 44f, showFeels = false, showMetrics = false),
    MEDIUM_SHORT(R.layout.widget_medium, 220f, 0f, 44f, iconDp = 50f, showFeels = false, showMetrics = false),
    MEDIUM_NARROW(R.layout.widget_medium, 220f, 150f, 50f, iconDp = 54f, showFeels = false),
    MEDIUM(R.layout.widget_medium, 280f, 150f, 54f, iconDp = 62f),
    EXPANDED_NARROW(R.layout.widget_expanded, 250f, 250f, 56f, iconDp = 62f, showFeels = false, forecast = true),
    EXPANDED(R.layout.widget_expanded, 300f, 250f, 62f, iconDp = 72f, forecast = true),
    LARGE(R.layout.widget_expanded, 300f, 330f, 74f, iconDp = 84f, metricLabels = true, forecast = true);

    companion object {
        /** Mesma ideia da escolha do sistema no Android 12+: o layout mais próximo que cabe. */
        fun forSize(widthDp: Float, heightDp: Float): WidgetSize =
            entries.last { widthDp >= it.minWidthDp && heightDp >= it.minHeightDp }
    }
}

/** Tudo o que é necessário para desenhar o widget, separado do Android para facilitar testes. */
data class RenderModel(
    val snapshot: WeatherSnapshot?,
    val refreshing: Boolean,
    val lastError: String?,
    val lastAttemptAt: Long,
    val animations: Boolean,
    val intervalMinutes: Int,
    val pendingPlaceName: String?,
    val now: Instant = Instant.now(),
) {
    companion object {
        fun from(context: Context, store: WeatherStore = WeatherStore(context)): RenderModel {
            val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return RenderModel(
                snapshot = store.snapshot,
                refreshing = store.isRefreshing(),
                lastError = store.lastError,
                lastAttemptAt = store.lastAttemptAt,
                // Economia de bateria: animações ambientes desligam junto com o modo de economia.
                animations = store.animationsEnabled && power?.isPowerSaveMode != true,
                intervalMinutes = store.updateIntervalMinutes,
                pendingPlaceName = store.manualPlace?.name,
            )
        }
    }
}

object WidgetRenderer {

    private val DAY_NAMES = arrayOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
    private val METRIC_VALUE_IDS = intArrayOf(R.id.m0_value, R.id.m1_value, R.id.m2_value, R.id.m3_value)
    private val METRIC_LABEL_IDS = intArrayOf(R.id.m0_label, R.id.m1_label, R.id.m2_label, R.id.m3_label)
    private val DAY_IDS = arrayOf(
        intArrayOf(R.id.d0_name, R.id.d0_icon, R.id.d0_max, R.id.d0_min),
        intArrayOf(R.id.d1_name, R.id.d1_icon, R.id.d1_max, R.id.d1_min),
        intArrayOf(R.id.d2_name, R.id.d2_icon, R.id.d2_max, R.id.d2_min),
        intArrayOf(R.id.d3_name, R.id.d3_icon, R.id.d3_max, R.id.d3_min),
        intArrayOf(R.id.d4_name, R.id.d4_icon, R.id.d4_max, R.id.d4_min),
    )

    /**
     * Constrói o RemoteViews responsivo de um widget.
     * [commit] = true persiste o estado das transições (use false para pré-visualizações).
     */
    fun buildResponsive(
        context: Context,
        widgetId: Int,
        model: RenderModel,
        fallbackSize: WidgetSize,
        commit: Boolean,
    ): RemoteViews {
        val state = CrossfadeState(context, widgetId, commit)
        state.prepare(model)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RemoteViews(WidgetSize.entries.associate { size ->
                SizeF(size.minWidthDp.coerceAtLeast(110f), size.minHeightDp.coerceAtLeast(100f)) to
                    build(context, size, model, state)
            })
        } else {
            build(context, fallbackSize, model, state)
        }.also { state.commit() }
    }

    /** Constrói um único tamanho (pré-visualização no app e testes). */
    fun buildSingle(context: Context, size: WidgetSize, model: RenderModel): RemoteViews {
        val state = CrossfadeState(context, -1, commit = false)
        state.prepare(model)
        return build(context, size, model, state)
    }

    private fun build(context: Context, size: WidgetSize, model: RenderModel, state: CrossfadeState): RemoteViews {
        val rv = RemoteViews(context.packageName, size.layout)
        val snap = model.snapshot
        val atmosphere = snap?.let { Atmosphere.resolve(it, model.now) } ?: Atmosphere.NIGHT
        val compact = size == WidgetSize.COMPACT

        // --- Atmosfera (crossfade entre duas camadas de fundo) ---
        state.background.apply(rv, R.id.bg_a, R.id.bg_b) { id, value ->
            val atm = runCatching { Atmosphere.valueOf(value) }.getOrDefault(atmosphere)
            rv.setImageViewResource(id, atm.background)
        }
        rv.setInt(android.R.id.background, "setBackgroundResource", atmosphere.background)
        Atmosphere.AMBIENT_IDS.forEach { id ->
            rv.setViewVisibility(id, if (model.animations && atmosphere.ambient == id) View.VISIBLE else View.GONE)
        }
        Atmosphere.GLOW_IDS.forEach { id ->
            rv.setViewVisibility(id, if (model.animations && snap != null && atmosphere.glow == id) View.VISIBLE else View.GONE)
        }

        // --- Interações ---
        rv.setOnClickPendingIntent(android.R.id.background, openAppIntent(context))
        if (!compact) rv.setOnClickPendingIntent(R.id.refresh, WeatherWidgetProvider.refreshIntent(context))
        rv.setViewVisibility(R.id.loading, if (model.refreshing && !compact) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.refresh, if (model.refreshing) View.INVISIBLE else View.VISIBLE)

        // --- Status de atualização ---
        val (statusText, fresh) = statusLine(context, model, short = compact || size.minWidthDp < 280f)
        rv.setTextViewText(R.id.updated, statusText)
        rv.setInt(R.id.status_dot, "setColorFilter", context.getColor(if (fresh) R.color.status_ok else R.color.status_stale))
        rv.setViewVisibility(R.id.status_dot, if (snap != null && !model.refreshing) View.VISIBLE else View.GONE)

        if (snap == null) {
            renderEmpty(context, rv, size, model, state)
            return rv
        }

        val sky = SkyClock.of(snap, model.now)
        val cur = snap.current
        val today = snap.today

        rv.setTextViewText(R.id.city, snap.place.name)
        rv.setTextViewText(R.id.region, snap.place.region.takeIf { it.isNotBlank() }?.let { "· $it" } ?: "")

        state.temp.apply(rv, R.id.temp_a, R.id.temp_b) { id, value ->
            rv.setTextViewText(id, tempSpan(value, atmosphere.accent))
        }
        state.icon.apply(rv, R.id.icon_a, R.id.icon_b) { id, value ->
            rv.setImageViewResource(id, iconFromKey(value))
            rv.setInt(id, "setImageAlpha", 255)
        }

        val conditionLabel = WeatherCodes.label(cur.weatherCode, sky.isDay)
        rv.setTextViewText(R.id.condition, conditionLabel)
        rv.setTextViewText(
            R.id.hilo,
            today?.let {
                if (size.longHiLo) "Máx ${deg(it.max)}  ·  Mín ${deg(it.min)}" else "↑ ${deg(it.max)}   ↓ ${deg(it.min)}"
            } ?: "",
        )
        rv.setTextViewText(R.id.feels, "Sensação ${deg(cur.apparentTemperature)}")
        rv.setViewVisibility(R.id.feels, if (size.showFeels) View.VISIBLE else View.GONE)

        // --- Indicadores ---
        val showMetrics = size.showMetrics
        rv.setViewVisibility(R.id.metrics, if (showMetrics) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.hint, View.GONE)
        if (showMetrics) {
            val twoLine = size.metricLabels
            METRIC_LABEL_IDS.forEach { rv.setViewVisibility(it, if (twoLine) View.VISIBLE else View.GONE) }
            val rain = cur.precipitationProbability ?: today?.precipitationProbability
            val uv = (cur.uvIndex ?: if (sky.isDay) today?.uvIndexMax else 0.0)
            rv.setTextViewText(R.id.m0_value, rain?.let { "$it%" } ?: "--")
            rv.setTextViewText(R.id.m1_value, cur.humidity.takeIf { it >= 0 }?.let { "$it%" } ?: "--")
            rv.setTextViewText(R.id.m2_value, cur.windSpeedKmh.takeIf { it >= 0 }?.let { "${it.roundToInt()} km/h" } ?: "--")
            val uvRounded = uv?.roundToInt()
            rv.setTextViewText(R.id.m3_value, uvRounded?.let { if (twoLine) "$it" else "UV $it" } ?: "--")
            if (twoLine) {
                rv.setTextViewText(R.id.m0_label, "Chuva")
                rv.setTextViewText(R.id.m1_label, "Umidade")
                rv.setTextViewText(R.id.m2_label, "Vento")
                rv.setTextViewText(R.id.m3_label, uv?.let { "UV · ${WeatherCodes.uvLabel(it)}" } ?: "UV")
            }
        }

        // --- Próximos cinco dias ---
        if (size.forecast) {
            val days = snap.nextDays
            rv.setViewVisibility(R.id.forecast, if (days.isEmpty()) View.GONE else View.VISIBLE)
            DAY_IDS.forEachIndexed { i, ids ->
                val day = days.getOrNull(i)
                if (day == null) {
                    ids.forEach { rv.setViewVisibility(it, View.INVISIBLE) }
                } else {
                    ids.forEach { rv.setViewVisibility(it, View.VISIBLE) }
                    rv.setTextViewText(ids[0], dayName(day.date))
                    rv.setImageViewResource(ids[1], WeatherCodes.icon(day.weatherCode, true))
                    rv.setContentDescription(ids[1], WeatherCodes.label(day.weatherCode))
                    rv.setTextViewText(ids[2], deg(day.max))
                    rv.setTextViewText(ids[3], deg(day.min))
                }
            }
        }

        applyScale(rv, size)

        rv.setContentDescription(
            android.R.id.background,
            "${snap.place.name}, ${deg(cur.temperature)}, $conditionLabel. $statusText",
        )
        return rv
    }

    private fun renderEmpty(context: Context, rv: RemoteViews, size: WidgetSize, model: RenderModel, state: CrossfadeState) {
        rv.setTextViewText(R.id.city, model.pendingPlaceName ?: context.getString(R.string.app_name))
        rv.setTextViewText(R.id.region, "")
        state.temp.apply(rv, R.id.temp_a, R.id.temp_b) { id, _ ->
            rv.setTextViewText(id, tempSpan("\u2014", Atmosphere.NIGHT.accent))
        }
        state.icon.apply(rv, R.id.icon_a, R.id.icon_b) { id, value ->
            rv.setImageViewResource(id, iconFromKey(value))
            rv.setInt(id, "setImageAlpha", 90)
        }
        val message = when {
            model.refreshing -> "Carregando…"
            model.lastError != null -> "Sem dados"
            else -> "Configure no app"
        }
        rv.setTextViewText(R.id.condition, message)
        rv.setTextViewText(R.id.hilo, "")
        rv.setTextViewText(R.id.feels, "")
        rv.setViewVisibility(R.id.metrics, View.GONE)
        rv.setViewVisibility(R.id.forecast, View.GONE)
        applyScale(rv, size)
        val showHint = size.forecast && !model.refreshing
        rv.setViewVisibility(R.id.hint, if (showHint) View.VISIBLE else View.GONE)
        rv.setTextViewText(R.id.hint, model.lastError?.let { "$it." } ?: "Abra o app para permitir a localização ou escolher uma cidade.")
        rv.setContentDescription(android.R.id.background, "Tempo. $message. ${model.lastError.orEmpty()}")
    }

    private fun applyScale(rv: RemoteViews, size: WidgetSize) {
        rv.setTextViewTextSize(R.id.temp_a, TypedValue.COMPLEX_UNIT_SP, size.tempSp)
        rv.setTextViewTextSize(R.id.temp_b, TypedValue.COMPLEX_UNIT_SP, size.tempSp)
        if (size.minWidthDp < 280f) {
            METRIC_VALUE_IDS.forEach { rv.setTextViewTextSize(it, TypedValue.COMPLEX_UNIT_SP, 11.5f) }
        }
        if (size == WidgetSize.LARGE) {
            rv.setTextViewTextSize(R.id.condition, TypedValue.COMPLEX_UNIT_SP, 17f)
            rv.setTextViewTextSize(R.id.hilo, TypedValue.COMPLEX_UNIT_SP, 13f)
            rv.setTextViewTextSize(R.id.feels, TypedValue.COMPLEX_UNIT_SP, 13f)
            rv.setTextViewTextSize(R.id.city, TypedValue.COMPLEX_UNIT_SP, 16.5f)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        size.iconDp?.let { icon ->
            val box = icon * 1.22f
            rv.setViewLayoutWidth(R.id.icon_box, box, TypedValue.COMPLEX_UNIT_DIP)
            rv.setViewLayoutHeight(R.id.icon_box, box, TypedValue.COMPLEX_UNIT_DIP)
            listOf(R.id.icon_a, R.id.icon_b).forEach {
                rv.setViewLayoutWidth(it, icon, TypedValue.COMPLEX_UNIT_DIP)
                rv.setViewLayoutHeight(it, icon, TypedValue.COMPLEX_UNIT_DIP)
            }
        }
        if (size == WidgetSize.LARGE) {
            DAY_IDS.forEach {
                rv.setViewLayoutWidth(it[1], 34f, TypedValue.COMPLEX_UNIT_DIP)
                rv.setViewLayoutHeight(it[1], 34f, TypedValue.COMPLEX_UNIT_DIP)
            }
        }
    }

    // --- Formatação --------------------------------------------------------------------------

    /** Arredonda e usa o sinal de menos tipográfico (−) para negativos; nunca exibe "-0". */
    fun tempText(value: Double): String {
        val r = value.roundToInt()
        return if (r < 0) "−${-r}" else "$r"
    }

    fun deg(value: Double) = "${tempText(value)}°"

    private fun tempSpan(digits: String, accent: Int): CharSequence {
        val s = SpannableString("$digits°")
        val i = digits.length
        // Grau menor, elevado e tingido pela luz da atmosfera: detalhe tipográfico de display.
        s.setSpan(RelativeSizeSpan(0.62f), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(SuperscriptSpan(), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(ForegroundColorSpan(accent), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    fun dayName(isoDate: String): String = runCatching {
        DAY_NAMES[LocalDate.parse(isoDate).dayOfWeek.value - 1]
    }.getOrDefault("--")

    private fun statusLine(context: Context, model: RenderModel, short: Boolean): Pair<String, Boolean> {
        val snap = model.snapshot
        if (model.refreshing) return (if (short) "Atualizando" else "Atualizando…") to true
        if (snap == null) return "" to false
        val fetched = Instant.ofEpochMilli(snap.fetchedAt)
        val zone = ZoneId.systemDefault()
        val sameDay = fetched.atZone(zone).toLocalDate() == model.now.atZone(zone).toLocalDate()
        val pattern = (if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a")
            .let { if (sameDay) it else "dd/MM $it" }
        val time = DateTimeFormatter.ofPattern(pattern).withZone(zone).format(fetched)
        val failedSince = model.lastError != null && model.lastAttemptAt > snap.fetchedAt
        val ageMin = (model.now.toEpochMilli() - snap.fetchedAt) / 60_000
        val stale = ageMin > model.intervalMinutes * 2 + 15
        return when {
            failedSince -> (if (short) "Offline $time" else "Offline · $time") to false
            short -> time to !stale
            else -> "Atualizado $time" to !stale
        }
    }

    private fun iconFromKey(key: String): Int {
        val parts = key.split('|')
        val code = parts.getOrNull(0)?.toIntOrNull() ?: return R.drawable.wx_cloudy
        return WeatherCodes.icon(code, parts.getOrNull(1)?.toBooleanStrictOrNull() ?: true)
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    // --- Transições --------------------------------------------------------------------------

    /**
     * Mantém, por widget, qual das duas camadas (A/B) está visível. Quando um valor muda, o novo
     * vai para a camada oculta e a visibilidade é invertida; o LayoutTransition do container
     * (animateLayoutChanges) faz o crossfade sem nenhum salto de layout.
     */
    private class CrossfadeState(context: Context, private val widgetId: Int, private val commit: Boolean) {
        private val store = WeatherStore(context)
        lateinit var temp: Layered
        lateinit var icon: Layered
        lateinit var background: Layered

        fun prepare(model: RenderModel) {
            val snap = model.snapshot
            val atm = snap?.let { Atmosphere.resolve(it, model.now) } ?: Atmosphere.NIGHT
            val tempValue = snap?.let { tempText(it.current.temperature) } ?: "\u2014"
            // Chaves estáveis (não IDs de recurso, que mudam entre versões do app).
            val iconValue = snap?.let {
                "${it.current.weatherCode}|${SkyClock.of(it, model.now).isDay}"
            } ?: "empty"
            temp = pair("temp", tempValue)
            icon = pair("icon", iconValue)
            background = pair("bg", atm.name)
        }

        private fun pair(key: String, value: String): Layered {
            if (widgetId < 0) return Layered(key, 0, value, value, changed = false)
            val slot = store.widgetSlot(widgetId, key)
            val old = store.widgetValue(widgetId, key)
            val changed = old != null && old != value
            return Layered(key, if (changed) 1 - slot else slot, value, old ?: value, changed)
        }

        fun commit() {
            if (!commit || widgetId < 0) return
            listOf(temp, icon, background).forEach { store.saveWidgetValue(widgetId, it.key, it.slot, it.value) }
        }
    }

    private class Layered(val key: String, val slot: Int, val value: String, val old: String, val changed: Boolean) {
        fun apply(rv: RemoteViews, @IdRes a: Int, @IdRes b: Int, set: (Int, String) -> Unit) {
            val shown = if (slot == 0) a else b
            val hidden = if (slot == 0) b else a
            set(shown, value)
            // A camada que sai continua mostrando o valor antigo enquanto desaparece.
            set(hidden, old)
            rv.setViewVisibility(shown, View.VISIBLE)
            rv.setViewVisibility(hidden, View.INVISIBLE)
        }
    }
}
