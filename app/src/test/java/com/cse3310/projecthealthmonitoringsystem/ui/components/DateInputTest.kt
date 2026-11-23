package com.cse3310.projecthealthmonitoringsystem.ui.components

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DateInputTest {
    @Test fun julyTwentyEighth1960CanBeSelectedDirectlyAndTyped() {
        val chosen = calendarDate(1960, Calendar.JULY, 28)!!
        val typed = parseUsDate("07/28/1960")!!
        assertEquals(chosen, typed)
        val date = Calendar.getInstance().apply { timeInMillis = chosen }
        assertEquals(1960, date.get(Calendar.YEAR))
        assertEquals(Calendar.JULY, date.get(Calendar.MONTH))
        assertEquals(28, date.get(Calendar.DAY_OF_MONTH))
        assertEquals("07/28/1960", SimpleDateFormat("MM/dd/yyyy", Locale.US).format(Date(chosen)))
    }
    @Test fun malformedDatesAndInvalidLeapDaysAreRejected() {
        listOf("7/28/1960", "07/32/1960", "02/29/1900", "13/01/2026", "02/30/2026", "07/28/1960junk", "NaN").forEach {
            assertNull(it, parseUsDate(it))
        }
        assertNotNull(parseUsDate("02/29/2000"))
        assertNull(calendarDate(2026, Calendar.FEBRUARY, 29))
        assertNull(calendarDate(0, Calendar.JANUARY, 1))
    }
    @Test fun restrictionsCompareCivilDaysInsteadOfRejectingTimesOnTheSameDay() {
        val limit = parseUsDate("11/22/2026")!!
        val noon = Calendar.getInstance().apply { timeInMillis = limit; set(Calendar.HOUR_OF_DAY, 12) }.timeInMillis
        assertTrue(dateAllowed(limit, noon, noon))
        assertTrue(dateAllowed(noon, limit, limit))
        assertFalse(dateAllowed(parseUsDate("11/23/2026")!!, null, limit))
        assertFalse(dateAllowed(parseUsDate("11/21/2026")!!, limit, null))
    }
    @Test fun selectingAnewDatePreservesExistingAppointmentOrIntakeClock() {
        val previous = Calendar.getInstance().apply { clear(); set(2026, Calendar.NOVEMBER, 22, 10, 35, 45) }.timeInMillis
        val chosen = combineDateTime(parseUsDate("07/28/1960")!!, previous)
        val result = Calendar.getInstance().apply { timeInMillis = chosen }
        assertEquals(1960, result.get(Calendar.YEAR))
        assertEquals(Calendar.JULY, result.get(Calendar.MONTH))
        assertEquals(28, result.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, result.get(Calendar.HOUR_OF_DAY))
        assertEquals(35, result.get(Calendar.MINUTE))
        assertEquals(0, result.get(Calendar.SECOND))
        assertEquals(previous, Calendar.getInstance().apply { timeInMillis = previous }.timeInMillis)
    }
}
