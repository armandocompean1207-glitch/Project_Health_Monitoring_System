package com.cse3310.projecthealthmonitoringsystem.medication

import com.cse3310.projecthealthmonitoringsystem.data.entity.Medication
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class IndependentMedicationScheduleTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun at(day: Int, hour: Int = 0, minute: Int = 0, zone: TimeZone = utc, month: Int = Calendar.NOVEMBER) =
        Calendar.getInstance(zone).apply { clear(); set(2026, month, day, hour, minute) }.timeInMillis
    private fun prescription(times: List<Int>, start: Long = at(22), end: Long? = null) = Medication(
        medicationId = 1, userId = "fictional", name = "Fictional medicine", dosage = "1 tablet",
        frequency = Frequency.label(times.size), intakeTimeMinutes = times.min(),
        startDate = start, endDate = end, createdAt = start, updatedAt = start,
        doseTimesMinutes = MedicationTimes.encode(times))

    @Test fun independentTimesAreUsedExactlyForEachFrequency() {
        val choices = listOf(8 * 60 + 10, 9 * 60 + 5, 17 * 60 + 40, 22 * 60 + 15)
        for (count in 1..4) {
            val med = prescription(choices.take(count))
            val actual = scheduledIntakes(med, at(22), MedicationTimes.dayEnd(at(22), utc), utc)
            assertEquals(choices.take(count).map { at(22, it / 60, it % 60) }, actual)
            assertEquals(count, actual.size)
        }
        val med = prescription(choices)
        assertEquals(at(22, 9, 5), nextScheduledIntake(med, at(22, 8, 11), utc))
        assertEquals(at(23, 8, 10), nextScheduledIntake(med, at(22, 23), utc))
    }

    @Test fun datesBoundSchedulesAndEndDateIncludesTheEntireSelectedDay() {
        val end = MedicationTimes.dayEnd(at(24), utc)
        val med = prescription(listOf(0, 8 * 60 + 7, 23 * 60 + 59), at(23), end)
        val occurrences = scheduledIntakes(med, at(22), MedicationTimes.dayEnd(at(25), utc), utc)
        assertEquals(listOf(at(23), at(23, 8, 7), at(23, 23, 59), at(24), at(24, 8, 7), at(24, 23, 59)), occurrences)
        assertEquals(at(23), nextScheduledIntake(med, at(22, 12), utc))
        assertNull(nextScheduledIntake(med, at(24, 23, 59), utc))
        assertNull(nextScheduledIntake(med, at(25), utc))
        assertTrue(scheduledIntakes(med.copy(isActive = false), at(23), end, utc).isEmpty())
        assertNotNull(nextScheduledIntake(med.copy(endDate = null), at(25), utc))
    }

    @Test fun validationRejectsMissingDuplicateAndInvalidTimesAndReversedDates() {
        assertNotNull(MedicationTimes.error(listOf(480, null), 2, at(22), null, true))
        assertNotNull(MedicationTimes.error(listOf(480, 480), 2, at(22), null, true))
        assertNotNull(MedicationTimes.error(listOf(1440), 1, at(22), null, true))
        assertNotNull(MedicationTimes.error(listOf(480), 1, at(22), at(21), false))
        assertNotNull(MedicationTimes.error(listOf(480), 1, at(22), null, false))
        assertNull(MedicationTimes.error(listOf(480, 545), 2, at(22), null, true))
        assertNull(MedicationTimes.decode("480,480"))
        assertNull(MedicationTimes.decode("480,NaN"))
        assertEquals(listOf(480, 545), MedicationTimes.decode("545,480"))
    }

    @Test fun legacyRecordsRemainUnchangedAndRetainTheirExistingTimes() {
        val zone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(utc)
            val legacy = prescription(listOf(480, 1200)).copy(doseTimesMinutes = null)
            assertEquals(listOf(at(22, 8), at(22, 20)), scheduledIntakes(legacy, at(22), at(23) - 1))
            assertEquals(listOf(480, 1200), MedicationTimes.forEditing(legacy))
            assertNull(legacy.doseTimesMinutes)
            assertEquals(at(22, 20), nextScheduledIntake(legacy, at(22, 12)))
        } finally { TimeZone.setDefault(zone) }
    }

    @Test fun localTimesStayLocalAcrossDaylightSavingWithoutDuplicateDoses() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val start = at(7, zone = zone, month = Calendar.MARCH)
        val med = prescription(listOf(8 * 60 + 10, 20 * 60 + 45), start)
        val end = MedicationTimes.dayEnd(at(9, zone = zone, month = Calendar.MARCH), zone)
        val occurrences = scheduledIntakes(med, start, end, zone)
        assertEquals(6, occurrences.size)
        occurrences.forEach {
            val c = Calendar.getInstance(zone).apply { timeInMillis = it }
            assertTrue(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE) in listOf(490, 1245))
        }
        val gap = prescription(listOf(150), at(8, zone = zone, month = Calendar.MARCH))
        assertTrue(scheduledIntakes(gap, gap.startDate, MedicationTimes.dayEnd(gap.startDate, zone), zone).isEmpty())
        assertEquals(at(9, 2, 30, zone, Calendar.MARCH), nextScheduledIntake(gap, gap.startDate - 1, zone))
        val repeated = prescription(listOf(90), at(1, zone = zone))
        assertEquals(1, scheduledIntakes(repeated, repeated.startDate, MedicationTimes.dayEnd(repeated.startDate, zone), zone).size)
    }
}
