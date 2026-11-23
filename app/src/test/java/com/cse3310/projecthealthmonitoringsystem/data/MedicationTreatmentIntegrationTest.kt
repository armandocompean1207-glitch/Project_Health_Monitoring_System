package com.cse3310.projecthealthmonitoringsystem.data

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cse3310.projecthealthmonitoringsystem.medication.*
import com.cse3310.projecthealthmonitoringsystem.monitoring.DoseAlarms
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.Shadows

class MedicationTreatmentIntegrationTest : DbTest() {
    private val start get() = MedicationTimes.dayStart(NOON)
    private fun custom() = medication(A, "Fictional medicine").copy(startDate = start,
        endDate = MedicationTimes.dayEnd(NOON), frequency = Frequency.label(2), intakeTimeMinutes = 480,
        doseTimesMinutes = MedicationTimes.encode(listOf(480, 13 * 60 + 5)))

    @Test fun selectedTimesPersistRejectOtherTimesAndEditsRetainFinalIntakesAndAudit() = runBlocking<Unit> {
        val id = medications.addMedication(A, custom())!!
        val stored = medications.getMedication(A, id)!!
        assertEquals("480,785", stored.doseTimesMinutes)
        val times = scheduledIntakes(stored, start, stored.endDate!!)
        assertEquals(2, times.size)
        assertTrue(medications.observeDoseLog(A, start, stored.endDate).first().isEmpty())
        assertNull(medications.scheduleDose(A, id, start - 1))
        assertNull(medications.scheduleDose(A, id, stored.endDate + 1))
        assertNull(medications.scheduleDose(A, id, times.first() + MINUTE))
        val doseId = medications.scheduleDose(A, id, times.first())!!
        assertTrue(medications.markDoseTaken(A, doseId))
        val snapshot = medications.getDose(A, doseId)
        assertTrue(medications.editMedication(A, stored.copy(doseTimesMinutes = "480,1030")))
        assertEquals(snapshot, medications.getDose(A, doseId))
        assertEquals(DoseStatus.TAKEN, medications.getDose(A, doseId)!!.status)
        assertFalse(medications.markDoseMissed(A, doseId))
        assertNull(medications.getMedication(B, id))
        val audit = medications.getAuditLog(A, id)
        assertTrue(audit.any { it.field == "doseTimesMinutes" && it.oldValue == "480,785" && it.newValue == "480,1030" })
    }

    @Test fun repositoryRejectsDuplicateIncompleteOrReversedTreatmentData() = runBlocking<Unit> {
        suspend fun rejects(action: suspend () -> Unit) {
            try { action(); fail("Expected invalid medication to be rejected") } catch (_: IllegalArgumentException) {}
        }
        rejects { medications.addMedication(A, custom().copy(doseTimesMinutes = "480,480")) }
        rejects { medications.addMedication(A, custom().copy(doseTimesMinutes = "480")) }
        rejects { medications.addMedication(A, custom().copy(endDate = start - 1)) }
        rejects { medications.addMedication(A, custom().copy(doseTimesMinutes = "480,1440")) }
        rejects { medications.addMedication(A, custom().copy(intakeTimeMinutes = 500)) }
    }

    @Test fun alarmAdvancesToSelectedTimeCancelsAfterEndAndRespectsReminderOwner() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val owner = context.getSharedPreferences("reminder_account", Context.MODE_PRIVATE)
        val alarms = Shadows.shadowOf(context.getSystemService(AlarmManager::class.java))
        try {
            owner.edit().putString("owner", A).commit()
            val id = medications.addMedication(A, custom())!!
            val stored = medications.getMedication(A, id)!!
            val expected = nextScheduledIntake(stored, NOON)!!
            DoseAlarms.scheduleNextReminder(context, stored, NOON)
            assertEquals(expected, alarms.scheduledAlarms.single().triggerAtTime)
            DoseAlarms.scheduleNextReminder(context, stored, stored.endDate!! + 1)
            assertTrue(alarms.scheduledAlarms.isEmpty())
            owner.edit().putString("owner", B).commit()
            DoseAlarms.scheduleNextReminder(context, stored, NOON)
            assertTrue(alarms.scheduledAlarms.isEmpty())
        } finally {
            owner.edit().clear().commit()
        }
    }
}
