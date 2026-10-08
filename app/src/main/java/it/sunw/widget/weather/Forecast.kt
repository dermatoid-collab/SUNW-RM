package it.sunw.widget.weather

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** What the weather card shows, parsed from MeteoBlue's basic-1h_basic-day package. */
data class Forecast(
    val latitude: Double,
    val longitude: Double,
    /** Model run used, as reported by MeteoBlue (UTC). */
    val modelRun: String,
    /** Offset of the location's local time from UTC, in hours (MeteoBlue times are local). */
    val utcOffsetHours: Double,
    val fetchedAt: Instant,
    val hours: List<Hour>,
    val days: List<Day>,
) {
    data class Hour(
        /** Local time at the forecast location. */
        val time: LocalDateTime,
        val temperature: Double,
        val feltTemperature: Double,
        val precipitation: Double,
        val precipitationProbability: Int,
        val windSpeed: Double,
        val windDirection: Int,
        val condition: Condition,
        val uvIndex: Int,
        val isDaylight: Boolean,
    )

    data class Day(
        val date: LocalDate,
        val condition: Condition,
        val temperatureMax: Double,
        val temperatureMin: Double,
        val precipitation: Double,
        val precipitationProbability: Int,
        val windSpeedMax: Double,
        val windDirection: Int,
        val uvIndex: Int,
    )

    /** Local date-time at the forecast location for an instant. */
    fun localTime(instant: Instant): LocalDateTime =
        LocalDateTime.ofEpochSecond(instant.epochSecond + (utcOffsetHours * 3600).toLong(), 0, java.time.ZoneOffset.UTC)

    /** The hour slot containing [now] (local time at the location), or the closest one. */
    fun hourAt(now: LocalDateTime): Hour? =
        hours.lastOrNull { !it.time.isAfter(now) } ?: hours.firstOrNull()

    /** Night icon for [date]: the most significant condition between 21:00 and 05:00. */
    fun nightCondition(date: LocalDate): Condition? {
        val from = date.atTime(21, 0)
        val to = date.plusDays(1).atTime(5, 0)
        return hours.filter { !it.time.isBefore(from) && !it.time.isAfter(to) }
            .map { it.condition }
            .maxByOrNull { it.severity }
    }

    companion object {
        private val HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        /** Parses a MeteoBlue JSON response; throws [ForecastException] for API errors. */
        fun parse(json: String, fetchedAt: Instant): Forecast {
            val root = JSONObject(json)
            root.optString("error_message").takeIf { it.isNotEmpty() }?.let { throw ForecastException(it) }
            val meta = root.optJSONObject("metadata") ?: JSONObject()
            val h = root.optJSONObject("data_1h") ?: throw ForecastException("data_1h missing")
            val d = root.optJSONObject("data_day") ?: throw ForecastException("data_day missing")

            val hourTimes = h.getJSONArray("time")
            val hours = (0 until hourTimes.length()).map { i ->
                Hour(
                    time = LocalDateTime.parse(hourTimes.getString(i), HOUR_FORMAT),
                    temperature = h.getJSONArray("temperature").optDouble(i, 0.0),
                    feltTemperature = h.optJSONArray("felttemperature")?.optDouble(i, Double.NaN)
                        ?.takeUnless { it.isNaN() } ?: h.getJSONArray("temperature").optDouble(i, 0.0),
                    precipitation = h.optJSONArray("precipitation")?.optDouble(i, 0.0) ?: 0.0,
                    precipitationProbability = h.optJSONArray("precipitation_probability")?.optInt(i, 0) ?: 0,
                    windSpeed = h.optJSONArray("windspeed")?.optDouble(i, 0.0) ?: 0.0,
                    windDirection = h.optJSONArray("winddirection")?.optInt(i, 0) ?: 0,
                    condition = Condition.fromHourlyPictocode(h.getJSONArray("pictocode").optInt(i, 1)),
                    uvIndex = h.optJSONArray("uvindex")?.optInt(i, 0) ?: 0,
                    isDaylight = h.optJSONArray("isdaylight")?.optInt(i, 1) != 0,
                )
            }

            val dayTimes = d.getJSONArray("time")
            val days = (0 until dayTimes.length()).map { i ->
                Day(
                    date = LocalDate.parse(dayTimes.getString(i)),
                    condition = Condition.fromDailyPictocode(d.getJSONArray("pictocode").optInt(i, 1)),
                    temperatureMax = d.getJSONArray("temperature_max").optDouble(i, 0.0),
                    temperatureMin = d.getJSONArray("temperature_min").optDouble(i, 0.0),
                    precipitation = d.optJSONArray("precipitation")?.optDouble(i, 0.0) ?: 0.0,
                    precipitationProbability = d.optJSONArray("precipitation_probability")?.optInt(i, 0) ?: 0,
                    windSpeedMax = d.optJSONArray("windspeed_max")?.optDouble(i, 0.0) ?: 0.0,
                    windDirection = d.optJSONArray("winddirection")?.optInt(i, 0) ?: 0,
                    uvIndex = d.optJSONArray("uvindex")?.optInt(i, 0) ?: 0,
                )
            }
            return Forecast(
                latitude = meta.optDouble("latitude", Double.NaN),
                longitude = meta.optDouble("longitude", Double.NaN),
                modelRun = meta.optString("modelrun_utc"),
                utcOffsetHours = meta.optDouble("utc_timeoffset", 0.0),
                fetchedAt = fetchedAt,
                hours = hours,
                days = days,
            )
        }
    }
}

class ForecastException(message: String) : Exception(message)
