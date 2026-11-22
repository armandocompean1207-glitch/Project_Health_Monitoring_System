package com.cse3310.projecthealthmonitoringsystem.data

import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseStatus
import com.cse3310.projecthealthmonitoringsystem.data.repository.MedicationRepository.DeleteResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationRepositoryTest : DbTest() {

    @Test
    fun addAndListActiveAndAll() = runBlocking<Unit> {
        val aspirin = addMedication(A, "Aspirin")
        addMedication(A, "Metformin")
        medications.deleteMedication(A, aspirin) // no doses -> deleted
        val lisinopril = addMedication(A, "Lisinopril")
        medications.scheduleDose(A, lisinopril, NOON)
        medications.deleteMedication(A, lisinopril) // has doses -> deactivated

        assertEquals(listOf("Metformin"), medications.observeActiveMedications(A).first().map { it.name })
        assertEquals(listOf("Metformin", "Lisinopril"), medications.observeAllMedications(A).first().map { it.name })
        assertEquals(listOf("Metformin"), medications.getActiveMedications(A).map { it.name })
    }

    @Test
    fun addSetsTimestampsAndValidates() = runBlocking<Unit> {
        val id = addMedication(A, "Aspirin")
        val med = medications.getMedication(A, id)!!
        assertEquals(now, med.createdAt)
        assertEquals(now, med.updatedAt)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { medications.addMedication(A, medication(A, " ")) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { medications.addMedication(A, medication(A, "X").copy(intakeTimeMinutes = 24 * 60)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { medications.addMedication(B, medication(A, "X")) }
        }
    }

    @Test
    fun prescribingDoctorMustBelongToUser() = runBlocking<Unit> {
        val bDoctor = addDoctor(B)
        assertNull(medications.addMedication(A, medication(A, "Aspirin", doctorId = bDoctor)))
        val aDoctor = addDoctor(A)
        assertNotNull(medications.addMedication(A, medication(A, "Aspirin", doctorId = aDoctor)))
    }

    @Test
    fun deleteWithoutDosesDeletesWithDosesDeactivates() = runBlocking<Unit> {
        val noDoses = addMedication(A, "Aspirin")
        assertEquals(DeleteResult.DELETED, medications.deleteMedication(A, noDoses))
        assertNull(medications.getMedication(A, noDoses))

        val withDoses = addMedication(A, "Metformin")
        medications.scheduleDose(A, withDoses, NOON)
        now += HOUR
        assertEquals(DeleteResult.DEACTIVATED, medications.deleteMedication(A, withDoses))
        val med = medications.getMedication(A, withDoses)!!
        assertFalse(med.isActive)
        assertEquals(now, med.updatedAt)
        assertEquals(1, count("DoseLog", "medicationId = $withDoses"))

        assertEquals(DeleteResult.NOT_FOUND, medications.deleteMedication(B, withDoses))
    }

    @Test
    fun editWritesAuditLogForNameDosageAndIntakeTimeOnly() = runBlocking<Unit> {
        val id = addMedication(A, "Aspirin", "81 mg")
        val original = medications.getMedication(A, id)!!

        now += HOUR
        assertTrue(medications.editMedication(A, original.copy(frequency = "Twice daily", instructions = "With food")))
        assertTrue(medications.getAuditLog(A, id).isEmpty())

        now += HOUR
        val edited = medications.getMedication(A, id)!!
        assertTrue(medications.editMedication(A, edited.copy(name = "Aspirin EC", dosage = "325 mg", intakeTimeMinutes = 21 * 60)))

        val audit = medications.getAuditLog(A, id).associateBy { it.field }
        assertEquals(setOf("name", "dosage", "intakeTimeMinutes"), audit.keys)
        assertEquals("Aspirin", audit.getValue("name").oldValue)
        assertEquals("Aspirin EC", audit.getValue("name").newValue)
        assertEquals("81 mg", audit.getValue("dosage").oldValue)
        assertEquals("325 mg", audit.getValue("dosage").newValue)
        assertEquals("480", audit.getValue("intakeTimeMinutes").oldValue)
        assertEquals("1260", audit.getValue("intakeTimeMinutes").newValue)
        audit.values.forEach {
            assertEquals(now, it.changedAt)
            assertEquals(A, it.userId)
        }
        assertEquals(3, medications.observeAuditLog(A, id).first().size)
        assertTrue(medications.getAuditLog(B, id).isEmpty())
    }

    @Test
    fun scheduleDoseIsIdempotentAndOnlyForActiveOwnMedication() = runBlocking<Unit> {
        val id = addMedication(A, "Aspirin")
        val first = medications.scheduleDose(A, id, NOON)
        val again = medications.scheduleDose(A, id, NOON)
        assertNotNull(first)
        assertEquals(first, again)
        assertEquals(1, count("DoseLog"))

        assertNull(medications.scheduleDose(B, id, NOON + DAY))
        medications.deleteMedication(A, id) // deactivated
        assertNull(medications.scheduleDose(A, id, NOON + DAY))
    }

    @Test
    fun dosesByMedicationAndDateRange() = runBlocking<Unit> {
        val aspirin = addMedication(A, "Aspirin")
        val metformin = addMedication(A, "Metformin")
        for (d in 0..4) medications.scheduleDose(A, aspirin, NOON + d * DAY)
        medications.scheduleDose(A, metformin, NOON + DAY)

        val range = medications.observeDoses(A, aspirin, NOON + DAY, NOON + 3 * DAY).first()
        assertEquals(listOf(NOON + DAY, NOON + 2 * DAY, NOON + 3 * DAY), range.map { it.scheduledAt })

        val log = medications.observeDoseLog(A, NOON + DAY, NOON + DAY).first()
        assertEquals(setOf("Aspirin", "Metformin"), log.map { it.medicationName }.toSet())
        assertTrue(medications.observeDoses(B, aspirin, 0, Long.MAX_VALUE).first().isEmpty())
    }

    @Test
    fun markDoseTakenOnlyOnce() = runBlocking<Unit> {
        val id = addMedication(A, "Aspirin")
        val dose = medications.scheduleDose(A, id, NOON)!!
        assertFalse(medications.markDoseTaken(B, dose))
        now += 5 * MINUTE
        assertTrue(medications.markDoseTaken(A, dose))
        assertFalse(medications.markDoseTaken(A, dose))
        assertFalse(medications.markDoseMissed(A, dose))
        val stored = medications.getDose(A, dose)!!
        assertEquals(DoseStatus.TAKEN, stored.status)
        assertEquals(now, stored.recordedAt)
    }

    @Test
    fun missedDoseQueryUsesWindow() = runBlocking<Unit> {
        val id = addMedication(A, "Aspirin")
        val window = 30 * MINUTE
        val due = medications.scheduleDose(A, id, NOON)!!
        medications.scheduleDose(A, id, NOON + HOUR)
        val taken = medications.scheduleDose(A, id, NOON - HOUR)!!
        medications.markDoseTaken(A, taken)

        assertTrue(medications.getOverdueDoses(A, NOON + window - 1, window).isEmpty())
        val overdue = medications.getOverdueDoses(A, NOON + window, window)
        assertEquals(listOf(due), overdue.map { it.dose.doseId })
        assertEquals("Aspirin", overdue.single().medicationName)
        assertTrue(medications.getOverdueDoses(B, NOON + DAY, window).isEmpty())
    }
}
