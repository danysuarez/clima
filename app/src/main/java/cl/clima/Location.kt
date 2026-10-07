package cl.clima

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

fun hasLocationPermission(context: Context): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

class LocationDisabledException : Exception("La ubicación del teléfono está desactivada")

/**
 * Obtiene una ubicación actual. Para el clima basta la precisión de red,
 * así que se intenta primero NETWORK y luego GPS. Si ninguno responde,
 * se usa la última ubicación conocida.
 */
@SuppressLint("MissingPermission")
suspend fun currentLocation(context: Context): Location? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        .filter { lm.isProviderEnabled(it) }
    if (providers.isEmpty()) throw LocationDisabledException()

    for (provider in providers) {
        val loc = try {
            withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    LocationManagerCompat.getCurrentLocation(
                        lm, provider, signal, ContextCompat.getMainExecutor(context)
                    ) { location -> cont.resume(location) }
                }
            }
        } catch (e: SecurityException) {
            null // p. ej. GPS con solo permiso aproximado en Android < 12
        }
        if (loc != null) return loc
    }

    return providers
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
}

/** Nombre de la ciudad a partir de las coordenadas; si falla, las coordenadas. */
suspend fun cityName(context: Context, loc: Location): String = withContext(Dispatchers.IO) {
    val name = try {
        @Suppress("DEPRECATION")
        val address = Geocoder(context, Locale.forLanguageTag("es-CL"))
            .getFromLocation(loc.latitude, loc.longitude, 1)
            ?.firstOrNull()
        address?.locality ?: address?.subAdminArea ?: address?.adminArea
    } catch (e: Exception) {
        null
    }
    name ?: String.format(Locale.US, "%.3f, %.3f", loc.latitude, loc.longitude)
}
