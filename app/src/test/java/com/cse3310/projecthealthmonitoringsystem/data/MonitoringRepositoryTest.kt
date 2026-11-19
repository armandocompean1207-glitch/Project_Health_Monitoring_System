package com.cse3310.projecthealthmonitoringsystem.data

import com.cse3310.projecthealthmonitoringsystem.data.entity.AlertType
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseStatus
import com.cse3310.projecthealthmonitoringsystem.data.repository.MonitoringRepository.Companion.DEFAULT_MISSED_WINDOW_MS
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringRepositoryTest : DbTest() {

    @Test
    fun missedDoseRuleMarksDosesAndRaisesOneAlertEach() = runBlocking<Unit> {
        val aspirin = addMedication(A, "Aspirin", "81 mg")
        val d1 = medications.scheduleDose(A, aspirin, NOON)!!
        val d2 = medications.scheduleDose(A, aspirin, NOON + 5 * MINUTE)!!
        val taken = medications.scheduleDose(A, aspirin, NOON - DAY)!!
        medications.markDoseTaken(A, taken)
        val bMed = addMedication(B, "Aspirin")
        val bDose = medications.scheduleDose(B, bMed, NOON)!!

        assertTrue(monitoring.checkMissedDoses(A, now = NOON + DEFAULT_MISSED_WINDOW_MS - 1).isEmpty())

        val alerts = monitoring.checkMissedDoses(A, now = NOON + DEFAULT_MISSED_WINDOW_MS + 5 * MINUTE)
        assertEquals(setOf(d1, d2), alerts.map { it.doseId }.toSet())
        alerts.forEach {
            assertEquals(AlertType.MISSED_DOSE, it.type)
            assertEquals(A, it.userId)
            assertEquals("Missed dose: Aspirin 81 mg", it.message)
        }
        assertEquals(DoseStatus.MISSED, medications.getDose(A, d1)!!.status)
        assertEquals(DoseStatus.TAKEN, medications.getDose(A, taken)!!.status)
        assertEquals(DoseStatus.SCHEDULED, medications.getDose(B, bDose)!!.status)

        // running again finds nothing new
        assertTrue(monitoring.checkMissedDoses(A, now = NOON + DAY).isEmpty())
        assertEquals(2, monitoring.observeAlerts(A).first().size)
        assertTrue(monitoring.observeAlerts(B).first().isEmpty())
    }

    @Test
    fun singleDoseCheckForTheMissedWindowAlarm() = runBlocking<Unit> {
        val id = addMedication(A, "Metformin", "500 mg")
        val dose = medications.scheduleDose(A, id, NOON)!!

        assertNull(monitoring.checkMissedDose(A, dose, now = NOON + 10 * MINUTE))
        assertNull(monitoring.checkMissedDose(B, dose, now = NOON + HOUR))
        val alert = monitoring.checkMissedDose(A, dose, now = NOON + HOUR)
        assertNotNull(alert)
        assertEquals("Missed dose: Metformin 500 mg", alert!!.message)
        assertNull(monitoring.checkMissedDose(A, dose, now = NOON + 2 * HOUR))
        assertEquals(1, monitoring.observeAlerts(A).first().size)
    }

    @Test
    fun takenDoseNeverRaisesAlert() = runBlocking<Unit> {
        val id = addMedication(A, "Aspirin")
        val dose = medications.scheduleDose(A, id, NOON)!!
        medications.markDoseTaken(A, dose)
        assertNull(monitoring.checkMissedDose(A, dose, now = NOON + DAY))
        assertTrue(monitoring.checkMissedDoses(A, now = NOON + DAY).isEmpty())
    }

    @Test
    fun acknowledgeAndViewDetails() = runBlocking<Unit> {
        val id = addMedication(A, "Aspirin")
        val dose = medications.scheduleDose(A, id, NOON)!!
        val alert = monitoring.checkMissedDose(A, dose, now = NOON + HOUR)!!

        assertEquals(alert.alertId, monitoring.getAlert(A, alert.alertId)?.alertId)
        assertNull(monitoring.getAlert(B, alert.alertId))
        assertEquals(1, monitoring.observeUnacknowledgedAlerts(A).first().size)

        assertFalse(monitoring.acknowledgeAlert(B, alert.alertId))
        now += MINUTE
        assertTrue(monitoring.acknowledgeAlert(A, alert.alertId))
        assertFalse(monitoring.acknowledgeAlert(A, alert.alertId))
        assertEquals(now, monitoring.getAlert(A, alert.alertId)!!.acknowledgedAt)
        assertTrue(monitoring.observeUnacknowledgedAlerts(A).first().isEmpty())
    }

    @Test
    fun conflictAlerts() = runBlocking<Unit> {
        addMedication(A, "Warfarin")
        addMedication(A, "Aspirin")
        val conflicts = medications.detectConflicts(A)
        val ids = monitoring.raiseConflictAlerts(A, conflicts)
        assertEquals(1, ids.size)
        val alert = monitoring.getAlert(A, ids.single())!!
        assertEquals(AlertType.CONFLICT, alert.type)
        assertTrue(alert.message, alert.message.contains("bleeding"))
    }
}
