package app.tempo.weather.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
import app.tempo.weather.data.WeatherAlert
import app.tempo.weather.data.isActiveNow
import app.tempo.weather.data.isActiveOrUpcoming
import app.tempo.weather.ui.DetailActivity
import app.tempo.weather.ui.DetailSection
import java.time.LocalDateTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Variações responsivas. Cada família (compacta, média, expandida) tem um XML próprio com
 * composição própria; as variações de uma família ajustam escala e densidade.
 * Os valores padrão do XML são os mais compactos (seguros também abaixo do Android 12).
 */
enum class WidgetSize(
    @LayoutRes val layout: Int,
    val minWidthDp: Float,
    val minHeightDp: Float,
    val tempSp: Float,
    /** Tamanho do ícone principal (aplicado no Android 12+). null = valor do XML; 0 = oculto. */
    val iconDp: Float? = null,
    val showFeels: Boolean = true,
    val longHiLo: Boolean = false,
    val showMetrics: Boolean = true,
    val metricLabels: Boolean = false,
    val miniForecast: Boolean = false,
    val forecast: Boolean = false,
    /** Faixa de alerta + linha de título com IQA (só quando há altura de sobra). */
    val roomy: Boolean = false,
) {
    COMPACT(R.layout.widget_compact, 0f, 0f, 46f, showFeels = false, showMetrics = false),
    MEDIUM_SHORT(R.layout.widget_expanded, 220f, 0f, 44f, iconDp = 44f, showFeels = false, showMetrics = false),
    MEDIUM_NARROW(R.layout.widget_expanded, 220f, 150f, 46f, iconDp = 44f, showFeels = false),
    MEDIUM(R.layout.widget_medium, 280f, 150f, 48f, miniForecast = true),
    EXPANDED_NARROW(R.layout.widget_expanded, 250f, 250f, 54f, iconDp = 48f, showFeels = false, showMetrics = false, forecast = true),
    EXPANDED(R.layout.widget_expanded, 300f, 250f, 62f, forecast = true),
    LARGE(R.layout.widget_expanded, 300f, 330f, 72f, iconDp = 68f, metricLabels = true, forecast = true, roomy = true);

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
    val intervalMinutes: Int,
    val pendingPlaceName: String?,
    val now: Instant = Instant.now(),
) {
    companion object {
        fun from(context: Context, store: WeatherStore = WeatherStore(context)): RenderModel = RenderModel(
            snapshot = store.snapshot,
            refreshing = store.isRefreshing(),
            lastError = store.lastError,
            lastAttemptAt = store.lastAttemptAt,
            intervalMinutes = store.updateIntervalMinutes,
            pendingPlaceName = store.manualPlace?.name,
        )
    }
}

object WidgetRenderer {

    private val DAY_NAMES = arrayOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
    private val COMPASS = arrayOf("N", "NE", "L", "SE", "S", "SO", "O", "NO")
    private val METRIC_VALUE_IDS = intArrayOf(R.id.m0_value, R.id.m1_value, R.id.m2_value, R.id.m3_value)
    private val METRIC_LABEL_IDS = intArrayOf(R.id.m0_label, R.id.m1_label, R.id.m2_label, R.id.m3_label)
    private val METRIC_ICON_IDS = intArrayOf(R.id.m0_icon, R.id.m1_icon, R.id.m2_icon, R.id.m3_icon)
    private val DAY_IDS = arrayOf(
        intArrayOf(R.id.d0_name, R.id.d0_icon, R.id.d0_max, R.id.d0_min),
        intArrayOf(R.id.d1_name, R.id.d1_icon, R.id.d1_max, R.id.d1_min),
        intArrayOf(R.id.d2_name, R.id.d2_icon, R.id.d2_max, R.id.d2_min),
        intArrayOf(R.id.d3_name, R.id.d3_icon, R.id.d3_max, R.id.d3_min),
        intArrayOf(R.id.d4_name, R.id.d4_icon, R.id.d4_max, R.id.d4_min),
    )
    private val ROW_IDS = arrayOf(
        intArrayOf(R.id.r0, R.id.r0_name, R.id.r0_icon, R.id.r0_max, R.id.r0_min),
        intArrayOf(R.id.r1, R.id.r1_name, R.id.r1_icon, R.id.r1_max, R.id.r1_min),
        intArrayOf(R.id.r2, R.id.r2_name, R.id.r2_icon, R.id.r2_max, R.id.r2_min),
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
        val expandedFamily = size.layout == R.layout.widget_expanded
        val mediumFamily = size.layout == R.layout.widget_medium

        // --- Cenário: fotografia (período × condição) ou degradê de reserva, em crossfade A/B ---
        val photoMode = state.background.value.startsWith(PHOTO)
        state.background.apply(rv, R.id.bg_a, R.id.bg_b) { id, value ->
            rv.setImageViewResource(id, backgroundRes(context, value, atmosphere))
        }
        rv.setInt(android.R.id.background, "setBackgroundResource", atmosphere.background)
        // Sobre a foto: véu escuro translúcido para legibilidade. No degradê: detalhe estático.
        rv.setViewVisibility(R.id.scrim, if (photoMode) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.deco, if (photoMode) View.GONE else View.VISIBLE)
        if (!photoMode) rv.setImageViewResource(R.id.deco, atmosphere.deco)
        val panel = if (photoMode) R.drawable.glass_dark else R.drawable.glass_soft
        rv.setInt(R.id.metrics, "setBackgroundResource", panel)
        if (expandedFamily) rv.setInt(R.id.forecast, "setBackgroundResource", panel)

        // --- Interações (todas funcionais) ---
        rv.setOnClickPendingIntent(android.R.id.background, detailIntent(context, DetailSection.TOP))
        if (!compact) {
            rv.setOnClickPendingIntent(R.id.refresh, WeatherWidgetProvider.refreshIntent(context))
            rv.setOnClickPendingIntent(R.id.alert_chip, detailIntent(context, DetailSection.ALERTS))
        }
        if (expandedFamily) {
            rv.setOnClickPendingIntent(R.id.alert_banner, detailIntent(context, DetailSection.ALERTS))
            rv.setOnClickPendingIntent(R.id.forecast, detailIntent(context, DetailSection.DAILY))
            rv.setOnClickPendingIntent(R.id.metrics, detailIntent(context, DetailSection.DETAILS))
        }
        if (mediumFamily) {
            rv.setOnClickPendingIntent(R.id.mini_forecast, detailIntent(context, DetailSection.DAILY))
            rv.setOnClickPendingIntent(R.id.metrics, detailIntent(context, DetailSection.DETAILS))
        }
        rv.setViewVisibility(R.id.loading, if (model.refreshing && !compact) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.refresh, if (model.refreshing) View.INVISIBLE else View.VISIBLE)

        // --- Status de atualização ---
        val (statusText, fresh) = statusLine(context, model, short = !size.roomy)
        rv.setTextViewText(R.id.updated, statusText)
        // Em larguras estreitas só o ponto de status fica, para o nome da cidade caber.
        if (!compact) rv.setViewVisibility(R.id.updated, if (size.minWidthDp < 280f && snap != null && !model.refreshing) View.GONE else View.VISIBLE)
        rv.setInt(R.id.status_dot, "setColorFilter", context.getColor(if (fresh) R.color.status_ok else R.color.status_stale))
        rv.setViewVisibility(R.id.status_dot, if (snap != null && !model.refreshing) View.VISIBLE else View.GONE)

        if (snap == null) {
            renderEmpty(context, rv, size, model)
            applyScale(rv, size)
            return rv
        }

        val sky = SkyClock.of(snap, model.now)
        val cur = snap.current
        val today = snap.today

        rv.setTextViewText(R.id.city, snap.place.name)

        rv.setViewVisibility(R.id.temp_box, View.VISIBLE)
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
                if (size.longHiLo) "Máx ${deg(it.max)}  ·  Mín ${deg(it.min)}" else "↑${deg(it.max)} ↓${deg(it.min)}"
            } ?: "",
        )
        rv.setTextViewText(R.id.feels, "Sensação ${deg(cur.apparentTemperature)}")
        rv.setViewVisibility(R.id.feels, if (size.showFeels) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.hint, View.GONE)

        // --- Alerta oficial do INMET ---
        val alert = primaryAlert(snap, model.now)
        val alertColor = alert?.level?.color ?: 0
        val showChip = alert != null && !size.roomy && !compact
        rv.setViewVisibility(R.id.alert_chip, if (showChip) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.region, if (showChip || snap.place.region.isBlank()) View.GONE else View.VISIBLE)
        rv.setTextViewText(R.id.region, "· ${snap.place.region}")
        if (alert != null) {
            rv.setInt(R.id.alert_icon, "setColorFilter", alertColor)
            rv.setTextViewText(R.id.alert_text, "Alerta ${alert.level.colorName}")
            // Em larguras estreitas o chip vira só o ícone, para a cidade continuar legível.
            rv.setViewVisibility(R.id.alert_text, if (size.minWidthDp < 360f) View.GONE else View.VISIBLE)
            rv.setTextColor(R.id.alert_text, alertColor)
            rv.setContentDescription(R.id.alert_chip, alert.title)
            rv.setContentDescription(R.id.alert_icon, alert.title)
        }
        // Sempre define a visibilidade: o launcher reaproveita a view anterior (ex.: estado vazio
        // escondeu o ícone) e só aplica o que for enviado agora.
        rv.setViewVisibility(R.id.alert_icon, if (alert != null) View.VISIBLE else View.GONE)
        if (expandedFamily) {
            val showBanner = alert != null && size.roomy
            rv.setViewVisibility(R.id.alert_banner, if (showBanner) View.VISIBLE else View.GONE)
            if (alert != null) {
                rv.setInt(R.id.banner_icon, "setColorFilter", alertColor)
                rv.setTextViewText(R.id.banner_text, bannerText(alert, model.now))
            }
        }

        // --- Indicadores ---
        rv.setViewVisibility(R.id.metrics, if (size.showMetrics) View.VISIBLE else View.GONE)
        if (size.showMetrics) {
            val labels = size.metricLabels
            val rain = cur.precipitationProbability ?: today?.precipitationProbability
            val uv = cur.uvIndex ?: if (sky.isDay) today?.uvIndexMax else 0.0
            val uvRounded = uv?.roundToInt()
            rv.setTextViewText(R.id.m0_value, rain?.let { "$it%" } ?: "--")
            rv.setTextViewText(R.id.m1_value, cur.humidity.takeIf { it >= 0 }?.let { "$it%" } ?: "--")
            rv.setTextViewText(R.id.m2_value, cur.windSpeedKmh.takeIf { it >= 0 }?.let { unitSpan("${it.roundToInt()}", if (size.minWidthDp < 280f) "km/h" else " km/h") } ?: "--")
            rv.setTextViewText(R.id.m3_value, uvRounded?.let { if (labels) "$it" else "UV $it" } ?: "--")
            METRIC_ICON_IDS.forEach { rv.setInt(it, "setImageAlpha", 200) }
            METRIC_LABEL_IDS.forEach { rv.setViewVisibility(it, if (labels) View.VISIBLE else View.GONE) }
            if (labels) {
                rv.setTextViewText(R.id.m0_label, "Chuva")
                rv.setTextViewText(R.id.m1_label, "Umidade")
                rv.setTextViewText(R.id.m2_label, cur.windDirection?.let { "Vento · ${compass(it)}" } ?: "Vento")
                rv.setTextViewText(R.id.m3_label, uv?.let { "UV · ${WeatherCodes.uvLabel(it)}" } ?: "UV")
            }
            rv.setContentDescription(R.id.m0, "Chuva ${rain ?: "--"}%")
            rv.setContentDescription(R.id.m1, "Umidade ${cur.humidity}%")
            rv.setContentDescription(R.id.m2, "Vento ${cur.windSpeedKmh.roundToInt()} quilômetros por hora")
            rv.setContentDescription(R.id.m3, "Índice UV ${uvRounded ?: "--"}")
        }

        // --- Resumo de 3 dias (média) ---
        if (mediumFamily) {
            rv.setViewVisibility(R.id.mini_forecast, if (size.miniForecast) View.VISIBLE else View.GONE)
            rv.setViewVisibility(R.id.divider, if (size.miniForecast) View.VISIBLE else View.GONE)
            if (size.miniForecast) {
                val days = snap.nextDays
                ROW_IDS.forEachIndexed { i, ids ->
                    val day = days.getOrNull(i)
                    rv.setViewVisibility(ids[0], if (day == null) View.INVISIBLE else View.VISIBLE)
                    if (day != null) {
                        rv.setTextViewText(ids[1], dayName(day.date))
                        rv.setImageViewResource(ids[2], WeatherCodes.icon(day.weatherCode, true))
                        rv.setTextViewText(ids[3], deg(day.max))
                        rv.setTextViewText(ids[4], deg(day.min))
                        rv.setContentDescription(ids[0], "${dayName(day.date)}, ${WeatherCodes.label(day.weatherCode)}, máxima ${deg(day.max)}, mínima ${deg(day.min)}")
                    }
                }
            }
        }

        // --- Próximos cinco dias (expandida) ---
        if (expandedFamily) {
            val days = snap.nextDays
            rv.setViewVisibility(R.id.forecast, if (days.isEmpty() || !size.forecast) View.GONE else View.VISIBLE)
            DAY_IDS.forEachIndexed { i, ids ->
                val day = days.getOrNull(i)
                if (day == null) {
                    ids.forEach { rv.setViewVisibility(it, View.INVISIBLE) }
                } else {
                    ids.forEach { rv.setViewVisibility(it, View.VISIBLE) }
                    rv.setTextViewText(ids[0], dayName(day.date))
                    rv.setImageViewResource(ids[1], WeatherCodes.icon(day.weatherCode, true))
                    rv.setContentDescription(ids[1], WeatherCodes.label(day.weatherCode))
                    if (size.minWidthDp < 280f) {
                        rv.setViewVisibility(ids[3], View.VISIBLE)
                        rv.setTextViewText(ids[2], deg(day.max))
                        rv.setTextViewText(ids[3], deg(day.min))
                    } else {
                        // Máx e mín na mesma linha: mais compacto, leitura imediata.
                        rv.setTextViewText(ids[2], maxMinSpan(day.max, day.min))
                        rv.setViewVisibility(ids[3], View.GONE)
                    }
                }
            }
            val aq = snap.airQuality
            rv.setViewVisibility(R.id.forecast_title, if (size.roomy) View.VISIBLE else View.GONE)
            rv.setViewVisibility(R.id.aqi_dot, if (aq != null) View.VISIBLE else View.GONE)
            rv.setTextViewText(R.id.aqi_text, aq?.let { "IQA ${it.usAqi} · ${it.category}" } ?: "")
            aq?.let { rv.setInt(R.id.aqi_dot, "setColorFilter", it.color) }
        }

        applyScale(rv, size)

        rv.setContentDescription(
            android.R.id.background,
            listOfNotNull(
                "${snap.place.name}, ${deg(cur.temperature)}, $conditionLabel",
                alert?.title,
                statusText,
            ).joinToString(". "),
        )
        return rv
    }

    private fun renderEmpty(context: Context, rv: RemoteViews, size: WidgetSize, model: RenderModel) {
        rv.setTextViewText(R.id.city, model.pendingPlaceName ?: context.getString(R.string.app_name))
        rv.setViewVisibility(R.id.region, View.GONE)
        rv.setViewVisibility(R.id.alert_chip, View.GONE)
        rv.setViewVisibility(R.id.alert_banner, View.GONE)
        rv.setViewVisibility(R.id.alert_icon, View.GONE)
        // Sem dados não há temperatura: nada de placeholder que pareça um número.
        rv.setViewVisibility(R.id.temp_box, View.GONE)
        rv.setImageViewResource(R.id.icon_a, R.drawable.wx_cloudy)
        rv.setInt(R.id.icon_a, "setImageAlpha", 90)
        rv.setViewVisibility(R.id.icon_a, View.VISIBLE)
        rv.setViewVisibility(R.id.icon_b, View.INVISIBLE)
        val message = when {
            model.refreshing -> "Carregando…"
            size == WidgetSize.COMPACT -> "Escolher cidade"
            else -> "Toque para escolher a cidade"
        }
        rv.setTextViewText(R.id.condition, message)
        rv.setTextViewText(R.id.hilo, "")
        rv.setViewVisibility(R.id.feels, View.GONE)
        rv.setViewVisibility(R.id.metrics, View.GONE)
        rv.setViewVisibility(R.id.forecast, View.GONE)
        rv.setViewVisibility(R.id.forecast_title, View.GONE)
        rv.setViewVisibility(R.id.mini_forecast, View.GONE)
        rv.setViewVisibility(R.id.divider, View.GONE)
        val showHint = size.forecast && !model.refreshing
        rv.setViewVisibility(R.id.hint, if (showHint) View.VISIBLE else View.GONE)
        rv.setTextViewText(R.id.hint, model.lastError?.let { "$it." } ?: "Abra o app para permitir a localização ou escolher uma cidade.")
        rv.setContentDescription(android.R.id.background, "Tempo. $message. ${model.lastError.orEmpty()}")
    }

    private fun applyScale(rv: RemoteViews, size: WidgetSize) {
        rv.setTextViewTextSize(R.id.temp_a, TypedValue.COMPLEX_UNIT_SP, size.tempSp)
        rv.setTextViewTextSize(R.id.temp_b, TypedValue.COMPLEX_UNIT_SP, size.tempSp)
        if (size.minWidthDp in 1f..279f) {
            METRIC_VALUE_IDS.forEach { rv.setTextViewTextSize(it, TypedValue.COMPLEX_UNIT_SP, 12f) }
        }
        if (size == WidgetSize.LARGE) {
            rv.setTextViewTextSize(R.id.condition, TypedValue.COMPLEX_UNIT_SP, 17f)
            rv.setTextViewTextSize(R.id.hilo, TypedValue.COMPLEX_UNIT_SP, 13.5f)
            rv.setTextViewTextSize(R.id.feels, TypedValue.COMPLEX_UNIT_SP, 13f)
            rv.setTextViewTextSize(R.id.city, TypedValue.COMPLEX_UNIT_SP, 16.5f)
            METRIC_VALUE_IDS.forEach { rv.setTextViewTextSize(it, TypedValue.COMPLEX_UNIT_SP, 15f) }
        }
        val icon = size.iconDp
        if (icon == 0f) {
            rv.setViewVisibility(R.id.icon_box, View.GONE)
        } else {
            rv.setViewVisibility(R.id.icon_box, View.VISIBLE)
            if (icon != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                rv.setViewLayoutWidth(R.id.icon_box, icon, TypedValue.COMPLEX_UNIT_DIP)
                rv.setViewLayoutHeight(R.id.icon_box, icon, TypedValue.COMPLEX_UNIT_DIP)
            }
        }
        if (size == WidgetSize.LARGE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DAY_IDS.forEach {
                rv.setViewLayoutWidth(it[1], 30f, TypedValue.COMPLEX_UNIT_DIP)
                rv.setViewLayoutHeight(it[1], 30f, TypedValue.COMPLEX_UNIT_DIP)
            }
        }
    }

    /** O aviso mais severo em vigor agora; se nenhum estiver em vigor, o próximo a começar. */
    fun primaryAlert(snap: WeatherSnapshot, now: Instant = Instant.now()): WeatherAlert? {
        val brt = LocalDateTime.ofInstant(now, ZoneId.of("America/Sao_Paulo"))
        val live = snap.alerts.filter { it.isActiveOrUpcoming(brt) }
        return live.filter { it.isActiveNow(brt) }.maxByOrNull { it.level.ordinal }
            ?: live.minByOrNull { it.start }
    }

    private fun bannerText(alert: WeatherAlert, now: Instant): String {
        val brt = LocalDateTime.ofInstant(now, ZoneId.of("America/Sao_Paulo"))
        val f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val start = runCatching { LocalDateTime.parse(alert.start, f) }.getOrNull()
        val end = runCatching { LocalDateTime.parse(alert.end, f) }.getOrNull()
        val whenText = when {
            start != null && start.isAfter(brt) ->
                if (start.toLocalDate() == brt.toLocalDate().plusDays(1)) "a partir de amanhã" else "a partir de ${start.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))}"
            end != null -> if (end.toLocalDate() == brt.toLocalDate()) "até ${end.format(DateTimeFormatter.ofPattern("HH:mm"))}" else "até ${end.format(DateTimeFormatter.ofPattern("dd/MM"))}"
            else -> ""
        }
        return listOf(alert.title, whenText).filter { it.isNotBlank() }.joinToString(" · ")
    }

    fun compass(degrees: Int): String = COMPASS[(((degrees % 360) + 360) % 360 + 22) / 45 % 8]

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

    /** Número em destaque e unidade menor e mais discreta ("3 km/h"). */
    private fun unitSpan(value: String, unit: String): CharSequence {
        val s = SpannableString(value + unit)
        s.setSpan(RelativeSizeSpan(0.74f), value.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(ForegroundColorSpan(0xE0E8F5FF.toInt()), value.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    private fun maxMinSpan(max: Double, min: Double): CharSequence {
        val a = deg(max)
        val s = SpannableString("$a ${deg(min)}")
        s.setSpan(ForegroundColorSpan(0x99C9D6F2.toInt()), a.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(RelativeSizeSpan(0.82f), a.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.NORMAL), a.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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

    private const val PHOTO = "photo:"
    private const val GRADIENT = "grad:"

    /**
     * Chave estável do fundo: a mesma chave = nenhuma troca (sem piscar nem recarregar).
     * Usa a foto do cenário se ela existir no app; senão, o degradê da atmosfera.
     */
    fun backgroundKey(context: Context, snap: WeatherSnapshot?, now: Instant, atm: Atmosphere): String {
        if (snap == null) return GRADIENT + atm.name
        // Antes do Android 12 o launcher não recorta a foto nos cantos arredondados: usa o degradê.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return GRADIENT + atm.name
        val scene = Scenery.sceneFor(snap, now)
        return if (Scenery.photoRes(context, scene.photo) != 0) PHOTO + scene.photo else GRADIENT + atm.name
    }

    private fun backgroundRes(context: Context, key: String, fallback: Atmosphere): Int = when {
        key.startsWith(PHOTO) -> Scenery.photoRes(context, key.removePrefix(PHOTO)).takeIf { it != 0 } ?: fallback.background
        key.startsWith(GRADIENT) -> runCatching { Atmosphere.valueOf(key.removePrefix(GRADIENT)).background }.getOrDefault(fallback.background)
        else -> runCatching { Atmosphere.valueOf(key).background }.getOrDefault(fallback.background) // chave da versão anterior
    }

    private fun iconFromKey(key: String): Int {
        val parts = key.split('|')
        val code = parts.getOrNull(0)?.toIntOrNull() ?: return R.drawable.wx_cloudy
        return WeatherCodes.icon(code, parts.getOrNull(1)?.toBooleanStrictOrNull() ?: true)
    }

    /** Abre a previsão detalhada já na seção tocada (requestCode distinto por seção). */
    private fun detailIntent(context: Context, section: DetailSection): PendingIntent = PendingIntent.getActivity(
        context, 100 + section.ordinal,
        Intent(context, DetailActivity::class.java)
            .putExtra(DetailActivity.EXTRA_SECTION, section.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    // --- Transições --------------------------------------------------------------------------

    /**
     * Mantém, por widget, qual das duas camadas (A/B) está visível. Quando um valor muda, o novo
     * vai para a camada oculta e a visibilidade é invertida; o LayoutTransition do container
     * (animateLayoutChanges) faz o crossfade sem nenhum salto de layout.
     */
    private class CrossfadeState(private val context: Context, private val widgetId: Int, private val commit: Boolean) {
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
            background = pair("bg", backgroundKey(context, snap, model.now, atm))
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
