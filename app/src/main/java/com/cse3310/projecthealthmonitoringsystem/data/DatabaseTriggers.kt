package com.cse3310.projecthealthmonitoringsystem.data

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Tamper protection enforced by SQLite itself, so no app bug can rewrite history.
 *
 * - A DoseLog row may change exactly once, from SCHEDULED to TAKEN or MISSED, and only its status and
 *   recordedAt may change. Every other UPDATE aborts.
 * - A DoseLog row can never be deleted. `recursive_triggers` is turned on in [enableOnOpen] so that
 *   INSERT OR REPLACE (which deletes the old row) also hits the delete trigger.
 * - Changing a medication's name, dosage or intake time writes an AuditLog row (what, from, to, when).
 *   "when" is the medication's new updatedAt, which MedicationRepository sets to the current time.
 */
object DatabaseTriggers {

    const val DOSE_FINAL_MESSAGE = "DoseLog rows change once, from SCHEDULED to TAKEN or MISSED"
    const val DOSE_NO_DELETE_MESSAGE = "DoseLog rows cannot be deleted"

    private val statements = listOf(
        """
        CREATE TRIGGER IF NOT EXISTS doselog_update_once
        BEFORE UPDATE ON DoseLog
        WHEN OLD.status <> 'SCHEDULED'
          OR NEW.status NOT IN ('TAKEN', 'MISSED')
          OR NEW.doseId IS NOT OLD.doseId
          OR NEW.userId IS NOT OLD.userId
          OR NEW.medicationId IS NOT OLD.medicationId
          OR NEW.scheduledAt IS NOT OLD.scheduledAt
        BEGIN
          SELECT RAISE(ABORT, '$DOSE_FINAL_MESSAGE');
        END
        """,
        """
        CREATE TRIGGER IF NOT EXISTS doselog_no_delete
        BEFORE DELETE ON DoseLog
        BEGIN
          SELECT RAISE(ABORT, '$DOSE_NO_DELETE_MESSAGE');
        END
        """,
        auditTrigger("medication_audit_name", column = "name", field = "name"),
        auditTrigger("medication_audit_dosage", column = "dosage", field = "dosage"),
        auditTrigger("medication_audit_intake_time", column = "intakeTimeMinutes", field = "intakeTimeMinutes")
    )

    private fun auditTrigger(name: String, column: String, field: String) = """
        CREATE TRIGGER IF NOT EXISTS $name
        AFTER UPDATE OF $column ON Medication
        WHEN OLD.$column IS NOT NEW.$column
        BEGIN
          INSERT INTO AuditLog (userId, medicationId, field, oldValue, newValue, changedAt)
          VALUES (NEW.userId, NEW.medicationId, '$field', CAST(OLD.$column AS TEXT), CAST(NEW.$column AS TEXT), NEW.updatedAt);
        END
        """

    /** Called from RoomDatabase.Callback.onCreate. */
    fun create(db: SupportSQLiteDatabase) {
        statements.forEach { db.execSQL(it.trimIndent()) }
    }

    /** Additional schedule audit for version 3; all existing dose protections remain unchanged. */
    fun createMedicationTimesAudit(db: SupportSQLiteDatabase) {
        db.execSQL(auditTrigger("medication_audit_dose_times", "doseTimesMinutes", "doseTimesMinutes").trimIndent())
    }

    /** Called from RoomDatabase.Callback.onOpen on every connection open. */
    fun enableOnOpen(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA recursive_triggers = ON")
    }
}
