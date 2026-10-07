package it.sunw.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Reference values from PyEphem 4.x (standard refraction for rise/set). */
class MoonCalculatorTest {

    @Test
    fun illuminationMatchesEphem() {
        val cases = mapOf(
            "2026-10-07T18:33:00Z" to 9.39,
            "2026-10-18T16:00:00Z" to 50.04,
            "2026-10-26T04:00:00Z" to 99.84,
            "2027-03-14T20:00:00Z" to 40.57,
            "2025-12-25T09:00:00Z" to 25.39,
        )
        for ((t, expected) in cases) {
            assertEquals(t, expected, MoonCalculator.phase(Instant.parse(t)).illumination * 100, 0.3)
        }
    }

    @Test
    fun phaseNames() {
        val waningCrescent = MoonCalculator.phase(Instant.parse("2026-10-07T18:33:00Z"))
        assertEquals(MoonCalculator.Name.WANING_CRESCENT, waningCrescent.name)
        assertTrue(!waningCrescent.waxing)
        assertEquals(MoonCalculator.Name.FIRST_QUARTER, MoonCalculator.phase(Instant.parse("2026-10-18T16:00:00Z")).name)
        assertEquals(MoonCalculator.Name.FULL, MoonCalculator.phase(Instant.parse("2026-10-26T04:00:00Z")).name)
    }

    private fun assertLocal(expected: String, actual: Instant?, zone: ZoneId) {
        assertNotNull(actual)
        val diff = ChronoUnit.MINUTES.between(LocalTime.parse(expected), actual!!.atZone(zone).toLocalTime())
        assertTrue("expected ~$expected, got ${actual.atZone(zone).toLocalTime()}", abs(diff) <= 2)
    }

    @Test
    fun riseAndSet() {
        val rome = ZoneId.of("Europe/Rome")
        MoonCalculator.riseSet(LocalDate.parse("2026-10-07"), 44.8015, 10.3279, rome).let {
            assertLocal("03:34", it.rise, rome)
            assertLocal("17:20", it.set, rome)
        }
        val ny = ZoneId.of("America/New_York")
        MoonCalculator.riseSet(LocalDate.parse("2027-02-03"), 40.7128, -74.006, ny).let {
            assertLocal("05:26", it.rise, ny)
            assertLocal("14:22", it.set, ny)
        }
        val sydney = ZoneId.of("Australia/Sydney")
        MoonCalculator.riseSet(LocalDate.parse("2026-10-20"), -33.8688, 151.2093, sydney).let {
            assertLocal("13:10", it.rise, sydney)
            assertLocal("02:39", it.set, sydney)
        }
    }

    @Test
    fun nextQuarters() {
        val from = Instant.parse("2026-10-07T00:00:00Z")
        val expected = listOf(
            MoonCalculator.Quarter.NEW to "2026-10-10T15:50:00Z",
            MoonCalculator.Quarter.FIRST_QUARTER to "2026-10-18T16:12:00Z",
            MoonCalculator.Quarter.FULL to "2026-10-26T04:11:00Z",
            MoonCalculator.Quarter.LAST_QUARTER to "2026-11-01T20:28:00Z",
        )
        val actual = MoonCalculator.nextQuarters(from)
        assertEquals(expected.map { it.first }, actual.map { it.first })
        for ((e, a) in expected.zip(actual)) {
            val minutes = abs(Duration.between(Instant.parse(e.second), a.second).toMinutes())
            assertTrue("${e.first}: ${a.second}", minutes <= 15)
        }
    }
}
