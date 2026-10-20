package io.github.jameslunaa.monetra.core

import io.github.jameslunaa.monetra.core.time.Period
import io.github.jameslunaa.monetra.core.time.PeriodType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

class PeriodTest {
    private val oct16 = LocalDate.of(2026, 10, 16)

    @Test fun month() {
        val p = Period.containing(PeriodType.MONTH, oct16)
        assertEquals(LocalDate.of(2026, 10, 1), p.start)
        assertEquals(LocalDate.of(2026, 11, 1), p.endExclusive)
        assertEquals("October 2026", p.label(Locale.US))
        assertEquals("September 2026", p.previous().label(Locale.US))
        assertTrue(LocalDate.of(2026, 10, 31) in p)
        assertFalse(LocalDate.of(2026, 11, 1) in p)
    }

    @Test fun weekRespectsFirstDay() {
        val sun = Period.containing(PeriodType.WEEK, oct16, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 10, 11), sun.start)
        assertEquals("Oct 11 – 17, 2026", sun.label(Locale.US))
        val mon = Period.containing(PeriodType.WEEK, oct16, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 10, 12), mon.start)
        assertEquals("Oct 26 – Nov 1, 2026", Period.containing(PeriodType.WEEK, LocalDate.of(2026, 10, 30), DayOfWeek.MONDAY).label(Locale.US))
    }

    @Test fun multiMonthAlignment() {
        val q = Period.containing(PeriodType.QUARTER, oct16)
        assertEquals(LocalDate.of(2026, 10, 1), q.start)
        assertEquals("Oct – Dec 2026", q.label(Locale.US))
        val h = Period.containing(PeriodType.HALF_YEAR, oct16)
        assertEquals(LocalDate.of(2026, 7, 1), h.start)
        assertEquals("Jan – Jun 2027", h.next().label(Locale.US))
        assertEquals("2026", Period.containing(PeriodType.YEAR, oct16).label(Locale.US))
    }
}
