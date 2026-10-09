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

    @Test
    fun azimuthPointsEastAtEquinoxSunriseAndSouthAtNoon() {
        val zone = ZoneId.of("Europe/Rome")
        val day = normal("2026-03-20", 44.8015, 10.3279, zone)
        assertEquals(90.0, SunCalculator.azimuth(day.sunrise, 44.8015, 10.3279), 2.0)
        assertEquals(180.0, SunCalculator.azimuth(day.solarNoon, 44.8015, 10.3279), 0.5)
        assertEquals(270.0, SunCalculator.azimuth(day.sunset, 44.8015, 10.3279), 2.0)
        // Summer: rises well north of east (Parma, June: cos Az ≈ sin δ / cos φ → about 55°).
        val june = normal("2026-06-21", 44.8015, 10.3279, zone)
        assertEquals(55.0, SunCalculator.azimuth(june.sunrise, 44.8015, 10.3279), 2.0)
    }

    @Test
    fun crossingsMatchTheirAltitudeAndOrder() {
        val zone = ZoneId.of("Europe/Rome")
        val date = LocalDate.parse("2026-10-09")
        val lat = 44.8015
        val lon = 10.3279
        val day = SunCalculator.day(date, lat, lon, zone) as SunCalculator.Day.Normal
        val horizon = SunCalculator.crossing(date, lat, lon, zone, SunCalculator.HORIZON_DEG)
        assertEquals(day.sunrise.epochSecond.toDouble(), horizon.morning!!.epochSecond.toDouble(), 5.0)
        val altitudes = listOf(-18.0, -12.0, -6.0, -4.0, 6.0)
        for (alt in altitudes) {
            val c = SunCalculator.crossing(date, lat, lon, zone, alt)
            assertEquals(alt, SunCalculator.elevation(c.morning!!, lat, lon), 0.05)
            assertEquals(alt, SunCalculator.elevation(c.evening!!, lat, lon), 0.05)
        }
        val mornings = altitudes.map { SunCalculator.crossing(date, lat, lon, zone, it).morning!! }
        assertEquals(mornings.sorted(), mornings)
        // Civil twilight lasts about half an hour at this latitude in October.
        val civil = SunCalculator.crossing(date, lat, lon, zone, -6.0)
        val minutes = java.time.Duration.between(civil.morning, day.sunrise).toMinutes()
        assertTrue("civil twilight $minutes min", minutes in 26..36)
    }

    @Test
    fun noAstronomicalNightInLondonAtMidsummer() {
        val zone = ZoneId.of("Europe/London")
        val astro = SunCalculator.crossing(LocalDate.parse("2024-06-21"), 51.4769, 0.0, zone, -18.0)
        assertEquals(null, astro.morning)
        assertEquals(null, astro.evening)
        val nautical = SunCalculator.crossing(LocalDate.parse("2024-06-21"), 51.4769, 0.0, zone, -12.0)
        assertTrue(nautical.morning != null && nautical.evening != null)
    }

    @Test
    fun seasons2026() {
        // USNO: equinoxes and solstices of 2026 (UTC).
        val from = Instant.parse("2026-01-01T00:00:00Z")
        fun check(expected: String, season: SunCalculator.Season) {
            val diff = ChronoUnit.MINUTES.between(Instant.parse(expected), SunCalculator.nextSeason(season, from))
            assertTrue("$season off by $diff min", kotlin.math.abs(diff) <= 15)
        }
        check("2026-03-20T14:46:00Z", SunCalculator.Season.MARCH_EQUINOX)
        check("2026-06-21T08:24:00Z", SunCalculator.Season.JUNE_SOLSTICE)
        check("2026-09-23T00:05:00Z", SunCalculator.Season.SEPTEMBER_EQUINOX)
        check("2026-12-21T20:50:00Z", SunCalculator.Season.DECEMBER_SOLSTICE)
    }
}
