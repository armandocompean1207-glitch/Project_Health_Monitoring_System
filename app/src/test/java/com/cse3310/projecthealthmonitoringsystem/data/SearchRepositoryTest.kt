package com.cse3310.projecthealthmonitoringsystem.data

import com.cse3310.projecthealthmonitoringsystem.data.dao.SearchResultKind
import com.cse3310.projecthealthmonitoringsystem.data.entity.DietEntry
import com.cse3310.projecthealthmonitoringsystem.data.entity.Doctor
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoctorVisit
import com.cse3310.projecthealthmonitoringsystem.data.entity.Food
import com.cse3310.projecthealthmonitoringsystem.data.entity.Weight
import com.cse3310.projecthealthmonitoringsystem.data.entity.WeightUnit
import com.cse3310.projecthealthmonitoringsystem.data.repository.SearchRepository
import com.cse3310.projecthealthmonitoringsystem.data.repository.SearchRepository.SearchCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SearchRepositoryTest : DbTest() {

    @Test
    fun medicationsByKeywordAndDateRange() = runBlocking<Unit> {
        addMedication(A, "Metformin")
        addMedication(A, "Aspirin")
        addMedication(B, "Metformin")

        val hits = search.search(A, SearchCategory.MEDICATIONS, "metf")
        assertEquals(1, hits.size)
        assertEquals(SearchResultKind.MEDICATION, hits.single().kind)
        assertEquals("Metformin", hits.single().title)
        assertEquals(2, search.search(A, SearchCategory.MEDICATIONS, "").size)
        assertTrue(search.search(A, SearchCategory.MEDICATIONS, "metf", from = NOON).isEmpty())
        assertTrue(search.search(A, SearchCategory.MEDICATIONS, "zzz").isEmpty())
    }

    @Test
    fun doctorsAndVisits() = runBlocking<Unit> {
        val cardiology = db.lookupDao().getSpecialties().first { it.name == "Cardiology" }.specialtyId
        val doc = maintenance.addDoctor(A, Doctor(userId = A, name = "Dr. Grey", specialtyId = cardiology))
        maintenance.addDoctorVisit(A, DoctorVisit(userId = A, doctorId = doc, visitAt = NOON - DAY, reason = "Chest pain"))
        maintenance.addDoctorVisit(A, DoctorVisit(userId = A, doctorId = doc, visitAt = NOON + 7 * DAY, reason = "Follow-up"))

        val all = search.search(A, SearchCategory.DOCTORS_AND_VISITS, "grey")
        assertEquals(listOf(SearchResultKind.DOCTOR_VISIT, SearchResultKind.DOCTOR_VISIT, SearchResultKind.DOCTOR),
            all.map { it.kind })
        assertEquals("Cardiology", search.search(A, SearchCategory.DOCTORS_AND_VISITS, "cardio").single().subtitle)
        assertEquals("Chest pain", search.search(A, SearchCategory.DOCTORS_AND_VISITS, "chest").single().subtitle)
        // with a date range, doctors (no date) drop out
        val ranged = search.search(A, SearchCategory.DOCTORS_AND_VISITS, "", from = NOON, to = NOON + 30 * DAY)
        assertEquals(listOf("Follow-up"), ranged.map { it.subtitle })
        assertTrue(search.search(B, SearchCategory.DOCTORS_AND_VISITS, "grey").isEmpty())
    }

    @Test
    fun vitalSigns() = runBlocking<Unit> {
        val hr = vitals.observeVitalTypes().first().first { it.name == "Heart Rate" }.vitalTypeId
        vitals.addVitalSign(A, hr, 130.0, recordedAt = NOON, note = "after run")
        val hit = search.search(A, SearchCategory.VITAL_SIGNS, "heart").single()
        assertEquals("130.0 bpm (abnormal)", hit.subtitle)
        assertEquals(1, search.search(A, SearchCategory.VITAL_SIGNS, "run").size)
        assertTrue(search.search(A, SearchCategory.VITAL_SIGNS, "heart", to = NOON - 1).isEmpty())
    }

    @Test
    fun dietAndWeight() = runBlocking<Unit> {
        diet.addDietEntry(A, DietEntry(userId = A, mealType = "Breakfast", eatenAt = NOON),
            listOf(Food(userId = A, dietEntryId = 0, name = "Oatmeal", calories = 150), Food(userId = A, dietEntryId = 0, name = "Banana", calories = 105)))
        diet.addWeight(A, Weight(userId = A, value = 70.5, unit = WeightUnit.KG, recordedAt = NOON))

        val oat = search.search(A, SearchCategory.DIET_AND_WEIGHT, "oatmeal").single()
        assertEquals(SearchResultKind.DIET_ENTRY, oat.kind)
        assertEquals("255 kcal", oat.subtitle)
        val weight = search.search(A, SearchCategory.DIET_AND_WEIGHT, "weight").single()
        assertEquals("70.5 kg", weight.subtitle)
        assertEquals(2, search.search(A, SearchCategory.DIET_AND_WEIGHT, "").size)
    }

    @Test
    fun notesAndLikeEscaping() = runBlocking<Unit> {
        notes.createNote(A, "Budget", "Copay is 50% this year")
        notes.createNote(A, "Plain", "Copay is 50 dollars")
        notes.createNote(A, "snake_case", "x")
        notes.createNote(B, "Budget", "B's note")

        assertEquals(listOf("Budget"), search.search(A, SearchCategory.NOTES, "50%").map { it.title })
        assertEquals(listOf("snake_case"), search.search(A, SearchCategory.NOTES, "e_c").map { it.title })
        assertEquals(1, search.search(B, SearchCategory.NOTES, "budget").size)
        assertEquals("%a\\%b\\_c\\\\%", SearchRepository.likePattern(" a%b_c\\ "))
    }

    @Test
    fun invalidDateRangeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { search.search(A, SearchCategory.NOTES, "", from = NOON, to = NOON - 1) }
        }
    }

    @Test
    fun nextAndLastDoctorVisit() = runBlocking<Unit> {
        assertNull(search.nextDoctorVisit(A))
        val doc = addDoctor(A, "Dr. Who")
        for (offset in listOf(-30L, -2L, 3L, 10L)) {
            maintenance.addDoctorVisit(A, DoctorVisit(userId = A, doctorId = doc, visitAt = NOON + offset * DAY))
        }
        val bDoc = addDoctor(B)
        maintenance.addDoctorVisit(B, DoctorVisit(userId = B, doctorId = bDoc, visitAt = NOON + DAY))

        assertEquals(NOON + 3 * DAY, search.nextDoctorVisit(A)!!.visit.visitAt)
        assertEquals("Dr. Who", search.nextDoctorVisit(A)!!.doctorName)
        assertEquals(NOON - 2 * DAY, search.lastDoctorVisit(A)!!.visit.visitAt)
    }

    @Test
    fun pastMedications() = runBlocking<Unit> {
        addMedication(A, "Current")
        val stopped = addMedication(A, "Stopped")
        medications.scheduleDose(A, stopped, NOON)
        medications.deleteMedication(A, stopped)
        medications.addMedication(A, medication(A, "Ended").copy(endDate = NOON - DAY))
        assertEquals(setOf("Stopped", "Ended"), search.pastMedications(A).map { it.name }.toSet())
        assertTrue(search.pastMedications(B).isEmpty())
    }

    @Test
    fun nextAnnualCheckupRollsForward() = runBlocking<Unit> {
        assertNull(search.nextAnnualCheckup(A))
        val profile = auth.getProfile(A)!!
        auth.updateProfile(A, profile.copy(annualCheckupAt = NOON + 10 * DAY))
        assertEquals(NOON + 10 * DAY, search.nextAnnualCheckup(A))

        val lastYear = Calendar.getInstance(UTC).apply { timeInMillis = NOON; add(Calendar.YEAR, -2); add(Calendar.DAY_OF_MONTH, 5) }
        auth.updateProfile(A, profile.copy(annualCheckupAt = lastYear.timeInMillis))
        val expected = Calendar.getInstance(UTC).apply { timeInMillis = NOON; add(Calendar.DAY_OF_MONTH, 5) }.timeInMillis
        assertEquals(expected, search.nextAnnualCheckup(A))
    }
}
