package com.cse3310.projecthealthmonitoringsystem.data

import com.cse3310.projecthealthmonitoringsystem.data.entity.*
import com.cse3310.projecthealthmonitoringsystem.settings.MeasurementPreferences
import com.cse3310.projecthealthmonitoringsystem.settings.MeasurementUnits as Units
import com.cse3310.projecthealthmonitoringsystem.validation.GlucoseContext
import com.cse3310.projecthealthmonitoringsystem.validation.HealthValidation
import com.cse3310.projecthealthmonitoringsystem.validation.VitalAssessment
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VitalAlertRegressionTest : DbTest() {
    private suspend fun type(name: String) = vitals.observeVitalTypes().first().first { it.name == name }
    private suspend fun rejects(block: suspend () -> Unit) {
        try { block(); fail("Expected validation error") } catch (_: IllegalArgumentException) { }
    }

    @Test fun fahrenheit97IsNormalAndSavedExactlyOnceInCelsius() = runBlocking<Unit> {
        val t = type("Body Temperature")
        val result = vitals.addEnteredVitalSign(A, t.vitalTypeId, 97.0, "°F", null, NOON)!!
        assertFalse(result.isAbnormal)
        assertNull(result.alertId)
        val reading = vitals.getVitalSign(A, result.vitalId)!!
        assertEquals(36.1111111111, reading.value, 1e-9)
        assertEquals(97.0, Units.displayVital(reading.value, t.name, t.unit, MeasurementPreferences()), 1e-9)
        assertEquals("97.0 °F (36.1 °C)", VitalAssessment.reading(t.name, t.unit, reading.value))
        assertEquals(NOON, reading.recordedAt)
        assertTrue(monitoring.observeAlerts(A).first().isEmpty())
        assertTrue(vitals.observeVitalSigns(B).first().isEmpty())
    }

    @Test fun abnormalTemperatureAlertUsesItsOwnRecordNotTheLatestAndRetainsActualTime() = runBlocking<Unit> {
        val t = type("Body Temperature")
        val earlier = NOON - DAY
        val bad = vitals.addEnteredVitalSign(A, t.vitalTypeId, 104.0, "°F", null, earlier)!!
        vitals.addEnteredVitalSign(A, t.vitalTypeId, 97.0, "°F", null, NOON)
        val alert = monitoring.getAlert(A, bad.alertId!!)!!
        assertEquals(bad.vitalId, alert.vitalId)
        assertEquals(NOON, alert.createdAt) // generation time is separate from measurement time
        assertTrue(alert.message.contains("104.0 °F (40.0 °C)"))
        assertTrue(alert.message.contains(VitalAssessment.date(earlier)))
        assertFalse(alert.message.contains("97.0"))
        assertFalse(alert.message.contains("777777"))
        assertNull(monitoring.getAlert(B, alert.alertId))
    }

    @Test fun normalAndInclusiveReferenceBoundariesForAllSixMeasurements() = runBlocking<Unit> {
        val samples = listOf(
            Triple("Blood Pressure", listOf(90.0, 115.0, 120.0), 60.0),
            Triple("Blood Glucose", listOf(70.0, 100.0, 140.0), null),
            Triple("Body Temperature", listOf(36.1, 36.6, 37.2), null),
            Triple("Cholesterol", listOf(0.1, 180.0, 200.0), null),
            Triple("Heart Rate", listOf(60.0, 72.0, 100.0), null),
            Triple("Oxygen Saturation", listOf(95.0, 98.0, 100.0), null)
        )
        for ((name, values, second) in samples) {
            val t = type(name)
            for (value in values) assertFalse("$name: $value", vitals.addVitalSign(A, t.vitalTypeId, value, second)!!.isAbnormal)
        }
        val bp = type("Blood Pressure")
        assertFalse(vitals.addVitalSign(A, bp.vitalTypeId, 120.0, 80.0)!!.isAbnormal)
        assertTrue(monitoring.observeAlerts(A).first().isEmpty())
    }

    @Test fun abnormalReadingsForAllSixStayRecordableAndHaveLinkedAlerts() = runBlocking<Unit> {
        val samples = listOf(
            Triple("Blood Pressure", 190.0, 121.0),
            Triple("Blood Glucose", 45.0, null),
            Triple("Body Temperature", 34.0, null),
            Triple("Cholesterol", 301.0, null),
            Triple("Heart Rate", 150.0, null),
            Triple("Oxygen Saturation", 85.0, null)
        )
        for ((name, value, second) in samples) {
            val t = type(name)
            val result = vitals.addVitalSign(A, t.vitalTypeId, value, second)!!
            assertTrue(name, result.isAbnormal)
            assertEquals(result.vitalId, monitoring.getAlert(A, result.alertId!!)!!.vitalId)
        }
        assertEquals(6, monitoring.observeAlerts(A).first().size)
    }

    @Test fun invalidMissingNonfiniteWrongUnitsAndBadPressureRelationshipsAreRejected() = runBlocking<Unit> {
        val invalid = listOf(
            Triple("Blood Pressure", 1000.0, 120.0), Triple("Blood Pressure", 120.0, 130.0),
            Triple("Blood Pressure", 120.0, null), Triple("Blood Pressure", 120.0, Double.NaN),
            Triple("Blood Glucose", 0.0, null), Triple("Blood Glucose", 2001.0, null),
            Triple("Body Temperature", 97.0, null), Triple("Body Temperature", 51.0, null),
            Triple("Cholesterol", -1.0, null), Triple("Cholesterol", 3001.0, null),
            Triple("Heart Rate", 0.0, null), Triple("Heart Rate", 501.0, null),
            Triple("Oxygen Saturation", -1.0, null), Triple("Oxygen Saturation", 101.0, null)
        )
        for ((name, value, second) in invalid) {
            val t = type(name)
            rejects { vitals.addVitalSign(A, t.vitalTypeId, value, second) }
        }
        for (t in vitals.observeVitalTypes().first()) {
            for (n in listOf(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY))
                rejects { vitals.addVitalSign(A, t.vitalTypeId, n) }
            assertNotNull(HealthValidation.vital(t.name, t.unit, null, null, t.secondaryLabel != null))
        }
        val temp = type("Body Temperature")
        rejects { vitals.addEnteredVitalSign(A, temp.vitalTypeId, 97.0, "kelvin", null, NOON) }
        val glucose = type("Blood Glucose")
        rejects { vitals.addEnteredVitalSign(A, glucose.vitalTypeId, 5.5, "mmol/L", null, NOON) }
        rejects { vitals.addEnteredVitalSign(A, temp.vitalTypeId, 97.0, "°F", null, -1) }
        rejects { vitals.addVitalSign(A, glucose.vitalTypeId, 100.0, 80.0) }
        assertTrue(vitals.observeVitalSigns(A).first().isEmpty())
        assertTrue(monitoring.observeAlerts(A).first().isEmpty())
    }

    @Test fun displayedTemperatureBoundariesRoundTripWithoutFalseAlerts() = runBlocking<Unit> {
        val t = type("Body Temperature")
        for (c in listOf(t.normalMin, t.normalMax)) {
            val result = vitals.addEnteredVitalSign(A, t.vitalTypeId, Units.fahrenheit(c), "°F", null, NOON)!!
            assertFalse("Boundary $c", result.isAbnormal)
        }
        val abnormal = vitals.addEnteredVitalSign(A, t.vitalTypeId, 96.0, "°F", null, NOON)!!
        assertTrue(abnormal.isAbnormal)
        assertEquals(36.11111111111111, Units.celsius(97.0), 1e-12)
    }

    @Test fun temperatureUnitPreferenceDoesNotChangeSavedValuesOrAlertMeaning() = runBlocking<Unit> {
        val t = type("Body Temperature")
        val a = vitals.addEnteredVitalSign(A, t.vitalTypeId, 104.0, "°F", null, NOON)!!
        val b = vitals.addEnteredVitalSign(A, t.vitalTypeId, 40.0, "°C", null, NOON + 1)!!
        assertEquals(vitals.getVitalSign(A, a.vitalId)!!.value, vitals.getVitalSign(A, b.vitalId)!!.value, 1e-10)
        for (r in listOf(a, b)) assertTrue(monitoring.getAlert(A, r.alertId!!)!!.message.contains("104.0 °F (40.0 °C)"))
    }

    @Test fun glucoseContextIsPersistedAndSharedByConfirmationAndRepository() = runBlocking<Unit> {
        val t = type("Blood Glucose")
        assertFalse(vitals.addEnteredVitalSign(A, t.vitalTypeId, 150.0, t.unit, null, NOON, GlucoseContext.AFTER_MEAL)!!.isAbnormal)
        val before = vitals.addEnteredVitalSign(A, t.vitalTypeId, 150.0, t.unit, null, NOON, GlucoseContext.BEFORE_MEAL)!!
        assertTrue(before.isAbnormal)
        val record = vitals.getVitalSign(A, before.vitalId)!!
        assertEquals(GlucoseContext.BEFORE_MEAL, GlucoseContext.fromNote(record.note))
        assertTrue(monitoring.getAlert(A, before.alertId!!)!!.message.contains(GlucoseContext.BEFORE_MEAL.label))
        for (context in GlucoseContext.entries) for (v in listOf(54.0, 70.0, 79.0, 80.0, 99.0, 100.0, 130.0, 140.0, 179.0, 180.0)) {
            val result = vitals.addEnteredVitalSign(A, t.vitalTypeId, v, t.unit, null, NOON, context)!!
            assertEquals(VitalAssessment.isAbnormal(t, v, null, context), result.isAbnormal)
        }
        assertFalse(VitalAssessment.isAbnormal(t, 99.0, null, GlucoseContext.FASTING))
        assertTrue(VitalAssessment.isAbnormal(t, 100.0, null, GlucoseContext.FASTING))
        assertFalse(VitalAssessment.isAbnormal(t, 179.0, null, GlucoseContext.AFTER_MEAL))
        assertTrue(VitalAssessment.isAbnormal(t, 180.0, null, GlucoseContext.AFTER_MEAL))
    }

    @Test fun retriesAndConcurrentAlertCreationAreIdempotent() = runBlocking<Unit> {
        val t = type("Body Temperature")
        val first = vitals.addEnteredVitalSign(A, t.vitalTypeId, 104.0, "°F", null, NOON)!!
        val retry = vitals.addEnteredVitalSign(A, t.vitalTypeId, 104.0, "°F", null, NOON)!!
        assertEquals(first, retry)
        val ids = coroutineScope { (1..5).map { async { monitoring.raiseAbnormalVitalAlert(A, first.vitalId) } }.awaitAll() }
        assertEquals(setOf(first.alertId), ids.toSet())
        assertEquals(1, vitals.observeVitalSigns(A).first().size)
        assertEquals(1, monitoring.observeAlerts(A).first().size)
        rejects { monitoring.raiseAbnormalVitalAlert(B, first.vitalId) }
    }

    @Test fun legacyWrongMessageIsRepairedFromLinkedSourceWithoutChangingRecordsOrHistory() = runBlocking<Unit> {
        val t = type("Body Temperature")
        val result = vitals.addEnteredVitalSign(A, t.vitalTypeId, 104.0, "°F", null, NOON - DAY)!!
        val alert = monitoring.getAlert(A, result.alertId!!)!!
        val log = communication.logCommunication(A, alert.alertId, null, ContactMethod.EMAIL, DeliveryStatus.OPENED)!!
        assertTrue(db.alertDao().updateAlert(A, alert.copy(message = "Abnormal Body Temperature: 12.777777777777779 °C")))
        assertEquals(1, monitoring.repairVitalAlerts(A))
        val fixed = monitoring.getAlert(A, alert.alertId)!!
        assertTrue(fixed.message.contains("104.0 °F (40.0 °C)"))
        assertEquals(alert.createdAt, fixed.createdAt)
        assertEquals(alert.vitalId, fixed.vitalId)
        assertNull(fixed.acknowledgedAt)
        assertEquals(log, communication.getLogsForAlert(A, alert.alertId).single().commId)
        assertEquals(40.0, vitals.getVitalSign(A, result.vitalId)!!.value, 1e-9)
        assertEquals(0, monitoring.repairVitalAlerts(A))
    }

    @Test fun confirmedCorrectedNormalReadingResolvesStaleAlertButKeepsHistoryAndAccountIsolation() = runBlocking<Unit> {
        val t = type("Body Temperature")
        val result = vitals.addEnteredVitalSign(A, t.vitalTypeId, 104.0, "°F", null, NOON)!!
        val record = vitals.getVitalSign(A, result.vitalId)!!
        assertFalse(vitals.editVitalSign(B, record.copy(value = Units.celsius(97.0))))
        assertTrue(vitals.editVitalSign(A, record.copy(value = Units.celsius(97.0))))
        val alert = monitoring.getAlert(A, result.alertId!!)!!
        assertTrue(alert.message.contains("97.0 °F (36.1 °C)"))
        assertTrue(alert.message.startsWith("Corrected reading"))
        assertNotNull(alert.acknowledgedAt)
        assertFalse(vitals.getVitalSign(A, record.vitalId)!!.isAbnormal)
        assertTrue(monitoring.observeUnacknowledgedAlerts(A).first().isEmpty())
        assertEquals(1, monitoring.observeAlerts(A).first().size)
    }

    @Test fun extremeSavedValueIsNotGuessedOrSilentlyReplacedWith97() = runBlocking<Unit> {
        val t = type("Body Temperature")
        val result = vitals.addEnteredVitalSign(A, t.vitalTypeId, 55.0, "°F", null, NOON)!!
        val alert = monitoring.getAlert(A, result.alertId!!)!!
        assertTrue(alert.message.contains("55.0 °F (12.8 °C)"))
        assertNotNull(VitalAssessment.urgentAdvice(t.name, t.unit, Units.celsius(55.0), null))
        assertEquals(Units.celsius(55.0), vitals.getVitalSign(A, result.vitalId)!!.value, 1e-9)
    }

    @Test fun urgentGuidanceUsesDocumentedThresholdsAndDoesNotBlockSaving() {
        assertNull(VitalAssessment.urgentAdvice("Blood Pressure", "mmHg", 180.0, 120.0))
        assertNotNull(VitalAssessment.urgentAdvice("Blood Pressure", "mmHg", 181.0, 80.0))
        assertNotNull(VitalAssessment.urgentAdvice("Blood Pressure", "mmHg", 140.0, 121.0))
        assertNull(VitalAssessment.urgentAdvice("Body Temperature", "°F", 97.0, null))
        assertNotNull(VitalAssessment.urgentAdvice("Body Temperature", "°F", 94.0, null))
        assertNotNull(VitalAssessment.urgentAdvice("Oxygen Saturation", "%", 88.0, null))
        assertNotNull(VitalAssessment.urgentAdvice("Blood Glucose", "mg/dL", 54.0, null))
        assertNotNull(VitalAssessment.urgentAdvice("Blood Glucose", "mg/dL", 300.0, null))
        assertNull(VitalAssessment.urgentAdvice("Cholesterol", "mg/dL", 250.0, null))
    }
}
