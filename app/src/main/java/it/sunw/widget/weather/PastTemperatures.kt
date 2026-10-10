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
 * Hourly 2 m temperature of yesterday and today from Open-Meteo (no key needed), in local time at
 * the place. These are model values for the place, not a weather-station reading; the meteogram
 * shows them dimmed to the left of "now".
 */
class PastTemperatures(private val values: Map<LocalDateTime, Double>) {

    data class Extreme(val time: LocalDateTime, val value: Double)

    operator fun get(time: LocalDateTime): Double? = values[time]

    val isEmpty: Boolean get() = values.isEmpty()

    /** Hours of the 24 hours up to the current one (included), oldest first. */
    fun last24h(now: LocalDateTime): List<Pair<LocalDateTime, Double>> {
        val end = now.truncatedTo(ChronoUnit.HOURS)
        val start = end.minusHours(24)
        return values.entries.filter { !it.key.isBefore(start) && !it.key.isAfter(end) }
            .sortedBy { it.key }.map { it.key to it.value }
    }

    /** Coldest and warmest hour of the last 24 hours (the earliest one when equal), or null without data. */
    fun extremes(now: LocalDateTime): Pair<Extreme, Extreme>? {
        val hours = last24h(now)
        if (hours.isEmpty()) return null
        val min = hours.minBy { it.second }
        val max = hours.maxBy { it.second }
        return Extreme(min.first, min.second) to Extreme(max.first, max.second)
    }

    companion object {
        /** Parses an Open-Meteo response with `hourly=temperature_2m&timezone=auto`; throws on anything else. */
        fun parse(json: String): PastTemperatures {
            val root = JSONObject(json)
            root.optString("reason").takeIf { root.optBoolean("error") && it.isNotEmpty() }?.let { throw IOException(it) }
            val hourly = root.optJSONObject("hourly") ?: throw IOException("hourly missing")
            val times = hourly.getJSONArray("time")
            val temps = hourly.getJSONArray("temperature_2m")
            val map = LinkedHashMap<LocalDateTime, Double>()
            for (i in 0 until minOf(times.length(), temps.length())) {
                if (temps.isNull(i)) continue
                map[LocalDateTime.parse(times.getString(i))] = temps.getDouble(i)
            }
            return PastTemperatures(map)
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
                "&hourly=temperature_2m&past_days=1&forecast_days=1&timezone=auto",
            latitude, longitude,
        )
    }
}
