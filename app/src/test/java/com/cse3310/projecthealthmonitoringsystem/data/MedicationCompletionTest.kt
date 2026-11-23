package com.cse3310.projecthealthmonitoringsystem.data

import com.cse3310.projecthealthmonitoringsystem.medication.scheduledIntakes
import com.cse3310.projecthealthmonitoringsystem.monitoring.DoseTimes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MedicationCompletionTest : DbTest() {
    @Test fun deactivationKeepsPrescriptionAndFinalDose() = runBlocking<Unit> {
        val id = addMedication(A, "Demo")
        val doseId = medications.scheduleDose(A, id, NOON)!!
        assertTrue(medications.markDoseTaken(A, doseId))
        val original = medications.getDose(A, doseId)
        assertFalse(medications.deactivateMedication(B, id))
        assertTrue(medications.deactivateMedication(A, id))
        assertFalse(medications.getMedication(A, id)!!.isActive)
        assertEquals(original, medications.getDose(A, doseId))
        assertFalse(medications.markDoseMissed(A, doseId))
        assertEquals(1, medications.observeDoseLog(A, 0, Long.MAX_VALUE).first().size)
    }

    @Test fun deactivationKeepsPrescriptionEvenWithoutDoses() = runBlocking<Unit> {
        val id = addMedication(A, "Demo")
        assertTrue(medications.deactivateMedication(A, id))
        assertNotNull(medications.getMedication(A, id))
        assertTrue(medications.observeActiveMedications(A).first().isEmpty())
    }

    @Test fun scheduleHonorsStartEndAndDoesNotCreateDoseRecords() = runBlocking<Unit> {
        val id = addMedication(A, "Demo")
        val range = DoseTimes.todayRange(NOON)
        val med = medications.getMedication(A, id)!!.copy(startDate = range.first, frequency = "Twice a day")
        val times = scheduledIntakes(med, range.first, range.second)
        assertEquals(2, times.size)
        assertEquals(listOf(times.last()), scheduledIntakes(med.copy(startDate = times.first() + 1), range.first, range.second))
        assertEquals(listOf(times.first()), scheduledIntakes(med.copy(endDate = times.first()), range.first, range.second))
        assertTrue(scheduledIntakes(med.copy(isActive = false), range.first, range.second).isEmpty())
        assertTrue(medications.observeDoseLog(A, range.first, range.second).first().isEmpty())
    }
}
