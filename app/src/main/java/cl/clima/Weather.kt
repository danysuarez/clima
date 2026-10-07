package cl.clima

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Locale

data class CurrentWeather(
    val temperature: Double,
    val code: Int,
    val windKmh: Double,
    val humidity: Int,
)

data class DayForecast(
    val date: LocalDate,
    val code: Int,
    val max: Double,
    val min: Double,
    val rainProbability: Int?,
)

data class Forecast(val current: CurrentWeather, val days: List<DayForecast>)

/** Pronóstico de 7 días desde Open-Meteo (gratuito, sin clave de API). */
suspend fun fetchForecast(lat: Double, lon: Double): Forecast = withContext(Dispatchers.IO) {
    val url = URL(
        String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&timezone=auto&forecast_days=7",
            lat, lon
        )
    )
    val conn = url.openConnection() as HttpURLConnection
    conn.connectTimeout = 10_000
    conn.readTimeout = 10_000
    try {
        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("El servidor respondió ${conn.responseCode}")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        parse(JSONObject(body))
    } finally {
        conn.disconnect()
    }
}

private fun parse(json: JSONObject): Forecast {
    val c = json.getJSONObject("current")
    val current = CurrentWeather(
        temperature = c.getDouble("temperature_2m"),
        code = c.getInt("weather_code"),
        windKmh = c.getDouble("wind_speed_10m"),
        humidity = c.getInt("relative_humidity_2m"),
    )

    val d = json.getJSONObject("daily")
    val dates = d.getJSONArray("time")
    val codes = d.getJSONArray("weather_code")
    val maxs = d.getJSONArray("temperature_2m_max")
    val mins = d.getJSONArray("temperature_2m_min")
    val rain = d.getJSONArray("precipitation_probability_max")

    val days = (0 until dates.length()).map { i ->
        DayForecast(
            date = LocalDate.parse(dates.getString(i)),
            code = codes.getInt(i),
            max = maxs.getDouble(i),
            min = mins.getDouble(i),
            rainProbability = if (rain.isNull(i)) null else rain.getInt(i),
        )
    }
    return Forecast(current, days)
}

/** Códigos WMO de Open-Meteo → (ícono, descripción). */
fun describe(code: Int): Pair<String, String> = when (code) {
    0 -> "☀️" to "Despejado"
    1 -> "🌤️" to "Mayormente despejado"
    2 -> "⛅" to "Parcialmente nublado"
    3 -> "☁️" to "Nublado"
    45, 48 -> "🌫️" to "Niebla"
    51, 53, 55 -> "🌦️" to "Llovizna"
    56, 57 -> "🌧️" to "Llovizna helada"
    61, 63 -> "🌧️" to "Lluvia"
    65 -> "🌧️" to "Lluvia intensa"
    66, 67 -> "🌧️" to "Lluvia helada"
    71, 73, 75, 77 -> "🌨️" to "Nieve"
    80, 81, 82 -> "🌦️" to "Chubascos"
    85, 86 -> "🌨️" to "Chubascos de nieve"
    95 -> "⛈️" to "Tormenta"
    96, 99 -> "⛈️" to "Tormenta con granizo"
    else -> "❔" to "Sin datos"
}
