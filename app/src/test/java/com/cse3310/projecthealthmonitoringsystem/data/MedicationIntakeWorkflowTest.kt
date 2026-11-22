package com.cse3310.projecthealthmonitoringsystem.data

import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseStatus
import com.cse3310.projecthealthmonitoringsystem.data.repository.MonitoringRepository
import com.cse3310.projecthealthmonitoringsystem.data.repository.MonitoringRepository.IntakeOutcome
import com.cse3310.projecthealthmonitoringsystem.medication.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class MedicationIntakeWorkflowTest : DbTest() {
    private suspend fun prescription(at: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = at }
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return medications.addMedication(A, medication(A, "Fictional intake medicine").copy(
            intakeTimeMinutes = minutes, frequency = "Once a day", startDate = MedicationTimes.dayStart(at),
            doseTimesMinutes = MedicationTimes.encode(listOf(minutes))))!!
    }

    @Test fun scheduledBecomesDueAtTimeButPassingTimeNeverTakesIt() = runBlocking<Unit> {
        val at = NOON + MINUTE
        val id = prescription(at)
        assertEquals(MedicationIntakeState.SCHEDULED, medicationIntakeState(null, at, now, true))
        assertEquals(IntakeOutcome.TOO_EARLY, monitoring.confirmMedicationIntake(A, id, at).outcome)
        assertTrue(medications.observeDoseLog(A, 0, Long.MAX_VALUE).first().isEmpty())
        now = at
        assertEquals(MedicationIntakeState.DUE, medicationIntakeState(null, at, now, true))
        assertTrue(medications.observeDoseLog(A, 0, Long.MAX_VALUE).first().isEmpty())
        val pending = medications.scheduleDose(A, id, at)!!
        now += MINUTE
        assertEquals(DoseStatus.SCHEDULED, medications.getDose(A, pending)!!.status)
        assertEquals(MedicationIntakeState.DUE, medicationIntakeState(DoseStatus.SCHEDULED, at, now, true))
        now = at + MonitoringRepository.DEFAULT_MISSED_WINDOW_MS
        val alerts = monitoring.checkMissedDoses(A)
        assertEquals(1, alerts.size)
        assertEquals(DoseStatus.MISSED, medications.getDose(A, pending)!!.status)
        assertEquals(MedicationIntakeState.MISSED, medicationIntakeState(DoseStatus.MISSED, at, now, true))
    }

    @Test fun explicitConfirmationSavesActualTimeAndUpdatesHomeHistoryAndRemainingFlows() = runBlocking<Unit> {
        val at = NOON
        val id = prescription(at)
        medications.scheduleDose(A, id, at)
        val range = MedicationTimes.dayStart(at) to MedicationTimes.dayEnd(at)
        val homeUpdate = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5000) { medications.observeDoseLog(A, range.first, range.second).first { rows -> rows.any { it.dose.status == DoseStatus.TAKEN } } }
        }
        val historyUpdate = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5000) { medications.observeDoses(A, id, range.first, range.second).first { rows -> rows.any { it.status == DoseStatus.TAKEN } } }
        }
        now = at + 7 * MINUTE
        val result = monitoring.confirmMedicationIntake(A, id, at)
        assertEquals(IntakeOutcome.TAKEN, result.outcome)
        assertEquals(now, result.recordedAt)
        val home = homeUpdate.await()
        assertEquals(now, home.single().dose.recordedAt)
        assertEquals(now, historyUpdate.await().single().recordedAt)
        assertEquals(1, home.count { it.dose.status == DoseStatus.TAKEN })
        val day = medicationDayProgress(listOf(id to at), home.map { it.dose }, setOf(id), now)
        assertEquals(1, day.total)
        assertEquals(1, day.taken)
        assertEquals(0, day.remaining)
        assertEquals(MedicationIntakeState.TAKEN, medicationIntakeState(home.single().dose.status, at, now, true))
        assertTrue(medications.observeDoseLog(B, 0, Long.MAX_VALUE).first().isEmpty())
    }

    @Test fun simultaneousAndRepeatedConfirmationsPreserveOneRowAndOriginalTimestamp() = runBlocking<Unit> {
        val id = prescription(NOON)
        now = NOON + MINUTE
        val attempts = coroutineScope {
            listOf(async(Dispatchers.Default) { monitoring.confirmMedicationIntake(A, id, NOON) },
                async(Dispatchers.Default) { monitoring.confirmMedicationIntake(A, id, NOON) }).awaitAll()
        }
        assertEquals(1, attempts.count { it.outcome == IntakeOutcome.TAKEN })
        assertEquals(1, attempts.count { it.outcome == IntakeOutcome.ALREADY_TAKEN })
        val saved = medications.observeDoseLog(A, 0, Long.MAX_VALUE).first().single().dose
        assertEquals(now, saved.recordedAt)
        now += 10 * MINUTE
        val repeated = monitoring.confirmMedicationIntake(A, id, NOON)
        assertEquals(IntakeOutcome.ALREADY_TAKEN, repeated.outcome)
        assertEquals(saved.recordedAt, repeated.recordedAt)
        assertEquals(saved, medications.getDose(A, saved.doseId))
    }

    @Test fun lateConfirmationUsesUnchangedMissedRuleAndCreatesOnlyOneAlert() = runBlocking<Unit> {
        val id = prescription(NOON)
        val dose = medications.scheduleDose(A, id, NOON)!!
        now = NOON + MonitoringRepository.DEFAULT_MISSED_WINDOW_MS
        val late = monitoring.confirmMedicationIntake(A, id, NOON)
        assertEquals(IntakeOutcome.MISSED, late.outcome)
        assertNotNull(late.alert)
        assertEquals(DoseStatus.MISSED, medications.getDose(A, dose)!!.status)
        assertEquals(now, medications.getDose(A, dose)!!.recordedAt)
        val repeated = monitoring.confirmMedicationIntake(A, id, NOON)
        assertEquals(IntakeOutcome.MISSED, repeated.outcome)
        assertNull(repeated.alert)
        assertEquals(1, monitoring.observeAlerts(A).first().size)
        assertFalse(medications.markDoseTaken(A, dose))
    }

    @Test fun justBeforeDeadlineCanBeTakenAndMissedCheckCannotOverwriteIt() = runBlocking<Unit> {
        val id = prescription(NOON)
        val dose = medications.scheduleDose(A, id, NOON)!!
        now = NOON + MonitoringRepository.DEFAULT_MISSED_WINDOW_MS - 1
        val result = monitoring.confirmMedicationIntake(A, id, NOON)
        assertEquals(IntakeOutcome.TAKEN, result.outcome)
        val takenAt = now
        now += MINUTE
        assertNull(monitoring.checkMissedDose(A, dose))
        assertEquals(DoseStatus.TAKEN, medications.getDose(A, dose)!!.status)
        assertEquals(takenAt, medications.getDose(A, dose)!!.recordedAt)
        assertTrue(monitoring.observeAlerts(A).first().isEmpty())
    }

    @Test fun expiredUnrecordedOccurrenceNeverCreatesFalseTakenOrMissedHistory() = runBlocking<Unit> {
        val id = prescription(NOON)
        now = NOON + MonitoringRepository.DEFAULT_MISSED_WINDOW_MS
        assertEquals(IntakeOutcome.NOT_RECORDED, monitoring.confirmMedicationIntake(A, id, NOON).outcome)
        assertTrue(medications.observeDoseLog(A, 0, Long.MAX_VALUE).first().isEmpty())
        assertTrue(monitoring.observeAlerts(A).first().isEmpty())
    }

    @Test fun wrongAccountOrUnscheduledOccurrenceCannotConfirmIntake() = runBlocking<Unit> {
        val id = prescription(NOON)
        now = NOON + MINUTE
        assertEquals(IntakeOutcome.UNAVAILABLE, monitoring.confirmMedicationIntake(B, id, NOON).outcome)
        assertEquals(IntakeOutcome.UNAVAILABLE, monitoring.confirmMedicationIntake(A, id, NOON + MINUTE).outcome)
        assertTrue(medications.observeDoseLog(A, 0, Long.MAX_VALUE).first().isEmpty())
    }
}
