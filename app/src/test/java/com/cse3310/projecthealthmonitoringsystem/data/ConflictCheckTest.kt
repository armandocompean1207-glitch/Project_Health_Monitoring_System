package com.cse3310.projecthealthmonitoringsystem.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictCheckTest : DbTest() {

    @Test
    fun candidateConflictsInEitherOrderIgnoringCaseAndSpaces() = runBlocking<Unit> {
        val warfarin = addMedication(A, "Warfarin")
        val hits = medications.detectConflict(A, "  ASPIRIN ")
        assertEquals(listOf(warfarin), hits.map { it.conflictingMedicationId })
        assertEquals("Increased bleeding risk", hits.single().description)

        val aspirin = addMedication(A, " aspirin")
        assertEquals(listOf(aspirin), medications.detectConflict(A, "warfarin").map { it.conflictingMedicationId })
        assertTrue(medications.detectConflict(A, "Metformin").isEmpty())
    }

    @Test
    fun editingAMedicationExcludesItself() = runBlocking<Unit> {
        val warfarin = addMedication(A, "Warfarin")
        assertTrue(medications.detectConflict(A, "Warfarin", excludeMedicationId = warfarin).isEmpty())
    }

    @Test
    fun conflictsAmongActiveMedications() = runBlocking<Unit> {
        addMedication(A, "Warfarin")
        addMedication(A, "Ibuprofen")
        addMedication(A, "Lithium")
        addMedication(A, "Metformin")

        val pairs = medications.detectConflicts(A)
            .map { setOf(it.medicationNameA, it.medicationNameB) }
            .toSet()
        assertEquals(setOf(setOf("Warfarin", "Ibuprofen"), setOf("Ibuprofen", "Lithium")), pairs)
    }

    @Test
    fun inactiveMedicationsAreIgnored() = runBlocking<Unit> {
        val warfarin = addMedication(A, "Warfarin")
        medications.scheduleDose(A, warfarin, NOON)
        medications.deleteMedication(A, warfarin) // deactivated
        addMedication(A, "Aspirin")
        assertTrue(medications.detectConflicts(A).isEmpty())
        assertTrue(medications.detectConflict(A, "Aspirin").isEmpty())
    }

    @Test
    fun otherUsersMedicationsNeverConflict() = runBlocking<Unit> {
        addMedication(B, "Warfarin")
        addMedication(A, "Aspirin")
        assertTrue(medications.detectConflicts(A).isEmpty())
        // Ibuprofen conflicts only with B's Warfarin, so A sees nothing
        assertTrue(medications.detectConflict(A, "Ibuprofen").isEmpty())
        // but Warfarin does conflict with A's own Aspirin
        assertEquals(1, medications.detectConflict(A, "Warfarin").size)
    }

    @Test
    fun editedRulesApplyWithNoCodeChange() = runBlocking<Unit> {
        val ruleId = maintenance.addConflictRule("Metformin", "Contrast Dye", "MEDIUM", "Kidney risk")
        assertNotNull(ruleId)
        assertNull(maintenance.addConflictRule("contrast dye", "METFORMIN", "LOW", "duplicate pair"))
        addMedication(A, "Contrast Dye")
        assertEquals(1, medications.detectConflict(A, "metformin").size)

        assertTrue(maintenance.deleteConflictRule(ruleId!!))
        assertTrue(medications.detectConflict(A, "metformin").isEmpty())
    }
}
