package com.cse3310.projecthealthmonitoringsystem.data.repository

import com.cse3310.projecthealthmonitoringsystem.data.dao.CandidateConflict
import com.cse3310.projecthealthmonitoringsystem.data.dao.DoseWithMedication
import com.cse3310.projecthealthmonitoringsystem.data.dao.MedicationConflict
import com.cse3310.projecthealthmonitoringsystem.data.dao.MedicationDao
import com.cse3310.projecthealthmonitoringsystem.data.entity.AuditLog
import com.cse3310.projecthealthmonitoringsystem.data.entity.ConflictRule
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseLog
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseStatus
import com.cse3310.projecthealthmonitoringsystem.data.entity.Medication
import kotlinx.coroutines.flow.Flow

/**
 * Medication (Diyanshu): medications, the dose log and the conflict check.
 * Monitoring asks this class for doses; it never uses MedicationDao directly.
 */
class MedicationRepository(
    private val medicationDao: MedicationDao,
    private val tx: TransactionRunner,
    private val clock: Clock = SystemClock
) {

    enum class DeleteResult { DELETED, DEACTIVATED, NOT_FOUND }

    // ---- Medications ----

    /** AddMedication. Returns the new medicationId, or null if the prescribing doctor is not this user's. */
    suspend fun addMedication(userId: String, medication: Medication): Long? {
        require(medication.userId == userId) { "Medication must belong to the logged-in user" }
        validate(medication)
        val doctorId = medication.doctorId
        if (doctorId != null && !medicationDao.doctorOwned(userId, doctorId)) return null
        val now = clock()
        return medicationDao.insertMedication(medication.copy(medicationId = 0, createdAt = now, updatedAt = now))
    }

    /** EditMedication. Name, dosage and intake time changes are written to AuditLog by triggers. */
    suspend fun editMedication(userId: String, medication: Medication): Boolean {
        validate(medication)
        val doctorId = medication.doctorId
        if (doctorId != null && !medicationDao.doctorOwned(userId, doctorId)) return false
        return medicationDao.updateMedication(userId, medication.copy(updatedAt = clock()))
    }

    /** DeleteMedication: deletes a medication with no doses, deactivates one that has doses. */
    suspend fun deleteMedication(userId: String, medicationId: Long): DeleteResult = tx.run {
        when {
            !medicationDao.medicationOwned(userId, medicationId) -> DeleteResult.NOT_FOUND
            medicationDao.doseCount(userId, medicationId) > 0 -> {
                medicationDao.deactivateMedication(userId, medicationId, clock())
                DeleteResult.DEACTIVATED
            }
            else -> {
                medicationDao.deleteMedication(userId, medicationId)
                DeleteResult.DELETED
            }
        }
    }

    suspend fun getMedication(userId: String, medicationId: Long): Medication? =
        medicationDao.getMedication(userId, medicationId)

    fun observeActiveMedications(userId: String): Flow<List<Medication>> = medicationDao.observeActiveMedications(userId)

    fun observeAllMedications(userId: String): Flow<List<Medication>> = medicationDao.observeAllMedications(userId)

    /** For rescheduling alarms after reboot or edit. */
    suspend fun getActiveMedications(userId: String): List<Medication> = medicationDao.getActiveMedications(userId)

    fun observeAuditLog(userId: String, medicationId: Long): Flow<List<AuditLog>> =
        medicationDao.observeAuditLog(userId, medicationId)

    suspend fun getAuditLog(userId: String, medicationId: Long): List<AuditLog> =
        medicationDao.getAuditLog(userId, medicationId)

    // ---- Doses ----

    /**
     * Creates the SCHEDULED dose row for [scheduledAt] (call this when the reminder fires, not weeks ahead:
     * dose rows can never be deleted). Idempotent: returns the existing doseId if the row is already there.
     * Returns null if the medication is not this user's or is inactive.
     */
    suspend fun scheduleDose(userId: String, medicationId: Long, scheduledAt: Long): Long? = tx.run {
        val medication = medicationDao.getMedication(userId, medicationId)
        if (medication == null || !medication.isActive) return@run null
        val id = medicationDao.insertDoseIfAbsent(
            DoseLog(userId = userId, medicationId = medicationId, scheduledAt = scheduledAt)
        )
        if (id > 0) id else medicationDao.getDoseAt(userId, medicationId, scheduledAt)?.doseId
    }

    suspend fun getDose(userId: String, doseId: Long): DoseLog? = medicationDao.getDose(userId, doseId)

    /** Mark as taken. Returns false if the dose is not this user's or is already TAKEN or MISSED. */
    suspend fun markDoseTaken(userId: String, doseId: Long): Boolean =
        medicationDao.markDose(userId, doseId, DoseStatus.TAKEN, clock()) > 0

    suspend fun markDoseMissed(userId: String, doseId: Long): Boolean =
        medicationDao.markDose(userId, doseId, DoseStatus.MISSED, clock()) > 0

    fun observeDoses(userId: String, medicationId: Long, from: Long, to: Long): Flow<List<DoseLog>> =
        medicationDao.observeDosesForMedication(userId, medicationId, from, to)

    fun observeDoseLog(userId: String, from: Long, to: Long): Flow<List<DoseWithMedication>> =
        medicationDao.observeDoseLog(userId, from, to)

    /** SCHEDULED doses whose missed window has passed. */
    suspend fun getOverdueDoses(userId: String, now: Long, windowMs: Long): List<DoseWithMedication> =
        medicationDao.getOverdueScheduledDoses(userId, now, windowMs)

    /** Marks the given doses MISSED in one statement. Already-final doses are skipped. */
    suspend fun markDosesMissed(userId: String, doseIds: List<Long>, at: Long): Int =
        if (doseIds.isEmpty()) 0 else medicationDao.markDosesMissed(userId, doseIds, at)

    // ---- Conflicts ----

    /** DetectConflict: active medications that conflict with [medicationName] (e.g. before adding it). */
    suspend fun detectConflict(userId: String, medicationName: String, excludeMedicationId: Long = 0): List<CandidateConflict> =
        medicationDao.getConflictsForCandidate(userId, ConflictRule.normalize(medicationName), excludeMedicationId)

    /** Every conflicting pair among the user's active medications. */
    suspend fun detectConflicts(userId: String): List<MedicationConflict> = medicationDao.getConflictsAmongActive(userId)

    private fun validate(medication: Medication) {
        require(medication.name.isNotBlank()) { "Medication name is required" }
        require(medication.dosage.isNotBlank()) { "Dosage is required" }
        require(medication.intakeTimeMinutes in 0 until 24 * 60) { "Intake time must be within the day" }
        val end = medication.endDate
        require(end == null || end >= medication.startDate) { "End date must not be before the start date" }
    }
}
