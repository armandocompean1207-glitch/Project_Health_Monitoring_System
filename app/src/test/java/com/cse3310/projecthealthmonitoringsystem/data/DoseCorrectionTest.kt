package com.cse3310.projecthealthmonitoringsystem.data

import com.cse3310.projecthealthmonitoringsystem.data.entity.*
import com.cse3310.projecthealthmonitoringsystem.medication.medicationDayProgress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import java.util.TimeZone

class DoseCorrectionTest : DbTest() {
    private lateinit var originalZone: TimeZone
    @Before fun useUtcFixture() { originalZone = TimeZone.getDefault(); TimeZone.setDefault(UTC) }
    @After fun restoreZone() { TimeZone.setDefault(originalZone) }
    private val eight get() = NOON - 4 * HOUR
    private suspend fun record(med: Long, status: DoseStatus = DoseStatus.TAKEN, actual: Long? = eight + 5 * MINUTE,
        revision: Long = 0) = monitoring.recordIntake(A, med, eight, status, actual, "Patient confirmed this entry", revision)
    private suspend fun latest(id: Long) = medications.intakeCorrectionHistory(A, id).last().correctionId

    @Test fun lateUnrecordedDoseUsesActualTimeAndNeverInventsIntakeFromClock() = runBlocking<Unit> {
        val med = addMedication(A, "Fictional")
        assertTrue(medications.observeDoseLog(A, NOON - DAY, NOON).first().isEmpty())
        val id = record(med)
        assertEquals(DoseStatus.SCHEDULED, medications.originalDose(A, id)!!.status)
        assertEquals(DoseStatus.TAKEN, medications.getDose(A, id)!!.status)
        assertEquals(eight + 5 * MINUTE, medications.getDose(A, id)!!.recordedAt)
        val correction = medications.intakeCorrectionHistory(A, id).single()
        assertEquals(NOON, correction.changedAt)
        assertEquals(eight + 5 * MINUTE, correction.actualTakenAt)
        assertTrue(monitoring.checkMissedDoses(A).isEmpty())
    }

    @Test fun correctionsPreserveFinalOriginalAndResolveAlertWithoutDeletingHistory() = runBlocking<Unit> {
        val med = addMedication(A, "Fictional")
        val id = medications.scheduleDose(A, med, eight)!!
        val alert = monitoring.checkMissedDose(A, id)!!
        val original = medications.originalDose(A, id)!!
        assertEquals(DoseStatus.MISSED, original.status)
        assertEquals(id, record(med))
        assertEquals(original, medications.originalDose(A, id))
        assertNotNull(monitoring.getAlert(A, alert.alertId)!!.acknowledgedAt)
        val rev = latest(id)
        record(med, DoseStatus.SKIPPED, null, rev)
        val skipped = medications.getDose(A, id)!!
        assertEquals(DoseStatus.SKIPPED, skipped.status)
        val progress = medicationDayProgress(listOf(med to eight), listOf(skipped), setOf(med), now)
        assertEquals(0, progress.taken)
        assertEquals(1, progress.skipped)
        assertEquals(0, progress.remaining)
        assertEquals(0, progress.missed)
        record(med, DoseStatus.SCHEDULED, null, latest(id))
        assertEquals(DoseStatus.SCHEDULED, medications.getDose(A, id)!!.status)
        monitoring.checkMissedDose(A, id)
        assertEquals(DoseStatus.MISSED, medications.getDose(A, id)!!.status)
        assertEquals(original, medications.originalDose(A, id))
        assertEquals(1, monitoring.observeAlerts(A).first().size)
    }

    @Test fun concurrentRepeatedConfirmationsHaveOneOccurrenceAndOneEntry() = runBlocking<Unit> {
        val med = addMedication(A, "Fictional")
        val ids = coroutineScope { (1..5).map { async { record(med) } }.awaitAll() }
        assertEquals(1, ids.toSet().size)
        assertEquals(1, medications.intakeCorrectionHistory(A, ids.first()).size)
        assertEquals(1, medications.observeDoseLog(A, NOON - DAY, NOON).first().size)
        val stale = runCatching { record(med, DoseStatus.SKIPPED, null, 0) }
        assertTrue(stale.exceptionOrNull() is IllegalStateException)
    }

    @Test fun invalidFutureCrossAccountAndUnscheduledEntriesAreRejected() = runBlocking<Unit> {
        val med = addMedication(A, "Fictional")
        suspend fun rejects(block: suspend () -> Unit) {
            try { block(); fail("Expected validation failure") } catch (_: IllegalArgumentException) { }
        }
        rejects { record(med, actual = now + MINUTE) }
        rejects { monitoring.recordIntake(B, med, eight, DoseStatus.TAKEN, eight, "Wrong owner", 0) }
        rejects { monitoring.recordIntake(A, med, NOON, DoseStatus.TAKEN, NOON, "Unscheduled", 0) }
        rejects { monitoring.recordIntake(A, med, eight, DoseStatus.TAKEN, eight, "", 0) }
        assertTrue(medications.observeDoseLog(A, NOON - DAY, NOON).first().isEmpty())
    }

    @Test fun skipsSuppressMissedChecksAndUndoRestoresMonitoringWithoutWeakeningTriggers() = runBlocking<Unit> {
        val med = addMedication(A, "Fictional")
        val id = record(med, DoseStatus.SKIPPED, null)
        assertNull(monitoring.checkMissedDose(A, id))
        assertTrue(monitoring.checkMissedDoses(A).isEmpty())
        val revision = latest(id)
        assertTrue(runCatching { sql.execSQL("UPDATE DoseLog SET status='MISSED', recordedAt=? WHERE doseId=?", arrayOf(NOON, id)) }.isFailure)
        assertTrue(runCatching { sql.execSQL("UPDATE DoseCorrection SET reason='tamper' WHERE correctionId=?", arrayOf(revision)) }.isFailure)
        assertTrue(runCatching { sql.execSQL("DELETE FROM DoseCorrection WHERE correctionId=?", arrayOf(revision)) }.isFailure)
        assertTrue(runCatching {
            sql.execSQL("INSERT INTO DoseCorrection(userId,doseId,status,actualTakenAt,changedAt,reason) VALUES (?,?,?,NULL,?,?)",
                arrayOf<Any>(B, id, "SKIPPED", NOON, "Foreign account"))
        }.isFailure)
        record(med, DoseStatus.SCHEDULED, null, revision)
        assertNotNull(monitoring.checkMissedDose(A, id))
        assertEquals(DoseStatus.MISSED, medications.getDose(A, id)!!.status)
        assertEquals(1, monitoring.observeAlerts(A).first().size)
    }

    @Test fun effectiveRoomFlowImmediatelyEmitsCorrectionsForHomeAndHistory() = runBlocking<Unit> {
        val med = addMedication(A, "Fictional")
        val id = medications.scheduleDose(A, med, eight)!!
        val listening = CompletableDeferred<Unit>()
        val emissions = async {
            medications.observeDoseLog(A, NOON - DAY, NOON).onEach { listening.complete(Unit) }.take(2).toList()
        }
        withTimeout(5000) { listening.await() }
        record(med)
        val states = withTimeout(5000) { emissions.await() }
        assertEquals(DoseStatus.SCHEDULED, states.first().single().dose.status)
        assertEquals(DoseStatus.TAKEN, states.last().single().dose.status)
        assertEquals(id, states.last().single().dose.doseId)
    }
}
