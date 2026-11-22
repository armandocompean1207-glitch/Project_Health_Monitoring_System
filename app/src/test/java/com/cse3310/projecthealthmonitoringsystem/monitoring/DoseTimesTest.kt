package com.cse3310.projecthealthmonitoringsystem.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DoseTimesTest {

    private fun at(hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 20, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun doseIsMissedOnlyAfterTheWindow() {
        val scheduled = at(8, 0)
        assertFalse(DoseTimes.isPastWindow(scheduled, 30, at(8, 29)))
        assertTrue(DoseTimes.isPastWindow(scheduled, 30, at(8, 30)))
    }

    @Test
    fun onceADayLaterTodayIsToday() {
        assertEquals(at(9, 0), DoseTimes.nextDoseTime(9, 0, 1, at(7, 0)))
    }

    @Test
    fun onceADayAlreadyPassedIsTomorrow() {
        assertEquals(at(9, 0) + DoseTimes.DAY_MILLIS, DoseTimes.nextDoseTime(9, 0, 1, at(10, 0)))
    }

    @Test
    fun twiceADayPicksTheEveningDose() {
        assertEquals(at(20, 0), DoseTimes.nextDoseTime(8, 0, 2, at(12, 0)))
    }

    @Test
    fun repeatSkipsDosesThatAlreadyPassed() {
        val twelveHours = DoseTimes.DAY_MILLIS / 2
        assertEquals(at(20, 0), DoseTimes.nextAfter(at(8, 0), twelveHours, at(13, 0)))
    }

    @Test
    fun invalidTimesAreRejected() {
        assertNull(DoseTimes.parseTime("25:00"))
        assertNull(DoseTimes.parseTime("8am"))
        assertEquals(8 to 30, DoseTimes.parseTime("08:30"))
    }
}
