package com.cse3310.projecthealthmonitoringsystem.data

import com.cse3310.projecthealthmonitoringsystem.data.entity.AlertType
import com.cse3310.projecthealthmonitoringsystem.data.entity.VitalType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VitalsRepositoryTest : DbTest() {

    private suspend fun type(name: String): VitalType = vitals.observeVitalTypes().first().first { it.name == name }

    @Test
    fun normalReadingIsNotFlagged() = runBlocking<Unit> {
        val hr = type("Heart Rate")
        val result = vitals.addVitalSign(A, hr.vitalTypeId, 72.0)!!
        assertFalse(result.isAbnormal)
        assertNull(result.alertId)
        assertTrue(monitoring.observeAlerts(A).first().isEmpty())
    }

    @Test
    fun abnormalReadingIsFlaggedAndRaisesAlert() = runBlocking<Unit> {
        val glucose = type("Blood Glucose")
        val result = vitals.addVitalSign(A, glucose.vitalTypeId, 250.0)!!
        assertTrue(result.isAbnormal)
        val alert = monitoring.getAlert(A, result.alertId!!)!!
        assertEquals(AlertType.ABNORMAL_VITAL, alert.type)
        assertEquals(result.vitalId, alert.vitalId)
        assertTrue(alert.message.startsWith("Abnormal Blood Glucose: 250 mg/dL\nRecorded "))
        assertTrue(alert.message.contains(com.cse3310.projecthealthmonitoringsystem.validation.VitalAssessment.date(NOON)))
    }

    @Test
    fun bloodPressureChecksBothNumbers() = runBlocking<Unit> {
        val bp = type("Blood Pressure")
        assertFalse(vitals.addVitalSign(A, bp.vitalTypeId, 115.0, 75.0)!!.isAbnormal)
        assertTrue(vitals.addVitalSign(A, bp.vitalTypeId, 115.0, 95.0)!!.isAbnormal)
        assertTrue(vitals.addVitalSign(A, bp.vitalTypeId, 150.0, 75.0)!!.isAbnormal)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { vitals.addVitalSign(A, bp.vitalTypeId, 115.0) }
        }
    }

    @Test
    fun unknownTypeAndBadNumbersAreRejected() = runBlocking<Unit> {
        assertNull(vitals.addVitalSign(A, 9999, 1.0))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { vitals.addVitalSign(A, type("Heart Rate").vitalTypeId, Double.NaN) }
        }
    }

    @Test
    fun byTypeAndDateRangeAndAbnormalList() = runBlocking<Unit> {
        val hr = type("Heart Rate").vitalTypeId
        val temp = type("Body Temperature").vitalTypeId
        vitals.addVitalSign(A, hr, 70.0, recordedAt = NOON - 2 * DAY)
        vitals.addVitalSign(A, hr, 130.0, recordedAt = NOON - DAY)
        vitals.addVitalSign(A, hr, 75.0, recordedAt = NOON)
        vitals.addVitalSign(A, temp, 39.0, recordedAt = NOON)
        vitals.addVitalSign(B, hr, 140.0, recordedAt = NOON)

        val range = vitals.observeVitalSigns(A, hr, NOON - DAY, NOON).first()
        assertEquals(listOf(75.0, 130.0), range.map { it.vital.value })
        assertEquals("bpm", range.first().unit)
        assertEquals(listOf(true, false), range.map { it.vital.isAbnormal }.reversed().let { listOf(it[0], it[1]) })

        val abnormal = vitals.observeAbnormalVitalSigns(A).first()
        assertEquals(setOf("Heart Rate", "Body Temperature"), abnormal.map { it.typeName }.toSet())
        assertEquals(4, vitals.observeVitalSigns(A).first().size)
    }

    @Test
    fun editRecomputesFlagAndDeleteRemoves() = runBlocking<Unit> {
        val hr = type("Heart Rate").vitalTypeId
        val id = vitals.addVitalSign(A, hr, 72.0)!!.vitalId
        val stored = vitals.getVitalSign(A, id)!!
        assertTrue(vitals.editVitalSign(A, stored.copy(value = 150.0)))
        assertTrue(vitals.getVitalSign(A, id)!!.isAbnormal)
        assertTrue(vitals.deleteVitalSign(A, id))
        assertNull(vitals.getVitalSign(A, id))
    }

    @Test
    fun editedRangesApplyWithNoCodeChange() = runBlocking<Unit> {
        val hr = type("Heart Rate")
        assertTrue(maintenance.editVitalType(hr.copy(normalMax = 120.0)))
        assertFalse(vitals.addVitalSign(A, hr.vitalTypeId, 110.0)!!.isAbnormal)
    }

    @Test
    fun vitalTypeInUseCannotBeDeleted() = runBlocking<Unit> {
        val hr = type("Heart Rate").vitalTypeId
        vitals.addVitalSign(A, hr, 72.0)
        assertFalse(maintenance.deleteVitalType(hr))
        val newType = maintenance.addVitalType(VitalType(name = "Weight Index", unit = "", normalMin = 18.5, normalMax = 25.0))
        assertNotNull(newType)
        assertNull(maintenance.addVitalType(VitalType(name = "Heart Rate", unit = "bpm", normalMin = 1.0, normalMax = 2.0)))
        assertTrue(maintenance.deleteVitalType(newType!!))
    }
}
