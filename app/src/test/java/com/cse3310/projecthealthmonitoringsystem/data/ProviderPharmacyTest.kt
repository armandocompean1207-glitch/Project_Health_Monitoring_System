package com.cse3310.projecthealthmonitoringsystem.data

import com.cse3310.projecthealthmonitoringsystem.data.entity.*
import com.cse3310.projecthealthmonitoringsystem.settings.*
import com.cse3310.projecthealthmonitoringsystem.validation.ProviderPharmacyValidation as V
import com.cse3310.projecthealthmonitoringsystem.maintenance.visitDisplay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProviderPharmacyTest : DbTest() {
    private suspend fun rejects(block: suspend () -> Unit) {
        try { block(); fail("Expected invalid input") } catch (_: IllegalArgumentException) { }
    }
    @Test fun providerTypesKeepAppointmentAndMedicationLinks() = runBlocking<Unit> {
        for (kind in listOf("Physician", "Nurse practitioner", "Physician assistant", "Other", "")) {
            val id = maintenance.addDoctor(A, Doctor(userId = A, name = "Fictional Provider", providerType = kind, clinicName = "Fictional Clinic"))
            val p = maintenance.getDoctor(A, id)!!
            assertEquals(kind, p.providerType)
            val visit = maintenance.addDoctorVisit(A, DoctorVisit(userId = A, doctorId = id, visitAt = NOON))!!
            val med = medications.addMedication(A, medication(A, "Fictional medicine", doctorId = id))!!
            assertEquals(id, maintenance.getDoctorVisit(A, visit)!!.doctorId)
            assertEquals(id, medications.getMedication(A, med)!!.doctorId)
            assertTrue(maintenance.editDoctor(A, p.copy(clinicName = "Updated Clinic")))
            assertFalse(maintenance.editDoctor(B, p))
            assertNull(maintenance.getDoctor(B, id))
        }
        rejects { maintenance.addDoctor(A, Doctor(userId = A, name = "")) }
    }
    @Test fun preferredPharmacyIsUniquePerAccountEvenWithConcurrentWrites() = runBlocking<Unit> {
        coroutineScope {
            (1..4).map { n -> async { maintenance.addPharmacy(A, Pharmacy(userId = A, name = "Fictional $n", isPreferred = true)) } }.awaitAll()
        }
        val other = maintenance.addPharmacy(B, Pharmacy(userId = B, name = "Other", isPreferred = true))
        val rows = maintenance.observePharmacies(A).first()
        assertEquals(1, rows.count { it.isPreferred })
        for (p in rows) {
            assertTrue(maintenance.setPreferredPharmacy(A, p.pharmacyId))
            assertEquals(p.pharmacyId, maintenance.observePharmacies(A).first().single { it.isPreferred }.pharmacyId)
        }
        assertTrue(maintenance.getPharmacy(B, other)!!.isPreferred)
        assertFalse(maintenance.setPreferredPharmacy(A, other))
        assertFalse(maintenance.deletePharmacy(B, rows.first().pharmacyId))
    }
    @Test fun prescriptionsPersistAndRejectForeignPharmacyLinks() = runBlocking<Unit> {
        val p = maintenance.addPharmacy(A, Pharmacy(userId = A, name = "Fictional", street = "123 Example St", city = "Arlington", state = "TX", zip = "76010"))
        val foreign = maintenance.addPharmacy(B, Pharmacy(userId = B, name = "Other"))
        val id = medications.addMedication(A, medication(A, "Fictional medicine").copy(pharmacyId = p, prescriptionNumber = "RX-123", refillsRemaining = 0, nextRefillAt = NOON + DAY))!!
        val med = medications.getMedication(A, id)!!
        assertEquals("RX-123", med.prescriptionNumber)
        assertEquals(0, med.refillsRemaining)
        assertEquals(p, med.pharmacyId)
        assertTrue(medications.editMedication(A, med.copy(refillsRemaining = 2, nextRefillAt = NOON - DAY)))
        assertEquals(2, medications.getMedication(A, id)!!.refillsRemaining)
        assertNull(medications.addMedication(A, medication(A, "Foreign").copy(pharmacyId = foreign)))
        assertFalse(medications.editMedication(A, med.copy(pharmacyId = foreign)))
        assertTrue(runCatching { sql.execSQL("UPDATE Medication SET pharmacyId=? WHERE medicationId=?", arrayOf(foreign, id)) }.isFailure)
        val pharmacy = maintenance.getPharmacy(A, p)!!
        assertTrue(maintenance.editPharmacy(A, pharmacy.copy(pharmacistName = "Fictional Pharmacist")))
        assertFalse(maintenance.editPharmacy(B, pharmacy))
        assertEquals("123 Example St, Arlington, TX 76010", pharmacy.directionsAddress())
    }
    @Test fun deletingPharmacyKeepsRxAndFinalDoseHistory() = runBlocking<Unit> {
        val p = maintenance.addPharmacy(A, Pharmacy(userId = A, name = "Fictional"))
        val id = medications.addMedication(A, medication(A, "Fictional medicine").copy(pharmacyId = p, prescriptionNumber = "RX-42"))!!
        val dose = medications.scheduleDose(A, id, NOON)!!
        assertTrue(medications.markDoseTaken(A, dose))
        val history = medications.getDose(A, dose)
        assertTrue(maintenance.deletePharmacy(A, p))
        assertNull(medications.getMedication(A, id)!!.pharmacyId)
        assertEquals("RX-42", medications.getMedication(A, id)!!.prescriptionNumber)
        assertEquals(history, medications.getDose(A, dose))
        assertTrue(runCatching { sql.execSQL("DELETE FROM DoseLog WHERE doseId=?", arrayOf(dose)) }.isFailure)
    }
    @Test fun optionalFieldsValidateWithoutInventingRefillLimits() = runBlocking<Unit> {
        assertTrue(maintenance.addPharmacy(A, Pharmacy(userId = A, name = "Name only")) > 0)
        for (p in listOf(Pharmacy(userId = A, name = ""), Pharmacy(userId = A, name = "Test", phone = "123"),
            Pharmacy(userId = A, name = "Test", zip = "ABC"), Pharmacy(userId = A, name = "Test", state = "Texas"))) {
            rejects { maintenance.addPharmacy(A, p) }
        }
        rejects { medications.addMedication(A, medication(A, "Bad").copy(refillsRemaining = -1)) }
        rejects { medications.addMedication(A, medication(A, "Bad").copy(nextRefillAt = -1)) }
        rejects { medications.addMedication(A, medication(A, "Bad").copy(prescriptionNumber = "X\nY")) }
        assertNotNull(V.refillInput("1.5"))
        assertNotNull(V.refillInput("999999999999999999"))
        assertNull(V.refillInput(""))
        assertNull(V.refillInput("0"))
    }
    @Test fun usDisplayDefaultsRetainCanonicalMeasurementsAndAlternatives() {
        val prefs = MeasurementPreferences()
        assertTrue(prefs.fahrenheit && prefs.pounds && prefs.imperialHeight)
        assertEquals("lbs", prefs.weightLabel)
        assertEquals("kg", MeasurementPreferences(false, false, false).weightLabel)
        assertEquals(36.1111111111, MeasurementUnits.storedVital(97.0, "Body Temperature", "°C", prefs), 1e-9)
        assertTrue(visitDisplay(NOON).matches(Regex("[0-9]{2}/[0-9]{2}/[0-9]{4} · [0-9]{1,2}:[0-9]{2} [AP]M")))
    }
}
