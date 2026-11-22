package com.cse3310.projecthealthmonitoringsystem.data

import com.cse3310.projecthealthmonitoringsystem.data.entity.DoctorVisit
import com.cse3310.projecthealthmonitoringsystem.data.repository.SearchRepository.SearchCategory
import com.cse3310.projecthealthmonitoringsystem.maintenance.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MaintenanceIntegrationTest : DbTest() {
    @Test
    fun appointmentEditsPropagateToHomeFlowAndSearchWithoutLeaking() = runBlocking<Unit> {
        val doctor = addDoctor(A, "Dr. Fictional")
        val id = requireNotNull(maintenance.addDoctorVisit(A, DoctorVisit(userId = A, doctorId = doctor,
            visitAt = NOON + DAY, reason = "Annual checkup", notes = "Fictional verification")))
        assertEquals(id, maintenance.observeDoctorVisits(A).first().single().visit.visitId)
        assertEquals(id, search.nextDoctorVisit(A)!!.visit.visitId)
        assertEquals(id, search.search(A, SearchCategory.DOCTORS_AND_VISITS, "Annual").single().itemId)
        assertTrue(maintenance.observeDoctorVisits(B).first().isEmpty())
        assertNull(maintenance.getDoctorVisit(B, id))
        assertFalse(maintenance.deleteDoctorVisit(B, id))
        assertFalse(maintenance.editDoctorVisit(B, maintenance.getDoctorVisit(A, id)!!.copy(userId = B)))
        assertTrue(maintenance.editDoctorVisit(A, maintenance.getDoctorVisit(A, id)!!.copy(visitAt = NOON - DAY, reason = "Completed checkup")))
        assertNull(search.nextDoctorVisit(A))
        assertEquals(id, search.lastDoctorVisit(A)!!.visit.visitId)
        assertEquals("Completed checkup", maintenance.observeDoctorVisits(A).first().single().visit.reason)
        assertTrue(maintenance.deleteDoctorVisit(A, id))
        assertTrue(maintenance.observeDoctorVisits(A).first().isEmpty())
        assertTrue(search.search(A, SearchCategory.DOCTORS_AND_VISITS, "Completed").isEmpty())
    }

    @Test
    fun annualDueDateIsPrivateAndIndependentOfVisitHistory() = runBlocking<Unit> {
        val profile = auth.getProfile(A)!!
        val doctor = addDoctor(A)
        val visit = requireNotNull(maintenance.addDoctorVisit(A, DoctorVisit(userId = A, doctorId = doctor,
            visitAt = NOON - DAY, reason = "Annual checkup")))
        assertTrue(auth.updateProfile(A, profile.copy(annualCheckupAt = NOON + 30 * DAY)))
        assertEquals(NOON + 30 * DAY, search.nextAnnualCheckup(A))
        assertNull(search.nextAnnualCheckup(B))
        assertFalse(auth.updateProfile(B, profile.copy(annualCheckupAt = NOON)))
        assertTrue(auth.updateProfile(A, auth.getProfile(A)!!.copy(annualCheckupAt = null)))
        assertNull(search.nextAnnualCheckup(A))
        assertNotNull(maintenance.getDoctorVisit(A, visit))
    }

    @Test
    fun deletingDoctorPreservesFinalDoseAndMedicationHistory() = runBlocking<Unit> {
        val doctor = addDoctor(A)
        val medication = requireNotNull(medications.addMedication(A, medication(A, "Fictional maintenance medicine", doctorId = doctor)))
        val dose = requireNotNull(medications.scheduleDose(A, medication, NOON))
        assertTrue(medications.markDoseTaken(A, dose))
        assertTrue(maintenance.deleteDoctor(A, doctor))
        assertNull(medications.getMedication(A, medication)!!.doctorId)
        assertEquals("TAKEN", medications.getDose(A, dose)!!.status.name)
        assertFalse(medications.markDoseMissed(A, dose))
    }

    @Test
    fun formsRejectMalformedDatesContactsAndNonFiniteRanges() {
        assertNotNull(parseVisitTime("2026-12-03 14:30"))
        assertNull(parseVisitTime("2026-02-30 14:30"))
        assertNull(parseVisitTime("2026-12-03 14:30 trailing"))
        assertNull(doctorContactError("+1 (202) 555-0123", "fictional@example.com"))
        assertNotNull(doctorContactError("123", ""))
        assertNotNull(doctorContactError("", "invalid-email"))
        assertNotNull(rangeError(Double.NaN, 10.0, "", ""))
        assertNotNull(rangeError(0.0, Double.POSITIVE_INFINITY, "", ""))
        assertNotNull(rangeError(20.0, 10.0, "", ""))
        assertNotNull(rangeError(0.0, 10.0, "1", ""))
        assertNotNull(rangeError(0.0, 10.0, "5", "2"))
        assertNull(rangeError(0.0, 10.0, "1", "2"))
        assertTrue(isAnnualCheckup("Annual checkup · routine"))
        assertFalse(isAnnualCheckup("Follow-up"))
    }
}
