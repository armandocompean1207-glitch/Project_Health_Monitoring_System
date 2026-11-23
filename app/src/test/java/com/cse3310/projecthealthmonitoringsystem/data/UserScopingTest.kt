package com.cse3310.projecthealthmonitoringsystem.data

import com.cse3310.projecthealthmonitoringsystem.data.entity.ContactMethod
import com.cse3310.projecthealthmonitoringsystem.data.entity.DeliveryStatus
import com.cse3310.projecthealthmonitoringsystem.data.entity.DietEntry
import com.cse3310.projecthealthmonitoringsystem.data.entity.Designee
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoctorVisit
import com.cse3310.projecthealthmonitoringsystem.data.entity.Food
import com.cse3310.projecthealthmonitoringsystem.data.entity.Weight
import com.cse3310.projecthealthmonitoringsystem.data.entity.WeightUnit
import com.cse3310.projecthealthmonitoringsystem.data.repository.SearchRepository.SearchCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * User A creates one row in every user-owned table. User B must not be able to read, update or delete
 * any of them, through any DAO or repository, even when B knows the row ids.
 */
class UserScopingTest : DbTest() {

    private var doctorId = 0L
    private var visitId = 0L
    private var medId = 0L
    private var doseId = 0L
    private var alertId = 0L
    private var vitalId = 0L
    private var entryId = 0L
    private var foodId = 0L
    private var weightId = 0L
    private var noteId = 0L
    private var designeeId = 0L
    private var commId = 0L

    private fun seedUserA() = runBlocking<Unit> {
        doctorId = addDoctor(A, "Dr. Alpha")
        visitId = maintenance.addDoctorVisit(A, DoctorVisit(userId = A, doctorId = doctorId, visitAt = NOON + DAY, reason = "alpha visit"))!!
        medId = addMedication(A, "Warfarin")
        doseId = medications.scheduleDose(A, medId, NOON)!!
        alertId = monitoring.checkMissedDose(A, doseId, now = NOON + HOUR)!!.alertId
        val hr = vitals.observeVitalTypes().first().first { it.name == "Heart Rate" }.vitalTypeId
        vitalId = vitals.addVitalSign(A, hr, 72.0, recordedAt = NOON, note = "alpha")!!.vitalId
        entryId = diet.addDietEntry(A, DietEntry(userId = A, mealType = "alpha meal", eatenAt = NOON),
            listOf(Food(userId = A, dietEntryId = 0, name = "alpha food", calories = 500)))
        foodId = diet.getFoods(A, entryId).single().foodId
        weightId = diet.addWeight(A, Weight(userId = A, value = 70.0, unit = WeightUnit.KG, recordedAt = NOON))
        noteId = notes.createNote(A, "alpha secret", "alpha body")
        designeeId = communication.addDesignee(A, Designee(userId = A, name = "Alpha Mom", phone = "2025550142", preferredMethod = ContactMethod.SMS))
        commId = communication.logCommunication(A, alertId, designeeId, ContactMethod.SMS, DeliveryStatus.SENT)!!
        val profile = auth.getProfile(A)!!
        auth.updateProfile(A, profile.copy(annualCheckupAt = NOON + 30 * DAY))
    }

    @Test
    fun userBCannotReadUserARows() = runBlocking<Unit> {
        seedUserA()
        // single-row reads by id
        assertNull(maintenance.getDoctor(B, doctorId))
        assertNull(maintenance.getDoctorVisit(B, visitId))
        assertNull(medications.getMedication(B, medId))
        assertNull(medications.getDose(B, doseId))
        assertNull(monitoring.getAlert(B, alertId))
        assertNull(vitals.getVitalSign(B, vitalId))
        assertNull(db.dietDao().getDietEntry(B, entryId))
        assertTrue(diet.getFoods(B, entryId).isEmpty())
        assertNull(notes.openNote(B, noteId))
        assertNull(communication.getDesignee(B, designeeId))
        assertTrue(communication.getLogsForAlert(B, alertId).isEmpty())
        assertTrue(medications.getAuditLog(B, medId).isEmpty())

        // lists
        assertTrue(maintenance.observeDoctors(B).first().isEmpty())
        assertTrue(maintenance.observeDoctorVisits(B).first().isEmpty())
        assertTrue(medications.observeAllMedications(B).first().isEmpty())
        assertTrue(medications.observeDoseLog(B, 0, Long.MAX_VALUE).first().isEmpty())
        assertTrue(monitoring.observeAlerts(B).first().isEmpty())
        assertTrue(vitals.observeVitalSigns(B).first().isEmpty())
        assertTrue(vitals.observeAbnormalVitalSigns(B).first().isEmpty())
        assertTrue(diet.observeDietEntries(B).first().isEmpty())
        assertEquals(0, diet.getDailyCalorieTotal(B, NOON))
        assertTrue(diet.observeWeights(B).first().isEmpty())
        assertNull(diet.weightChangeSinceLast(B))
        assertTrue(notes.observeNotes(B).first().isEmpty())
        assertTrue(communication.getDesignees(B).isEmpty())
        assertTrue(communication.observeCommunicationLog(B).first().isEmpty())
        assertTrue(medications.detectConflict(B, "Aspirin").isEmpty())

        // search and shortcuts
        for (category in SearchCategory.values()) {
            assertTrue("search $category", search.search(B, category, "alpha").isEmpty())
            assertTrue("search all $category", search.search(B, category, "").isEmpty())
        }
        assertNull(search.nextDoctorVisit(B))
        assertNull(search.nextAnnualCheckup(B))
        assertTrue(search.pastMedications(B).isEmpty())
    }

    @Test
    fun userBCannotUpdateUserARows() = runBlocking<Unit> {
        seedUserA()
        val doctor = maintenance.getDoctor(A, doctorId)!!
        val visit = maintenance.getDoctorVisit(A, visitId)!!
        val med = medications.getMedication(A, medId)!!
        val vital = vitals.getVitalSign(A, vitalId)!!
        val entry = db.dietDao().getDietEntry(A, entryId)!!
        val food = diet.getFoods(A, entryId).single()
        val weight = diet.observeWeights(A).first().single()
        val designee = communication.getDesignee(A, designeeId)!!

        // B passes A's row as-is (still tagged userId = A)
        assertFalse(maintenance.editDoctor(B, doctor))
        assertFalse(maintenance.editDoctorVisit(B, visit))
        assertFalse(medications.editMedication(B, med))
        assertFalse(vitals.editVitalSign(B, vital))
        assertFalse(diet.editDietEntry(B, entry))
        assertFalse(diet.editFood(B, food))
        assertFalse(diet.editWeight(B, weight))
        assertFalse(communication.editDesignee(B, designee))
        assertFalse(notes.editNote(B, noteId, "hacked", "hacked"))

        // B re-tags A's row as its own to try to take it over
        assertFalse(maintenance.editDoctor(B, doctor.copy(userId = B, name = "hacked")))
        assertFalse(medications.editMedication(B, med.copy(userId = B, name = "hacked")))
        assertFalse(vitals.editVitalSign(B, vital.copy(userId = B)))
        assertFalse(diet.editDietEntry(B, entry.copy(userId = B)))
        assertFalse(diet.editFood(B, food.copy(userId = B)))
        assertFalse(diet.editWeight(B, weight.copy(userId = B)))
        assertFalse(communication.editDesignee(B, designee.copy(userId = B)))
        assertFalse(db.alertDao().updateAlert(B, monitoring.getAlert(A, alertId)!!.copy(userId = B)))

        // B cannot attach its own rows to A's alert, designee, doctor or diet entry
        assertNull(communication.logCommunication(B, alertId, null, ContactMethod.SMS, DeliveryStatus.SENT))
        assertNull(communication.logCommunication(B, null, designeeId, ContactMethod.SMS, DeliveryStatus.SENT))
        assertNull(maintenance.addDoctorVisit(B, DoctorVisit(userId = B, doctorId = doctorId, visitAt = NOON)))
        assertNull(medications.addMedication(B, medication(B, "Aspirin", doctorId = doctorId)))
        assertNull(diet.addFood(B, Food(userId = B, dietEntryId = entryId, name = "x", calories = 1)))
        assertNull(medications.scheduleDose(B, medId, NOON + DAY))

        // dose and alert state changes
        assertFalse(medications.markDoseTaken(B, doseId))
        assertFalse(monitoring.acknowledgeAlert(B, alertId))
        assertEquals(0, medications.markDosesMissed(B, listOf(doseId), NOON))

        // nothing changed for A
        assertEquals("Warfarin", medications.getMedication(A, medId)!!.name)
        assertEquals(A, medications.getMedication(A, medId)!!.userId)
        assertEquals("alpha secret", notes.openNote(A, noteId)!!.title)
        assertNull(monitoring.getAlert(A, alertId)!!.acknowledgedAt)
        assertTrue(medications.getAuditLog(A, medId).isEmpty())
    }

    @Test
    fun userBCannotDeleteUserARows() = runBlocking<Unit> {
        seedUserA()
        assertFalse(maintenance.deleteDoctor(B, doctorId))
        assertFalse(maintenance.deleteDoctorVisit(B, visitId))
        assertEquals(com.cse3310.projecthealthmonitoringsystem.data.repository.MedicationRepository.DeleteResult.NOT_FOUND,
            medications.deleteMedication(B, medId))
        assertFalse(monitoring.deleteAlert(B, alertId))
        assertFalse(vitals.deleteVitalSign(B, vitalId))
        assertFalse(diet.deleteDietEntry(B, entryId))
        assertFalse(diet.deleteFood(B, foodId))
        assertFalse(diet.deleteWeight(B, weightId))
        assertFalse(notes.deleteNote(B, noteId))
        assertFalse(communication.deleteDesignee(B, designeeId))
        assertFalse(communication.deleteLog(B, commId))

        for ((table, n) in listOf(
            "Doctor" to 1, "DoctorVisit" to 1, "Medication" to 1, "DoseLog" to 1, "Alert" to 1, "VitalSign" to 1,
            "DietEntry" to 1, "Food" to 1, "Weight" to 1, "Note" to 1, "Designee" to 1, "CommunicationLog" to 1
        )) {
            assertEquals(table, n, count(table, "userId = '$A'"))
        }
        assertTrue(medications.getActiveMedications(A).single().isActive)
    }
}
