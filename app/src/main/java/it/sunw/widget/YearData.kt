package it.sunw.widget

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Day-by-day values for the Sun and Moon page's year charts, from [today] −[range] to +[range]
 * days (index = offset + range): what [SunCalculator] and [MoonCalculator] say for each day.
 * Missing values (polar day or night, no sunrise) are [Double.NaN].
 */
object YearData {

    class SunYear(
        /** Sunrise → sunset, minutes. */
        val dayMinutes: DoubleArray,
        /** Local clock time of sunrise and sunset, minutes after midnight (DST included). */
        val riseMinute: DoubleArray,
        val setMinute: DoubleArray,
        /** Sunset → next sunrise, minutes. */
        val nightMinutes: DoubleArray,
        /** The Sun's elevation at solar noon, degrees. */
        val noonElevation: DoubleArray,
    )

    class MoonYear(
        /** Minutes the Moon is above the horizon during the local day. */
        val skyMinutes: DoubleArray,
        /** The Moon's highest altitude that day, degrees. */
        val maxAltitude: DoubleArray,
        /** New and full moons (and quarters), as day offsets with the fraction of the day. */
        val phases: List<Phase>,
    ) {
        class Phase(val offset: Double, val quarter: MoonCalculator.Quarter)
    }

    fun sun(today: LocalDate, latitude: Double, longitude: Double, zone: ZoneId, range: Int): SunYear {
        val size = 2 * range + 1
        val days = (0..size).map { SunCalculator.day(today.plusDays((it - range).toLong()), latitude, longitude, zone) } // one extra day for the night
        val dayMinutes = DoubleArray(size)
        val rise = DoubleArray(size) { Double.NaN }
        val set = DoubleArray(size) { Double.NaN }
        val night = DoubleArray(size) { Double.NaN }
        val noon = DoubleArray(size)
        for (i in 0 until size) {
            val day = days[i]
            noon[i] = SunCalculator.elevation(day.solarNoon, latitude, longitude)
            when (day) {
                is SunCalculator.Day.Normal -> {
                    dayMinutes[i] = day.length.seconds / 60.0
                    rise[i] = minuteOfDay(day.sunrise, zone)
                    set[i] = minuteOfDay(day.sunset, zone)
                    (days[i + 1] as? SunCalculator.Day.Normal)?.let { night[i] = Duration.between(day.sunset, it.sunrise).seconds / 60.0 }
                }
                is SunCalculator.Day.PolarDay -> dayMinutes[i] = 24 * 60.0
                is SunCalculator.Day.PolarNight -> dayMinutes[i] = 0.0
            }
        }
        return SunYear(dayMinutes, rise, set, night, noon)
    }

    fun moon(today: LocalDate, latitude: Double, longitude: Double, zone: ZoneId, range: Int): MoonYear {
        val size = 2 * range + 1
        val sky = DoubleArray(size)
        val high = DoubleArray(size)
        for (i in 0 until size) {
            val stats = MoonCalculator.dayStats(today.plusDays((i - range).toLong()), latitude, longitude, zone)
            sky[i] = stats.minutesAbove
            high[i] = stats.maxAltitude
        }
        val first = today.minusDays(range.toLong()).atStartOfDay(zone).toInstant()
        val last = today.plusDays(range + 1L).atStartOfDay(zone).toInstant()
        val midnight = today.atStartOfDay(zone).toInstant()
        val phases = MoonCalculator.quartersBetween(first, last).map { (quarter, at) ->
            MoonYear.Phase(Duration.between(midnight, at).seconds / 86_400.0, quarter)
        }
        return MoonYear(sky, high, phases)
    }

    private fun minuteOfDay(instant: Instant, zone: ZoneId): Double {
        val t = ZonedDateTime.ofInstant(instant, zone)
        return t.hour * 60.0 + t.minute + t.second / 60.0
    }
}
