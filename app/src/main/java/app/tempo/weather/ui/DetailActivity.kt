package app.tempo.weather.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import app.tempo.weather.R
import app.tempo.weather.data.AirQuality
import app.tempo.weather.data.DailyForecast
import app.tempo.weather.data.HourlyForecast
import app.tempo.weather.data.WeatherAlert
import app.tempo.weather.data.WeatherCodes
import app.tempo.weather.data.WeatherSnapshot
import app.tempo.weather.data.WeatherStore
import app.tempo.weather.data.isActiveNow
import app.tempo.weather.data.isActiveOrUpcoming
import app.tempo.weather.widget.Atmosphere
import app.tempo.weather.widget.SkyClock
import app.tempo.weather.widget.WidgetRenderer
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Seções da previsão detalhada que o widget pode abrir diretamente. */
enum class DetailSection { TOP, ALERTS, HOURLY, DAILY, DETAILS }

private val Ink2 = Color(0xE0E8F5FF)
private val Ink3 = Color(0x99C9D6F2)
private val Glass = Color(0x14FFFFFF)

/**
 * Previsão detalhada: alertas do INMET, 24 horas, 5 dias, UV, umidade, sensação, vento e direção,
 * qualidade do ar e nascer/pôr do sol. Lê o mesmo cache que o widget; nada é buscado em paralelo.
 */
class DetailActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SECTION = "section"
    }

    private var section by mutableStateOf(DetailSection.TOP)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        section = sectionFrom(intent)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF8FC4FF))) {
                DetailScreen(
                    section = section,
                    onRefresh = { lifecycleScope.launch { refreshWithForegroundLocation(this@DetailActivity) } },
                    onSettings = { startActivity(Intent(this, MainActivity::class.java)) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        section = sectionFrom(intent)
    }

    override fun onResume() {
        super.onResume()
        val store = WeatherStore(this)
        val age = store.snapshot?.let { System.currentTimeMillis() - it.fetchedAt } ?: Long.MAX_VALUE
        if (age > 15 * 60_000L && !store.isRefreshing()) {
            lifecycleScope.launch { refreshWithForegroundLocation(this@DetailActivity) }
        }
    }

    private fun sectionFrom(intent: Intent?) =
        runCatching { DetailSection.valueOf(intent?.getStringExtra(EXTRA_SECTION)!!) }.getOrDefault(DetailSection.TOP)
}

@Composable
private fun DetailScreen(section: DetailSection, onRefresh: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val store = remember { WeatherStore(context) }
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> version++ }
        store.registerListener(l)
        onDispose { store.unregisterListener(l) }
    }
    val snap = remember(version) { store.snapshot }
    val refreshing = remember(version) { store.isRefreshing() }
    val error = remember(version) { store.lastError }
    val offline = remember(version) { error != null && snap != null && store.lastAttemptAt > snap.fetchedAt }
    val now = remember(version) { Instant.now() }
    val atm = snap?.let { Atmosphere.resolve(it, now) } ?: Atmosphere.NIGHT

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(atm.screen.map { Color(it) })),
    ) {
        if (snap == null) {
            EmptyState(refreshing, error, onRefresh, onSettings)
            return@Box
        }
        val alerts = snap.alerts.filter { it.isActiveOrUpcoming() }
        val listState = rememberLazyListState()
        // Índices fixos dos itens abaixo, para abrir direto na seção tocada no widget.
        val indexOf = mapOf(
            DetailSection.TOP to 0,
            DetailSection.ALERTS to 1,
            DetailSection.HOURLY to 2,
            DetailSection.DAILY to 3,
            DetailSection.DETAILS to 4,
        )
        LaunchedEffect(section) {
            listState.scrollToItem(indexOf.getValue(section).coerceAtMost(4))
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Hero(snap, now, refreshing, offline, onRefresh, onSettings) }
            item { if (alerts.isNotEmpty()) AlertsCard(alerts) else Spacer(Modifier.height(0.dp)) }
            item { if (snap.hourly.isNotEmpty()) HourlyCard(snap) else Spacer(Modifier.height(0.dp)) }
            item { DailyCard(snap) }
            item { DetailsGrid(snap, now) }
            item {
                Text(
                    "Previsão: Open-Meteo.com (CC BY 4.0) · Avisos: INMET · Qualidade do ar: Open-Meteo (índice US AQI)",
                    color = Ink3, fontSize = 11.sp, modifier = Modifier.navigationBarsPadding().padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(refreshing: Boolean, error: String?, onRefresh: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.wx_cloudy), null, Modifier.size(96.dp), alpha = 0.6f)
        Spacer(Modifier.height(16.dp))
        Text(if (refreshing) "Carregando previsão…" else (error ?: "Sem dados ainda"), color = Color.White, fontSize = 17.sp)
        Spacer(Modifier.height(20.dp))
        if (refreshing) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Atualizar", onRefresh)
                Pill("Escolher cidade", onSettings)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun Hero(
    snap: WeatherSnapshot,
    now: Instant,
    refreshing: Boolean,
    offline: Boolean,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
) {
    val sky = SkyClock.of(snap, now)
    val cur = snap.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(snap.place.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val status = when {
                    refreshing -> "Atualizando…"
                    offline -> "Offline · dados de ${hhmm(snap.fetchedAt)}"
                    else -> "Atualizado às ${hhmm(snap.fetchedAt)}"
                }
                Text(listOf(snap.place.region, status).filter { it.isNotBlank() }.joinToString(" · "), color = Ink3, fontSize = 12.5.sp)
            }
            IconButton(R.drawable.gl_refresh, "Atualizar", onRefresh, spinning = refreshing)
            Spacer(Modifier.width(8.dp))
            IconButton(R.drawable.gl_settings, "Configurações", onSettings)
        }
        Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    WidgetRenderer.deg(cur.temperature), color = Color.White, fontSize = 96.sp,
                    fontWeight = FontWeight.Light, letterSpacing = (-3).sp,
                )
                Text(WeatherCodes.label(cur.weatherCode, sky.isDay), color = Ink2, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                snap.today?.let {
                    Text(
                        "Máx ${WidgetRenderer.deg(it.max)}  ·  Mín ${WidgetRenderer.deg(it.min)}  ·  Sensação ${WidgetRenderer.deg(cur.apparentTemperature)}",
                        color = Ink3, fontSize = 13.5.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                snap.airQuality?.let { AqiPill(it) }
            }
            Image(painterResource(WeatherCodes.icon(cur.weatherCode, sky.isDay)), null, Modifier.size(120.dp))
        }
    }
}

@Composable
private fun AqiPill(aq: AirQuality) {
    Row(
        Modifier.padding(top = 12.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x1FFFFFFF))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.gl_leaf), null, Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text("IQA ${aq.usAqi} · ${aq.category}", color = Color.White, fontSize = 12.5.sp)
    }
}

@Composable
private fun AlertsCard(alerts: List<WeatherAlert>) {
    Card {
        alerts.forEachIndexed { i, a ->
            var open by remember { mutableStateOf(false) }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { open = !open }
                    .animateContentSize().padding(vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 4.dp, height = 34.dp).clip(RoundedCornerShape(2.dp)).background(Color(a.level.color)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "${a.level.label} · ${period(a)}${if (a.isActiveNow()) " · em vigor" else ""}",
                            color = Ink3, fontSize = 12.sp,
                        )
                    }
                    Text(if (open) "−" else "+", color = Ink3, fontSize = 18.sp)
                }
                if (open) {
                    a.risks.forEach { Text(it, color = Ink2, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp)) }
                    if (a.instructions.isNotEmpty()) {
                        Text("Recomendações", color = Ink3, fontSize = 11.5.sp, modifier = Modifier.padding(start = 16.dp, top = 10.dp))
                        a.instructions.forEach { Text("•  $it", color = Ink2, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 4.dp)) }
                    }
                }
            }
            if (i < alerts.lastIndex) Divider()
        }
        Text("Fonte: INMET", color = Ink3, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun HourlyCard(snap: WeatherSnapshot) {
    val hours = snap.hourly
    val sun = snap.daily.flatMap { listOfNotNull(it.sunrise?.let { s -> s to "Nascer do sol" }, it.sunset?.let { s -> s to "Pôr do sol" }) }
    val colW = 58.dp
    Card(title = "Previsão para 24 horas") {
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Column {
                TempCurve(hours, colW)
                Row {
                    hours.forEachIndexed { i, h ->
                        val event = sun.firstOrNull { (t, _) -> t.take(13) == h.time.take(13) }
                        Column(Modifier.width(colW), horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(painterResource(WeatherCodes.icon(h.weatherCode, h.isDay)), null, Modifier.size(30.dp))
                            Text(
                                h.precipitationProbability?.takeIf { it >= 10 }?.let { "$it%" } ?: " ",
                                color = Color(0xFF8FC4FF), fontSize = 11.sp,
                            )
                            Text(h.windSpeedKmh?.let { "${it.roundToInt()} km/h" } ?: "", color = Ink3, fontSize = 10.5.sp)
                            Text(
                                if (i == 0) "Agora" else h.time.substring(11, 16),
                                color = if (i == 0) Color.White else Ink3, fontSize = 11.5.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Text(event?.let { "${if (it.second.startsWith("N")) "↑" else "↓"} ${it.first.substring(11, 16)}" } ?: " ", color = Color(0xFFFFC870), fontSize = 10.5.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Curva suave das temperaturas, com os valores sobre cada ponto. */
@Composable
private fun TempCurve(hours: List<HourlyForecast>, colW: Dp) {
    val temps = hours.map { it.temperature }
    val min = temps.minOrNull() ?: 0.0
    val max = temps.maxOrNull() ?: 1.0
    val span = (max - min).coerceAtLeast(1.0)
    Box(Modifier.width(colW * hours.size).height(64.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = colW.toPx()
            val top = 24.dp.toPx()
            val bottom = size.height - 6.dp.toPx()
            fun y(t: Double) = (bottom - (t - min) / span * (bottom - top)).toFloat()
            val pts = temps.mapIndexed { i, t -> Offset(w * i + w / 2, y(t)) }
            if (pts.size > 1) {
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) {
                        val p0 = pts[i - 1]; val p1 = pts[i]
                        val cx = (p0.x + p1.x) / 2
                        cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                    }
                }
                drawPath(
                    path,
                    Brush.horizontalGradient(listOf(Color(0xFF8FC4FF), Color(0xFFFFC870), Color(0xFF8FC4FF))),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            pts.firstOrNull()?.let { drawCircle(Color.White, 3.5.dp.toPx(), it) }
        }
        Row {
            temps.forEach { t ->
                Box(Modifier.width(colW).fillMaxHeight()) {
                    val frac = ((t - min) / span).toFloat()
                    Text(
                        WidgetRenderer.deg(t), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = ((1f - frac) * 22f).dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyCard(snap: WeatherSnapshot) {
    val days = snap.daily.take(6)
    val lo = days.minOfOrNull { it.min } ?: 0.0
    val hi = days.maxOfOrNull { it.max } ?: 1.0
    Card(title = "Previsão para 5 dias") {
        days.forEachIndexed { i, d ->
            DayRow(if (i == 0) "Hoje" else if (i == 1) "Amanhã" else WidgetRenderer.dayName(d.date), d, lo, hi, isToday = i == 0, now = snap.current.temperature)
            if (i < days.lastIndex) Divider()
        }
    }
}

@Composable
private fun DayRow(name: String, d: DailyForecast, lo: Double, hi: Double, isToday: Boolean, now: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = Color.White, fontSize = 15.sp, modifier = Modifier.width(72.dp))
        Image(painterResource(WeatherCodes.icon(d.weatherCode, true)), null, Modifier.size(28.dp))
        Text(
            d.precipitationProbability?.takeIf { it >= 10 }?.let { "$it%" } ?: "",
            color = Color(0xFF8FC4FF), fontSize = 12.sp, modifier = Modifier.width(44.dp).padding(start = 6.dp),
        )
        Text(WidgetRenderer.deg(d.min), color = Ink3, fontSize = 14.sp, modifier = Modifier.width(38.dp))
        RangeBar(d.min, d.max, lo, hi, if (isToday) now else null, Modifier.weight(1f).height(5.dp))
        Text(WidgetRenderer.deg(d.max), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(42.dp).padding(start = 8.dp))
    }
}

@Composable
private fun RangeBar(min: Double, max: Double, lo: Double, hi: Double, now: Double?, modifier: Modifier) {
    Canvas(modifier) {
        val span = (hi - lo).coerceAtLeast(1.0)
        val r = size.height / 2
        drawRoundRect(Color(0x1FFFFFFF), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        val x0 = ((min - lo) / span * size.width).toFloat()
        val x1 = ((max - lo) / span * size.width).toFloat().coerceAtLeast(x0 + size.height)
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color(0xFF8FC4FF), Color(0xFFFFC870)), startX = x0, endX = x1),
            topLeft = Offset(x0, 0f), size = Size(x1 - x0, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
        )
        now?.let {
            val x = ((it - lo) / span * size.width).toFloat().coerceIn(x0, x1)
            drawCircle(Color(0xFF0B1433), r * 1.6f, Offset(x, r))
            drawCircle(Color.White, r * 1.1f, Offset(x, r))
        }
    }
}

@Composable
private fun DetailsGrid(snap: WeatherSnapshot, now: Instant) {
    val cur = snap.current
    val sky = SkyClock.of(snap, now)
    val uv = cur.uvIndex ?: if (sky.isDay) snap.today?.uvIndexMax else 0.0
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("Índice UV", uv?.let { WeatherCodes.uvLabel(it) } ?: "--", Modifier.weight(1f)) {
                Gauge((uv ?: 0.0) / 11.0, uv?.roundToInt()?.toString() ?: "--",
                    listOf(Color(0xFF7CE0A0), Color(0xFFF5D76E), Color(0xFFFF9442), Color(0xFFFF5A52), Color(0xFFC28CFF)))
            }
            Tile("Umidade", if (cur.humidity >= 0) "${cur.humidity}%" else "--", Modifier.weight(1f)) {
                Gauge(cur.humidity.coerceAtLeast(0) / 100.0, "", listOf(Color(0xFF5AB8FF), Color(0xFF3D7BFF)), R.drawable.gl_humidity)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("Sensação térmica", WidgetRenderer.deg(cur.apparentTemperature), Modifier.weight(1f)) {
                val diff = cur.apparentTemperature - cur.temperature
                Text(
                    when {
                        diff >= 2 -> "Parece mais quente que a temperatura real por causa da umidade."
                        diff <= -2 -> "Parece mais frio que a temperatura real por causa do vento."
                        else -> "Próxima da temperatura real."
                    },
                    color = Ink3, fontSize = 12.5.sp,
                )
            }
            Tile(
                "Vento",
                cur.windDirection?.let { "${WidgetRenderer.compass(it)} · ${cur.windSpeedKmh.roundToInt()} km/h" } ?: "${cur.windSpeedKmh.roundToInt()} km/h",
                Modifier.weight(1f),
            ) { Compass(cur.windDirection) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val aq = snap.airQuality
            Tile("Qualidade do ar", aq?.let { "${it.usAqi} · ${it.category}" } ?: "Indisponível", Modifier.weight(1f)) {
                if (aq != null) {
                    Gauge(aq.usAqi / 300.0, "", listOf(Color(0xFF7CE0A0), Color(0xFFF5D76E), Color(0xFFFF9442), Color(0xFFFF5A52), Color(0xFFC28CFF)), R.drawable.gl_leaf)
                    Text(
                        listOfNotNull(aq.pm25?.let { "PM2,5 ${it.roundToInt()}" }, aq.pm10?.let { "PM10 ${it.roundToInt()}" }).joinToString(" · ") + " µg/m³",
                        color = Ink3, fontSize = 11.5.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    Text("Sem resposta do serviço de qualidade do ar.", color = Ink3, fontSize = 12.5.sp)
                }
            }
            val t = snap.today
            Tile("Sol", t?.sunrise?.let { "↑ ${it.substring(11, 16)}" } ?: "--", Modifier.weight(1f)) {
                Text(t?.sunset?.let { "Pôr do sol às ${it.substring(11, 16)}" } ?: "", color = Ink2, fontSize = 13.sp)
                Text(if (sky.isDay) "Agora é dia" else "Agora é noite", color = Ink3, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

// --- Componentes -----------------------------------------------------------------------------

@Composable
private fun Card(title: String? = null, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0x1CFFFFFF), Glass)))
            .padding(16.dp),
    ) {
        title?.let { Text(it.uppercase(), color = Ink3, fontSize = 11.sp, letterSpacing = 1.1.sp, modifier = Modifier.padding(bottom = 10.dp)) }
        content()
    }
}

@Composable
private fun Tile(title: String, value: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Brush.verticalGradient(listOf(Color(0x1CFFFFFF), Glass)))
            .padding(14.dp).height(150.dp),
    ) {
        Text(title.uppercase(), color = Ink3, fontSize = 10.5.sp, letterSpacing = 1.sp)
        Text(value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        content()
    }
}

@Composable
private fun Gauge(fraction: Double, label: String, colors: List<Color>, @DrawableRes icon: Int? = null) {
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Color(0x1FFFFFFF), 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Brush.sweepGradient(colors), 135f, (270f * fraction.toFloat().coerceIn(0.02f, 1f)), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        if (icon != null) Image(painterResource(icon), null, Modifier.size(20.dp))
        else Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Compass(direction: Int?) {
    Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0x14FFFFFF)), contentAlignment = Alignment.Center) {
        Text("N", color = Ink3, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 3.dp))
        if (direction != null) {
            // A seta aponta para onde o vento vai (direção de origem + 180°).
            Image(painterResource(R.drawable.gl_arrow), null, Modifier.size(26.dp).rotate(direction + 180f))
        }
    }
}

@Composable
private fun IconButton(@DrawableRes icon: Int, cd: String, onClick: () -> Unit, spinning: Boolean = false) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color(0x1FFFFFFF)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (spinning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
        else Image(painterResource(icon), cd, Modifier.size(18.dp))
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0x26FFFFFF)).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) { Text(text, color = Color.White, fontSize = 14.sp) }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))
}

private fun hhmm(epoch: Long): String =
    DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault()).format(Instant.ofEpochMilli(epoch))

private fun period(a: WeatherAlert): String {
    val f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val out = DateTimeFormatter.ofPattern("dd/MM HH:mm")
    val s = runCatching { LocalDateTime.parse(a.start, f).format(out) }.getOrDefault(a.start)
    val e = runCatching { LocalDateTime.parse(a.end, f).format(out) }.getOrDefault(a.end)
    return "$s até $e"
}
