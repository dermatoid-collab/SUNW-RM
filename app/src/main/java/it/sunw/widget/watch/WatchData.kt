package it.sunw.widget.watch

import it.sunw.widget.Place
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

/**
 * What the weather watch reads from Open-Meteo for the place: hourly values (yesterday to the day after
 * tomorrow) and, where the service has them, the precipitation every 15 minutes for the next day. Times are
 * local to the place; any value can be missing. Precipitation, snow and gusts are the values of the hour (or
 * 15 minutes) that ends at the stamp.
 */
class WatchData(val hours: List<Hour>, val fine: List<Fine>, val utcOffsetSeconds: Int) {

    class Hour(
        val time: LocalDateTime,
        val temperature: Double?,
        val precipitation: Double?,
        /** Probability of precipitation, 0–100. */
        val probability: Double?,
        /** Snowfall in cm. */
        val snowfall: Double?,
        /** Wind gusts in km/h. */
        val gust: Double?,
        /** Visibility in metres. */
        val visibility: Double?,
        val humidity: Double?,
        /** WMO weather code. */
        val code: Int?,
        /** Convective available potential energy, J/kg: the fuel of thunderstorms (and of hail). */
        val cape: Double? = null,
        /** Height of the 0 °C level, in metres. */
        val freezingLevel: Double? = null,
    )

    /** Precipitation in mm over the 15 minutes ending at [time]. */
    class Fine(val time: LocalDateTime, val precipitation: Double?)

    private val byTime = hours.associateBy { it.time }

    fun hourAt(time: LocalDateTime): Hour? = byTime[time]

    /** The local time at the place at [now]. */
    fun localNow(now: Instant): LocalDateTime =
        LocalDateTime.ofEpochSecond(now.epochSecond, 0, ZoneOffset.ofTotalSeconds(utcOffsetSeconds))

    companion object {
        private const val HOURLY = "temperature_2m,precipitation,precipitation_probability,snowfall,wind_gusts_10m,visibility,relative_humidity_2m,weather_code,cape,freezing_level_height"

        fun url(latitude: Double, longitude: Double): String = String.format(
            Locale.ROOT,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&hourly=$HOURLY&minutely_15=precipitation" +
                "&wind_speed_unit=kmh&past_days=1&forecast_days=3&timezone=auto",
            latitude, longitude,
        )

        /** Parses an Open-Meteo response; throws on an error or when there are no hours. */
        fun parse(json: String): WatchData {
            val root = JSONObject(json)
            root.optString("reason").takeIf { root.optBoolean("error") && it.isNotEmpty() }?.let { throw IOException(it) }
            val hourly = root.optJSONObject("hourly") ?: throw IOException("hourly missing")
            val times = hourly.optJSONArray("time") ?: throw IOException("time missing")
            fun number(block: JSONObject, name: String, i: Int): Double? =
                block.optJSONArray(name)?.takeIf { i < it.length() && !it.isNull(i) }?.optDouble(i, Double.NaN)?.takeUnless { it.isNaN() }
            val hours = (0 until times.length()).map { i ->
                Hour(
                    time = LocalDateTime.parse(times.getString(i)),
                    temperature = number(hourly, "temperature_2m", i),
                    precipitation = number(hourly, "precipitation", i),
                    probability = number(hourly, "precipitation_probability", i),
                    snowfall = number(hourly, "snowfall", i),
                    gust = number(hourly, "wind_gusts_10m", i),
                    visibility = number(hourly, "visibility", i),
                    humidity = number(hourly, "relative_humidity_2m", i),
                    code = number(hourly, "weather_code", i)?.toInt(),
                    cape = number(hourly, "cape", i),
                    freezingLevel = number(hourly, "freezing_level_height", i),
                )
            }
            if (hours.isEmpty()) throw IOException("no hours")
            val minutely = root.optJSONObject("minutely_15")
            val fineTimes = minutely?.optJSONArray("time")
            val fine = if (minutely == null || fineTimes == null) emptyList() else (0 until fineTimes.length()).map { i ->
                Fine(LocalDateTime.parse(fineTimes.getString(i)), number(minutely, "precipitation", i))
            }
            return WatchData(hours, fine, root.optInt("utc_offset_seconds", 0))
        }

        /** Downloads and parses (blocking: call off the main thread). Short timeouts, so a broadcast can wait for it. */
        fun fetch(place: Place): WatchData {
            val connection = URL(url(place.latitude, place.longitude)).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            try {
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw IOException("HTTP $code")
                return parse(body)
            } finally {
                connection.disconnect()
            }
        }
    }
}
