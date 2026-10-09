package it.sunw.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SunDayFactsTest {

    private val rome = ZoneId.of("Europe/Rome")

    @Test
    fun hoursFollowEachOtherAroundSunriseAndSunset() {
        val f = SunDayFacts(LocalDate.parse("2026-10-09"), 44.8015, 10.3279, rome)
        val day = f.normal!!
        val blue = f.morningBlue!!
        val golden = f.morningGolden!!
        assertEquals(f.civil.morning, blue.start)
        assertEquals(blue.end, golden.start)
        assertTrue(golden.start.isBefore(day.sunrise) && golden.end.isAfter(day.sunrise))
        assertTrue(f.eveningGolden!!.start.isBefore(day.sunset) && f.eveningGolden!!.end.isAfter(day.sunset))
        assertEquals(f.eveningGolden!!.end, f.eveningBlue!!.start)
        assertEquals(f.civil.evening, f.eveningBlue!!.end)
        assertTrue(f.usefulLight!!.length > day.length)
        // Mid-October in Parma: sunrise a little south of east, sunset a little south of west.
        assertTrue(f.sunriseAzimuth!! in 95.0..110.0)
        assertTrue(f.sunsetAzimuth!! in 250.0..265.0)
        // 90° − latitude + declination (≈ −6.4° on 9 October).
        assertEquals(38.8, f.noonElevation, 0.5)
    }

    @Test
    fun polarDayHasNoSunriseButFullDaylight() {
        val f = SunDayFacts(LocalDate.parse("2026-06-21"), 69.6496, 18.9560, ZoneId.of("Europe/Oslo"))
        assertNull(f.normal)
        assertNull(f.sunriseAzimuth)
        assertNull(f.morningBlue)
        assertEquals(24L, f.daylight.toHours())
    }

    @Test
    fun directionNamesTheNearestOfSixteenPoints() {
        assertEquals("101° E", SunDayFacts.direction(101.0))
        assertEquals("104° ESE", SunDayFacts.direction(104.0))
        assertEquals("0° N", SunDayFacts.direction(359.8))
        assertEquals("259° W", SunDayFacts.direction(259.0))
    }
}
