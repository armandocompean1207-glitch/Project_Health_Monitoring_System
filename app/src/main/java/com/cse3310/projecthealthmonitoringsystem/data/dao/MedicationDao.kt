package com.cse3310.projecthealthmonitoringsystem.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.cse3310.projecthealthmonitoringsystem.data.entity.AuditLog
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseLog
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseStatus
import com.cse3310.projecthealthmonitoringsystem.data.entity.Medication
import kotlinx.coroutines.flow.Flow

/**
 * Medication, DoseLog, AuditLog and the conflict check. Every query is scoped by userId.
 *
 * There is no DoseLog delete and no general DoseLog update: the only allowed change is [markDose],
 * and SQLite triggers reject anything else.
 */
@Dao
abstract class MedicationDao {

    @Query("SELECT EXISTS(SELECT 1 FROM Pharmacy WHERE pharmacyId = :pharmacyId AND userId = :userId)")
    abstract suspend fun pharmacyOwned(userId: String, pharmacyId: Long): Boolean

    // ---- Medication ----

    @Insert
    abstract suspend fun insertMedication(medication: Medication): Long

    @Update
    protected abstract suspend fun updateMedicationRow(medication: Medication): Int

    /** Updates only if the row exists and belongs to [userId]. Name, dosage and intake time changes are audited by triggers. */
    @Transaction
    open suspend fun updateMedication(userId: String, medication: Medication): Boolean =
        medication.userId == userId &&
            medicationOwned(userId, medication.medicationId) &&
            updateMedicationRow(medication) > 0

    /** Hard delete. Fails with a foreign-key error if the medication has doses; use [deactivateMedication] then. */
    @Query("DELETE FROM Medication WHERE medicationId = :medicationId AND userId = :userId")
    abstract suspend fun deleteMedication(userId: String, medicationId: Long): Int

    @Query("UPDATE Medication SET isActive = 0, updatedAt = :now WHERE medicationId = :medicationId AND userId = :userId")
    abstract suspend fun deactivateMedication(userId: String, medicationId: Long, now: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM Medication WHERE medicationId = :medicationId AND userId = :userId)")
    abstract suspend fun medicationOwned(userId: String, medicationId: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM Doctor WHERE doctorId = :doctorId AND userId = :userId)")
    abstract suspend fun doctorOwned(userId: String, doctorId: Long): Boolean

    @Query("SELECT * FROM Medication WHERE medicationId = :medicationId AND userId = :userId")
    abstract suspend fun getMedication(userId: String, medicationId: Long): Medication?

    @Query("SELECT * FROM Medication WHERE userId = :userId AND isActive = 1 ORDER BY name COLLATE NOCASE")
    abstract fun observeActiveMedications(userId: String): Flow<List<Medication>>

    /** For rescheduling alarms after reboot. */
    @Query("SELECT * FROM Medication WHERE userId = :userId AND isActive = 1 ORDER BY name COLLATE NOCASE")
    abstract suspend fun getActiveMedications(userId: String): List<Medication>

    @Query("SELECT * FROM Medication WHERE userId = :userId ORDER BY isActive DESC, name COLLATE NOCASE")
    abstract fun observeAllMedications(userId: String): Flow<List<Medication>>

    // ---- DoseLog ----

    @Query("SELECT COUNT(*) FROM DoseLog WHERE medicationId = :medicationId AND userId = :userId")
    abstract suspend fun doseCount(userId: String, medicationId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertDose(dose: DoseLog): Long

    /** Returns -1 if a dose for the same medication and time already exists. Never replaces. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertDoseIfAbsent(dose: DoseLog): Long

    @Query("SELECT * FROM EffectiveDoseLog WHERE doseId = :doseId AND userId = :userId")
    abstract suspend fun getDose(userId: String, doseId: Long): DoseLog?

    @Query("SELECT * FROM EffectiveDoseLog WHERE userId = :userId AND medicationId = :medicationId AND scheduledAt = :scheduledAt")
    abstract suspend fun getDoseAt(userId: String, medicationId: Long, scheduledAt: Long): DoseLog?

    @Query(
        """
        SELECT * FROM EffectiveDoseLog
        WHERE userId = :userId AND medicationId = :medicationId AND scheduledAt BETWEEN :from AND :to
        ORDER BY scheduledAt
        """
    )
    abstract fun observeDosesForMedication(userId: String, medicationId: Long, from: Long, to: Long): Flow<List<DoseLog>>

    @Query(
        """
        SELECT dl.*, m.name AS medicationName, m.dosage AS dosage
        FROM EffectiveDoseLog dl JOIN Medication m ON m.medicationId = dl.medicationId AND m.userId = dl.userId
        WHERE dl.userId = :userId AND dl.scheduledAt BETWEEN :from AND :to
        ORDER BY dl.scheduledAt
        """
    )
    abstract fun observeDoseLog(userId: String, from: Long, to: Long): Flow<List<DoseWithMedication>>

    /** SCHEDULED -> TAKEN or MISSED. Returns 0 if the dose is not this user's or is already final. */
    @Query(
        """
        UPDATE DoseLog SET status = :status, recordedAt = :recordedAt
        WHERE doseId = :doseId AND userId = :userId AND status = 'SCHEDULED'
        """
    )
    abstract suspend fun markDose(userId: String, doseId: Long, status: DoseStatus, recordedAt: Long): Int

    /**
     * Missed-dose query: SCHEDULED doses whose scheduledAt plus the missed window has passed.
     * Written as `scheduledAt <= now - window` so the (userId, status, scheduledAt) index is used.
     */
    @Query(
        """
        SELECT dl.*, m.name AS medicationName, m.dosage AS dosage
        FROM EffectiveDoseLog dl JOIN Medication m ON m.medicationId = dl.medicationId AND m.userId = dl.userId
        WHERE dl.userId = :userId AND dl.status = 'SCHEDULED' AND dl.scheduledAt <= :now - :windowMs
        ORDER BY dl.scheduledAt
        """
    )
    abstract suspend fun getOverdueScheduledDoses(userId: String, now: Long, windowMs: Long): List<DoseWithMedication>

    /** Marks many doses MISSED in one statement (no per-row loop). Already-final doses are skipped. */
    @Query(
        """
        UPDATE DoseLog SET status = 'MISSED', recordedAt = :recordedAt
        WHERE userId = :userId AND doseId IN (:doseIds) AND status = 'SCHEDULED'
        """
    )
    abstract suspend fun markDosesMissed(userId: String, doseIds: List<Long>, recordedAt: Long): Int

    @Query("SELECT * FROM DoseLog WHERE userId = :userId AND doseId = :doseId")
    abstract suspend fun originalDose(userId: String, doseId: Long): DoseLog?

    @Insert
    abstract suspend fun insertCorrection(correction: com.cse3310.projecthealthmonitoringsystem.data.entity.DoseCorrection): Long

    @Query("SELECT * FROM DoseCorrection WHERE userId = :userId AND doseId = :doseId ORDER BY correctionId DESC LIMIT 1")
    abstract suspend fun latestCorrection(userId: String, doseId: Long): com.cse3310.projecthealthmonitoringsystem.data.entity.DoseCorrection?

    @Query("""
        SELECT c.* FROM DoseCorrection c JOIN DoseLog d ON c.doseId=d.doseId AND c.userId=d.userId
        WHERE c.userId=:userId AND d.scheduledAt BETWEEN :from AND :to ORDER BY c.correctionId
    """)
    abstract fun observeCorrections(userId: String, from: Long, to: Long): Flow<List<com.cse3310.projecthealthmonitoringsystem.data.entity.DoseCorrection>>

    @Query("SELECT * FROM DoseCorrection WHERE userId=:userId AND doseId=:doseId ORDER BY correctionId")
    abstract suspend fun correctionHistory(userId: String, doseId: Long): List<com.cse3310.projecthealthmonitoringsystem.data.entity.DoseCorrection>

    // ---- AuditLog (rows are written by triggers) ----

    @Query("SELECT * FROM AuditLog WHERE userId = :userId AND medicationId = :medicationId ORDER BY changedAt DESC, auditId DESC")
    abstract fun observeAuditLog(userId: String, medicationId: Long): Flow<List<AuditLog>>

    @Query("SELECT * FROM AuditLog WHERE userId = :userId AND medicationId = :medicationId ORDER BY changedAt DESC, auditId DESC")
    abstract suspend fun getAuditLog(userId: String, medicationId: Long): List<AuditLog>

    // ---- Conflict check (DetectConflict) ----

    /** Every pair of the user's active medications that matches a ConflictRule. */
    @Query(
        """
        SELECT r.ruleId, r.severity, r.description,
               a.medicationId AS medicationIdA, a.name AS medicationNameA,
               b.medicationId AS medicationIdB, b.name AS medicationNameB
        FROM ConflictRule r
        JOIN Medication a ON a.userId = :userId AND a.isActive = 1 AND lower(trim(a.name)) = r.drugA
        JOIN Medication b ON b.userId = :userId AND b.isActive = 1 AND lower(trim(b.name)) = r.drugB
        ORDER BY r.drugA, r.drugB
        """
    )
    abstract suspend fun getConflictsAmongActive(userId: String): List<MedicationConflict>

    /**
     * Active medications that conflict with [candidateName] (already trimmed and lowercased).
     * [excludeMedicationId] skips the medication being edited; pass 0 when adding.
     */
    @Query(
        """
        SELECT r.ruleId, r.severity, r.description,
               m.medicationId AS conflictingMedicationId, m.name AS conflictingMedicationName
        FROM ConflictRule r
        JOIN Medication m ON m.userId = :userId AND m.isActive = 1 AND m.medicationId <> :excludeMedicationId
        WHERE (r.drugA = :candidateName AND lower(trim(m.name)) = r.drugB)
           OR (r.drugB = :candidateName AND lower(trim(m.name)) = r.drugA)
        ORDER BY m.name
        """
    )
    abstract suspend fun getConflictsForCandidate(
        userId: String,
        candidateName: String,
        excludeMedicationId: Long
    ): List<CandidateConflict>
}
