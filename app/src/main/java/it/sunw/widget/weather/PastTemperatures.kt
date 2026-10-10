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
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Hourly (and, where available, 15-minute) 2 m temperature of yesterday and today from Open-Meteo
 * (no key needed), in local time at the place. These are model values for the place, not a weather-station reading; the meteogram
 * shows them dimmed to the left of "now".
 */
class PastTemperatures(
    private val values: Map<LocalDateTime, Double>,
    /** The same temperatures every 15 minutes, when Open-Meteo gives them. */
    private val fine: Map<LocalDateTime, Double> = emptyMap(),
) {

    operator fun get(time: LocalDateTime): Double? = values[time]

    val isEmpty: Boolean get() = values.isEmpty()

    /** True when the 15-minute series is there. */
    val hasFine: Boolean get() = fine.isNotEmpty()

    /**
     * The last 24 hours up to [now], oldest first: every 15 minutes when available, otherwise
     * every hour (the current hour included).
     */
    fun last24h(now: LocalDateTime): List<Pair<LocalDateTime, Double>> {
        if (fine.isNotEmpty()) {
            val start = now.minusHours(24)
            return fine.entries.filter { !it.key.isBefore(start) && !it.key.isAfter(now) }
                .sortedBy { it.key }.map { it.key to it.value }
        }
        val end = now.truncatedTo(ChronoUnit.HOURS)
        val start = end.minusHours(24)
        return values.entries.filter { !it.key.isBefore(start) && !it.key.isAfter(end) }
            .sortedBy { it.key }.map { it.key to it.value }
    }

    companion object {
        private fun series(root: JSONObject, name: String): Map<LocalDateTime, Double> {
            val block = root.optJSONObject(name) ?: return emptyMap()
            val times = block.optJSONArray("time") ?: return emptyMap()
            val temps = block.optJSONArray("temperature_2m") ?: return emptyMap()
            val map = LinkedHashMap<LocalDateTime, Double>()
            for (i in 0 until minOf(times.length(), temps.length())) {
                if (temps.isNull(i)) continue
                map[LocalDateTime.parse(times.getString(i))] = temps.getDouble(i)
            }
            return map
        }

        /**
         * Parses an Open-Meteo response with `hourly=temperature_2m` (required) and
         * `minutely_15=temperature_2m` (optional) in the place's time zone; throws on anything else.
         */
        fun parse(json: String): PastTemperatures {
            val root = JSONObject(json)
            root.optString("reason").takeIf { root.optBoolean("error") && it.isNotEmpty() }?.let { throw IOException(it) }
            if (root.optJSONObject("hourly") == null) throw IOException("hourly missing")
            return PastTemperatures(series(root, "hourly"), series(root, "minutely_15"))
        }
    }
}

/** Downloads and caches the Open-Meteo temperatures like [WeatherRepository] does for MeteoBlue. */
class PastTemperatureRepository(context: Context) {

    private val cacheFile = File(context.cacheDir, "openmeteo.json")
    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)

    fun cached(place: Place): PastTemperatures? {
        if (!cacheFile.exists() || !matches(place)) return null
        return runCatching { PastTemperatures.parse(cacheFile.readText()) }.getOrNull()
    }

    fun isFresh(now: Instant = Instant.now()): Boolean =
        Duration.between(Instant.ofEpochMilli(prefs.getLong(KEY_FETCHED, 0)), now) < MAX_AGE

    /** Downloads on a background thread and reports on [callback] (any thread). */
    fun refresh(place: Place, callback: (Result<PastTemperatures>) -> Unit) {
        executor.execute {
            callback(runCatching {
                val json = download(place.latitude, place.longitude)
                val data = PastTemperatures.parse(json) // validate before caching
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

    private fun download(latitude: Double, longitude: Double): String {
        val url = URL(url(latitude, longitude))
        val connection = url.openConnection() as HttpURLConnection
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
        private const val KEY_FETCHED = "om_fetched"
        private const val KEY_LAT = "om_lat"
        private const val KEY_LON = "om_lon"
        private val executor = Executors.newSingleThreadExecutor()

        /** Yesterday and today at the place, in the place's own time zone. */
        fun url(latitude: Double, longitude: Double): String = String.format(
            Locale.ROOT,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&hourly=temperature_2m&minutely_15=temperature_2m&past_days=1&past_minutely_15=96" +
                "&forecast_days=1&timezone=auto",
            latitude, longitude,
        )
    }
}
