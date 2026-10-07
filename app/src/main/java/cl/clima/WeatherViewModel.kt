package cl.clima

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface UiState {
    data object Loading : UiState
    data object NoPermission : UiState
    data class Error(val message: String) : UiState
    data class Ready(val city: String, val forecast: Forecast) : UiState
}

class WeatherViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state

    /** Evita repetir la carga inicial al rotar la pantalla. */
    var started = false

    fun hasPermission() = hasLocationPermission(getApplication())

    fun permissionDenied() {
        _state.value = UiState.NoPermission
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                val ctx = getApplication<Application>()
                val loc = currentLocation(ctx)
                if (loc == null) {
                    UiState.Error("No se pudo obtener la ubicación. Intenta de nuevo al aire libre o con datos activos.")
                } else {
                    val forecast = fetchForecast(loc.latitude, loc.longitude)
                    UiState.Ready(cityName(ctx, loc), forecast)
                }
            } catch (e: LocationDisabledException) {
                UiState.Error("Activa la ubicación del teléfono y reintenta.")
            } catch (e: Exception) {
                UiState.Error("No se pudo obtener el pronóstico. Revisa la conexión.\n(${e.message})")
            }
        }
    }
}
