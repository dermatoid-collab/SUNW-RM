package it.sunw.widget

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything the Sun and Moon page shows about the Sun on one date, from [SunCalculator]:
 * sunrise and sunset with their directions, twilights, blue and golden hours, noon height.
 */
class SunDayFacts(val date: LocalDate, val latitude: Double, val longitude: Double, val zone: ZoneId) {

    val day: SunCalculator.Day = SunCalculator.day(date, latitude, longitude, zone)
    val normal: SunCalculator.Day.Normal? get() = day as? SunCalculator.Day.Normal

    val civil = cross(CIVIL)
    val nautical = cross(NAUTICAL)
    val astronomical = cross(ASTRONOMICAL)

    /** Blue hour: −6° → −4°; golden hour: −4° → +6° (morning), mirrored in the evening. */
    private val blueGolden = cross(BLUE_GOLDEN)
    private val golden = cross(GOLDEN_TOP)
    val morningBlue = span(civil.morning, blueGolden.morning)
    val morningGolden = span(blueGolden.morning, golden.morning)
    val eveningGolden = span(golden.evening, blueGolden.evening)
    val eveningBlue = span(blueGolden.evening, civil.evening)

    /** "Useful light": civil dawn to civil dusk. */
    val usefulLight = span(civil.morning, civil.evening)

    val noonElevation: Double get() = SunCalculator.elevation(day.solarNoon, latitude, longitude)
    val sunriseAzimuth: Double? get() = normal?.let { SunCalculator.azimuth(it.sunrise, latitude, longitude) }
    val sunsetAzimuth: Double? get() = normal?.let { SunCalculator.azimuth(it.sunset, latitude, longitude) }

    /** Daylight (sunrise → sunset); 24 h for midnight sun, 0 for polar night. */
    val daylight: Duration get() = when (val d = day) {
        is SunCalculator.Day.Normal -> d.length
        is SunCalculator.Day.PolarDay -> Duration.ofHours(24)
        is SunCalculator.Day.PolarNight -> Duration.ZERO
    }

    data class Span(val start: Instant, val end: Instant) {
        val length: Duration get() = Duration.between(start, end)
    }

    private fun cross(altitude: Double) = SunCalculator.crossing(date, latitude, longitude, zone, altitude)
    private fun span(a: Instant?, b: Instant?) = if (a != null && b != null && b.isAfter(a)) Span(a, b) else null

    companion object {
        const val CIVIL = -6.0
        const val NAUTICAL = -12.0
        const val ASTRONOMICAL = -18.0
        const val BLUE_GOLDEN = -4.0
        const val GOLDEN_TOP = 6.0

        /** Daylight on [date] only, for curves over many days. */
        fun daylight(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): Duration =
            when (val d = SunCalculator.day(date, latitude, longitude, zone)) {
                is SunCalculator.Day.Normal -> d.length
                is SunCalculator.Day.PolarDay -> Duration.ofHours(24)
                is SunCalculator.Day.PolarNight -> Duration.ZERO
            }

        val POINTS = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")

        /** "101° ESE", with the 16 compass points in [points] (N first, clockwise). */
        fun direction(azimuth: Double, points: Array<String> = POINTS): String {
            val deg = Math.round(azimuth).toInt().mod(360)
            return "$deg° ${points[Math.round(azimuth / 22.5).toInt().mod(16)]}"
        }
    }
}
