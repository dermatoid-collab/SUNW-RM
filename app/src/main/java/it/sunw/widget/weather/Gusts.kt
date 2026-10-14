package it.sunw.widget.weather

import android.content.Context
import it.sunw.widget.Place
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Hourly wind gusts (km/h at 10 m) for the next days from Open-Meteo, in local time at the place.
 * MeteoBlue's package has only the mean wind, so the day chart takes the gusts from here.
 */
class Gusts(private val values: Map<LocalDateTime, Double>) {

    operator fun get(time: LocalDateTime): Double? = values[time]

    val isEmpty: Boolean get() = values.isEmpty()

    companion object {
        /** Parses an Open-Meteo response with `hourly=wind_gusts_10m`; throws on anything else. */
        fun parse(json: String): Gusts {
            val root = JSONObject(json)
            root.optString("reason").takeIf { root.optBoolean("error") && it.isNotEmpty() }?.let { throw IOException(it) }
            val hourly = root.optJSONObject("hourly") ?: throw IOException("hourly missing")
            val times = hourly.optJSONArray("time") ?: throw IOException("time missing")
            val gusts = hourly.optJSONArray("wind_gusts_10m") ?: throw IOException("wind_gusts_10m missing")
            val map = LinkedHashMap<LocalDateTime, Double>()
            for (i in 0 until minOf(times.length(), gusts.length())) {
                if (gusts.isNull(i)) continue
                map[LocalDateTime.parse(times.getString(i))] = gusts.getDouble(i)
            }
            return Gusts(map)
        }
    }
}

/** Downloads and caches the Open-Meteo gusts like [PastTemperatureRepository] does for temperatures. */
class GustRepository(context: Context) {

    private val cacheFile = File(context.cacheDir, "gusts.json")
    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)

    fun cached(place: Place): Gusts? {
        if (!cacheFile.exists() || !matches(place)) return null
        return runCatching { Gusts.parse(cacheFile.readText()) }.getOrNull()
    }

    fun isFresh(now: Instant = Instant.now()): Boolean =
        Duration.between(Instant.ofEpochMilli(prefs.getLong(KEY_FETCHED, 0)), now) < MAX_AGE

    /** Downloads on a background thread and reports on [callback] (any thread). */
    fun refresh(place: Place, callback: (Result<Gusts>) -> Unit) {
        executor.execute {
            callback(runCatching {
                val json = download(url(place.latitude, place.longitude))
                val data = Gusts.parse(json) // validate before caching
                cacheFile.writeText(json)
                prefs.edit()
                    .putLong(KEY_FETCHED, System.currentTimeMillis())
                    .putLong(KEY_LAT, place.latitude.toRawBits())
                    .putLong(KEY_LON, place.longitude.toRawBits())
                    .apply()
                data
            })
        }
    }

    private fun matches(place: Place): Boolean {
        val lat = Double.fromBits(prefs.getLong(KEY_LAT, Double.NaN.toRawBits()))
        val lon = Double.fromBits(prefs.getLong(KEY_LON, Double.NaN.toRawBits()))
        return abs(lat - place.latitude) < SAME_PLACE_DEG && abs(lon - place.longitude) < SAME_PLACE_DEG
    }

    private fun download(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", "application/json")
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code")
            return body
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        val MAX_AGE: Duration = Duration.ofMinutes(30)
        private const val SAME_PLACE_DEG = 0.02 // ~2 km
        private const val KEY_FETCHED = "gust_fetched"
        private const val KEY_LAT = "gust_lat"
        private const val KEY_LON = "gust_lon"

        /** Where downloads run; tests swap it so nothing goes on the network. */
        @Volatile
        internal var executor: Executor = Executors.newSingleThreadExecutor()

        /** Today and the next days at the place, in the place's own time zone, in km/h. */
        fun url(latitude: Double, longitude: Double): String = String.format(
            Locale.ROOT,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&hourly=wind_gusts_10m&wind_speed_unit=kmh&forecast_days=8&timezone=auto",
            latitude, longitude,
        )
    }
}
