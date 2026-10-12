package it.sunw.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test
    fun countdown() {
        assertEquals("00:00:00", Formatters.countdown(0))
        assertEquals("00:00:59", Formatters.countdown(59))
        assertEquals("01:02:03", Formatters.countdown(3723))
        assertEquals("23:59:59", Formatters.countdown(86_399))
        assertEquals("00:00:00", Formatters.countdown(-5))
    }

    @Test
    fun gapsDropTheHoursWhenThereAreNone() {
        assertEquals("−2h 13m", Formatters.gap(java.time.Duration.ofMinutes(-133)))
        assertEquals("−6h 38m", Formatters.gap(java.time.Duration.ofMinutes(-398)))
        assertEquals("+7m", Formatters.gap(java.time.Duration.ofMinutes(7)))
        assertEquals("+1h 00m", Formatters.gap(java.time.Duration.ofMinutes(60)))
        assertEquals("+0m", Formatters.gap(java.time.Duration.ZERO))
    }
}
