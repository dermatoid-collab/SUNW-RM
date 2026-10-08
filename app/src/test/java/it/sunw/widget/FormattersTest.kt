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
}
