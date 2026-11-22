package com.cse3310.projecthealthmonitoringsystem.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cse3310.projecthealthmonitoringsystem.data.entity.*
import com.cse3310.projecthealthmonitoringsystem.settings.*
import com.cse3310.projecthealthmonitoringsystem.validation.HealthValidation as V
import com.cse3310.projecthealthmonitoringsystem.data.repository.DietRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProductRefinementTest : DbTest() {
    private suspend fun rejects(action: suspend () -> Unit) {
        try { action(); fail("Expected invalid input to be rejected") } catch (_: IllegalArgumentException) { }
    }
    @Test fun rejectInvalidVitalsButKeepAbnormalReadingsAndAlerts() = runBlocking<Unit> {
        val types = vitals.observeVitalTypes().first()
        val bp = types.first { it.name == "Blood Pressure" }.vitalTypeId
        rejects { vitals.addVitalSign(A, bp, 1000.0, 120.0) }
        rejects { vitals.addVitalSign(A, bp, 80.0, 100.0) }
        rejects { vitals.addVitalSign(A, bp, Double.NaN, 80.0) }
        rejects { vitals.addVitalSign(A, bp, 120.0, Double.POSITIVE_INFINITY) }
        val abnormal = vitals.addVitalSign(A, bp, 190.0, 110.0)!!
        assertTrue(abnormal.isAbnormal)
        assertNotNull(abnormal.alertId)
        assertEquals(1, vitals.observeVitalSigns(A).first().size)
        assertTrue(vitals.observeVitalSigns(B).first().isEmpty())
        val pain = types.first { it.name == "Pain Level" }.vitalTypeId
        rejects { vitals.addVitalSign(A, pain, 11.0) }
        assertNotNull(vitals.addVitalSign(A, pain, 0.0))
        assertNotNull(V.vital("Oxygen Saturation", "%", 101.0, null, false))
        assertNotNull(V.vital("Respiratory Rate", "breaths/min", -1.0, null, false))
        assertNotNull(V.vital("Heart Rate", "bpm", Double.NEGATIVE_INFINITY, null, false))
        assertNotNull(V.vital("Body Temperature", "°C", 100.0, null, false))
        assertNull(V.vital("Body Temperature", "°F", 105.0, null, false))
        assertNull(V.vital("Blood Glucose", "mg/dL", 700.0, null, false))
    }
    @Test fun conversionsRoundTripAndPreferencesStayAccountSpecific() {
        for (c in listOf(20.0, 36.6, 40.5)) assertEquals(c, MeasurementUnits.celsius(MeasurementUnits.fahrenheit(c)), 1e-9)
        assertEquals(177.8, MeasurementUnits.centimeters(5.0, 10.0), 1e-9)
        assertEquals(70.0, MeasurementUnits.inches(177.8), 1e-9)
        assertEquals(75.0, DietRepository.convert(DietRepository.convert(75.0, WeightUnit.KG, WeightUnit.LB), WeightUnit.LB, WeightUnit.KG), 1e-9)
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("measurement_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        assertEquals(MeasurementPreferences(), loadMeasurementPreferences(context, A))
        val metric = MeasurementPreferences(false, false, false)
        saveMeasurementPreferences(context, A, metric)
        assertEquals(metric, loadMeasurementPreferences(context, A))
        assertEquals(MeasurementPreferences(), loadMeasurementPreferences(context, B))
        assertEquals(37.0, MeasurementUnits.storedVital(98.6, "Body Temperature", "°C", MeasurementPreferences()), 1e-9)
    }
    @Test fun birthDatesAndProfileUpdatesPreserveLegacyFields() = runBlocking<Unit> {
        assertNotNull(V.birthDate("2099-01-01", NOON))
        assertNotNull(V.birthDate("2026-02-30", NOON))
        val beforeBirthday = V.parseBirthDate("2026-11-19")!!
        assertEquals(25, V.age("2000-11-20", beforeBirthday))
        assertEquals(26, V.age("2000-11-19", beforeBirthday))
        val old = auth.getProfile(A)!!
        assertNull(old.dateOfBirth)
        assertTrue(auth.updateProfile(A, old.copy(dateOfBirth = "2000-11-19")))
        val updated = auth.getProfile(A)!!
        assertEquals(old.heightCm, updated.heightCm, 0.0)
        assertEquals(old.weight, updated.weight, 0.0)
        assertEquals(old.weightUnit, updated.weightUnit)
        rejects { auth.updateProfile(A, updated.copy(dateOfBirth = "2099-01-01")) }
        assertFalse(auth.updateProfile(B, updated))
    }
    @Test fun repositoriesRejectMalformedFoodContactsAndMedication() = runBlocking<Unit> {
        rejects { medications.addMedication(A, medication(A, "Invalid", dosage = "NaN mg")) }
        rejects { medications.addMedication(A, medication(A, "Invalid", dosage = "-5 mg")) }
        rejects { medications.addMedication(A, medication(A, "Invalid").copy(frequency = "whenever")) }
        rejects { diet.addWeight(A, Weight(userId = A, value = Double.POSITIVE_INFINITY, unit = WeightUnit.LB, recordedAt = NOON)) }
        rejects { diet.addDietEntry(A, DietEntry(userId = A, mealType = "Lunch", eatenAt = NOON),
            listOf(Food(userId = A, dietEntryId = 0, name = "Food", calories = 100, proteinGrams = Double.NaN))) }
        rejects { communication.addDesignee(A, Designee(userId = A, name = "Contact", phone = "123", preferredMethod = ContactMethod.SMS)) }
        rejects { communication.addDesignee(A, Designee(userId = A, name = "Contact", preferredMethod = ContactMethod.EMAIL)) }
        assertNotNull(V.phone("++123"))
        assertNotNull(V.heightCm(Double.NaN))
        assertNull(V.phone("+1 (202) 555-0142"))
    }
}
