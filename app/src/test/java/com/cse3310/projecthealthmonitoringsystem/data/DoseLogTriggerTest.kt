package com.cse3310.projecthealthmonitoringsystem.data

import android.database.sqlite.SQLiteConstraintException
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseLog
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The database itself must reject any rewrite of dose history, even from raw SQL. */
class DoseLogTriggerTest : DbTest() {

    private var medId = 0L
    private var doseId = 0L

    private fun setUpDose() = runBlocking<Unit> {
        medId = addMedication(A, "Aspirin")
        doseId = medications.scheduleDose(A, medId, NOON)!!
    }

    private fun status(): String =
        sql.query("SELECT status FROM DoseLog WHERE doseId = $doseId").use { it.moveToFirst(); it.getString(0) }

    private fun assertRejected(statement: String) {
        val e = assertThrows(SQLiteConstraintException::class.java) { sql.execSQL(statement) }
        assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("DoseLog"))
    }

    @Test
    fun scheduledCanBecomeTaken() {
        setUpDose()
        sql.execSQL("UPDATE DoseLog SET status = 'TAKEN', recordedAt = 1 WHERE doseId = $doseId")
        assertEquals("TAKEN", status())
    }

    @Test
    fun scheduledCanBecomeMissed() {
        setUpDose()
        sql.execSQL("UPDATE DoseLog SET status = 'MISSED', recordedAt = 1 WHERE doseId = $doseId")
        assertEquals("MISSED", status())
    }

    @Test
    fun takenIsFinal() {
        setUpDose()
        sql.execSQL("UPDATE DoseLog SET status = 'TAKEN' WHERE doseId = $doseId")
        assertRejected("UPDATE DoseLog SET status = 'MISSED' WHERE doseId = $doseId")
        assertRejected("UPDATE DoseLog SET status = 'SCHEDULED' WHERE doseId = $doseId")
        assertRejected("UPDATE DoseLog SET recordedAt = 99 WHERE doseId = $doseId")
        assertEquals("TAKEN", status())
    }

    @Test
    fun missedIsFinal() {
        setUpDose()
        sql.execSQL("UPDATE DoseLog SET status = 'MISSED' WHERE doseId = $doseId")
        assertRejected("UPDATE DoseLog SET status = 'TAKEN' WHERE doseId = $doseId")
        assertRejected("UPDATE DoseLog SET status = 'SCHEDULED' WHERE doseId = $doseId")
        assertEquals("MISSED", status())
    }

    @Test
    fun scheduledRowCannotBeRewritten() {
        setUpDose()
        assertRejected("UPDATE DoseLog SET status = 'SCHEDULED' WHERE doseId = $doseId")
        assertRejected("UPDATE DoseLog SET status = 'BOGUS' WHERE doseId = $doseId")
        assertRejected("UPDATE DoseLog SET status = 'TAKEN', scheduledAt = 5 WHERE doseId = $doseId")
        assertRejected("UPDATE DoseLog SET status = 'TAKEN', userId = '$B' WHERE doseId = $doseId")
        assertRejected("UPDATE DoseLog SET status = 'TAKEN', medicationId = 999 WHERE doseId = $doseId")
        assertRejected("UPDATE DoseLog SET status = 'TAKEN', doseId = 999 WHERE doseId = $doseId")
        assertEquals("SCHEDULED", status())
    }

    @Test
    fun doseCannotBeDeletedInAnyState() {
        setUpDose()
        assertRejected("DELETE FROM DoseLog WHERE doseId = $doseId")
        sql.execSQL("UPDATE DoseLog SET status = 'TAKEN' WHERE doseId = $doseId")
        assertRejected("DELETE FROM DoseLog")
        assertEquals(1, count("DoseLog"))
    }

    @Test
    fun insertOrReplaceCannotOverwriteADose() {
        setUpDose()
        sql.execSQL("UPDATE DoseLog SET status = 'TAKEN' WHERE doseId = $doseId")
        assertRejected(
            "INSERT OR REPLACE INTO DoseLog (doseId, userId, medicationId, scheduledAt, status) " +
                "VALUES ($doseId, '$A', $medId, $NOON, 'SCHEDULED')"
        )
        assertEquals("TAKEN", status())
    }

    @Test
    fun medicationWithDosesCannotBeHardDeleted() {
        setUpDose()
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { db.medicationDao().deleteMedication(A, medId) }
        }
        assertEquals(1, count("Medication", "medicationId = $medId"))
    }

    @Test
    fun daoInsertOfDuplicateDoseDoesNotReplace() = runBlocking<Unit> {
        setUpDose()
        medications.markDoseTaken(A, doseId)
        val dup = DoseLog(userId = A, medicationId = medId, scheduledAt = NOON, status = DoseStatus.SCHEDULED)
        assertEquals(-1L, db.medicationDao().insertDoseIfAbsent(dup))
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { db.medicationDao().insertDose(dup) }
        }
        assertEquals("TAKEN", status())
    }
}
