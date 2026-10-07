package cl.clima

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClimaTheme {
                Surface(Modifier.fillMaxSize()) { ClimaScreen() }
            }
        }
    }
}

@Composable
fun ClimaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

@Composable
fun ClimaScreen(vm: WeatherViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) vm.load() else vm.permissionDenied()
    }

    LaunchedEffect(Unit) {
        if (!vm.started) {
            vm.started = true
            if (vm.hasPermission()) vm.load() else launcher.launch(LOCATION_PERMISSIONS)
        }
    }

    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        when (val s = state) {
            UiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            UiState.NoPermission -> Message(
                text = "La app necesita tu ubicación para mostrar el clima de tu ciudad.\n" +
                    "Si rechazaste el permiso definitivamente, actívalo en Ajustes › Apps › Clima.",
                action = "Dar permiso",
                onAction = { launcher.launch(LOCATION_PERMISSIONS) },
            )

            is UiState.Error -> Message(s.message, "Reintentar", vm::load)

            is UiState.Ready -> ForecastView(s, onRefresh = vm::load)
        }
    }
}

@Composable
private fun Message(text: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun ForecastView(s: UiState.Ready, onRefresh: () -> Unit) {
    val current = s.forecast.current
    val (icon, desc) = describe(current.code)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.city,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRefresh) { Text("Actualizar") }
            }
        }

        item {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(icon, fontSize = 64.sp)
                Text(
                    "${current.temperature.roundToInt()}°",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Light,
                )
                Text(desc, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Humedad ${current.humidity}% · Viento ${current.windKmh.roundToInt()} km/h",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        items(s.forecast.days) { DayRow(it) }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEEE d", Locale.forLanguageTag("es-CL"))

private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Hoy"
        today.plusDays(1) -> "Mañana"
        else -> date.format(dayFormat).replaceFirstChar { it.uppercase() }
    }
}

@Composable
private fun DayRow(day: DayForecast) {
    val (icon, desc) = describe(day.code)
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(dayLabel(day.date), style = MaterialTheme.typography.titleMedium)
                val rain = day.rainProbability?.let { " · lluvia $it%" } ?: ""
                Text(desc + rain, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "${day.max.roundToInt()}° / ${day.min.roundToInt()}°",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
