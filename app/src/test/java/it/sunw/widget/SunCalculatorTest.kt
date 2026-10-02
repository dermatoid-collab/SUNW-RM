package it.sunw.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class SunCalculatorTest {

    private fun assertTime(expected: String, actual: Instant, zone: ZoneId, toleranceMin: Long = 1) {
        val local = actual.atZone(zone).toLocalTime()
        val diff = ChronoUnit.MINUTES.between(LocalTime.parse(expected), local)
        assertTrue("expected ~$expected, got $local", kotlin.math.abs(diff) <= toleranceMin)
    }

    private fun normal(date: String, lat: Double, lon: Double, zone: ZoneId) =
        SunCalculator.day(LocalDate.parse(date), lat, lon, zone) as SunCalculator.Day.Normal

    // Reference values from the NOAA Solar Calculator (gml.noaa.gov/grad/solcalc).

    @Test
    fun greenwichSummerSolstice() {
        val zone = ZoneId.of("Europe/London")
        val day = normal("2024-06-21", 51.4769, 0.0, zone)
        assertTime("04:43", day.sunrise, zone)
        assertTime("21:21", day.sunset, zone)
        assertTime("13:02", day.solarNoon, zone)
    }

    @Test
    fun newYorkWinterSolstice() {
        val zone = ZoneId.of("America/New_York")
        val day = normal("2024-12-21", 40.7128, -74.0060, zone)
        assertTime("07:17", day.sunrise, zone)
        assertTime("16:32", day.sunset, zone)
    }

    @Test
    fun southernHemisphereHasLongDaysInJanuary() {
        val zone = ZoneId.of("Australia/Sydney")
        val january = normal("2026-01-15", -33.8688, 151.2093, zone)
        val july = normal("2026-07-15", -33.8688, 151.2093, zone)
        assertTrue(january.length > july.length)
        assertTrue(january.length.toHours() >= 14)
    }

    @Test
    fun polarCases() {
        val zone = ZoneId.of("Europe/Oslo")
        val tromsoLat = 69.6496
        val tromsoLon = 18.9560
        assertTrue(SunCalculator.day(LocalDate.parse("2026-06-21"), tromsoLat, tromsoLon, zone) is SunCalculator.Day.PolarDay)
        assertTrue(SunCalculator.day(LocalDate.parse("2026-12-21"), tromsoLat, tromsoLon, zone) is SunCalculator.Day.PolarNight)
    }

    @Test
    fun elevationAtNoonMatchesGeometry() {
        // At an equinox solar noon, elevation ≈ 90° − latitude.
        val zone = ZoneId.of("Europe/Rome")
        val day = SunCalculator.day(LocalDate.parse("2026-03-20"), 44.8015, 10.3279, zone)
        val el = SunCalculator.elevation(day.solarNoon, 44.8015, 10.3279)
        assertEquals(45.2, el, 0.5)
    }
}
