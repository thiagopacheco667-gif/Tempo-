package app.tempo.weather.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.tempo.weather.data.CityResult
import app.tempo.weather.data.LocationMode
import app.tempo.weather.data.OpenMeteoClient
import app.tempo.weather.data.WeatherStore
import app.tempo.weather.location.DeviceLocator
import app.tempo.weather.widget.RenderModel
import app.tempo.weather.widget.WeatherWidgetProvider
import app.tempo.weather.widget.WidgetRenderer
import app.tempo.weather.widget.WidgetSize
import app.tempo.weather.work.UpdateScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Navy950 = Color(0xFF040917)
private val Navy900 = Color(0xFF081430)
private val Glass = Color(0x14FFFFFF)
private val GlassEdge = Color(0x24FFFFFF)
private val Ink2 = Color(0xE0E8F5FF)
private val Ink3 = Color(0x99C9D6F2)
private val Accent = Color(0xFF8FC4FF)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Configuração é opcional: o widget sempre é adicionado, mesmo se o usuário voltar.
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        }

        UpdateScheduler.schedulePeriodic(this)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Navy950, surface = Navy900)) {
                SettingsScreen(isConfiguring = widgetId != AppWidgetManager.INVALID_APPWIDGET_ID, onDone = { finish() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Com o app em primeiro plano a localização pode ser obtida; atualiza se os dados estiverem velhos.
        val store = WeatherStore(this)
        val age = store.snapshot?.let { System.currentTimeMillis() - it.fetchedAt } ?: Long.MAX_VALUE
        if (age > 15 * 60_000L && !store.isRefreshing()) {
            store.refreshingSince = System.currentTimeMillis()
            WeatherWidgetProvider.renderAll(this)
            UpdateScheduler.refreshNow(this)
        }
    }
}

@Composable
private fun SettingsScreen(isConfiguring: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val store = remember { WeatherStore(context) }
    val locator = remember { DeviceLocator(context) }
    val scope = rememberCoroutineScope()

    // Recompõe quando os dados persistidos mudam (worker terminou, etc.).
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> version++ }
        store.registerListener(l)
        onDispose { store.unregisterListener(l) }
    }

    fun refresh() {
        store.refreshingSince = System.currentTimeMillis()
        WeatherWidgetProvider.renderAll(context)
        UpdateScheduler.refreshNow(context)
    }

    var hasPermission by remember { mutableStateOf(locator.hasPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = locator.hasPermission()
        if (hasPermission) {
            store.locationMode = LocationMode.AUTO
            refresh()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0D1F45), Navy950, Color(0xFF02040C)))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Tempo", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
            Text("Premium Atmospheric Weather", color = Ink3, fontSize = 13.sp, modifier = Modifier.padding(top = 0.dp))

            WidgetPreview(version)

            // ----- Status -----
            val snap = remember(version) { store.snapshot }
            val refreshing = remember(version) { store.isRefreshing() }
            val error = remember(version) { store.lastError }
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                refreshing -> "Atualizando…"
                                snap != null -> "${snap.place.name} · ${WidgetRenderer.deg(snap.current.temperature)}"
                                else -> "Sem dados ainda"
                            },
                            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                        )
                        val sub = when {
                            error != null && snap != null -> "Última tentativa falhou ($error). Exibindo os últimos dados válidos."
                            error != null -> error
                            snap != null -> "Dados reais da Open-Meteo"
                            else -> "Escolha a localização abaixo"
                        }
                        Text(sub, color = Ink3, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (refreshing) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = Accent, strokeWidth = 2.dp)
                    } else {
                        Button(
                            onClick = { refresh() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FFFFFF), contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) { Text("Atualizar") }
                    }
                }
            }

            // ----- Localização -----
            SectionTitle("Localização")
            val mode = remember(version) { store.locationMode }
            GlassCard {
                ToggleRow(
                    title = "Usar minha localização",
                    subtitle = when {
                        !hasPermission -> "Requer permissão de localização aproximada"
                        !locator.hasBackgroundPermission() -> "Ativa. Para acompanhar deslocamentos com o app fechado, permita \"o tempo todo\"."
                        else -> "Ativa, inclusive em segundo plano"
                    },
                    checked = mode == LocationMode.AUTO && hasPermission,
                    onChange = { on ->
                        if (on) {
                            if (hasPermission) {
                                store.locationMode = LocationMode.AUTO
                                refresh()
                            } else {
                                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
                            }
                        } else {
                            store.locationMode = LocationMode.MANUAL
                            if (store.manualPlace != null) refresh()
                        }
                    },
                )
                if (hasPermission && !locator.hasBackgroundPermission() && mode == LocationMode.AUTO) {
                    Text(
                        "Abrir configurações de localização",
                        color = Accent, fontSize = 13.sp,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clickable {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            },
                    )
                }
            }

            CitySearch(
                selected = remember(version) { store.manualPlace?.let { listOf(it.name, it.region).filter(String::isNotBlank).joinToString(" · ") } },
                manualActive = mode == LocationMode.MANUAL || !hasPermission,
                onPick = { city ->
                    store.manualPlace = city.toPlace()
                    store.locationMode = LocationMode.MANUAL
                    refresh()
                },
            )

            // ----- Preferências -----
            SectionTitle("Atualização automática")
            val interval = remember(version) { store.updateIntervalMinutes }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30 to "30 min", 60 to "1 h", 120 to "2 h", 180 to "3 h").forEach { (min, label) ->
                    Chip(label, selected = interval == min, modifier = Modifier.weight(1f)) {
                        store.updateIntervalMinutes = min
                        UpdateScheduler.schedulePeriodic(context, replace = true)
                        WeatherWidgetProvider.renderAll(context)
                    }
                }
            }

            GlassCard {
                ToggleRow(
                    title = "Animações atmosféricas",
                    subtitle = "Chuva, estrelas, névoa e brilho do sol. Desligam sozinhas no modo de economia de bateria.",
                    checked = remember(version) { store.animationsEnabled },
                    onChange = {
                        store.animationsEnabled = it
                        WeatherWidgetProvider.renderAll(context)
                    },
                )
            }

            if (isConfiguring) {
                Button(
                    onClick = {
                        WeatherWidgetProvider.renderAll(context)
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Navy950),
                ) { Text("Concluir", fontWeight = FontWeight.Medium) }
            }

            Text(
                "Dados meteorológicos: Open-Meteo.com (CC BY 4.0). Previsões são estimativas do modelo e podem variar.",
                color = Ink3, fontSize = 11.5.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        }
    }

    // Ao abrir pela primeira vez sem nenhum local: pede a localização uma única vez.
    LaunchedEffect(Unit) {
        if (!hasPermission && store.manualPlace == null && store.snapshot == null) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
}

@Composable
private fun WidgetPreview(version: Int) {
    val context = LocalContext.current
    var size by remember { mutableStateOf(WidgetSize.EXPANDED) }
    val (w, h) = when (size) {
        WidgetSize.COMPACT -> 170 to 170
        WidgetSize.MEDIUM, WidgetSize.MEDIUM_SHORT -> 340 to 175
        else -> 340 to 300
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.animateContentSize().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { FrameLayout(it) },
                update = { frame ->
                    val model = RenderModel.from(context)
                    val views = WidgetRenderer.buildSingle(context, size, model)
                    frame.removeAllViews()
                    frame.addView(views.apply(context, frame))
                    frame.tag = version
                },
                modifier = Modifier.width(w.dp).height(h.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(WidgetSize.COMPACT to "Compacta", WidgetSize.MEDIUM to "Média", WidgetSize.EXPANDED to "Expandida")
                .forEach { (s, label) -> Chip(label, selected = size == s) { size = s } }
        }
    }
}

@Composable
private fun CitySearch(selected: String?, manualActive: Boolean, onPick: (CityResult) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CityResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = emptyList(); error = null; return@LaunchedEffect
        }
        delay(350) // debounce
        loading = true
        try {
            results = withContext(Dispatchers.IO) { OpenMeteoClient.searchCity(query, "pt") }
            error = if (results.isEmpty()) "Nenhuma cidade encontrada" else null
        } catch (e: Exception) {
            results = emptyList()
            error = "Sem conexão para buscar cidades"
        } finally {
            loading = false
        }
    }

    GlassCard {
        Text("Escolher cidade", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(
            if (selected != null) "Selecionada: $selected" + if (manualActive) "" else " (inativa: usando GPS)" else "Busque pelo nome",
            color = Ink3, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Ex.: Recife, Curitiba, Lisboa", color = Ink3) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent, unfocusedBorderColor = GlassEdge,
                focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Accent,
            ),
            trailingIcon = { if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Accent) },
        )
        AnimatedVisibility(error != null) {
            Text(error.orEmpty(), color = Ink3, fontSize = 12.5.sp, modifier = Modifier.padding(top = 8.dp))
        }
        results.forEach { city ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onPick(city)
                        query = ""
                        results = emptyList()
                    }
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(city.name, color = Color.White, fontSize = 15.sp)
                    Text(listOfNotNull(city.admin1, city.country).joinToString(", "), color = Ink3, fontSize = 12.sp)
                }
                Text(
                    "%.2f°, %.2f°".format(city.latitude, city.longitude),
                    color = Ink3, fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0x1AFFFFFF), Glass)))
            .border(1.dp, GlassEdge, RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), color = Ink3, fontSize = 11.5.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Ink3, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Navy950),
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0x338FC4FF) else Glass)
            .border(1.dp, if (selected) Accent.copy(alpha = 0.6f) else GlassEdge, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Color.White else Ink2, fontSize = 13.sp)
    }
}
