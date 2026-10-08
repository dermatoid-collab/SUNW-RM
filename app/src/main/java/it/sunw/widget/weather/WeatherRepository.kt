package it.sunw.widget.weather

import android.content.Context
import it.sunw.widget.BuildConfig
import it.sunw.widget.Place
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Fetches the MeteoBlue forecast for a place and keeps the last response on disk, so the card
 * shows immediately (and offline) and the network is hit at most once per [MAX_AGE].
 */
class WeatherRepository(context: Context) {

    private val cacheFile = File(context.cacheDir, "meteoblue.json")
    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)

    val hasKey: Boolean get() = BuildConfig.METEOBLUE_API_KEY.isNotBlank()

    /** Cached forecast for [place], if it was fetched for (about) the same coordinates. */
    fun cached(place: Place): Forecast? {
        if (!cacheFile.exists() || !matches(place)) return null
        return try {
            Forecast.parse(cacheFile.readText(), Instant.ofEpochMilli(prefs.getLong(KEY_FETCHED, 0)))
        } catch (e: Exception) {
            null
        }
    }

    fun isFresh(forecast: Forecast, now: Instant = Instant.now()) =
        Duration.between(forecast.fetchedAt, now) < MAX_AGE

    /** Downloads a new forecast on a background thread and reports back on [callback] (any thread). */
    fun refresh(place: Place, callback: (Result<Forecast>) -> Unit) {
        executor.execute {
            callback(runCatching {
                val json = download(place.latitude, place.longitude)
                val now = Instant.now()
                val forecast = Forecast.parse(json, now) // validate before caching
                cacheFile.writeText(json)
                prefs.edit()
                    .putLong(KEY_FETCHED, now.toEpochMilli())
                    .putLong(KEY_LAT, place.latitude.toRawBits())
                    .putLong(KEY_LON, place.longitude.toRawBits())
                    .apply()
                forecast
            })
        }
    }

    private fun matches(place: Place): Boolean {
        val lat = Double.fromBits(prefs.getLong(KEY_LAT, Double.NaN.toRawBits()))
        val lon = Double.fromBits(prefs.getLong(KEY_LON, Double.NaN.toRawBits()))
        return abs(lat - place.latitude) < SAME_PLACE_DEG && abs(lon - place.longitude) < SAME_PLACE_DEG
    }

    private fun download(latitude: Double, longitude: Double): String {
        val url = URL(
            String.format(
                Locale.ROOT,
                "https://my.meteoblue.com/packages/basic-1h_basic-day?lat=%.4f&lon=%.4f&format=json" +
                    "&windspeed=kmh&temperature=C&apikey=%s",
                latitude, longitude, BuildConfig.METEOBLUE_API_KEY,
            ),
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", "application/json")
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                // MeteoBlue explains errors in a JSON body; surface that rather than the bare code.
                val reason = runCatching { org.json.JSONObject(body).optString("error_message") }.getOrNull()
                throw IOException(reason?.takeIf { it.isNotEmpty() } ?: "HTTP $code")
            }
            return body
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        val MAX_AGE: Duration = Duration.ofMinutes(60)
        private const val SAME_PLACE_DEG = 0.02 // ~2 km
        private const val KEY_FETCHED = "fetched"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private val executor = Executors.newSingleThreadExecutor()
    }
}
