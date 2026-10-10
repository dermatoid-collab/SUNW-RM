package it.sunw.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Year series checked against the same algorithms run in Python for Parma (10 Oct 2026). */
class YearDataTest {

    private val zone = ZoneId.of("Europe/Rome")
    private val today = LocalDate.of(2026, 10, 10)
    private val lat = 44.80
    private val lon = 10.33
    private val range = 182

    @Test
    fun sunSeriesMatchTheReferenceDay() {
        val y = YearData.sun(today, lat, lon, zone, range)
        assertEquals(2 * range + 1, y.dayMinutes.size)
        assertEquals(675.5, y.dayMinutes[range], 0.2)       // 11 h 15.5 m
        assertEquals(447.6, y.riseMinute[range], 0.2)       // 07:27:36
        assertEquals(1123.0, y.setMinute[range], 0.2)       // 18:43
        assertEquals(765.8, y.nightMinutes[range], 0.3)     // 12 h 46 m
        assertEquals(38.5, y.noonElevation[range], 0.2)
        // Longest day in June, shortest in December.
        assertEquals(935.5, y.dayMinutes.max(), 0.5)
        assertEquals(527.5, y.dayMinutes.min(), 0.5)
        // The clocks go back on 25 Oct 2026: sunrise jumps by an hour.
        val jump = y.riseMinute[range + 15] - y.riseMinute[range + 14]
        assertEquals(-60.0, jump, 4.0)
    }

    @Test
    fun nightIsTheRestOfTheDayWithinMinutes() {
        val y = YearData.sun(today, lat, lon, zone, range)
        for (i in listOf(0, 90, range, 300, 2 * range)) {
            assertEquals("day $i", 24 * 60.0, y.dayMinutes[i] + y.nightMinutes[i], 8.0)
        }
    }

    @Test
    fun moonDayStatsMatchTheReferenceDay() {
        val s = MoonCalculator.dayStats(today, lat, lon, zone)
        assertEquals(665.5, s.minutesAbove, 3.0)   // 11 h 05 m above the horizon
        assertEquals(35.3, s.maxAltitude, 0.5)
    }

    @Test
    fun moonYearRangesAndPhases() {
        val m = YearData.moon(today, lat, lon, zone, range)
        assertEquals(2 * range + 1, m.skyMinutes.size)
        // Parma, 2026: the Moon's highest point swings between about 16° and 73°.
        assertEquals(16.3, m.maxAltitude.min(), 1.0)
        assertEquals(73.1, m.maxAltitude.max(), 1.0)
        assertTrue(m.skyMinutes.min() in 400.0..460.0)
        assertTrue(m.skyMinutes.max() in 990.0..1040.0)
        // About 26 principal phases in a year, new moon on 10 Oct 2026 and full on 26 Oct.
        val newMoons = m.phases.filter { it.quarter == MoonCalculator.Quarter.NEW }
        val fullMoons = m.phases.filter { it.quarter == MoonCalculator.Quarter.FULL }
        assertEquals(13.0, newMoons.size.toDouble(), 1.0)
        assertEquals(13.0, fullMoons.size.toDouble(), 1.0)
        assertEquals(0.0, newMoons.first { it.offset > -1 }.offset, 1.0)
        assertEquals(16.0, fullMoons.first { it.offset > 0 }.offset, 1.0)
    }
}
