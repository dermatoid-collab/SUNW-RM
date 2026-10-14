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
import kotlin.math.roundToInt

/**
 * What Open-Meteo says for each hour of today and the next days, in local time at the place: the
 * values the day page shows for the hours already gone (MeteoBlue's forecast for those hours is
 * replaced) and the wind gusts (MeteoBlue's package has only the mean wind). Any value can be missing.
 */
class OpenMeteoHours(private val hours: Map<LocalDateTime, Sample>) {

    class Sample(
        val temperature: Double?,
        val feltTemperature: Double?,
        val precipitation: Double?,
        val windSpeed: Double?,
        val windDirection: Int?,
        val gust: Double?,
        /** WMO weather code (0 clear … 99 thunderstorm with hail). */
        val weatherCode: Int?,
    )

    operator fun get(time: LocalDateTime): Sample? = hours[time]

    val isEmpty: Boolean get() = hours.isEmpty()

    companion object {
        /** Parses an Open-Meteo response with the hourly variables of [VARIABLES]; throws on anything else. */
        fun parse(json: String): OpenMeteoHours {
            val root = JSONObject(json)
            root.optString("reason").takeIf { root.optBoolean("error") && it.isNotEmpty() }?.let { throw IOException(it) }
            val hourly = root.optJSONObject("hourly") ?: throw IOException("hourly missing")
            val times = hourly.optJSONArray("time") ?: throw IOException("time missing")
            fun number(name: String, i: Int): Double? =
                hourly.optJSONArray(name)?.takeIf { i < it.length() && !it.isNull(i) }?.optDouble(i, Double.NaN)?.takeUnless { it.isNaN() }
            val map = LinkedHashMap<LocalDateTime, Sample>()
            for (i in 0 until times.length()) {
                map[LocalDateTime.parse(times.getString(i))] = Sample(
                    temperature = number("temperature_2m", i),
                    feltTemperature = number("apparent_temperature", i),
                    precipitation = number("precipitation", i),
                    windSpeed = number("wind_speed_10m", i),
                    windDirection = number("wind_direction_10m", i)?.roundToInt(),
                    gust = number("wind_gusts_10m", i),
                    weatherCode = number("weather_code", i)?.roundToInt(),
                )
            }
            return OpenMeteoHours(map)
        }

        const val VARIABLES = "temperature_2m,apparent_temperature,precipitation,wind_speed_10m,wind_direction_10m,wind_gusts_10m,weather_code"
    }
}

/** Downloads and caches the Open-Meteo hours like [PastTemperatureRepository] does for temperatures. */
class OpenMeteoHoursRepository(context: Context) {

    private val cacheFile = File(context.cacheDir, "openmeteo_hours.json")
    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)

    fun cached(place: Place): OpenMeteoHours? {
        if (!cacheFile.exists() || !matches(place)) return null
        return runCatching { OpenMeteoHours.parse(cacheFile.readText()) }.getOrNull()
    }

    fun isFresh(now: Instant = Instant.now()): Boolean =
        Duration.between(Instant.ofEpochMilli(prefs.getLong(KEY_FETCHED, 0)), now) < MAX_AGE

    /** Downloads on a background thread and reports on [callback] (any thread). */
    fun refresh(place: Place, callback: (Result<OpenMeteoHours>) -> Unit) {
        executor.execute {
            callback(runCatching {
                val json = download(url(place.latitude, place.longitude))
                val data = OpenMeteoHours.parse(json) // validate before caching
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
        private const val KEY_FETCHED = "omh_fetched"
        private const val KEY_LAT = "omh_lat"
        private const val KEY_LON = "omh_lon"

        /** Where downloads run; tests swap it so nothing goes on the network. */
        @Volatile
        internal var executor: Executor = Executors.newSingleThreadExecutor()

        /** Today (from midnight) and the next days at the place, in the place's own time zone, speeds in km/h. */
        fun url(latitude: Double, longitude: Double): String = String.format(
            Locale.ROOT,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&hourly=${OpenMeteoHours.VARIABLES}&wind_speed_unit=kmh&forecast_days=8&timezone=auto",
            latitude, longitude,
        )
    }
}
